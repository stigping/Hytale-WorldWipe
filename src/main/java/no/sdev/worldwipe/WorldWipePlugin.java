package no.sdev.worldwipe;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import no.sdev.worldwipe.commands.WorldWipePluginCommand;
import no.sdev.worldwipe.config.WorldWipeConfig;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class WorldWipePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static WorldWipePlugin instance;

    private static final DayOfWeek DEFAULT_SCHEDULE_DAY = DayOfWeek.MONDAY;
    private static final LocalTime DEFAULT_SCHEDULE_TIME = LocalTime.of(6, 0);
    private static final int DEFAULT_SCHEDULE_DAY_OF_MONTH = 1;

    public enum ScheduleMode {
        WEEKLY,
        DAILY,
        MONTHLY
    }

    private static final String DEFAULT_PROTECTED_WORLD = "default";
    private static final String DEFAULT_RESET_WORLD = "resource";

    public record WorldSchedule(
            String world,
            ScheduleMode mode,
            DayOfWeek day,
            int dayOfMonth,
            LocalTime time,
            ZoneId zone
    ) {
    }

    public record WipeResult(
            boolean success,
            String message
    ) {
    }

    private record ScheduleSpec(
            ScheduleMode mode,
            DayOfWeek day,
            int dayOfMonth,
            LocalTime time,
            ZoneId zone
    ) {
    }

    private final AtomicBoolean wipeInProgress = new AtomicBoolean(false);

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledWipeFuture;
    private volatile Instant nextScheduledWipeAt;

    private volatile List<String> protectedWorlds = new ArrayList<>(List.of(DEFAULT_PROTECTED_WORLD));
    private volatile List<WorldSchedule> worldSchedules = new ArrayList<>();
    private volatile List<String> nextScheduledWorlds = new ArrayList<>();
    private volatile boolean schedulingEnabled = false;
    private volatile boolean regenerateOnWipe = false;
    private volatile HashMap<String, Boolean> worldRegenerateOnWipe = new HashMap<>();
    private volatile HashMap<String, Instant> worldLastWipe = new HashMap<>();

    public WorldWipePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static WorldWipePlugin getInstance() {
        return instance;
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("[WorldWipe] Setting up...");

        loadConfig();

        registerCommands();
        LOGGER.at(Level.INFO).log("[WorldWipe] Setup complete!");
    }

    @Override
    protected void start() {
        LOGGER.at(Level.INFO).log("[WorldWipe] Started!");
        LOGGER.at(Level.INFO).log("[WorldWipe] Use /wipe help for commands");

        loadConfig();
        startSchedulerIfNeeded();
        catchUpMissedWipes();
        scheduleNextAutomaticWipe();
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("[WorldWipe] Shutting down...");
        stopScheduler();
        instance = null;
    }

    private void registerCommands() {
        try {
            getCommandRegistry().registerCommand(new WorldWipePluginCommand());
            LOGGER.at(Level.INFO).log("[WorldWipe] Registered /wipe command");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[WorldWipe] Failed to register commands");
        }
    }
    public Instant getNextScheduledWipeAt() {
        return nextScheduledWipeAt;
    }

    public String getScheduledTargetWorld() {
        List<String> resetWorlds = getResetWorlds();
        if (resetWorlds.isEmpty()) {
            return DEFAULT_RESET_WORLD;
        }
        return resetWorlds.get(0);
    }

    public List<String> getProtectedWorlds() {
        return protectedWorlds == null ? List.of() : new ArrayList<>(protectedWorlds);
    }

    public String getPrimaryProtectedWorld() {
        List<String> worlds = getProtectedWorlds();
        if (worlds.isEmpty()) {
            return DEFAULT_PROTECTED_WORLD;
        }
        for (String world : worlds) {
            if (world != null && world.equalsIgnoreCase(DEFAULT_PROTECTED_WORLD)) {
                return world;
            }
        }
        return worlds.get(0);
    }

    public List<String> getResetWorlds() {
        List<String> worlds = new ArrayList<>();
        if (worldSchedules == null) {
            return worlds;
        }
        for (WorldSchedule schedule : worldSchedules) {
            if (schedule == null || schedule.world() == null || schedule.world().isBlank()) {
                continue;
            }
            worlds.add(schedule.world());
        }
        return worlds;
    }

    public List<WorldSchedule> getWorldSchedules() {
        return worldSchedules == null ? List.of() : new ArrayList<>(worldSchedules);
    }

    public List<String> getNextScheduledWorlds() {
        return nextScheduledWorlds == null ? List.of() : new ArrayList<>(nextScheduledWorlds);
    }

    public boolean isSchedulingEnabled() {
        return schedulingEnabled;
    }

    public boolean isRegenerateOnWipe() {
        return regenerateOnWipe;
    }

    public boolean isRegenerateOnWipe(String worldName) {
        return shouldRegenerateWorld(worldName);
    }

    private void runScheduledWipeTick(List<String> scheduledWorlds) {
        if (!wipeInProgress.compareAndSet(false, true)) {
            LOGGER.at(Level.WARNING).log("[WorldWipe] Scheduled wipe skipped: wipe already in progress.");
            return;
        }

        boolean startedCleanup = false;
        try {
            if (!schedulingEnabled) {
                LOGGER.at(Level.INFO).log("[WorldWipe] Scheduled wipe skipped: scheduling disabled.");
                return;
            }
            List<String> targets = scheduledWorlds == null ? List.of() : scheduledWorlds;
            LOGGER.at(Level.INFO).log(
                    "[WorldWipe] Scheduled wipe trigger fired for worlds: " + formatWorldList(targets) + "."
            );

            if (targets.isEmpty()) {
                LOGGER.at(Level.WARNING).log("[WorldWipe] No reset worlds configured. Skipping scheduled wipe.");
                return;
            }

            World destinationWorld = resolveDestinationWorld();
            if (destinationWorld == null) {
                LOGGER.at(Level.WARNING).log("[WorldWipe] Scheduled wipe skipped: destination world unavailable.");
                return;
            }

            for (String targetWorldName : targets) {
                if (executeWipeForWorld(targetWorldName, destinationWorld)) {
                    startedCleanup = true;
                }
            }

        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[WorldWipe] Scheduled wipe trigger failed.");
        } finally {
            if (!startedCleanup) {
                wipeInProgress.set(false);
            }
        }
    }

    private World resolveDestinationWorld() {
        Universe universe = Universe.get();
        String destinationWorldName = getPrimaryProtectedWorld();
        World destinationWorld = destinationWorldName != null
                ? universe.getWorld(destinationWorldName)
                : null;
        if (destinationWorld == null) {
            destinationWorld = universe.getDefaultWorld();
        }
        return destinationWorld;
    }

    private boolean executeWipeForWorld(String targetWorldName, World destinationWorld) {
        if (targetWorldName == null || targetWorldName.isBlank()) {
            return false;
        }
        List<String> protectedList = getProtectedWorlds();
        if (isProtectedWorld(targetWorldName, protectedList)) {
            LOGGER.at(Level.INFO).log(
                    "[WorldWipe] Skipping protected world '%s' in scheduled wipe.",
                    targetWorldName
            );
            return false;
        }
        if (destinationWorld == null) {
            return false;
        }
        if (destinationWorld.getName().equalsIgnoreCase(targetWorldName)) {
            LOGGER.at(Level.WARNING).log(
                    "[WorldWipe] Skipping scheduled wipe: target '%s' is the destination world.",
                    targetWorldName
            );
            return false;
        }

        Universe universe = Universe.get();
        boolean shouldRegenerate = shouldRegenerateWorld(targetWorldName);
        World resettingWorld = universe.getWorld(targetWorldName);
        WorldConfig regenerationTemplate = captureRegenerationTemplate(targetWorldName, resettingWorld, shouldRegenerate);

        if (resettingWorld != null) {
            no.sdev.worldwipe.world.WorldEvacuationService.evacuate(resettingWorld, destinationWorld);
            scheduleWorldCleanup(targetWorldName, shouldRegenerate, 0, regenerationTemplate);
            return true;
        }

        if (shouldRegenerate || universe.isWorldLoadable(targetWorldName)) {
            scheduleWorldCleanup(targetWorldName, shouldRegenerate, 0, regenerationTemplate);
            return true;
        }

        if (!shouldRegenerate) {
            removeWorldSchedule(targetWorldName);
        }
        return false;
    }

    private void catchUpMissedWipes() {
        if (!schedulingEnabled) {
            return;
        }
        List<WorldSchedule> schedules = getWorldSchedules();
        if (schedules.isEmpty()) {
            return;
        }

        List<String> missedWorlds = new ArrayList<>();
        for (WorldSchedule schedule : schedules) {
            if (schedule == null) {
                continue;
            }
            ZoneId zone = schedule.zone() != null ? schedule.zone() : ZoneId.systemDefault();
            ZonedDateTime now = ZonedDateTime.now(zone);
            ZonedDateTime last = calculatePreviousOccurrence(
                    now,
                    schedule.mode(),
                    schedule.day(),
                    schedule.dayOfMonth(),
                    schedule.time()
            );
            if (last == null) {
                continue;
            }
            Instant lastInstant = last.toInstant();
            Instant lastWipe = getLastWipeInstant(schedule.world());
            if (lastWipe == null || lastWipe.isBefore(lastInstant)) {
                missedWorlds.add(schedule.world());
            }
        }

        if (missedWorlds.isEmpty()) {
            return;
        }

        LOGGER.at(Level.INFO).log(
                "[WorldWipe] Missed wipes detected: " + formatWorldList(missedWorlds) + ". Running now."
        );
        scheduleMissedWipeQueue(missedWorlds, 0);
    }

    private void scheduleMissedWipeQueue(List<String> worlds, int index) {
        if (worlds == null || index >= worlds.size()) {
            return;
        }
        if (scheduler == null) {
            startSchedulerIfNeeded();
        }
        if (scheduler == null) {
            return;
        }

        scheduler.schedule(() -> {
            if (!schedulingEnabled) {
                return;
            }
            if (!wipeInProgress.compareAndSet(false, true)) {
                scheduleMissedWipeQueue(worlds, index);
                return;
            }

            boolean startedCleanup = false;
            try {
                World destinationWorld = resolveDestinationWorld();
                if (destinationWorld == null) {
                    return;
                }
                String worldName = worlds.get(index);
                startedCleanup = executeWipeForWorld(worldName, destinationWorld);
            } finally {
                if (!startedCleanup) {
                    wipeInProgress.set(false);
                }
                scheduleMissedWipeQueue(worlds, index + 1);
            }
        }, 1000L, TimeUnit.MILLISECONDS);
    }

    private ZonedDateTime calculatePreviousOccurrence(
            @Nonnull ZonedDateTime now,
            @Nonnull ScheduleMode mode,
            @Nonnull DayOfWeek day,
            int dayOfMonth,
            @Nonnull LocalTime time
    ) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(time, "time");

        ZonedDateTime candidate;

        switch (mode) {
            case DAILY -> {
                candidate = now
                        .withHour(time.getHour())
                        .withMinute(time.getMinute())
                        .withSecond(0)
                        .withNano(0);
                if (candidate.isAfter(now)) {
                    candidate = candidate.minusDays(1);
                }
                return candidate;
            }
            case MONTHLY -> {
                int targetDay = Math.max(1, Math.min(31, dayOfMonth));
                int currentMonthLength = now.toLocalDate().lengthOfMonth();
                int resolvedDay = Math.min(targetDay, currentMonthLength);
                candidate = now
                        .withDayOfMonth(resolvedDay)
                        .withHour(time.getHour())
                        .withMinute(time.getMinute())
                        .withSecond(0)
                        .withNano(0);
                if (candidate.isAfter(now)) {
                    ZonedDateTime prevMonth = now.minusMonths(1);
                    int prevMonthLength = prevMonth.toLocalDate().lengthOfMonth();
                    int prevResolved = Math.min(targetDay, prevMonthLength);
                    candidate = prevMonth
                            .withDayOfMonth(prevResolved)
                            .withHour(time.getHour())
                            .withMinute(time.getMinute())
                            .withSecond(0)
                            .withNano(0);
                }
                return candidate;
            }
            case WEEKLY -> {
                candidate = now
                        .withHour(time.getHour())
                        .withMinute(time.getMinute())
                        .withSecond(0)
                        .withNano(0);

                int deltaDays = candidate.getDayOfWeek().getValue() - day.getValue();
                if (deltaDays < 0) {
                    deltaDays += 7;
                }
                candidate = candidate.minusDays(deltaDays);

                if (candidate.isAfter(now)) {
                    candidate = candidate.minusDays(7);
                }

                return candidate;
            }
            default -> {
                return now.minusDays(7);
            }
        }
    }

    private void startSchedulerIfNeeded() {
        if (scheduler != null) return;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WorldWipe-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    private void stopScheduler() {
        if (scheduledWipeFuture != null) {
            scheduledWipeFuture.cancel(false);
            scheduledWipeFuture = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        nextScheduledWipeAt = null;
    }

    private ZonedDateTime calculateNextOccurrence(
            @Nonnull ZonedDateTime now,
            @Nonnull ScheduleMode mode,
            @Nonnull DayOfWeek day,
            int dayOfMonth,
            @Nonnull LocalTime time
    ) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(time, "time");

        ZonedDateTime candidate;

        switch (mode) {
            case DAILY -> {
                candidate = now
                        .withHour(time.getHour())
                        .withMinute(time.getMinute())
                        .withSecond(0)
                        .withNano(0);
                if (!candidate.isAfter(now)) {
                    candidate = candidate.plusDays(1);
                }
                return candidate;
            }
            case MONTHLY -> {
                int targetDay = Math.max(1, Math.min(31, dayOfMonth));
                int currentMonthLength = now.toLocalDate().lengthOfMonth();
                int resolvedDay = Math.min(targetDay, currentMonthLength);
                candidate = now
                        .withDayOfMonth(resolvedDay)
                        .withHour(time.getHour())
                        .withMinute(time.getMinute())
                        .withSecond(0)
                        .withNano(0);
                if (!candidate.isAfter(now)) {
                    ZonedDateTime nextMonth = now.plusMonths(1);
                    int nextMonthLength = nextMonth.toLocalDate().lengthOfMonth();
                    int nextResolved = Math.min(targetDay, nextMonthLength);
                    candidate = nextMonth
                            .withDayOfMonth(nextResolved)
                            .withHour(time.getHour())
                            .withMinute(time.getMinute())
                            .withSecond(0)
                            .withNano(0);
                }
                return candidate;
            }
            case WEEKLY -> {
                candidate = now
                        .withHour(time.getHour())
                        .withMinute(time.getMinute())
                        .withSecond(0)
                        .withNano(0);

                int deltaDays = day.getValue() - candidate.getDayOfWeek().getValue();
                if (deltaDays < 0) deltaDays += 7;

                candidate = candidate.plusDays(deltaDays);

                if (!candidate.isAfter(now)) {
                    candidate = candidate.plusDays(7);
                }

                return candidate;
            }
            default -> {
                return now.plusDays(7);
            }
        }
    }

    public void scheduleNextAutomaticWipe() {
        startSchedulerIfNeeded();

        if (scheduledWipeFuture != null) {
            scheduledWipeFuture.cancel(false);
            scheduledWipeFuture = null;
        }

        List<WorldSchedule> schedules = getWorldSchedules();
        Universe universe = Universe.get();

        if (!schedules.isEmpty() && universe != null) {
            List<String> pruneSchedules = new ArrayList<>();
            for (WorldSchedule schedule : schedules) {
                if (schedule == null) {
                    continue;
                }
                String worldName = schedule.world();
                boolean loaded = worldName != null && universe.getWorld(worldName) != null;
                boolean loadable = worldName != null && universe.isWorldLoadable(worldName);
                if (!loaded && !loadable && !shouldRegenerateWorld(worldName)) {
                    pruneSchedules.add(worldName);
                }
            }
            if (!pruneSchedules.isEmpty()) {
                boolean removed = removeWorldSchedulesInternal(pruneSchedules, false);
                if (removed) {
                    schedules = getWorldSchedules();
                }
            }
        }

        if (!schedulingEnabled) {
            nextScheduledWipeAt = null;
            nextScheduledWorlds = List.of();
            LOGGER.at(Level.INFO).log("[WorldWipe] Scheduling is disabled.");
            return;
        }

        if (schedules.isEmpty()) {
            nextScheduledWipeAt = null;
            nextScheduledWorlds = List.of();
            LOGGER.at(Level.INFO).log("[WorldWipe] No scheduled worlds configured.");
            return;
        }

        Instant earliestInstant = null;
        List<String> earliestWorlds = new ArrayList<>();

        for (WorldSchedule schedule : schedules) {
            if (schedule == null) {
                continue;
            }
            if (universe != null) {
                String worldName = schedule.world();
                boolean loaded = worldName != null && universe.getWorld(worldName) != null;
                boolean loadable = worldName != null && universe.isWorldLoadable(worldName);
                if (!loaded && !loadable && !shouldRegenerateWorld(worldName)) {
                    continue;
                }
            }
            ZoneId zone = schedule.zone() != null ? schedule.zone() : ZoneId.systemDefault();
            ZonedDateTime now = ZonedDateTime.now(zone);
            ZonedDateTime next = calculateNextOccurrence(
                    now,
                    schedule.mode(),
                    schedule.day(),
                    schedule.dayOfMonth(),
                    schedule.time()
            );
            Instant nextInstant = next.toInstant();

            if (earliestInstant == null || nextInstant.isBefore(earliestInstant)) {
                earliestInstant = nextInstant;
                earliestWorlds = new ArrayList<>();
                earliestWorlds.add(schedule.world());
            } else if (nextInstant.equals(earliestInstant)) {
                earliestWorlds.add(schedule.world());
            }
        }

        if (earliestInstant == null) {
            nextScheduledWipeAt = null;
            nextScheduledWorlds = List.of();
            LOGGER.at(Level.INFO).log("[WorldWipe] No valid schedules found.");
            return;
        }

        Duration delay = Duration.between(Instant.now(), earliestInstant);
        long delayMillis = Math.max(0L, delay.toMillis());

        nextScheduledWipeAt = earliestInstant;
        nextScheduledWorlds = new ArrayList<>(earliestWorlds);

        ZonedDateTime displayTime = ZonedDateTime.ofInstant(earliestInstant, ZoneId.systemDefault());
        LOGGER.at(Level.INFO).log(
                "[WorldWipe] Next scheduled wipe: " + displayTime + " (in " + delay.toMinutes()
                        + " minutes) for worlds '" + formatWorldList(earliestWorlds) + "'."
        );

        List<String> scheduledWorlds = new ArrayList<>(earliestWorlds);
        scheduledWipeFuture = scheduler.schedule(() -> {
            try {
                runScheduledWipeTick(scheduledWorlds);
            } finally {
                scheduleNextAutomaticWipe();
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    public boolean requestManualWipe(@Nonnull String targetWorld) {
        Objects.requireNonNull(targetWorld, "targetWorld");

        if (!wipeInProgress.compareAndSet(false, true)) {
            return false;
        }

        try {
            LOGGER.at(Level.INFO).log("[WorldWipe] Manual wipe requested for world '" + targetWorld + "'.");
            return true;
        } finally {
            wipeInProgress.set(false);
        }
    }

    public WipeResult wipeWorldNow(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return new WipeResult(false, "World name is required.");
        }

        if (!wipeInProgress.compareAndSet(false, true)) {
            return new WipeResult(false, "A wipe is already in progress.");
        }

        try {
            List<String> protectedList = getProtectedWorlds();
            if (isProtectedWorld(worldName, protectedList)) {
                return new WipeResult(false, "World '" + worldName + "' is protected.");
            }

            Universe universe = Universe.get();
            String destinationWorldName = getPrimaryProtectedWorld();
            World destinationWorld = destinationWorldName != null
                    ? universe.getWorld(destinationWorldName)
                    : null;
            if (destinationWorld == null) {
                destinationWorld = universe.getDefaultWorld();
            }
            if (destinationWorld == null) {
                return new WipeResult(false, "Destination world could not be resolved.");
            }
            if (destinationWorld.getName().equalsIgnoreCase(worldName)) {
                return new WipeResult(false, "Cannot wipe the destination world.");
            }

            World resettingWorld = universe.getWorld(worldName);
            if (resettingWorld == null) {
                return new WipeResult(false, "World not loaded: " + worldName);
            }

            no.sdev.worldwipe.world.WorldEvacuationService.evacuate(resettingWorld, destinationWorld);

            boolean shouldRegenerate = shouldRegenerateWorld(worldName);
            WorldConfig regenerationTemplate = captureRegenerationTemplate(worldName, resettingWorld, shouldRegenerate);
            scheduleWorldCleanup(worldName, shouldRegenerate, 0, regenerationTemplate);

            return new WipeResult(true, "Wipe started for '" + worldName + "'. Evacuating players...");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[WorldWipe] Manual wipe failed.");
            wipeInProgress.set(false);
            return new WipeResult(false, "Wipe failed: " + e.getMessage());
        }
    }

    public boolean reloadConfig() {
        try {
            loadConfig();
            scheduleNextAutomaticWipe();
            return true;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[WorldWipe] Failed to reload config.");
            return false;
        }
    }

    public boolean updateSchedulingEnabled(boolean enabled) {
        try {
            Path configPath = resolveConfigPath();
            WorldWipeConfig config = WorldWipeConfig.loadOrCreate(configPath);
            config.schedulingEnabled = enabled;
            WorldWipeConfig.writeYaml(configPath, config);

            loadConfig();
            scheduleNextAutomaticWipe();
            return true;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[WorldWipe] Failed to update scheduling enabled.");
            return false;
        }
    }

    public boolean updateWorldSchedule(
            String worldName,
            DayOfWeek day,
            LocalTime time,
            String zone
    ) {
        return updateWorldSchedule(worldName, ScheduleMode.WEEKLY, day, DEFAULT_SCHEDULE_DAY_OF_MONTH, time, zone);
    }

    public boolean updateWorldSchedule(
            String worldName,
            ScheduleMode mode,
            DayOfWeek day,
            Integer dayOfMonth,
            LocalTime time,
            String zone
    ) {
        if (worldName == null || worldName.isBlank() || mode == null || time == null) {
            return false;
        }

        String resolvedZone = (zone == null || zone.isBlank()) ? "system" : zone.trim();
        int resolvedDayOfMonth = dayOfMonth != null ? dayOfMonth : DEFAULT_SCHEDULE_DAY_OF_MONTH;
        if (resolvedDayOfMonth < 1 || resolvedDayOfMonth > 31) {
            resolvedDayOfMonth = DEFAULT_SCHEDULE_DAY_OF_MONTH;
        }
        DayOfWeek resolvedDay = day != null ? day : DEFAULT_SCHEDULE_DAY;

        try {
            Path configPath = resolveConfigPath();
            WorldWipeConfig config = WorldWipeConfig.loadOrCreate(configPath);

            if (config.worlds == null) {
                config.worlds = new HashMap<>();
            }

            WorldWipeConfig.WorldEntry entry = config.worlds.getOrDefault(worldName, new WorldWipeConfig.WorldEntry());
            if (entry.schedule == null) {
                entry.schedule = new WorldWipeConfig.Schedule();
            }
            entry.schedule.mode = mode.name();
            entry.schedule.day = resolvedDay.name();
            entry.schedule.dayOfMonth = resolvedDayOfMonth;
            entry.schedule.time = time.toString();
            entry.schedule.zone = resolvedZone;
            if (entry.lastWipe == null || entry.lastWipe.isBlank()) {
                entry.lastWipe = Instant.now().toString();
            }

            config.worlds.put(worldName, entry);
            WorldWipeConfig.writeYaml(configPath, config);

            loadConfig();
            scheduleNextAutomaticWipe();
            return true;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[WorldWipe] Failed to update schedule for world '%s'.", worldName);
            return false;
        }
    }

    public boolean updateWorldRegenerateOnWipe(String worldName, boolean regenerate) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }

        try {
            Path configPath = resolveConfigPath();
            WorldWipeConfig config = WorldWipeConfig.loadOrCreate(configPath);

            if (config.worlds == null) {
                config.worlds = new HashMap<>();
            }

            WorldWipeConfig.WorldEntry entry = config.worlds.getOrDefault(worldName, new WorldWipeConfig.WorldEntry());
            entry.regenerateOnWipe = regenerate;
            config.worlds.put(worldName, entry);
            WorldWipeConfig.writeYaml(configPath, config);

            loadConfig();
            scheduleNextAutomaticWipe();
            return true;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[WorldWipe] Failed to update regenerateOnWipe for world '%s'.", worldName);
            return false;
        }
    }

    private boolean removeWorldSchedulesInternal(List<String> worldNames, boolean reschedule) {
        if (worldNames == null || worldNames.isEmpty()) {
            return false;
        }

        List<String> normalized = new ArrayList<>();
        for (String name : worldNames) {
            if (name == null) {
                continue;
            }
            String trimmed = name.trim();
            if (!trimmed.isBlank()) {
                normalized.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }

        if (normalized.isEmpty()) {
            return false;
        }

        try {
            Path configPath = resolveConfigPath();
            WorldWipeConfig config = WorldWipeConfig.loadOrCreate(configPath);

            if (config.worlds == null || config.worlds.isEmpty()) {
                return false;
            }

            boolean changed = false;
            var iterator = config.worlds.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                String key = entry.getKey();
                if (key == null) {
                    continue;
                }
                String keyNormalized = key.trim().toLowerCase(Locale.ROOT);
                if (normalized.contains(keyNormalized)) {
                    iterator.remove();
                    changed = true;
                }
            }

            if (!changed) {
                return false;
            }

            WorldWipeConfig.writeYaml(configPath, config);
            loadConfig();
            if (reschedule) {
                scheduleNextAutomaticWipe();
            }
            return true;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[WorldWipe] Failed to remove schedules for worlds '%s'.", worldNames);
            return false;
        }
    }

    public boolean removeWorldSchedule(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        return removeWorldSchedulesInternal(List.of(worldName), true);
    }

    public boolean addProtectedWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }

        String trimmed = worldName.trim();
        try {
            Path configPath = resolveConfigPath();
            WorldWipeConfig config = WorldWipeConfig.loadOrCreate(configPath);
            List<String> worlds = config.protectedWorlds != null
                    ? new ArrayList<>(config.protectedWorlds)
                    : new ArrayList<>();

            boolean exists = worlds.stream().anyMatch(w -> w.equalsIgnoreCase(trimmed));
            if (!exists) {
                worlds.add(trimmed);
            }

            config.protectedWorlds = worlds;
            WorldWipeConfig.writeYaml(configPath, config);

            loadConfig();
            scheduleNextAutomaticWipe();
            return !exists;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[WorldWipe] Failed to add protected world '%s'.", trimmed);
            return false;
        }
    }

    public boolean removeProtectedWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }

        String trimmed = worldName.trim();
        try {
            Path configPath = resolveConfigPath();
            WorldWipeConfig config = WorldWipeConfig.loadOrCreate(configPath);
            List<String> worlds = config.protectedWorlds != null
                    ? new ArrayList<>(config.protectedWorlds)
                    : new ArrayList<>();

            boolean removed = worlds.removeIf(w -> w.equalsIgnoreCase(trimmed));
            if (!removed) {
                return false;
            }

            if (worlds.isEmpty()) {
                worlds = new ArrayList<>();
                worlds.add(DEFAULT_PROTECTED_WORLD);
            }

            config.protectedWorlds = worlds;
            WorldWipeConfig.writeYaml(configPath, config);

            loadConfig();
            scheduleNextAutomaticWipe();
            return true;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[WorldWipe] Failed to remove protected world '%s'.", trimmed);
            return false;
        }
    }

    private void loadConfig() {
        Path configPath = resolveConfigPath();
        migrateLegacyConfig(configPath);
        WorldWipeConfig config = WorldWipeConfig.loadOrCreate(configPath);

        WorldWipeConfig.EffectiveConfig effective = config.resolve();

        protectedWorlds = normalizeProtectedWorlds(effective.protectedWorlds());
        schedulingEnabled = effective.schedulingEnabled();
        regenerateOnWipe = effective.regenerateOnWipe();
        worldSchedules = normalizeWorldSchedules(effective.worlds(), protectedWorlds);
        worldRegenerateOnWipe = resolveWorldRegenerateMap(effective.worlds());
        worldLastWipe = resolveWorldLastWipeMap(effective.worlds());

        if (worldSchedules.isEmpty()) {
            LOGGER.at(Level.INFO).log("[WorldWipe] No scheduled worlds configured.");
        }

        LOGGER.at(Level.INFO).log(
                "[WorldWipe] Config loaded from %s.",
                configPath
        );
    }

    private HashMap<String, Boolean> resolveWorldRegenerateMap(
            java.util.Map<String, WorldWipeConfig.WorldEntry> worlds
    ) {
        HashMap<String, Boolean> map = new HashMap<>();
        if (worlds == null || worlds.isEmpty()) {
            return map;
        }
        for (var entry : worlds.entrySet()) {
            String worldName = entry.getKey();
            if (worldName == null || worldName.isBlank()) {
                continue;
            }
            WorldWipeConfig.WorldEntry worldEntry = entry.getValue();
            if (worldEntry == null || worldEntry.regenerateOnWipe == null) {
                continue;
            }
            map.put(worldName.trim().toLowerCase(Locale.ROOT), worldEntry.regenerateOnWipe);
        }
        return map;
    }

    private HashMap<String, Instant> resolveWorldLastWipeMap(
            java.util.Map<String, WorldWipeConfig.WorldEntry> worlds
    ) {
        HashMap<String, Instant> map = new HashMap<>();
        if (worlds == null || worlds.isEmpty()) {
            return map;
        }
        for (var entry : worlds.entrySet()) {
            String worldName = entry.getKey();
            if (worldName == null || worldName.isBlank()) {
                continue;
            }
            WorldWipeConfig.WorldEntry worldEntry = entry.getValue();
            if (worldEntry == null || worldEntry.lastWipe == null || worldEntry.lastWipe.isBlank()) {
                continue;
            }
            Instant parsed = parseInstant(worldEntry.lastWipe);
            if (parsed != null) {
                map.put(worldName.trim().toLowerCase(Locale.ROOT), parsed);
            }
        }
        return map;
    }

    private boolean shouldRegenerateWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return regenerateOnWipe;
        }
        Boolean override = worldRegenerateOnWipe.get(worldName.trim().toLowerCase(Locale.ROOT));
        return override != null ? override : regenerateOnWipe;
    }

    private Instant getLastWipeInstant(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        return worldLastWipe.get(worldName.trim().toLowerCase(Locale.ROOT));
    }

    public boolean updateWorldLastWipe(String worldName, Instant instant) {
        if (worldName == null || worldName.isBlank() || instant == null) {
            return false;
        }

        try {
            Path configPath = resolveConfigPath();
            WorldWipeConfig config = WorldWipeConfig.loadOrCreate(configPath);
            if (config.worlds == null || !config.worlds.containsKey(worldName)) {
                return false;
            }

            WorldWipeConfig.WorldEntry entry = config.worlds.get(worldName);
            if (entry == null) {
                return false;
            }
            entry.lastWipe = instant.toString();
            config.worlds.put(worldName, entry);
            WorldWipeConfig.writeYaml(configPath, config);

            loadConfig();
            return true;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[WorldWipe] Failed to update lastWipe for world '%s'.", worldName);
            return false;
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (Exception ignored) {
        }
        try {
            long epoch = Long.parseLong(trimmed);
            return Instant.ofEpochMilli(epoch);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void scheduleWorldCleanup(String worldName, boolean regenerate, int attempt, WorldConfig regenerationTemplate) {
        startSchedulerIfNeeded();

        if (scheduler == null) {
            LOGGER.at(Level.WARNING).log("[WorldWipe] Cleanup scheduler unavailable.");
            wipeInProgress.set(false);
            return;
        }

        final int maxAttempts = 15;
        final long delayMs = 1000L;

        scheduler.schedule(() -> {
            try {
                Universe universe = Universe.get();
                World resettingWorld = universe.getWorld(worldName);
                if (resettingWorld == null) {
                    boolean deleted = no.sdev.worldwipe.world.WorldEvacuationService.deleteWorldFromDisk(worldName);
                    if (!deleted) {
                        if (attempt < maxAttempts) {
                            scheduleWorldCleanup(worldName, regenerate, attempt + 1, regenerationTemplate);
                            return;
                        }
                        LOGGER.at(Level.WARNING).log("[WorldWipe] Failed to delete world '%s' from disk.", worldName);
                        wipeInProgress.set(false);
                        return;
                    }

                    if (regenerate) {
                        no.sdev.worldwipe.world.WorldRegenerationService.regenerateWorld(worldName, regenerationTemplate)
                                .exceptionally(error -> {
                                    LOGGER.at(Level.WARNING).withCause(error)
                                            .log("[WorldWipe] Failed to regenerate world '%s'.", worldName);
                                    return null;
                                });
                        updateWorldLastWipe(worldName, Instant.now());
                    } else {
                        removeWorldSchedule(worldName);
                    }

                    wipeInProgress.set(false);
                    return;
                }

                if (resettingWorld.getPlayerRefs() != null && !resettingWorld.getPlayerRefs().isEmpty()) {
                    if (attempt < maxAttempts) {
                        scheduleWorldCleanup(worldName, regenerate, attempt + 1, regenerationTemplate);
                        return;
                    }
                    LOGGER.at(Level.WARNING).log(
                            "[WorldWipe] Cleanup aborted: players still in world '%s'.",
                            worldName
                    );
                    wipeInProgress.set(false);
                    return;
                }

                no.sdev.worldwipe.world.WorldEvacuationService.unloadWorld(resettingWorld);
                boolean deleted = no.sdev.worldwipe.world.WorldEvacuationService.deleteWorldFromDisk(resettingWorld);
                if (!deleted) {
                    if (attempt < maxAttempts) {
                        scheduleWorldCleanup(worldName, regenerate, attempt + 1, regenerationTemplate);
                        return;
                    }
                    LOGGER.at(Level.WARNING).log("[WorldWipe] Failed to delete world '%s' from disk.", worldName);
                    wipeInProgress.set(false);
                    return;
                }

                if (regenerate) {
                    no.sdev.worldwipe.world.WorldRegenerationService.regenerateWorld(worldName, regenerationTemplate)
                            .exceptionally(error -> {
                                LOGGER.at(Level.WARNING).withCause(error)
                                        .log("[WorldWipe] Failed to regenerate world '%s'.", worldName);
                                return null;
                            });
                    updateWorldLastWipe(worldName, Instant.now());
                } else {
                    removeWorldSchedule(worldName);
                }
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e).log("[WorldWipe] Cleanup failed for world '%s'.", worldName);
            } finally {
                wipeInProgress.set(false);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private WorldConfig captureRegenerationTemplate(String worldName, World loadedWorld, boolean shouldRegenerate) {
        if (!shouldRegenerate || worldName == null || worldName.isBlank()) {
            return null;
        }
        try {
            if (loadedWorld != null && loadedWorld.getWorldConfig() != null) {
                return loadedWorld.getWorldConfig();
            }

            Universe universe = Universe.get();
            if (universe == null || !universe.isWorldLoadable(worldName)) {
                return null;
            }

            Path savePath = universe.getPath().resolve("worlds").resolve(worldName);
            return universe.getWorldConfigProvider().load(savePath, worldName).join();
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[WorldWipe] Could not capture regeneration template for world '%s'.", worldName);
            return null;
        }
    }

    private List<String> normalizeProtectedWorlds(List<String> worldNames) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (worldNames != null) {
            for (String world : worldNames) {
                if (world == null) {
                    continue;
                }
                String trimmed = world.trim();
                if (!trimmed.isBlank()) {
                    unique.add(trimmed);
                }
            }
        }

        if (unique.isEmpty()) {
            unique.add(DEFAULT_PROTECTED_WORLD);
        }

        return new ArrayList<>(unique);
    }

    private List<WorldSchedule> normalizeWorldSchedules(
            java.util.Map<String, WorldWipeConfig.WorldEntry> worlds,
            List<String> protectedWorldNames
    ) {
        java.util.Map<String, WorldWipeConfig.WorldEntry> baseWorlds =
                worlds != null ? worlds : new java.util.LinkedHashMap<>();

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<WorldSchedule> resolved = new ArrayList<>();

        for (var entry : baseWorlds.entrySet()) {
            String worldName = entry.getKey();
            if (worldName == null) {
                continue;
            }
            String trimmed = worldName.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (isProtectedWorld(trimmed, protectedWorldNames)) {
                continue;
            }
            if (!seen.add(trimmed)) {
                continue;
            }

            WorldWipeConfig.WorldEntry worldEntry = entry.getValue();
            ScheduleSpec spec = resolveScheduleSpec(
                    worldEntry != null ? worldEntry.schedule : null,
                    trimmed
            );

            resolved.add(new WorldSchedule(
                    trimmed,
                    spec.mode(),
                    spec.day(),
                    spec.dayOfMonth(),
                    spec.time(),
                    spec.zone()
            ));
        }

        return resolved;
    }

    private boolean isProtectedWorld(String worldName, List<String> protectedWorldNames) {
        if (worldName == null || protectedWorldNames == null || protectedWorldNames.isEmpty()) {
            return false;
        }
        for (String protectedWorld : protectedWorldNames) {
            if (protectedWorld != null && worldName.equalsIgnoreCase(protectedWorld)) {
                return true;
            }
        }
        return false;
    }

    private ScheduleSpec resolveScheduleSpec(WorldWipeConfig.Schedule schedule, String worldName) {
        ScheduleMode resolvedMode = ScheduleMode.WEEKLY;
        DayOfWeek resolvedDay = DEFAULT_SCHEDULE_DAY;
        int resolvedDayOfMonth = DEFAULT_SCHEDULE_DAY_OF_MONTH;
        LocalTime resolvedTime = DEFAULT_SCHEDULE_TIME;
        ZoneId resolvedZone = ZoneId.systemDefault();

        if (schedule != null) {
            if (schedule.mode != null && !schedule.mode.isBlank()) {
                try {
                    resolvedMode = ScheduleMode.valueOf(schedule.mode.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    LOGGER.at(Level.WARNING).log(
                            "[WorldWipe] Invalid schedule mode '%s' for world '%s'. Using WEEKLY.",
                            schedule.mode,
                            worldName
                    );
                }
            }
            if (schedule.day != null && !schedule.day.isBlank()) {
                try {
                    resolvedDay = DayOfWeek.valueOf(schedule.day.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    LOGGER.at(Level.WARNING).log(
                            "[WorldWipe] Invalid schedule day '%s' for world '%s'. Using default.",
                            schedule.day,
                            worldName
                    );
                }
            }
            if (schedule.dayOfMonth != null) {
                int value = schedule.dayOfMonth;
                if (value < 1 || value > 31) {
                    LOGGER.at(Level.WARNING).log(
                            "[WorldWipe] Invalid dayOfMonth '%s' for world '%s'. Using 1.",
                            value,
                            worldName
                    );
                } else {
                    resolvedDayOfMonth = value;
                }
            }

            if (schedule.time != null && !schedule.time.isBlank()) {
                try {
                    resolvedTime = LocalTime.parse(schedule.time.trim());
                } catch (DateTimeParseException ignored) {
                    LOGGER.at(Level.WARNING).log(
                            "[WorldWipe] Invalid schedule time '%s' for world '%s'. Using default.",
                            schedule.time,
                            worldName
                    );
                }
            }

            if (schedule.zone != null && !schedule.zone.isBlank()) {
                String zoneValue = schedule.zone.trim();
                if ("system".equalsIgnoreCase(zoneValue)) {
                    resolvedZone = ZoneId.systemDefault();
                } else {
                    try {
                        resolvedZone = ZoneId.of(zoneValue);
                    } catch (Exception ignored) {
                        LOGGER.at(Level.WARNING).log(
                                "[WorldWipe] Invalid schedule zone '%s' for world '%s'. Using system default.",
                                zoneValue,
                                worldName
                        );
                    }
                }
            }
        }

        return new ScheduleSpec(resolvedMode, resolvedDay, resolvedDayOfMonth, resolvedTime, resolvedZone);
    }

    private String formatWorldList(List<String> worlds) {
        if (worlds == null || worlds.isEmpty()) {
            return "none";
        }
        return String.join(", ", worlds);
    }

    private Path resolveConfigPath() {
        return Paths.get("mods", "WorldWipe", "config.yml");
    }

    private Path resolveLegacyConfigPath() {
        Path[] candidates = new Path[] {
                Paths.get("mods", "WorldWipe", "config.json"),
                Paths.get("server", "mods", "WorldWipe", "config.json")
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void migrateLegacyConfig(Path configPath) {
        if (configPath == null || Files.exists(configPath)) {
            return;
        }
        Path legacy = resolveLegacyConfigPath();
        if (legacy == null) {
            return;
        }

        try {
            WorldWipeConfig legacyConfig = WorldWipeConfig.loadFromJson(legacy);
            if (legacyConfig == null) {
                return;
            }
            WorldWipeConfig.writeYaml(configPath, legacyConfig);
            LOGGER.at(Level.INFO).log(
                    "[WorldWipe] Migrated legacy config from %s to %s.",
                    legacy,
                    configPath
            );
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[WorldWipe] Failed to migrate legacy config.");
        }
    }

}
