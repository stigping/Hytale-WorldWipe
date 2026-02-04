package no.sdev.worldwipe.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import no.sdev.worldwipe.WorldWipePlugin;
import no.sdev.worldwipe.permissions.WorldWipePermissions;

import javax.annotation.Nonnull;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public class ScheduleSetSubCommand extends CommandBase {

    private static final DayOfWeek DEFAULT_DAY = DayOfWeek.MONDAY;
    private static final LocalTime DEFAULT_TIME = LocalTime.of(6, 0);
    private static final String DEFAULT_ZONE = "system";

    private final RequiredArg<String> worldArg;
    private final OptionalArg<String> modeArg;
    private final OptionalArg<String> dayArg;
    private final OptionalArg<String> timeArg;
    private final OptionalArg<String> zoneArg;

    public ScheduleSetSubCommand() {
        super("set", "Set the scheduled wipe for a world");
        this.setPermissionGroup(null);

        this.worldArg = this.withRequiredArg("world", "World name", ArgTypes.STRING);
        this.modeArg = this.withOptionalArg("mode", "Mode (WEEKLY, DAILY, MONTHLY)", ArgTypes.STRING);
        this.dayArg = this.withOptionalArg("dayOrDate", "Day (MONDAY..SUNDAY) or date (1-31)", ArgTypes.STRING);
        this.timeArg = this.withOptionalArg("time", "Time (HH:mm)", ArgTypes.STRING);
        this.zoneArg = this.withOptionalArg("zone", "Zone ID or 'system'", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    public Message getUsageString(CommandSender sender) {
        if (sender != null && !sender.hasPermission(WorldWipePermissions.HELP)) {
            return Message.raw("Unknown command.");
        }
        return super.getUsageString(sender);
    }

    @Override
    public Message getUsageShort(CommandSender sender, boolean includeArgs) {
        if (sender != null && !sender.hasPermission(WorldWipePermissions.HELP)) {
            return Message.raw("Unknown command.");
        }
        return super.getUsageShort(sender, includeArgs);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        WorldWipePlugin plugin = WorldWipePlugin.getInstance();

        String world = context.get(worldArg);
        String modeInput = context.provided(modeArg) ? context.get(modeArg) : null;
        String dayInput = context.provided(dayArg) ? context.get(dayArg) : null;
        String timeInput = context.provided(timeArg) ? context.get(timeArg) : null;
        String zoneInput = context.provided(zoneArg) ? context.get(zoneArg) : null;

        if (context.isPlayer()) {
            if (!WorldWipePermissions.hasCommand(context.sender(), "schedule.set")) {
                context.sendMessage(Message.raw("You don't have permission. ("
                        + WorldWipePermissions.command("schedule.set") + ")"));
                return;
            }
            if (!WorldWipePermissions.hasAdmin(context.sender())) {
                context.sendMessage(Message.raw("You don't have permission. ("
                        + WorldWipePermissions.ADMIN + ")"));
                return;
            }
        }
        if (world == null || world.isBlank()) {
            context.sendMessage(Message.raw("World name is required."));
            return;
        }

        WorldWipePlugin.ScheduleMode mode = parseMode(modeInput);
        String resolvedDayInput = dayInput;
        String resolvedTimeInput = timeInput;
        String resolvedZoneInput = zoneInput;

        if (mode == null) {
            mode = WorldWipePlugin.ScheduleMode.WEEKLY;
            resolvedDayInput = modeInput;
            resolvedTimeInput = dayInput;
            resolvedZoneInput = timeInput;
        }

        DayOfWeek day = DEFAULT_DAY;
        Integer dayOfMonth = 1;

        if (mode == WorldWipePlugin.ScheduleMode.WEEKLY) {
            day = resolvedDayInput == null ? DEFAULT_DAY : parseDay(resolvedDayInput);
            if (day == null) {
                context.sendMessage(Message.raw("Invalid day. Use MONDAY..SUNDAY."));
                return;
            }
        } else if (mode == WorldWipePlugin.ScheduleMode.MONTHLY) {
            dayOfMonth = resolvedDayInput == null ? 1 : parseDayOfMonth(resolvedDayInput);
            if (dayOfMonth == null) {
                context.sendMessage(Message.raw("Invalid date. Use 1-31."));
                return;
            }
        } else if (mode == WorldWipePlugin.ScheduleMode.DAILY) {
            resolvedTimeInput = resolvedDayInput;
            resolvedZoneInput = timeInput;
        }

        LocalTime time = resolvedTimeInput == null ? DEFAULT_TIME : parseTime(resolvedTimeInput);
        if (time == null) {
            context.sendMessage(Message.raw("Invalid time. Use HH:mm (24h)."));
            return;
        }

        String zoneToken = resolvedZoneInput == null ? DEFAULT_ZONE : parseZone(resolvedZoneInput);
        if (zoneToken == null) {
            context.sendMessage(Message.raw("Invalid zone. Use 'system' or a valid IANA zone (e.g. Europe/Oslo)."));
            return;
        }

        boolean updated = plugin.updateWorldSchedule(world, mode, day, dayOfMonth, time, zoneToken);
        if (!updated) {
            context.sendMessage(Message.raw("Failed to update schedule for world: " + world));
            return;
        }

        if (isProtectedWorld(world, plugin.getProtectedWorlds())) {
            context.sendMessage(Message.raw("Note: " + world + " is protected and will never wipe."));
        }

        context.sendMessage(Message.raw(buildSummary(world, mode, day, dayOfMonth, time, zoneToken)));
    }

    private String buildSummary(
            String world,
            WorldWipePlugin.ScheduleMode mode,
            DayOfWeek day,
            Integer dayOfMonth,
            LocalTime time,
            String zone
    ) {
        String timeLabel = time != null ? time.toString() : DEFAULT_TIME.toString();
        String zoneLabel = zone != null ? zone : DEFAULT_ZONE;
        return switch (mode) {
            case DAILY -> "Schedule updated: " + world + " -> DAILY @ " + timeLabel + " (" + zoneLabel + ")";
            case MONTHLY -> "Schedule updated: " + world + " -> MONTHLY " + dayOfMonth + " @ "
                    + timeLabel + " (" + zoneLabel + ")";
            case WEEKLY -> "Schedule updated: " + world + " -> " + day + " @ " + timeLabel + " (" + zoneLabel + ")";
        };
    }

    private static DayOfWeek parseDay(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return DayOfWeek.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer parseDayOfMonth(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(input.trim());
            return value >= 1 && value <= 31 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static WorldWipePlugin.ScheduleMode parseMode(String input) {
        if (input == null || input.isBlank()) {
            return WorldWipePlugin.ScheduleMode.WEEKLY;
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT);
        try {
            return WorldWipePlugin.ScheduleMode.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalTime parseTime(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(input.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String parseZone(String input) {
        if (input == null || input.isBlank() || "system".equalsIgnoreCase(input.trim())) {
            return "system";
        }
        String zone = input.trim();
        try {
            ZoneId.of(zone);
            return zone;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isProtectedWorld(String worldName, List<String> protectedWorlds) {
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
}
