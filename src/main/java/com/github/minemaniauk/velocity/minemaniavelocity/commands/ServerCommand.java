package com.github.minemaniauk.velocity.minemaniavelocity.commands;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.concurrent.CompletableFuture;

public class ServerCommand implements SimpleCommand {

    public final String name;
    public final RegisteredServer server;
    public final CommandManager cm;

    public ServerCommand(String name, RegisteredServer server, CommandManager cm) {
        this.name = name;
        this.server = server;
        this.cm = cm;
        this.cm.register(this.cm.metaBuilder(name).build(), this);
    }

    public void unregister() {
        this.cm.unregister(name);
    }

    @Override
    public void execute(Invocation invocation) {
        if (invocation.source() instanceof Player p) {
            p.createConnectionRequest(server)
                    .connect()
                    .thenAccept(result -> {
                        ConnectionRequestBuilder.Status status = result.getStatus();

                        switch (status) {
                            case ALREADY_CONNECTED -> p.sendMessage(
                                    LegacyComponentSerializer.legacyAmpersand().deserialize("&c&l> &cYou are already connected to that server.")
                            );

                            case CONNECTION_IN_PROGRESS -> p.sendMessage(
                                    LegacyComponentSerializer.legacyAmpersand().deserialize("&c&l> &cA connection is already in progress.")
                            );

                            case CONNECTION_CANCELLED -> p.sendMessage(
                                    LegacyComponentSerializer.legacyAmpersand().deserialize("&c&l> &cThe connection was cancelled.")
                            );

                            case SERVER_DISCONNECTED -> p.sendMessage(
                                    LegacyComponentSerializer.legacyAmpersand().deserialize("&c&l> &cThe destination server disconnected you.")
                            );
                        }
                    })
                    .exceptionally(throwable -> {
                        throwable.printStackTrace();

                        p.sendMessage(
                                LegacyComponentSerializer.legacyAmpersand().deserialize("&c&l> &cCould not connect to the server.")
                        );

                        return null;
                    });
        }
        else {
            invocation.source().sendPlainMessage("This command can only be used by a player");
        }
    }
}
