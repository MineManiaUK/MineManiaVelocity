package com.github.minemaniauk.velocity.minemaniavelocity.commands;

import com.github.minemaniauk.velocity.minemaniavelocity.MineManiaVelocity;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ReloadServerCommands implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
        boolean success = MineManiaVelocity.getInstance().loadServerCommands();
        if (success) {
            invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&a&l> &aReloaded &7all server commands"));
        }
        else {
            invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c&l> &cSomething went wrong reloading server commands. Check console logs"));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("minemaniavelocity.reload.servers");
    }
}
