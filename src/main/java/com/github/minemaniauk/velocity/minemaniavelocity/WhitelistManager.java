package com.github.minemaniauk.velocity.minemaniavelocity;

import com.github.minemaniauk.velocity.minemaniavelocity.MinecraftProfileService.MinecraftProfile;
import com.github.smuddgge.squishyconfiguration.ConfigurationFactory;
import com.github.smuddgge.squishyconfiguration.interfaces.Configuration;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class WhitelistManager {

    private List<String> loadedUuidList = new ArrayList<>();

    private boolean enabled;
    private String message;
    private Configuration whiteListConfig;

    public WhitelistManager(File dataDirectory) {
        whiteListConfig = ConfigurationFactory.YAML
                .create(dataDirectory, "whitelist")
                .setDefaultPath("whitelist.yml");
        reloadWhitelist();
    }

    @Subscribe
    public void onPlayerJoin(LoginEvent event) {
        if (!enabled) return;

        if (!check(event.getPlayer())){
            event.setResult(ResultedEvent.ComponentResult.denied(LegacyComponentSerializer.legacyAmpersand().deserialize(message)));
        }
    }

    public void add(UUID uuid) {
        String uuidString = uuid.toString();
        if (!loadedUuidList.contains(uuidString)) {
            List<String> newList = new ArrayList<>(loadedUuidList);
            newList.add(uuidString);
            whiteListConfig.set("whitelist", newList);
            whiteListConfig.save();
            reloadWhitelist();
        }
    }

    public void remove(UUID uuid) {
        String uuidString = uuid.toString();
        if (loadedUuidList.contains(uuidString)) {
            List<String> newList = new ArrayList<>(loadedUuidList);
            newList.remove(uuidString);
            whiteListConfig.set("whitelist", newList);
            whiteListConfig.save();
            reloadWhitelist();
        }
    }

    public List<String> getAllPlayers() throws IOException, InterruptedException {
        if (loadedUuidList.isEmpty()) {
            return List.of();
        }

        List<UUID> uuids = loadedUuidList.stream()
                .map(UUID::fromString)
                .toList();

        return MineManiaVelocity.getProfileService()
                .findAllByUuid(uuids)
                .stream()
                .filter(profile -> profile != null && profile.getName() != null && !profile.getName().isBlank())
                .map(MinecraftProfile::getName)
                .toList();
    }

    public boolean check(Player player) {
        return loadedUuidList.contains(player.getUniqueId().toString());
    }

    public void reloadWhitelist() {
        whiteListConfig.load();
        enabled = whiteListConfig.getBoolean("enabled");
        String configuredMessage = whiteListConfig.getString("message");
        message = configuredMessage == null || configuredMessage.isBlank()
                ? "&cYou are not whitelisted on this network."
                : configuredMessage;

        List<String> configuredWhitelist = whiteListConfig.getListString("whitelist");
        loadedUuidList = configuredWhitelist == null
                ? new ArrayList<>()
                : new ArrayList<>(configuredWhitelist);
    }

    public boolean isWhitelisted(UUID uuid) {
        return loadedUuidList.contains(uuid.toString());
    }

    public List<String> getLoadedUuidList() {
        return Collections.unmodifiableList(loadedUuidList);
    }
}
