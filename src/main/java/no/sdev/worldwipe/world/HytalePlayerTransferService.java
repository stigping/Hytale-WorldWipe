package no.sdev.worldwipe.world;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;

import java.util.Collection;

public final class HytalePlayerTransferService implements PlayerTransferService {

    @Override
    public int transferPlayers(
            World fromWorld,
            World toWorld,
            Collection<PlayerRef> players
    ) {
        if (fromWorld == null || toWorld == null || players == null || players.isEmpty()) {
            return 0;
        }

        WorldConfig worldConfig = toWorld.getWorldConfig();
        ISpawnProvider spawnProvider = worldConfig != null ? worldConfig.getSpawnProvider() : null;

        int moved = 0;

        for (PlayerRef playerRef : players) {
            if (playerRef == null) {
                continue;
            }

            fromWorld.execute(() -> {
                var store = fromWorld.getEntityStore().getStore();
                var ref = playerRef.getReference();
                if (store == null || ref == null) {
                    return;
                }

                Transform spawn = null;
                if (spawnProvider != null) {
                    spawn = spawnProvider.getSpawnPoint(ref, store);
                }
                if (spawn == null) {
                    spawn = new Transform();
                }

                Teleport teleport = Teleport.createForPlayer(toWorld, spawn);
                store.addComponent(ref, Teleport.getComponentType(), teleport);
            });

            moved++;
        }

        return moved;
    }
}
