package no.sdev.worldwipe.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import no.sdev.worldwipe.WorldWipePlugin;
import no.sdev.worldwipe.permissions.WorldWipePermissions;

import javax.annotation.Nonnull;

public class ScheduleDisableSubCommand extends CommandBase {

    public ScheduleDisableSubCommand() {
        super("disable", "Disable scheduled wipes");
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
        if (context.isPlayer()) {
            if (!WorldWipePermissions.hasCommand(context.sender(), "schedule.disable")) {
                context.sendMessage(Message.raw("You don't have permission. ("
                        + WorldWipePermissions.command("schedule.disable") + ")"));
                return;
            }
            if (!WorldWipePermissions.hasAdmin(context.sender())) {
                context.sendMessage(Message.raw("You don't have permission. ("
                        + WorldWipePermissions.ADMIN + ")"));
                return;
            }
        }
        WorldWipePlugin plugin = WorldWipePlugin.getInstance();
        boolean updated = plugin.updateSchedulingEnabled(false);
        if (!updated) {
            context.sendMessage(Message.raw("Failed to disable scheduling."));
            return;
        }
        context.sendMessage(Message.raw("Scheduling disabled."));
    }
}
