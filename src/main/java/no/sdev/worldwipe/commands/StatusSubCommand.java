package no.sdev.worldwipe.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import no.sdev.worldwipe.WorldWipePlugin;
import no.sdev.worldwipe.permissions.WorldWipePermissions;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class StatusSubCommand extends CommandBase {

    public StatusSubCommand() {
        super("status", "Show world wipe schedule and status");
        this.setPermissionGroup(null);
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
        if (context.isPlayer()
                && !WorldWipePermissions.hasCommand(context.sender(), "status")) {
            context.sendMessage(Message.raw("You don't have permission. ("
                    + WorldWipePermissions.command("status") + ")"));
            return;
        }
        WorldWipePlugin plugin = WorldWipePlugin.getInstance();

        context.sendMessage(Message.raw(""));
        context.sendMessage(Message.raw("=== WorldWipe Status ==="));

        List<String> protectedWorlds = plugin.getProtectedWorlds();
        String protectedWorldsLabel = protectedWorlds.isEmpty()
                ? "none"
                : String.join(", ", protectedWorlds);

        List<String> resetWorlds = plugin.getResetWorlds();
        if (context.isPlayer() && !WorldWipePermissions.hasAdmin(context.sender())) {
            resetWorlds = resetWorlds.stream()
                    .filter(world -> WorldWipePermissions.canViewSchedule(context.sender(), world))
                    .toList();
        }
        String resetWorldsLabel = resetWorlds.isEmpty() ? "none" : String.join(", ", resetWorlds);

        context.sendMessage(Message.raw("Protected worlds: " + protectedWorldsLabel));
        context.sendMessage(Message.raw("Scheduling enabled: " + plugin.isSchedulingEnabled()));
        context.sendMessage(Message.raw("Reset worlds: " + resetWorldsLabel));
        List<WorldWipePlugin.WorldSchedule> schedules = plugin.getWorldSchedules();
        if (schedules.isEmpty()) {
            context.sendMessage(Message.raw("Schedules: none"));
        } else {
            context.sendMessage(Message.raw("Schedules:"));
            boolean anyVisible = false;
            for (WorldWipePlugin.WorldSchedule schedule : schedules) {
                if (schedule == null) {
                    continue;
                }
                if (context.isPlayer() && !WorldWipePermissions.canViewSchedule(context.sender(), schedule.world())) {
                    continue;
                }
                String zone = schedule.zone() != null ? schedule.zone().toString() : "system";
                context.sendMessage(Message.raw(
                        schedule.world() + ": " + formatSchedule(schedule, zone)
                ));
                anyVisible = true;
            }
            if (!anyVisible) {
                context.sendMessage(Message.raw("Schedules: none (permission)."));
            }
        }

        if (!plugin.isSchedulingEnabled()) {
            context.sendMessage(Message.raw("Next wipe: Scheduling disabled"));
            context.sendMessage(Message.raw("========================"));
            return;
        }

        Instant next = plugin.getNextScheduledWipeAt();
        if (next == null) {
            context.sendMessage(Message.raw("Next wipe: Not scheduled"));
            context.sendMessage(Message.raw("========================"));
            return;
        }

        ZonedDateTime nextZoned = ZonedDateTime.ofInstant(next, ZoneId.systemDefault());
        long minutesRemaining = Duration.between(Instant.now(), next).toMinutes();

        List<String> nextWorlds = plugin.getNextScheduledWorlds();
        if (context.isPlayer() && !WorldWipePermissions.hasAdmin(context.sender())) {
            nextWorlds = nextWorlds.stream()
                    .filter(world -> WorldWipePermissions.canViewSchedule(context.sender(), world))
                    .toList();
        }
        String nextWorldsLabel = nextWorlds.isEmpty()
                ? "none"
                : String.join(", ", nextWorlds);

        context.sendMessage(Message.raw("Next wipe: " + nextZoned));
        context.sendMessage(Message.raw("Next wipe worlds: " + nextWorldsLabel));
        context.sendMessage(Message.raw("Time remaining: " + minutesRemaining + " minutes"));
        context.sendMessage(Message.raw("========================"));
    }

    private String formatSchedule(WorldWipePlugin.WorldSchedule schedule, String zone) {
        if (schedule == null) {
            return "not scheduled";
        }
        String time = schedule.time() != null ? schedule.time().toString() : "06:00";
        String zoneLabel = zone != null ? zone : "system";
        return switch (schedule.mode()) {
            case DAILY -> "DAILY @ " + time + " (" + zoneLabel + ")";
            case MONTHLY -> "MONTHLY " + schedule.dayOfMonth() + " @ " + time + " (" + zoneLabel + ")";
            case WEEKLY -> schedule.day() + " @ " + time + " (" + zoneLabel + ")";
        };
    }
}
