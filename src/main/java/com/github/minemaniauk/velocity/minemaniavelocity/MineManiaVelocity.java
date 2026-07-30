package com.github.minemaniauk.velocity.minemaniavelocity;

import com.github.minemaniauk.velocity.minemaniavelocity.commands.*;
import com.github.smuddgge.squishyconfiguration.ConfigurationFactory;
import com.github.smuddgge.squishyconfiguration.interfaces.Configuration;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Plugin(
        id = "minemaniavelocity",
        name = "MineMania Velocity",
        version = "2.0.0",
        authors = { "MineMania Development Team", "Computerwhz" },
        url = "minemania.co"

)
public class MineManiaVelocity {

    private static MineManiaVelocity instance;
    private final Configuration config;
    private final Configuration discordConfig;
    private final Configuration serverConfig;
    private final WhitelistManager whitelistManager;

    private DiscordWebhookManager discordWebhookManager;

    private List<ServerCommand> loadedServerCommands = new ArrayList<>();
    private final ProxyServer proxyServer;
    private final Logger logger;

    @Inject
    public MineManiaVelocity (ProxyServer proxyServer, @DataDirectory final Path dataDirectory, Logger logger){
        instance = this;
        this.proxyServer = proxyServer;
        this.logger = logger;

        this.config = ConfigurationFactory.YAML
                .create(dataDirectory.toFile(), "config")
                .setDefaultPath("config.yml");
        this.config.load();

        this.discordConfig = ConfigurationFactory.YAML
                .create(dataDirectory.toFile(), "discord")
                .setDefaultPath("discord.yml");
        this.discordConfig.load();

        this.serverConfig = ConfigurationFactory.YAML
                .create(dataDirectory.toFile(), "servers")
                .setDefaultPath("servers.yml");
        this.serverConfig.load();

        if (discordConfig.getBoolean("enabled")) {
            this.discordWebhookManager = new DiscordWebhookManager(discordConfig);
        }

        whitelistManager = new WhitelistManager(dataDirectory.toFile());
        CommandManager cm = this.proxyServer.getCommandManager();
        cm.register(cm.metaBuilder("report").build(), new ReportCommand());
        cm.register(cm.metaBuilder("suggest").build(), new SuggestCommand());
        cm.register(cm.metaBuilder("gwhitelist").build(), new GwhitelistCommand());
        cm.register(cm.metaBuilder("reloadservers").build(), new ReloadServerCommands());
        loadServerCommands();
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        proxyServer.getEventManager().register(this, whitelistManager);
    }

    public boolean loadServerCommands() {
        CommandManager cm = proxyServer.getCommandManager();
        serverConfig.load();
        unregisterServerCommands();

        for (String key : serverConfig.getKeys()) {
            Optional<RegisteredServer> optionalServer = proxyServer.getServer(key);
            if (optionalServer.isEmpty()) {
                logger.error("Server %s does not exist".formatted(key));
                unregisterServerCommands();
                return false;
            }

            String name = serverConfig.getString(key);
            RegisteredServer server = optionalServer.get();
            loadedServerCommands.add(new ServerCommand(name, server, cm));
            logger.info("Successfully registered new server command %s pointing to %s".formatted(name, server.getServerInfo().getName()));
        }
        return true;
    }

    public void unregisterServerCommands() {
        for (ServerCommand command : loadedServerCommands) {
            String name = command.name;
            String serverName = command.server.getServerInfo().getName();
            command.unregister();
            logger.info("Successfully unregistered server command %s pointing to %s".formatted(name, serverName));
        }
        loadedServerCommands.clear();
    }

    public Logger getLogger(){
        return this.logger;
    }

    public ProxyServer getProxyServer(){
        return this.proxyServer;
    }

    public DiscordWebhookManager getWebhookManager() {
        return discordWebhookManager;
    }

    public Configuration getConfig() { return this.config; }

    public WhitelistManager getWhitelistManager() {
        return this.whitelistManager;
    }

    public static MineManiaVelocity getInstance(){
        return instance;
    }
}
