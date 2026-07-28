package com.github.minemaniauk.velocity.minemaniavelocity;

import com.github.minemaniauk.velocity.minemaniavelocity.commands.ReportCommand;
import com.github.minemaniauk.velocity.minemaniavelocity.commands.SuggestCommand;
import com.github.smuddgge.squishyconfiguration.ConfigurationFactory;
import com.github.smuddgge.squishyconfiguration.interfaces.Configuration;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.nio.file.Path;

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

    private DiscordWebhookManager discordWebhookManager;

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

        if (discordConfig.getBoolean("enabled")) {
            this.discordWebhookManager = new DiscordWebhookManager(discordConfig);
        }

        CommandManager cm = this.proxyServer.getCommandManager();
        cm.register(cm.metaBuilder("report").build(), new ReportCommand());
        cm.register(cm.metaBuilder("suggest").build(), new SuggestCommand());
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

    public static MineManiaVelocity getInstance(){
        return instance;
    }
}
