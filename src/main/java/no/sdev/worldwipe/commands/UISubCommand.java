package no.sdev.worldwipe.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import no.sdev.worldwipe.permissions.WorldWipePermissions;
import no.sdev.worldwipe.ui.WorldWipeDashboardUI;

import javax.annotation.Nonnull;

public class UISubCommand extends AbstractPlayerCommand {

    public UISubCommand() {
        super("ui", "Open the plugin dashboard");
        this.addAliases(new String[]{"dashboard", "gui"});
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
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        if (context.isPlayer()
                && !WorldWipePermissions.hasCommand(context.sender(), "ui")) {
            context.sendMessage(Message.raw("You don't have permission. ("
                    + WorldWipePermissions.command("ui") + ")"));
            return;
        }
        context.sendMessage(Message.raw("Opening WorldWipe Dashboard..."));

        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("Error: Could not get Player component."));
                return;
            }

            WorldWipeDashboardUI dashboardPage = new WorldWipeDashboardUI(playerRef);
            player.getPageManager().openCustomPage(ref, store, dashboardPage);
            context.sendMessage(Message.raw("Dashboard opened. Press ESC to close."));
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error opening dashboard: " + e.getMessage()));
        }
    }
}
