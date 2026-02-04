package no.sdev.worldwipe.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import no.sdev.worldwipe.WorldWipePlugin;
import no.sdev.worldwipe.permissions.WorldWipePermissions;

import javax.annotation.Nonnull;
import java.util.List;

public class ScheduleListSubCommand extends CommandBase {

    public ScheduleListSubCommand() {
        super("list", "List scheduled wipes");
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
        WorldWipePlugin plugin = WorldWipePlugin.getInstance();
        List<WorldWipePlugin.WorldSchedule> schedules = plugin.getWorldSchedules();

        if (context.isPlayer()) {
            if (!WorldWipePermissions.hasCommand(context.sender(), "schedule.list")) {
                context.sendMessage(Message.raw("You don't have permission. ("
                        + WorldWipePermissions.command("schedule.list") + ")"));
                return;
            }
            if (!WorldWipePermissions.hasAdmin(context.sender())
                    && !WorldWipePermissions.hasScheduleView(context.sender())) {
                context.sendMessage(Message.raw("You don't have permission. ("
                        + WorldWipePermissions.SCHEDULE + ")"));
                return;
            }
        }

        context.sendMessage(Message.raw(""));
        context.sendMessage(Message.raw("=== WorldWipe Schedules ==="));
        context.sendMessage(Message.raw("Scheduling enabled: " + plugin.isSchedulingEnabled()));

        if (schedules.isEmpty()) {
            context.sendMessage(Message.raw("No scheduled worlds."));
            context.sendMessage(Message.raw("=========================="));
            return;
        }

        boolean anyVisible = false;
        for (WorldWipePlugin.WorldSchedule schedule : schedules) {
            if (schedule == null) {
                continue;
            }
            if (context.isPlayer() && !WorldWipePermissions.canViewSchedule(context.sender(), schedule.world())) {
                continue;
            }
            String zone = schedule.zone() != null ? schedule.zone().toString() : "system";
            String label = formatSchedule(schedule, zone);
            context.sendMessage(Message.raw(
                    schedule.world() + ": " + label
            ));
            anyVisible = true;
        }

        if (!anyVisible) {
            context.sendMessage(Message.raw("No scheduled worlds (permission)."));
        }

        context.sendMessage(Message.raw("=========================="));
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
