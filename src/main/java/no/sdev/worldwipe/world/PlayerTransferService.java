package no.sdev.worldwipe.world;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.Collection;

public interface PlayerTransferService {

    int transferPlayers(
            World fromWorld,
            World toWorld,
            Collection<PlayerRef> players
    );
}
