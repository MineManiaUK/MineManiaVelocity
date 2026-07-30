package com.github.minemaniauk.velocity.minemaniavelocity.commands;

import com.github.minemaniauk.velocity.minemaniavelocity.MineManiaVelocity;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class SuggestCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
        if (invocation.source() instanceof Player p) {
            String message = String.join(" ", invocation.arguments());

            if (message.isEmpty()) {
                p.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c&l> &cYour suggestion must include content"));
                return;
            }

            MineManiaVelocity.getInstance().getWebhookManager().sendSuggest(p, message);
            p.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&7&l> &7Your &2suggestion &7has been sent"));
        }
        else {
            invocation.source().sendPlainMessage("This command can only be used by a player");
        }

    }
}
