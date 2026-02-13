package no.sdev.worldwipe.world;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.concurrent.CompletableFuture;

public final class WorldRegenerationService {

    private WorldRegenerationService() {
    }

    public static CompletableFuture<World> regenerateWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("worldName cannot be null or empty");
        }

        Universe universe = Universe.get();
        return universe.addWorld(worldName, "default", "default");
    }
}
