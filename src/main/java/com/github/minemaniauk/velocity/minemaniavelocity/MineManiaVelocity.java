package com.github.minemaniauk.velocity.minemaniavelocity;

import com.github.minemaniauk.api.MineManiaAPI;
import com.github.minemaniauk.api.MineManiaAPIContract;
import com.github.minemaniauk.api.kerb.event.player.PlayerChatEvent;
import com.github.minemaniauk.api.kerb.event.useraction.*;
import com.github.minemaniauk.api.user.MineManiaUser;
import com.github.squishylib.configuration.Configuration;
import com.github.squishylib.configuration.ConfigurationFactory;
import com.github.squishylib.configuration.ConfigurationSection;
import com.google.inject.Inject;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.bson.Document;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

@Plugin(
        id = "minemaniavelocity",
        name = "MineMania Velocity",
        version = "1.0",
        authors = { "MineMania Devlopment Team", "Computerwhz" },
        url = "minemania.co"

)
public class MineManiaVelocity implements MineManiaAPIContract {

    private static MineManiaVelocity instance;
    private final @NotNull Configuration config;

    private final ProxyServer proxyServer;
    private final Logger logger;

    @Inject
    public MineManiaVelocity (ProxyServer proxyServer, @DataDirectory final Path dataDirectory, Logger logger){
        this.proxyServer = proxyServer;
        this.logger = logger;
        instance = this;

        this.config = ConfigurationFactory.YAML
                .create(dataDirectory.toFile(), "config.yml")
                .setResourcePath("config.yml");
        this.config.load();


        DatabaseConnection.Connect(config.getString("database.connection_string"), config.getString("database.database_name") );

        ConfigurationSection menuServers = config.getSection("menu");

        getDataBase()
                .getCollection("MenuServers")
                .updateOne(
                        new Document("_id", "Menu_Servers"),
                        new Document("$set", new Document("menu", new Document(menuServers.getMap()))),
                        new UpdateOptions().upsert(true) // creates if doesn't exist
                );

    }

    @Override
    public @NotNull MineManiaUser getUser(@NotNull UUID uuid) {
        return new MineManiaUser(uuid, this.proxyServer.getPlayer(uuid).orElseThrow().getUsername());
    }

    @Override
    public @NotNull MineManiaUser getUser(@NotNull String name) {
        return new MineManiaUser(this.proxyServer.getPlayer(name).orElseThrow().getUniqueId(), name);
    }
    @Override
    public @Nullable UserActionHasPermissionListEvent onHasPermission(@NotNull UserActionHasPermissionListEvent event) {
        return null;
    }

    @Override
    public @Nullable UserActionIsOnlineEvent onIsOnline(@NotNull UserActionIsOnlineEvent event) {
        return null;
    }

    @Override
    public @Nullable UserActionIsVanishedEvent onIsVanished(@NotNull UserActionIsVanishedEvent event) {
        return null;
    }

    @Override
    public @Nullable UserActionTeleportEvent onTeleport(@NotNull UserActionTeleportEvent event) {
        this.getPlayer(event.getUser()).ifPresent(user -> {
            RegisteredServer registeredServer = event.getLocation().getLocation(new VelocityLocationConverter());
            user.createConnectionRequest(registeredServer).connect();
        });
        return (UserActionTeleportEvent) event.setComplete(true);
    }

    public @NotNull Optional<Player> getPlayer(@NotNull MineManiaUser user) {
        return getInstance().getProxyServer().getPlayer(user.getUniqueId());
    }

    @Override
    public @NotNull PlayerChatEvent onChatEvent(@NotNull PlayerChatEvent event) {
        return null;
    }


    @Override
    public @Nullable UserActionMessageEvent onMessage(@NotNull UserActionMessageEvent event) {
        return null;
    }

    public MongoDatabase getDataBase(){
        return DatabaseConnection.getMongoDatabase();
    }

    public Logger getLogger(){
        return this.logger;
    }

    public ProxyServer getProxyServer(){
        return this.proxyServer;
    }

    public static MineManiaVelocity getInstance(){
        return instance;
    }
}
