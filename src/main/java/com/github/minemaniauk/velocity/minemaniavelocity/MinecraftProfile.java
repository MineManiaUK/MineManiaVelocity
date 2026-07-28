package com.github.minemaniauk.velocity.minemaniavelocity;

import java.util.UUID;

public final class MinecraftProfile {

    private final UUID uniqueId;
    private final String name;

    public MinecraftProfile(UUID uniqueId, String name) {
        this.uniqueId = uniqueId;
        this.name = name;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public String getName() {
        return name;
    }
}
