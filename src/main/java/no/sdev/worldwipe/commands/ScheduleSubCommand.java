package no.sdev.worldwipe.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import no.sdev.worldwipe.permissions.WorldWipePermissions;

public class ScheduleSubCommand extends AbstractCommandCollection {

    public ScheduleSubCommand() {
        super("schedule", "Manage scheduled wipes");

        this.addSubCommand(new ScheduleSetSubCommand());
        this.addSubCommand(new ScheduleRemoveSubCommand());
        this.addSubCommand(new ScheduleListSubCommand());
        this.addSubCommand(new ScheduleEnableSubCommand());
        this.addSubCommand(new ScheduleDisableSubCommand());
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
    public Message getFullUsage(CommandSender sender) {
        if (sender != null && !sender.hasPermission(WorldWipePermissions.HELP)) {
            return Message.raw("Unknown command.");
        }
        return super.getFullUsage(sender);
    }
}
