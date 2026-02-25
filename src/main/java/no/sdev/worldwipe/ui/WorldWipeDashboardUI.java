package no.sdev.worldwipe.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import no.sdev.worldwipe.WorldWipePlugin;
import no.sdev.worldwipe.permissions.WorldWipePermissions;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;

public class WorldWipeDashboardUI extends InteractiveCustomUIPage<WorldWipeDashboardUI.UIEventData> {

    public static final String LAYOUT = "worldwipe/Dashboard.ui";
    private static final String WORLD_LIST_ITEM = "worldwipe/WorldListButton.ui";
    private static final String WORLD_LIST_LABEL = "worldwipe/WorldListLabel.ui";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final DayOfWeek DEFAULT_DAY = DayOfWeek.MONDAY;
    private static final LocalTime DEFAULT_TIME = LocalTime.of(6, 0);
    private static final String DEFAULT_ZONE = "system";
    private static final String DEFAULT_MODE = "WEEKLY";
    private static final List<String> DAY_OPTIONS = buildDayOptions();
    private static final List<String> TIME_OPTIONS = buildTimeOptions();
    private static final List<String> ZONE_OPTIONS = buildZoneOptions();
    private static final List<String> MODE_OPTIONS = buildModeOptions();
    private static final List<String> DATE_OPTIONS = buildDateOptions();
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}:\\d{2})");

    private static final String VIEW_HOME = "home";
    private static final String VIEW_SCHEDULE_LIST = "scheduleList";
    private static final String VIEW_SCHEDULE_EDITOR = "scheduleEditor";
    private static final String VIEW_PROTECTED = "protected";
    private static final String VIEW_FORCE = "force";
    private static final String VIEW_STATUS = "status";

    private final PlayerRef playerRef;
    private int refreshCount = 0;
    private String currentView = VIEW_HOME;
    private String selectedWorld;
    private String forceWorld;
    private String pendingDay;
    private String pendingDayOfMonth;
    private String pendingTime;
    private String pendingZone;
    private String pendingMode;
    private String pendingCreateWorld;
    private String protectedAddWorld;
    private String protectedRemoveWorld;
    private Boolean pendingRegenerate;

    public WorldWipeDashboardUI(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        seedDefaults();
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append(LAYOUT);

        buildScheduleDropdownEntries(cmd);
        buildScheduledWorldList(cmd, evt);
        buildProtectedWorldLists(cmd, evt);
        buildForceWorldList(cmd, evt);
        applyInitialState(cmd);

        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#RefreshButton",
            new EventData().append("Action", "refresh"),
            false
        );

        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CloseButton",
            new EventData().append("Action", "close"),
            false
        );

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NavSchedules",
                new EventData().append("Action", "navSchedule"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NavProtected",
                new EventData().append("Action", "navProtected"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NavForce",
                new EventData().append("Action", "navForce"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NavStatus",
                new EventData().append("Action", "navStatus"), false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackFromScheduleList",
                new EventData().append("Action", "navHome"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackFromScheduleEditor",
                new EventData().append("Action", "navScheduleList"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackFromProtected",
                new EventData().append("Action", "navHome"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackFromForce",
                new EventData().append("Action", "navHome"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackFromStatus",
                new EventData().append("Action", "navHome"), false);

        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ToggleScheduleButton",
            new EventData().append("Action", "toggleSchedule"),
            false
        );

        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ApplyButton",
            new EventData()
                    .append("Action", "apply")
                    .append("@Mode", "#ModeDropdown.Value")
                    .append("@Day", "#DayDropdown.Value")
                    .append("@Date", "#DateDropdown.Value")
                    .append("@Time", "#TimeDropdown.Value")
                    .append("@Zone", "#ZoneDropdown.Value")
                    .append("@Regen", "#RegenerateCheck.Value"),
            false
        );
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#RemoveButton",
            new EventData().append("Action", "remove"),
            false
        );

        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CreateWorldButton",
            new EventData()
                    .append("Action", "createWorld")
                    .append("@Value", "#CreateWorldInput.Value"),
            false
        );

        evt.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#RegenerateCheck",
            new EventData()
                    .append("Field", "regenerate")
                    .append("@Value", "#RegenerateCheck.Value"),
            false
        );

        evt.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#ModeDropdown",
            new EventData()
                    .append("Field", "mode")
                    .append("@Value", "#ModeDropdown.Value"),
            false
        );
        evt.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#DayDropdown",
            new EventData()
                    .append("Field", "day")
                    .append("@Value", "#DayDropdown.Value"),
            false
        );
        evt.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#DateDropdown",
            new EventData()
                    .append("Field", "date")
                    .append("@Value", "#DateDropdown.Value"),
            false
        );
        evt.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#TimeDropdown",
            new EventData()
                    .append("Field", "time")
                    .append("@Value", "#TimeDropdown.Value"),
            false
        );
        evt.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#ZoneDropdown",
            new EventData()
                    .append("Field", "zone")
                    .append("@Value", "#ZoneDropdown.Value"),
            false
        );

        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ProtectedAddButton",
            new EventData().append("Action", "addProtected"),
            false
        );
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ProtectedRemoveButton",
            new EventData().append("Action", "removeProtected"),
            false
        );

        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ForceWipeButton",
            new EventData().append("Action", "forceWipe"),
            false
        );
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull String data
    ) {
        UIEventData decoded = decodeEventData(data);
        if (decoded == null) {
            return;
        }
        handleDataEvent(ref, store, decoded);
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull UIEventData data
    ) {
        try {
            if (data.field != null) {
                applyFieldUpdate(data.field, data.value);
                if (data.action == null) {
                    if ("mode".equalsIgnoreCase(data.field)) {
                        refreshDashboard(false);
                    }
                    return;
                }
            }

            applyScheduleOverrides(data);

            if (data.action == null) return;

            switch (data.action) {
                case "refresh":
                    refreshCount++;
                    refreshDashboard(true);
                    updateStatus("Refreshed " + refreshCount + " time(s)!", NotificationStyle.Success, false);
                    break;

                case "apply":
                    applySchedule();
                    break;

                case "remove":
                    removeSchedule();
                    break;

                case "toggleSchedule":
                    if (!hasAdminPermission()) {
                        updateStatus("You don't have permission.", NotificationStyle.Warning, true);
                        break;
                    }
                    toggleScheduling();
                    break;

                case "createWorld":
                    if (!hasAdminPermission()) {
                        updateStatus("You don't have permission.", NotificationStyle.Warning, true);
                        break;
                    }
                    if (data.value != null) {
                        String value = sanitize(data.value);
                        if (value != null && value.startsWith("#")) {
                            value = null;
                        }
                        pendingCreateWorld = value;
                    }
                    createWorld();
                    break;

                case "addProtected":
                    if (!hasAdminPermission()) {
                        updateStatus("You don't have permission.", NotificationStyle.Warning, true);
                        break;
                    }
                    addProtectedWorld();
                    break;

                case "removeProtected":
                    if (!hasAdminPermission()) {
                        updateStatus("You don't have permission.", NotificationStyle.Warning, true);
                        break;
                    }
                    removeProtectedWorld();
                    break;

                case "forceWipe":
                    if (!hasAdminPermission()) {
                        updateStatus("You don't have permission.", NotificationStyle.Warning, true);
                        break;
                    }
                    forceWipe();
                    break;

                case "selectForceWorld":
                    selectForceWorld(data.value);
                    break;

                case "selectProtectedAddWorld":
                    selectProtectedAddWorld(data.value);
                    break;

                case "selectProtectedRemoveWorld":
                    selectProtectedRemoveWorld(data.value);
                    break;

                case "selectScheduleWorld":
                    selectScheduleWorld(data.value);
                    break;

                case "navSchedule":
                    navigateTo(VIEW_SCHEDULE_LIST);
                    break;

                case "navScheduleList":
                    navigateTo(VIEW_SCHEDULE_LIST);
                    break;

                case "navProtected":
                    if (!hasAdminPermission()) {
                        updateStatus("You don't have permission.", NotificationStyle.Warning, true);
                        break;
                    }
                    navigateTo(VIEW_PROTECTED);
                    break;

                case "navForce":
                    if (!hasAdminPermission()) {
                        updateStatus("You don't have permission.", NotificationStyle.Warning, true);
                        break;
                    }
                    navigateTo(VIEW_FORCE);
                    break;

                case "navStatus":
                    navigateTo(VIEW_STATUS);
                    break;

                case "navHome":
                    navigateTo(VIEW_HOME);
                    break;

                case "close":
                    this.close();
                    break;
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[WorldWipe] UI event handling failed.");
        }
    }

    public static class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec.builder(
                UIEventData.class, UIEventData::new
        )
        .append(new KeyedCodec<>("Action", Codec.STRING, false), (e, v) -> e.action = v, e -> e.action).add()
        .append(new KeyedCodec<>("Field", Codec.STRING, false), (e, v) -> e.field = v, e -> e.field).add()
        .append(new KeyedCodec<>("Value", Codec.STRING, false), (e, v) -> e.value = v, e -> e.value).add()
        .append(new KeyedCodec<>("Day", Codec.STRING, false), (e, v) -> e.day = v, e -> e.day).add()
        .append(new KeyedCodec<>("Date", Codec.STRING, false), (e, v) -> e.date = v, e -> e.date).add()
        .append(new KeyedCodec<>("Time", Codec.STRING, false), (e, v) -> e.time = v, e -> e.time).add()
        .append(new KeyedCodec<>("Zone", Codec.STRING, false), (e, v) -> e.zone = v, e -> e.zone).add()
        .append(new KeyedCodec<>("Mode", Codec.STRING, false), (e, v) -> e.mode = v, e -> e.mode).add()
        .append(new KeyedCodec<>("Regen", Codec.STRING, false), (e, v) -> e.regen = v, e -> e.regen).add()
        .build();

        private String action;
        private String field;
        private String value;
        private String day;
        private String date;
        private String time;
        private String zone;
        private String mode;
        private String regen;

        public UIEventData() {}

        public UIEventData(String action, String field, String value) {
            this.action = action;
            this.field = field;
            this.value = value;
        }
    }

    private void applyFieldUpdate(String field, String value) {
        if (field == null) {
            return;
        }
        String trimmed = value != null ? value.trim() : null;
        if (trimmed != null && trimmed.startsWith("#")) {
            trimmed = null;
        }
        switch (field) {
            case "day":
                pendingDay = trimmed;
                break;
            case "date":
                pendingDayOfMonth = trimmed;
                break;
            case "time":
                pendingTime = trimmed;
                break;
            case "zone":
                pendingZone = trimmed;
                break;
            case "mode":
                pendingMode = trimmed;
                break;
            case "createWorld":
                pendingCreateWorld = trimmed;
                break;
            case "regenerate":
                pendingRegenerate = parseBooleanInput(trimmed);
                break;
            default:
                break;
        }
    }

    private void applyScheduleOverrides(UIEventData data) {
        if (data == null) {
            return;
        }
        String day = sanitize(data.day);
        if (day != null && day.startsWith("#")) {
            day = null;
        }
        if (day != null) {
            pendingDay = day;
        }
        String date = sanitize(data.date);
        if (date != null && date.startsWith("#")) {
            date = null;
        }
        if (date != null) {
            pendingDayOfMonth = date;
        }
        String time = sanitize(data.time);
        if (time != null && time.startsWith("#")) {
            time = null;
        }
        if (time != null) {
            pendingTime = time;
        }
        String zone = sanitize(data.zone);
        if (zone != null && zone.startsWith("#")) {
            zone = null;
        }
        if (zone != null) {
            pendingZone = zone;
        }
        String mode = sanitize(data.mode);
        if (mode != null && mode.startsWith("#")) {
            mode = null;
        }
        if (mode != null) {
            pendingMode = mode;
        }
        String regen = sanitize(data.regen);
        if (regen != null && regen.startsWith("#")) {
            regen = null;
        }
        if (regen != null) {
            Boolean parsed = parseBooleanInput(regen);
            if (parsed != null) {
                pendingRegenerate = parsed;
            }
        }
    }

    private void applySchedule() {
        String world = sanitize(selectedWorld);
        String modeInput = sanitize(pendingMode);
        String dayInput = sanitize(pendingDay);
        String dateInput = sanitize(pendingDayOfMonth);
        String timeInput = sanitize(pendingTime);
        String zoneInput = sanitize(pendingZone);

        if (world == null) {
            updateStatus("Select a world first.", NotificationStyle.Warning, true);
            return;
        }
        if (!hasAdminPermission()) {
            updateStatus("You don't have permission.", NotificationStyle.Warning, true);
            return;
        }

        WorldWipePlugin.ScheduleMode mode = parseMode(modeInput);
        if (mode == null) {
            updateStatus("Invalid mode. Use WEEKLY, DAILY, or MONTHLY.", NotificationStyle.Warning, true);
            return;
        }

        DayOfWeek day = DEFAULT_DAY;
        Integer dayOfMonth = 1;
        if (mode == WorldWipePlugin.ScheduleMode.WEEKLY) {
            day = dayInput == null ? DEFAULT_DAY : parseDay(dayInput);
            if (day == null) {
                updateStatus("Invalid day. Use MONDAY..SUNDAY.", NotificationStyle.Warning, true);
                return;
            }
        } else if (mode == WorldWipePlugin.ScheduleMode.MONTHLY) {
            dayOfMonth = dateInput == null ? 1 : parseDayOfMonth(dateInput);
            if (dayOfMonth == null) {
                updateStatus("Invalid date. Use 1-31.", NotificationStyle.Warning, true);
                return;
            }
        }

        LocalTime time = timeInput == null ? DEFAULT_TIME : parseTime(timeInput);
        if (time == null) {
            updateStatus("Invalid time. Use HH:mm (24h).", NotificationStyle.Warning, true);
            return;
        }

        String zoneToken = zoneInput == null ? DEFAULT_ZONE : parseZone(zoneInput);
        if (zoneToken == null) {
            updateStatus("Invalid zone. Use 'system' or a valid IANA zone.", NotificationStyle.Warning, true);
            return;
        }

        WorldWipePlugin plugin = WorldWipePlugin.getInstance();
        boolean updated = plugin != null
                && plugin.updateWorldSchedule(world, mode, day, dayOfMonth, time, zoneToken);
        if (!updated) {
            updateStatus("Failed to update schedule.", NotificationStyle.Warning, true);
            return;
        }

        if (plugin != null && pendingRegenerate != null) {
            plugin.updateWorldRegenerateOnWipe(world, pendingRegenerate);
        }

        seedScheduleForWorld(world);
        refreshDashboard(true);
        updateStatus("Schedule updated for " + world + ".", NotificationStyle.Success, true);
    }

    private void removeSchedule() {
        String world = sanitize(selectedWorld);
        if (world == null) {
            updateStatus("Select a world first.", NotificationStyle.Warning, true);
            return;
        }
        if (!hasAdminPermission()) {
            updateStatus("You don't have permission.", NotificationStyle.Warning, true);
            return;
        }

        WorldWipePlugin plugin = WorldWipePlugin.getInstance();
        boolean removed = plugin != null && plugin.removeWorldSchedule(world);
        if (!removed) {
            updateStatus("No schedule found for " + world + ".", NotificationStyle.Warning, true);
            return;
        }

        seedScheduleForWorld(world);
        refreshDashboard(true);
        updateStatus("Removed schedule for " + world + ".", NotificationStyle.Success, true);
    }

    private void toggleScheduling() {
        WorldWipePlugin plugin = WorldWipePlugin.getInstance();
        if (plugin == null) {
            updateStatus("Plugin not available.", NotificationStyle.Warning, true);
            return;
        }

        boolean enabled = plugin.isSchedulingEnabled();
        boolean updated = plugin.updateSchedulingEnabled(!enabled);
        if (!updated) {
            updateStatus("Failed to toggle scheduling.", NotificationStyle.Warning, true);
            return;
        }

        String message = !enabled ? "Scheduling enabled." : "Scheduling disabled.";
        updateStatus(message, NotificationStyle.Success, true);
    }

    private void createWorld() {
        String world = sanitize(pendingCreateWorld);
        if (world == null) {
            updateStatus("World name is required.", NotificationStyle.Warning, true);
            return;
        }

        Universe universe = Universe.get();
        if (universe.getWorld(world) != null || universe.isWorldLoadable(world)) {
            updateStatus("World already exists: " + world, NotificationStyle.Warning, true);
            return;
        }

        universe.addWorld(world)
                .exceptionally(error -> {
                    LOGGER.at(Level.WARNING).withCause(error)
                            .log("[WorldWipe] Failed to create world '%s'.", world);
                    return null;
                });

        selectedWorld = world;
        pendingCreateWorld = null;
        refreshDashboard(true);
        updateStatus("Creating world: " + world, NotificationStyle.Success, true);
    }

    private void addProtectedWorld() {
        String world = sanitize(protectedAddWorld);
        if (world == null) {
            updateStatus("Select a world to add.", NotificationStyle.Warning, true);
            return;
        }

        WorldWipePlugin plugin = WorldWipePlugin.getInstance();
        boolean added = plugin != null && plugin.addProtectedWorld(world);
        if (!added) {
            updateStatus("Protected world already exists: " + world, NotificationStyle.Warning, true);
            return;
        }

        refreshDashboard(true);
        updateStatus("Added protected world: " + world, NotificationStyle.Success, true);
    }

    private void removeProtectedWorld() {
        String world = sanitize(protectedRemoveWorld);
        if (world == null) {
            updateStatus("Select a world to remove.", NotificationStyle.Warning, true);
            return;
        }

        WorldWipePlugin plugin = WorldWipePlugin.getInstance();
        boolean removed = plugin != null && plugin.removeProtectedWorld(world);
        if (!removed) {
            updateStatus("Protected world not found: " + world, NotificationStyle.Warning, true);
            return;
        }

        refreshDashboard(true);
        updateStatus("Removed protected world: " + world, NotificationStyle.Success, true);
    }

    private void forceWipe() {
        String world = sanitize(forceWorld);
        if (world == null) {
            updateStatus("Select a world first.", NotificationStyle.Warning, true);
            return;
        }

        WorldWipePlugin plugin = WorldWipePlugin.getInstance();
        if (plugin == null) {
            updateStatus("Plugin not available.", NotificationStyle.Warning, true);
            return;
        }

        WorldWipePlugin.WipeResult result = plugin.wipeWorldNow(world);
        NotificationStyle style = result.success() ? NotificationStyle.Success : NotificationStyle.Warning;
        refreshDashboard(true);
        updateStatus(result.message(), style, true);
    }

    private void selectForceWorld(String worldName) {
        String world = sanitize(worldName);
        if (world == null) {
            return;
        }
        forceWorld = world;
        refreshDashboard(true);
    }

    private void selectProtectedAddWorld(String worldName) {
        String world = sanitize(worldName);
        if (world == null) {
            return;
        }
        protectedAddWorld = world;
        refreshDashboard(true);
    }

    private void selectProtectedRemoveWorld(String worldName) {
        String world = sanitize(worldName);
        if (world == null) {
            return;
        }
        protectedRemoveWorld = world;
        refreshDashboard(true);
    }

    private void selectScheduleWorld(String worldName) {
        String world = sanitize(worldName);
        if (world == null) {
            return;
        }
        if (!hasAdminPermission()) {
            updateStatus("You don't have permission.", NotificationStyle.Warning, true);
            return;
        }
        selectedWorld = world;
        seedScheduleForWorld(world);
        currentView = VIEW_SCHEDULE_EDITOR;
        refreshDashboard(true);
    }

    private void navigateTo(String view) {
        if (VIEW_SCHEDULE_EDITOR.equals(view)) {
            if (selectedWorld == null || selectedWorld.isBlank()) {
                updateStatus("Select a world first.", NotificationStyle.Warning, true);
                return;
            }
        }

        boolean isAdmin = hasAdminPermission();
        if (!isAdmin) {
            boolean canViewSchedules = canViewAnySchedule(loadVisibleScheduleWorlds());
            if (VIEW_SCHEDULE_LIST.equals(view) && canViewSchedules) {
                currentView = VIEW_SCHEDULE_LIST;
            } else {
                currentView = VIEW_STATUS;
            }
            refreshDashboard(false);
            return;
        }

        currentView = view != null ? view : VIEW_HOME;
        refreshDashboard(false);
    }

    private void updateStatus(String message, NotificationStyle style, boolean notify) {
        UICommandBuilder cmd = new UICommandBuilder();
        applyInitialState(cmd);
        cmd.set("#StatusText.Text", message);
        this.sendUpdate(cmd, false);

        if (notify) {
            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    Message.raw("WorldWipe"),
                    Message.raw(message),
                    style
            );
        }
    }

    private void refreshDashboard(boolean rebuildList) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        if (rebuildList) {
            buildScheduleDropdownEntries(cmd);
            buildScheduledWorldList(cmd, evt);
            buildProtectedWorldLists(cmd, evt);
            buildForceWorldList(cmd, evt);
            applyInitialState(cmd);
            this.sendUpdate(cmd, evt, false);
        } else {
            applyInitialState(cmd);
            this.sendUpdate(cmd, false);
        }
    }

    private void seedDefaults() {
        pendingMode = DEFAULT_MODE;
        pendingDay = DEFAULT_DAY.name();
        pendingDayOfMonth = "1";
        pendingTime = DEFAULT_TIME.toString();
        pendingZone = DEFAULT_ZONE;
    }

    private void seedScheduleForWorld(String world) {
        seedDefaults();
        if (world == null) {
            return;
        }

        WorldWipePlugin plugin = WorldWipePlugin.getInstance();
        if (plugin == null) {
            return;
        }

        pendingRegenerate = plugin.isRegenerateOnWipe(world);

        for (WorldWipePlugin.WorldSchedule schedule : plugin.getWorldSchedules()) {
            if (schedule == null || schedule.world() == null) {
                continue;
            }
            if (schedule.world().equalsIgnoreCase(world)) {
                pendingMode = schedule.mode() != null ? schedule.mode().name() : DEFAULT_MODE;
                pendingDay = schedule.day().name();
                pendingDayOfMonth = String.valueOf(schedule.dayOfMonth());
                pendingTime = schedule.time().toString();
                ZoneId zone = schedule.zone();
                if (zone == null || zone.equals(ZoneId.systemDefault())) {
                    pendingZone = DEFAULT_ZONE;
                } else {
                    pendingZone = zone.getId();
                }
                break;
            }
        }
    }

    private void applyInitialState(UICommandBuilder cmd) {
        WorldWipePlugin plugin = WorldWipePlugin.getInstance();
        if (plugin != null) {
            cmd.set("#StatusText.Text", buildNextWipeText(plugin));
            cmd.set("#ScheduleSummary.Text", buildSummaryText(plugin));
            cmd.set("#SchedulingStatus.Text", buildSchedulingStatus(plugin));
            cmd.set("#ToggleScheduleButton.Text", plugin.isSchedulingEnabled() ? "Disable" : "Enable");
            cmd.set("#ProtectedSummaryText.Text", buildProtectedSummary(plugin));
            cmd.set("#StatusDetailsText.Text", buildStatusDetails(plugin, selectedWorld));
        }

        boolean isAdmin = hasAdminPermission();
        boolean canViewSchedules = canViewAnySchedule(loadVisibleScheduleWorlds());
        if (!isAdmin) {
            currentView = canViewSchedules ? VIEW_SCHEDULE_LIST : VIEW_STATUS;
        }

        cmd.set("#RefreshButton.Visible", isAdmin);
        cmd.set("#ToggleScheduleButton.Visible", isAdmin);
        cmd.set("#NavProtected.Visible", isAdmin);
        cmd.set("#NavForce.Visible", isAdmin);
        cmd.set("#NavSchedules.Visible", isAdmin);
        cmd.set("#NavStatus.Visible", isAdmin);
        cmd.set("#ProtectedAddButton.Visible", isAdmin);
        cmd.set("#ProtectedRemoveButton.Visible", isAdmin);
        cmd.set("#ForceWipeButton.Visible", isAdmin);
        cmd.set("#ScheduleListHint.Text", isAdmin
                ? "Select a world to view or edit a schedule."
                : "Scheduled worlds (read-only).");

        String selectedLabel = selectedWorld != null ? selectedWorld : "none";
        cmd.set("#ScheduleWorldLabel.Text", "World: " + selectedLabel);
        String forceLabel = forceWorld != null ? forceWorld : "none";
        cmd.set("#ForceWorldLabel.Text", "Target: " + forceLabel);
        cmd.set("#CurrentScheduleLabel.Text", buildCurrentScheduleText(plugin, selectedWorld));
        cmd.set("#CreateWorldInput.Value", pendingCreateWorld != null ? pendingCreateWorld : "");
        cmd.set("#CreateWorldInput.Visible", isAdmin);
        cmd.set("#CreateWorldButton.Visible", isAdmin);
        cmd.set("#CreateWorldRow.Visible", isAdmin);
        cmd.set("#ApplyButton.Visible", isAdmin);
        cmd.set("#RemoveButton.Visible", isAdmin);
        cmd.set("#RegenerateCheck.Visible", isAdmin);
        cmd.set("#RegenRow.Visible", isAdmin);
        cmd.set("#RegenerateCheck.Value", pendingRegenerate != null && pendingRegenerate);

        applyScheduleSelectionState(cmd);
        applyViewState(cmd);
    }

    private void applyViewState(UICommandBuilder cmd) {
        boolean isAdmin = hasAdminPermission();
        cmd.set("#HomeView.Visible", VIEW_HOME.equals(currentView));
        cmd.set("#ScheduleListView.Visible", VIEW_SCHEDULE_LIST.equals(currentView));
        cmd.set("#ScheduleEditorView.Visible", VIEW_SCHEDULE_EDITOR.equals(currentView) && isAdmin);
        cmd.set("#ProtectedView.Visible", VIEW_PROTECTED.equals(currentView));
        cmd.set("#ForceView.Visible", VIEW_FORCE.equals(currentView));
        cmd.set("#StatusView.Visible", VIEW_STATUS.equals(currentView));
    }

    private void buildScheduleDropdownEntries(UICommandBuilder cmd) {
        List<String> modeOptions = mergeOption(MODE_OPTIONS, pendingMode);
        List<String> dayOptions = mergeOption(DAY_OPTIONS, pendingDay);
        List<String> dateOptions = mergeOption(DATE_OPTIONS, pendingDayOfMonth);
        List<String> timeOptions = mergeOption(TIME_OPTIONS, pendingTime);
        List<String> zoneOptions = mergeOption(ZONE_OPTIONS, pendingZone);

        cmd.set("#ModeDropdown.Entries", buildDropdownEntries(modeOptions));
        cmd.set("#DayDropdown.Entries", buildDropdownEntries(dayOptions));
        cmd.set("#DateDropdown.Entries", buildDropdownEntries(dateOptions));
        cmd.set("#TimeDropdown.Entries", buildDropdownEntries(timeOptions));
        cmd.set("#ZoneDropdown.Entries", buildDropdownEntries(zoneOptions));
    }

    private void applyScheduleSelectionState(UICommandBuilder cmd) {
        String mode = normalizeDropdownValue(pendingMode, MODE_OPTIONS, DEFAULT_MODE);
        String day = normalizeDropdownValue(pendingDay, DAY_OPTIONS, DEFAULT_DAY.name());
        String dayOfMonth = normalizeDropdownValue(pendingDayOfMonth, DATE_OPTIONS, "1");
        String time = normalizeDropdownValue(pendingTime, TIME_OPTIONS, DEFAULT_TIME.toString());
        String zone = normalizeDropdownValue(pendingZone, ZONE_OPTIONS, DEFAULT_ZONE);

        pendingMode = mode;
        pendingDay = day;
        pendingDayOfMonth = dayOfMonth;
        pendingTime = time;
        pendingZone = zone;

        cmd.set("#ModeDropdown.Value", mode);
        cmd.set("#DayDropdown.Value", day);
        cmd.set("#DateDropdown.Value", dayOfMonth);
        cmd.set("#TimeDropdown.Value", time);
        cmd.set("#ZoneDropdown.Value", zone);

        boolean isDaily = "DAILY".equalsIgnoreCase(mode);
        boolean isMonthly = "MONTHLY".equalsIgnoreCase(mode);
        cmd.set("#DayRow.Visible", !isDaily && !isMonthly);
        cmd.set("#DateRow.Visible", isMonthly);
        cmd.set("#DateHint.Visible", isMonthly);
    }

    private void buildScheduledWorldList(UICommandBuilder cmd, UIEventBuilder evt) {
        List<String> loadedWorlds = loadLoadedWorldNames();
        WorldWipePlugin plugin = WorldWipePlugin.getInstance();
        List<String> protectedWorlds = plugin != null ? plugin.getProtectedWorlds() : List.of();

        LinkedHashSet<String> allWorlds = new LinkedHashSet<>(loadedWorlds);
        if (plugin != null) {
            for (WorldWipePlugin.WorldSchedule schedule : plugin.getWorldSchedules()) {
                if (schedule != null && schedule.world() != null && !schedule.world().isBlank()) {
                    allWorlds.add(schedule.world());
                }
            }
        }

        List<String> visibleWorlds = new ArrayList<>();
        for (String world : allWorlds) {
            if (!isProtectedWorld(world, protectedWorlds)) {
                if (canViewScheduleWorld(world)) {
                    visibleWorlds.add(world);
                }
            }
        }

        if (selectedWorld != null && resolveWorldName(visibleWorlds, selectedWorld) == null) {
            selectedWorld = visibleWorlds.isEmpty() ? null : visibleWorlds.get(0);
            seedScheduleForWorld(selectedWorld);
        }

        cmd.clear("#ScheduledWorldList");
        cmd.set("#ScheduledWorldListEmpty.Visible", visibleWorlds.isEmpty());

        Universe universe = Universe.get();

        boolean isAdmin = hasAdminPermission();
        String itemTemplate = isAdmin ? WORLD_LIST_ITEM : WORLD_LIST_LABEL;
        for (int i = 0; i < visibleWorlds.size(); i++) {
            String world = visibleWorlds.get(i);
            String entry = "#ScheduledWorldList[" + i + "]";
            cmd.append("#ScheduledWorldList", itemTemplate);
            String label = buildScheduleListLabel(plugin, world);
            if (universe != null) {
                boolean loaded = resolveWorldName(loadedWorlds, world) != null;
                boolean loadable = universe.isWorldLoadable(world);
                if (!loaded && !loadable) {
                    label = label + " (missing)";
                }
            }
            if (isAdmin && world.equalsIgnoreCase(selectedWorld)) {
                label = "> " + label;
            }
            if (isAdmin) {
                cmd.set(entry + " #Button.Text", label);
            } else {
                cmd.set(entry + " #Label.Text", label);
            }
            if (isAdmin) {
                evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        entry + " #Button",
                        new EventData().append("Action", "selectScheduleWorld").append("Value", world),
                        false
                );
            }
        }
    }

    private void buildProtectedWorldLists(UICommandBuilder cmd, UIEventBuilder evt) {
        if (!hasAdminPermission()) {
            cmd.clear("#ProtectedWorldList");
            cmd.clear("#ProtectedCurrentList");
            cmd.set("#ProtectedWorldListEmpty.Visible", true);
            cmd.set("#ProtectedCurrentListEmpty.Visible", true);
            protectedAddWorld = null;
            protectedRemoveWorld = null;
            return;
        }
        List<String> loaded = loadLoadedWorldNames();
        WorldWipePlugin plugin = WorldWipePlugin.getInstance();
        List<String> protectedWorlds = plugin != null ? plugin.getProtectedWorlds() : List.of();
        List<String> protectedList = new ArrayList<>();

        for (String world : protectedWorlds) {
            if (world != null && !world.isBlank()) {
                protectedList.add(world);
            }
        }

        if (protectedAddWorld == null || resolveWorldName(loaded, protectedAddWorld) == null) {
            protectedAddWorld = loaded.isEmpty() ? null : loaded.get(0);
        }

        cmd.clear("#ProtectedWorldList");
        cmd.set("#ProtectedWorldListEmpty.Visible", loaded.isEmpty());

        for (int i = 0; i < loaded.size(); i++) {
            String world = loaded.get(i);
            String entry = "#ProtectedWorldList[" + i + "]";
            cmd.append("#ProtectedWorldList", WORLD_LIST_ITEM);
            String label = world.equalsIgnoreCase(protectedAddWorld) ? "> " + world : world;
            cmd.set(entry + " #Button.Text", label);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    entry + " #Button",
                    new EventData().append("Action", "selectProtectedAddWorld").append("Value", world),
                    false
            );
        }

        if (protectedRemoveWorld == null || resolveWorldName(protectedList, protectedRemoveWorld) == null) {
            protectedRemoveWorld = protectedList.isEmpty() ? null : protectedList.get(0);
        }

        cmd.clear("#ProtectedCurrentList");
        cmd.set("#ProtectedCurrentListEmpty.Visible", protectedList.isEmpty());

        for (int i = 0; i < protectedList.size(); i++) {
            String world = protectedList.get(i);
            String entry = "#ProtectedCurrentList[" + i + "]";
            cmd.append("#ProtectedCurrentList", WORLD_LIST_ITEM);
            String label = world.equalsIgnoreCase(protectedRemoveWorld) ? "> " + world : world;
            cmd.set(entry + " #Button.Text", label);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    entry + " #Button",
                    new EventData().append("Action", "selectProtectedRemoveWorld").append("Value", world),
                    false
            );
        }
    }

    private void buildForceWorldList(UICommandBuilder cmd, UIEventBuilder evt) {
        if (!hasAdminPermission()) {
            cmd.clear("#ForceWorldList");
            cmd.set("#ForceWorldListEmpty.Visible", true);
            forceWorld = null;
            return;
        }
        List<String> loaded = loadLoadedWorldNames();
        WorldWipePlugin plugin = WorldWipePlugin.getInstance();
        List<String> protectedWorlds = plugin != null ? plugin.getProtectedWorlds() : List.of();
        List<String> worlds = new ArrayList<>();
        for (String name : loaded) {
            if (name != null && !isProtectedWorld(name, protectedWorlds)) {
                worlds.add(name);
            }
        }

        if (forceWorld == null || resolveWorldName(worlds, forceWorld) == null) {
            forceWorld = worlds.isEmpty() ? null : worlds.get(0);
        }

        cmd.clear("#ForceWorldList");
        cmd.set("#ForceWorldListEmpty.Visible", worlds.isEmpty());

        for (int i = 0; i < worlds.size(); i++) {
            String world = worlds.get(i);
            String entry = "#ForceWorldList[" + i + "]";
            cmd.append("#ForceWorldList", WORLD_LIST_ITEM);
            String label = world.equalsIgnoreCase(forceWorld) ? "> " + world : world;
            cmd.set(entry + " #Button.Text", label);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    entry + " #Button",
                    new EventData().append("Action", "selectForceWorld").append("Value", world),
                    false
            );
        }
    }

    private List<String> loadLoadedWorldNames() {
        Set<String> names = new LinkedHashSet<>();
        Universe universe = Universe.get();

        for (String name : universe.getWorlds().keySet()) {
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }

        return new ArrayList<>(names);
    }

    private List<String> loadVisibleScheduleWorlds() {
        List<String> loaded = loadLoadedWorldNames();
        WorldWipePlugin plugin = WorldWipePlugin.getInstance();
        List<String> protectedWorlds = plugin != null ? plugin.getProtectedWorlds() : List.of();

        LinkedHashSet<String> allWorlds = new LinkedHashSet<>(loaded);
        if (plugin != null) {
            for (WorldWipePlugin.WorldSchedule schedule : plugin.getWorldSchedules()) {
                if (schedule != null && schedule.world() != null && !schedule.world().isBlank()) {
                    allWorlds.add(schedule.world());
                }
            }
        }

        List<String> visibleWorlds = new ArrayList<>();
        for (String world : allWorlds) {
            if (!isProtectedWorld(world, protectedWorlds) && canViewScheduleWorld(world)) {
                visibleWorlds.add(world);
            }
        }
        return visibleWorlds;
    }

    private String resolveWorldName(List<String> worlds, String selected) {
        if (worlds == null || selected == null) {
            return null;
        }
        for (String name : worlds) {
            if (name != null && name.equalsIgnoreCase(selected)) {
                return name;
            }
        }
        return null;
    }

    private String buildSummaryText(WorldWipePlugin plugin) {
        List<String> protectedWorlds = plugin.getProtectedWorlds();
        String protectedLabel = protectedWorlds.isEmpty()
                ? "none"
                : String.join(", ", protectedWorlds);

        Instant next = plugin.getNextScheduledWipeAt();
        if (next == null) {
            return "Protected worlds: " + protectedLabel + " | Next wipe: not scheduled";
        }

        ZonedDateTime nextZoned = ZonedDateTime.ofInstant(next, ZoneId.systemDefault());
        List<String> nextWorlds = plugin.getNextScheduledWorlds();
        String nextLabel = nextWorlds.isEmpty() ? "none" : String.join(", ", nextWorlds);

        return "Protected worlds: " + protectedLabel + " | Next: " + nextZoned + " (" + nextLabel + ")";
    }

    private String buildNextWipeText(WorldWipePlugin plugin) {
        if (!plugin.isSchedulingEnabled()) {
            return "Scheduling disabled";
        }
        Instant next = plugin.getNextScheduledWipeAt();
        if (next == null) {
            return "Next wipe: Not scheduled";
        }
        ZonedDateTime nextZoned = ZonedDateTime.ofInstant(next, ZoneId.systemDefault());
        List<String> nextWorlds = plugin.getNextScheduledWorlds();
        String nextLabel = nextWorlds.isEmpty() ? "none" : String.join(", ", nextWorlds);
        return "Next wipe: " + nextZoned + " (" + nextLabel + ")";
    }

    private String buildSchedulingStatus(WorldWipePlugin plugin) {
        return plugin.isSchedulingEnabled() ? "Scheduling: Enabled" : "Scheduling: Disabled";
    }

    private String buildProtectedSummary(WorldWipePlugin plugin) {
        List<String> protectedWorlds = plugin.getProtectedWorlds();
        String label = protectedWorlds.isEmpty() ? "none" : String.join(", ", protectedWorlds);
        return "Protected worlds: " + label;
    }

    private String buildStatusDetails(WorldWipePlugin plugin, String worldName) {
        String scheduling = buildSchedulingStatus(plugin);
        String globalRegen = plugin.isRegenerateOnWipe()
                ? "Regen (global): enabled"
                : "Regen (global): disabled";
        String worldRegen = "";
        if (worldName != null) {
            worldRegen = plugin.isRegenerateOnWipe(worldName)
                    ? " | Regen (" + worldName + "): enabled"
                    : " | Regen (" + worldName + "): disabled";
        }
        Instant next = plugin.getNextScheduledWipeAt();
        String nextLabel = next == null ? "Next wipe: not scheduled"
                : "Next wipe: " + ZonedDateTime.ofInstant(next, ZoneId.systemDefault());
        return scheduling + " | " + globalRegen + worldRegen + " | " + nextLabel;
    }

    private String buildCurrentScheduleText(WorldWipePlugin plugin, String worldName) {
        if (worldName == null || plugin == null) {
            return "Current: select a world";
        }
        for (WorldWipePlugin.WorldSchedule schedule : plugin.getWorldSchedules()) {
            if (schedule == null || schedule.world() == null) {
                continue;
            }
            if (schedule.world().equalsIgnoreCase(worldName)) {
                String zoneLabel = schedule.zone() == null
                        ? DEFAULT_ZONE
                        : schedule.zone().getId();
                return "Current: " + formatScheduleLabel(schedule, zoneLabel);
            }
        }
        return "Current: not scheduled (defaults: " + DEFAULT_MODE + " " + DEFAULT_DAY + " "
                + DEFAULT_TIME + " " + DEFAULT_ZONE + ")";
    }

    private String buildScheduleListLabel(WorldWipePlugin plugin, String worldName) {
        if (worldName == null || plugin == null) {
            return worldName;
        }
        for (WorldWipePlugin.WorldSchedule schedule : plugin.getWorldSchedules()) {
            if (schedule == null || schedule.world() == null) {
                continue;
            }
            if (schedule.world().equalsIgnoreCase(worldName)) {
                String zoneLabel = schedule.zone() == null
                        ? DEFAULT_ZONE
                        : schedule.zone().getId();
                return worldName + " - " + formatScheduleLabel(schedule, zoneLabel);
            }
        }
        return worldName + " - not scheduled";
    }

    private String formatScheduleLabel(WorldWipePlugin.WorldSchedule schedule, String zoneLabel) {
        if (schedule == null) {
            return "not scheduled";
        }
        WorldWipePlugin.ScheduleMode mode = schedule.mode() != null
                ? schedule.mode()
                : WorldWipePlugin.ScheduleMode.WEEKLY;
        String time = schedule.time() != null ? schedule.time().toString() : DEFAULT_TIME.toString();
        String zone = zoneLabel != null ? zoneLabel : DEFAULT_ZONE;

        return switch (mode) {
            case DAILY -> "Daily " + time + " (" + zone + ")";
            case MONTHLY -> "Monthly " + schedule.dayOfMonth() + " " + time + " (" + zone + ")";
            case WEEKLY -> schedule.day().name() + " " + time + " (" + zone + ")";
        };
    }

    private static List<String> buildDayOptions() {
        List<String> options = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            options.add(day.name());
        }
        return options;
    }

    private static List<String> buildModeOptions() {
        List<String> options = new ArrayList<>();
        options.add("WEEKLY");
        options.add("DAILY");
        options.add("MONTHLY");
        return options;
    }

    private static List<String> buildDateOptions() {
        List<String> options = new ArrayList<>();
        for (int i = 1; i <= 31; i++) {
            options.add(String.valueOf(i));
        }
        return options;
    }

    private static List<String> buildTimeOptions() {
        List<String> options = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            options.add(String.format("%02d:00", hour));
            options.add(String.format("%02d:30", hour));
        }
        return options;
    }

    private static List<String> buildZoneOptions() {
        List<String> options = new ArrayList<>();
        options.add(DEFAULT_ZONE);
        options.add("UTC");
        options.add("Europe/London");
        options.add("Europe/Oslo");
        options.add("Europe/Paris");
        options.add("Europe/Berlin");
        options.add("Europe/Warsaw");
        options.add("Europe/Moscow");
        options.add("America/New_York");
        options.add("America/Chicago");
        options.add("America/Denver");
        options.add("America/Los_Angeles");
        options.add("America/Sao_Paulo");
        options.add("Asia/Dubai");
        options.add("Asia/Kolkata");
        options.add("Asia/Shanghai");
        options.add("Asia/Tokyo");
        options.add("Asia/Seoul");
        options.add("Australia/Sydney");
        options.add("Australia/Perth");
        options.add("Pacific/Auckland");
        return options;
    }

    private static List<DropdownEntryInfo> buildDropdownEntries(List<String> values) {
        List<DropdownEntryInfo> entries = new ArrayList<>();
        if (values == null) {
            return entries;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(value), value));
        }
        return entries;
    }

    private static String normalizeDropdownValue(String value, List<String> options, String fallback) {
        if (options == null || options.isEmpty()) {
            return fallback;
        }
        String trimmed = value != null ? value.trim() : null;
        if (trimmed == null || trimmed.isBlank()) {
            return fallback;
        }
        for (String option : options) {
            if (option != null && option.equalsIgnoreCase(trimmed)) {
                return option;
            }
        }
        return trimmed;
    }

    private static Boolean parseBooleanInput(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "1".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalized) || "0".equals(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static String resolveOptionFromIndex(String input, List<String> options) {
        if (input == null || options == null || options.isEmpty()) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        try {
            int index = Integer.parseInt(trimmed);
            if (index >= 0 && index < options.size()) {
                return options.get(index);
            }
            if (index >= 1 && index <= options.size()) {
                return options.get(index - 1);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    private static boolean isProtectedWorld(String worldName, List<String> protectedWorlds) {
        if (worldName == null || protectedWorlds == null || protectedWorlds.isEmpty()) {
            return false;
        }
        for (String protectedWorld : protectedWorlds) {
            if (protectedWorld != null && worldName.equalsIgnoreCase(protectedWorld)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> mergeOption(List<String> base, String value) {
        if (base == null) {
            return List.of();
        }
        String trimmed = value != null ? value.trim() : null;
        if (trimmed == null || trimmed.isBlank()) {
            return base;
        }
        for (String option : base) {
            if (option != null && option.equalsIgnoreCase(trimmed)) {
                return base;
            }
        }
        List<String> merged = new ArrayList<>(base.size() + 1);
        merged.add(trimmed);
        merged.addAll(base);
        return merged;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean hasAdminPermission() {
        return WorldWipePermissions.hasAdmin(playerRef.getUuid());
    }

    private boolean canViewScheduleWorld(String worldName) {
        return WorldWipePermissions.canViewSchedule(playerRef.getUuid(), worldName);
    }

    private boolean canViewAnySchedule(List<String> worlds) {
        if (hasAdminPermission() || WorldWipePermissions.hasScheduleView(playerRef.getUuid())) {
            return true;
        }
        if (worlds == null || worlds.isEmpty()) {
            return false;
        }
        for (String world : worlds) {
            if (canViewScheduleWorld(world)) {
                return true;
            }
        }
        return false;
    }

    private static DayOfWeek parseDay(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String fromIndex = resolveOptionFromIndex(input, DAY_OPTIONS);
        if (fromIndex != null) {
            return DayOfWeek.valueOf(fromIndex);
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (normalized.contains(day.name())) {
                return day;
            }
        }
        try {
            return DayOfWeek.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static WorldWipePlugin.ScheduleMode parseMode(String input) {
        if (input == null || input.isBlank()) {
            return WorldWipePlugin.ScheduleMode.WEEKLY;
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT);
        for (String option : MODE_OPTIONS) {
            if (option.equalsIgnoreCase(normalized) || normalized.contains(option)) {
                return WorldWipePlugin.ScheduleMode.valueOf(option);
            }
        }
        try {
            return WorldWipePlugin.ScheduleMode.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer parseDayOfMonth(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        try {
            int value = Integer.parseInt(trimmed);
            return value >= 1 && value <= 31 ? value : null;
        } catch (NumberFormatException ignored) {
        }
        Matcher matcher = Pattern.compile("(\\d{1,2})").matcher(trimmed);
        if (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                return value >= 1 && value <= 31 ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static LocalTime parseTime(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String fromIndex = resolveOptionFromIndex(input, TIME_OPTIONS);
        if (fromIndex != null) {
            return LocalTime.parse(fromIndex);
        }
        String trimmed = input.trim();
        try {
            return LocalTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }
        Matcher matcher = TIME_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            try {
                return LocalTime.parse(matcher.group(1));
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String parseZone(String input) {
        if (input == null || input.isBlank() || "system".equalsIgnoreCase(input.trim())) {
            return DEFAULT_ZONE;
        }
        String fromIndex = resolveOptionFromIndex(input, ZONE_OPTIONS);
        if (fromIndex != null) {
            return fromIndex;
        }
        String trimmed = input.trim();
        try {
            ZoneId.of(trimmed);
            return trimmed;
        } catch (Exception ignored) {
        }
        String[] tokens = trimmed.split("\\s+");
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String candidate = token.replace(",", "");
            if ("system".equalsIgnoreCase(candidate)) {
                return DEFAULT_ZONE;
            }
            try {
                ZoneId.of(candidate);
                return candidate;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private UIEventData decodeEventData(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            BsonDocument document = BsonDocument.parse(data);
            String action = readString(document, "Action");
            String field = readString(document, "Field");
            String value = readString(document, "Value");
            if (value == null) {
                value = readString(document, "@Value");
            }
            String day = readString(document, "Day");
            if (day == null) {
                day = readString(document, "@Day");
            }
            String date = readString(document, "Date");
            if (date == null) {
                date = readString(document, "@Date");
            }
            String time = readString(document, "Time");
            if (time == null) {
                time = readString(document, "@Time");
            }
            String zone = readString(document, "Zone");
            if (zone == null) {
                zone = readString(document, "@Zone");
            }
            String mode = readString(document, "Mode");
            if (mode == null) {
                mode = readString(document, "@Mode");
            }
            String regen = readString(document, "Regen");
            if (regen == null) {
                regen = readString(document, "@Regen");
            }
            if (value == null) {
                value = readString(document, "Text");
            }

            if (action == null && field == null && value == null && day == null && date == null
                    && time == null && zone == null && mode == null && regen == null) {
                return null;
            }
            UIEventData eventData = new UIEventData(action, field, value);
            eventData.day = day;
            eventData.date = date;
            eventData.time = time;
            eventData.zone = zone;
            eventData.mode = mode;
            eventData.regen = regen;
            return eventData;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[WorldWipe] Failed to parse UI event data: %s", data);
            return null;
        }
    }

    private static String readString(BsonDocument document, String key) {
        if (document == null || key == null || !document.containsKey(key)) {
            return null;
        }
        return bsonValueToString(document.get(key));
    }

    private static String bsonValueToString(BsonValue value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isString()) {
            return value.asString().getValue();
        }
        if (value.isBoolean()) {
            return String.valueOf(value.asBoolean().getValue());
        }
        if (value.isInt32()) {
            return String.valueOf(value.asInt32().getValue());
        }
        if (value.isInt64()) {
            return String.valueOf(value.asInt64().getValue());
        }
        if (value.isDouble()) {
            return String.valueOf(value.asDouble().getValue());
        }
        if (value.isDocument()) {
            BsonDocument doc = value.asDocument();
            String text = readString(doc, "Text");
            if (text != null) {
                return text;
            }
            String nestedValue = readString(doc, "Value");
            if (nestedValue != null) {
                return nestedValue;
            }
            String selection = readString(doc, "Selection");
            if (selection != null) {
                return selection;
            }
            String selectedIndex = readString(doc, "SelectedIndex");
            if (selectedIndex != null) {
                return selectedIndex;
            }
            String index = readString(doc, "Index");
            if (index != null) {
                return index;
            }
            String checked = readString(doc, "Checked");
            if (checked != null) {
                return checked;
            }
            return doc.toJson();
        }
        return value.toString();
    }
}
