package no.sdev.worldwipe.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import no.sdev.worldwipe.WorldWipePlugin;
import no.sdev.worldwipe.permissions.WorldWipePermissions;

import javax.annotation.Nonnull;

public class ReloadSubCommand extends CommandBase {

    public ReloadSubCommand() {
        super("reload", "Reload plugin configuration");
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
            if (!WorldWipePermissions.hasCommand(context.sender(), "reload")) {
                context.sendMessage(Message.raw("You don't have permission. ("
                        + WorldWipePermissions.command("reload") + ")"));
                return;
            }
            if (!WorldWipePermissions.hasAdmin(context.sender())) {
                context.sendMessage(Message.raw("You don't have permission. ("
                        + WorldWipePermissions.ADMIN + ")"));
                return;
            }
        }
        WorldWipePlugin plugin = WorldWipePlugin.getInstance();

        if (plugin == null) {
            context.sendMessage(Message.raw("Error: Plugin not loaded"));
            return;
        }

        context.sendMessage(Message.raw("Reloading WorldWipe..."));

        boolean reloaded = plugin.reloadConfig();

        if (reloaded) {
            context.sendMessage(Message.raw("WorldWipe reloaded successfully!"));
        } else {
            context.sendMessage(Message.raw("WorldWipe reload failed. Check server logs."));
        }
    }
}
