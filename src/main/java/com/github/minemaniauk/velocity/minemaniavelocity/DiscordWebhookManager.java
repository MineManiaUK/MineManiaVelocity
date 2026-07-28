package com.github.minemaniauk.velocity.minemaniavelocity;

import com.eduardomcb.discord.webhook.WebhookClient;
import com.eduardomcb.discord.webhook.WebhookManager;
import com.eduardomcb.discord.webhook.models.Embed;
import com.eduardomcb.discord.webhook.models.Field;
import com.eduardomcb.discord.webhook.models.Message;
import com.github.smuddgge.squishyconfiguration.interfaces.Configuration;
import com.github.smuddgge.squishyconfiguration.interfaces.ConfigurationSection;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DiscordWebhookManager {

    private final Configuration config;
    private boolean reportEnabled;
    private String reportUrl;
    private boolean suggestEnabled;
    private String suggestUrl;

    public DiscordWebhookManager(Configuration discordConfig) {
        config = discordConfig;
        ConfigurationSection suggestSection = config.getSection("suggest-webhook");
        ConfigurationSection reportSection = config.getSection("report-webhook");

        reportEnabled = reportSection.getBoolean("enabled");
        suggestEnabled = suggestSection.getBoolean("enabled");
        reportUrl = reportSection.getString("url");
        suggestUrl = suggestSection.getString("url");
    }

    public void sendReport(Player sender, String message) {
        if (!reportEnabled) {
            MineManiaVelocity.getInstance().getLogger().warn("com.github.minemaniauk.velocity.minemaniavelocity.DiscordWebhookManager.sendReport Called when reporting is disabled");
            return;
        }

        message = message.replace("&", "\\&");
        String serverName = sender.getCurrentServer()
                .map(connection -> connection.getServer().getServerInfo().getName())
                .orElse("Unknown Server");

        for (Player p : MineManiaVelocity.getInstance().getProxyServer().getAllPlayers()) {
            if (p.hasPermission("minemaniavelocity.alert.report")) {
                p.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                        "&c&l> &f%s &7&l%s&r &chas reported &7: &f%s".formatted(
                                sender.getUsername(),
                                serverName,
                                message
                        )
                ));
            }
        }

        WebhookManager webhookManager = new WebhookManager()
                .setChannelUrl(reportUrl)
                .setListener(new WebhookClient.Callback() {
                    @Override
                    public void onSuccess(String response) { }

                    @Override
                    public void onFailure(int statusCode, String errorMessage) {
                        MineManiaVelocity.getInstance().getLogger().error("Could not send discord webhook message " + "Code: " + statusCode + " error: " + errorMessage);
                    }
                }
                );

        LocalDateTime timeNow = LocalDateTime.now();
        String formatedTimeNow = timeNow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        Embed embed = new Embed();
        embed.setColor(0xE76B74);
        embed.setTitle("Report");
        embed.setDescription(message);
        embed.setFields(new Field[] { new Field("\\u200B", "%s by %s".formatted(serverName, sender.getUsername()), false) });
        embed.setTimestamp(formatedTimeNow);

        Message discordMessage = new Message()
                .setUsername("Report")
                .setAvatarUrl("https://cdn.jsdelivr.net/gh/jdecked/twemoji@latest/assets/72x72/26a0.png");

        webhookManager.setMessage(discordMessage);
        webhookManager.setEmbeds(new Embed[] {embed});
        webhookManager.exec();
    }

    public void sendSuggest(Player sender, String message) {
        if (!suggestEnabled) {
            MineManiaVelocity.getInstance().getLogger().warn("com.github.minemaniauk.velocity.minemaniavelocity.DiscordWebhookManager.sendSuggest Called when suggesting is disabled");
            return;
        }

        message = message.replace("&", "\\&");
        String serverName = sender.getCurrentServer()
                .map(connection -> connection.getServer().getServerInfo().getName())
                .orElse("Unknown Server");

        for (Player p : MineManiaVelocity.getInstance().getProxyServer().getAllPlayers()) {
            if (p.hasPermission("minemaniavelocity.alert.suggest")) {
                p.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                        "&2&l> &f%s &7&l%s&r &2has suggested &7: &f%s".formatted(
                                sender.getUsername(),
                                serverName,
                                message
                        )
                ));
            }
        }

        WebhookManager webhookManager = new WebhookManager()
                .setChannelUrl(suggestUrl)
                .setListener(new WebhookClient.Callback() {
                                 @Override
                                 public void onSuccess(String response) { }

                                 @Override
                                 public void onFailure(int statusCode, String errorMessage) {
                                     MineManiaVelocity.getInstance().getLogger().error("Could not send discord webhook message " + "Code: " + statusCode + " error: " + errorMessage);
                                 }
                             }
                );

        LocalDateTime timeNow = LocalDateTime.now();
        String formatedTimeNow = timeNow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        Embed embed = new Embed();
        embed.setColor(0xE76B74);
        embed.setTitle("Suggestion");
        embed.setDescription(message);
        embed.setFields(new Field[] { new Field("\\u200B", "%s by %s".formatted(serverName, sender.getUsername()), false) });
        embed.setTimestamp(formatedTimeNow);

        Message discordMessage = new Message()
                .setUsername("Suggestion")
                .setAvatarUrl("https://cdn.jsdelivr.net/gh/jdecked/twemoji@latest/assets/72x72/1f4cb.png");

        webhookManager.setMessage(discordMessage);
        webhookManager.setEmbeds(new Embed[] {embed});
        webhookManager.exec();
    }
}