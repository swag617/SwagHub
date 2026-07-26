package com.SwagDev.SwagHub.bedrock;

import java.util.UUID;

/**
 * Selected by {@link BedrockService#create} whenever Floodgate isn't present (or its
 * API failed to load reflectively) — every player is reported as Java, unconditionally.
 * This is the ENTIRE mechanism behind §8.3's "with Floodgate absent: zero behavior
 * change" acceptance test: every feature that consults {@link BedrockService} sees
 * exactly the same answer it always got when a player was never Bedrock, so no feature
 * code needs its own "is Floodgate even installed" branch.
 */
final class NoOpBedrockService implements BedrockService {

    @Override
    public boolean isBedrockPlayer(UUID uuid) {
        return false;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
