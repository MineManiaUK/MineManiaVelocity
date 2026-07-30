package com.github.minemaniauk.velocity.minemaniavelocity;

import com.github.minemaniauk.velocity.minemaniavelocity.MinecraftProfileService.MinecraftProfileService;
import com.github.minemaniauk.velocity.minemaniavelocity.WhitelistManager.MigrationResult;
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
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
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

    private static final Duration WHITELIST_MIGRATION_RETRY_DELAY = Duration.ofMinutes(1);
    private static final MinecraftProfileService profileService = new MinecraftProfileService();
    private static MineManiaVelocity instance;
    private final Configuration config;
    private final Configuration discordConfig;
    private final Configuration serverConfig;
    private final WhitelistManager whitelistManager;

    private DiscordWebhookManager discordWebhookManager;

    private List<ServerCommand> loadedServerCommands = new ArrayList<>();
    private ScheduledTask whitelistMigrationTask;
    private boolean whitelistMigrationActive;
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
        cm.register(cm.metaBuilder("gwhitelist").build(), new GwhitelistCommand(profileService));
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

    public synchronized WhitelistMigrationStartResult startWhitelistMigration() {
        if (whitelistManager.getMissingNameCount() == 0) {
            return WhitelistMigrationStartResult.NOTHING_TO_MIGRATE;
        }

        if (whitelistMigrationActive) {
            return WhitelistMigrationStartResult.ALREADY_RUNNING;
        }

        whitelistMigrationActive = true;
        scheduleWhitelistMigration(Duration.ZERO);
        logger.info("Started background whitelist name migration.");
        return WhitelistMigrationStartResult.STARTED;
    }

    private void runWhitelistMigration() {
        MigrationResult result;

        try {
            result = whitelistManager.migrateCachedNames(profileService);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn("Whitelist name migration was interrupted.");
            finishWhitelistMigration();
            return;
        }

        if (result.getTotalMissingCount() == 0) {
            logger.info("Whitelist name migration found nothing to do.");
            finishWhitelistMigration();
            return;
        }

        if (result.isStoppedEarly()) {
            if (result.isRetryableFailure()) {
                logger.warn(
                        "Whitelist name migration migrated {}/{} names before a retryable failure: {}. Retrying in {} seconds.",
                        result.getMigratedCount(),
                        result.getTotalMissingCount(),
                        result.getFailureMessage(),
                        WHITELIST_MIGRATION_RETRY_DELAY.toSeconds()
                );
                scheduleWhitelistMigration(WHITELIST_MIGRATION_RETRY_DELAY);
                return;
            }

            logger.error(
                    "Whitelist name migration stopped after migrating {}/{} names: {}",
                    result.getMigratedCount(),
                    result.getTotalMissingCount(),
                    result.getFailureMessage()
            );
            finishWhitelistMigration();
            return;
        }

        int remainingNames = whitelistManager.getMissingNameCount();
        if (remainingNames > 0) {
            logger.info(
                    "Whitelist name migration resolved {}/{} names. {} entries still have no known name.",
                    result.getMigratedCount(),
                    result.getTotalMissingCount(),
                    remainingNames
            );
        } else {
            logger.info(
                    "Whitelist name migration resolved all {} missing names. {} came from online players.",
                    result.getMigratedCount(),
                    result.getOnlineResolvedCount()
            );
        }

        finishWhitelistMigration();
    }

    private synchronized void scheduleWhitelistMigration(Duration delay) {
        var taskBuilder = proxyServer.getScheduler().buildTask(this, this::runWhitelistMigration);
        if (!delay.isZero()) {
            taskBuilder.delay(delay);
        }
        whitelistMigrationTask = taskBuilder.schedule();
    }

    private synchronized void finishWhitelistMigration() {
        whitelistMigrationTask = null;
        whitelistMigrationActive = false;
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

    public static MinecraftProfileService getProfileService() {
        return profileService;
    }

    public static MineManiaVelocity getInstance(){
        return instance;
    }

    public enum WhitelistMigrationStartResult {
        STARTED,
        ALREADY_RUNNING,
        NOTHING_TO_MIGRATE
    }
}
