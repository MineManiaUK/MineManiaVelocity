/*
 * MineManiaChat
 * Used for interacting with the database and message broker.
 *
 * Copyright (C) 2023  MineManiaUK Staff
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.minemaniauk.velocity.minemaniavelocity;

import com.github.minemaniauk.api.MineManiaLocation;
import com.github.minemaniauk.velocity.minemaniavelocity.MineManiaVelocity;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Represents the velocity location converter.
 */
public class VelocityLocationConverter implements MineManiaLocation.LocationConverter<RegisteredServer> {

    @Override
    public @NotNull MineManiaLocation getMineManiaLocation(@NotNull RegisteredServer location) {
        return new MineManiaLocation(location.getServerInfo().getName(), "null", 0, 0, 0);
    }

    @Override
    public @NotNull RegisteredServer getLocationType(@NotNull MineManiaLocation location) {
        Optional<RegisteredServer> optional = MineManiaVelocity.getInstance().getProxyServer().getServer(location.getServerName());
        return optional.orElse(null);
    }
}