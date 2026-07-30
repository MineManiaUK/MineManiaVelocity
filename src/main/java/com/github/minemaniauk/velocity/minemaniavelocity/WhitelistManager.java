package com.github.minemaniauk.velocity.minemaniavelocity;

import com.github.minemaniauk.velocity.minemaniavelocity.MinecraftProfileService.MinecraftProfile;
import com.github.minemaniauk.velocity.minemaniavelocity.MinecraftProfileService.MinecraftProfileService;
import com.github.minemaniauk.velocity.minemaniavelocity.MinecraftProfileService.MinecraftProfileService.RetryableProfileLookupException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class WhitelistManager {

    private static final String WHITELIST_KEY = "whitelist";
    private static final String CACHED_NAMES_KEY = "cached-names";
    private static final String NAME_CACHE_SEPARATOR = "=";
    private static final long MIGRATION_REMOTE_LOOKUP_DELAY_MS = 150L;

    private List<String> loadedUuidList = new ArrayList<>();
    private Map<String, String> loadedNameCache = new LinkedHashMap<>();

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
        Player player = event.getPlayer();

        if (isWhitelisted(player.getUniqueId())) {
            rememberPlayerName(player.getUniqueId(), player.getUsername());
        }

        if (!enabled) return;

        if (!check(player)){
            event.setResult(ResultedEvent.ComponentResult.denied(LegacyComponentSerializer.legacyAmpersand().deserialize(message)));
        }
    }

    public synchronized void add(UUID uuid, String playerName) {
        String uuidString = uuid.toString();
        if (!loadedUuidList.contains(uuidString)) {
            List<String> newList = new ArrayList<>(loadedUuidList);
            Map<String, String> newNameCache = new LinkedHashMap<>(loadedNameCache);
            newList.add(uuidString);

            if (isValidPlayerName(playerName)) {
                newNameCache.put(uuidString, playerName);
            }

            saveWhitelist(newList, newNameCache);
        }
    }

    public synchronized void remove(UUID uuid) {
        String uuidString = uuid.toString();
        if (loadedUuidList.contains(uuidString)) {
            List<String> newList = new ArrayList<>(loadedUuidList);
            Map<String, String> newNameCache = new LinkedHashMap<>(loadedNameCache);
            newList.remove(uuidString);
            newNameCache.remove(uuidString);
            saveWhitelist(newList, newNameCache);
        }
    }

    public synchronized List<String> getAllPlayers() {
        if (loadedUuidList.isEmpty()) {
            return List.of();
        }

        return loadedUuidList.stream()
                .map(this::getDisplayName)
                .toList();
    }

    public synchronized List<String> getWhitelistedPlayerNames() {
        return loadedUuidList.stream()
                .map(this::getKnownPlayerName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public synchronized void rememberPlayerName(UUID uuid, String playerName) {
        if (!isWhitelisted(uuid) || !isValidPlayerName(playerName)) {
            return;
        }

        String uuidString = uuid.toString();
        if (playerName.equals(loadedNameCache.get(uuidString))) {
            return;
        }

        Map<String, String> updatedNameCache = new LinkedHashMap<>(loadedNameCache);
        updatedNameCache.put(uuidString, playerName);
        saveWhitelist(new ArrayList<>(loadedUuidList), updatedNameCache);
    }

    public synchronized int getMissingNameCount() {
        return (int) loadedUuidList.stream()
                .filter(uuidString -> !isValidPlayerName(loadedNameCache.get(uuidString)))
                .count();
    }

    public MigrationResult migrateCachedNames(MinecraftProfileService profileService) throws InterruptedException {
        List<String> missingUuidStrings;
        Map<String, String> updatedNameCache;

        synchronized (this) {
            missingUuidStrings = loadedUuidList.stream()
                    .filter(uuidString -> !isValidPlayerName(loadedNameCache.get(uuidString)))
                    .toList();
            updatedNameCache = new LinkedHashMap<>(loadedNameCache);
        }

        if (missingUuidStrings.isEmpty()) {
            return new MigrationResult(0, 0, 0, false, false, null);
        }

        int migratedCount = 0;
        int onlineResolvedCount = 0;
        String failureMessage = null;
        boolean retryableFailure = false;

        for (String uuidString : missingUuidStrings) {
            String playerName = getOnlinePlayerName(uuidString).orElse(null);

            if (isValidPlayerName(playerName)) {
                updatedNameCache.put(uuidString, playerName);
                migratedCount++;
                onlineResolvedCount++;
                continue;
            }

            try {
                MinecraftProfile profile = profileService.findByUuid(UUID.fromString(uuidString));
                if (profile != null && isValidPlayerName(profile.getName())) {
                    updatedNameCache.put(uuidString, profile.getName());
                    migratedCount++;
                }
            } catch (IllegalArgumentException exception) {
                failureMessage = "Invalid UUID in whitelist: " + uuidString;
                break;
            } catch (IOException exception) {
                failureMessage = exception.getMessage();
                retryableFailure = exception instanceof RetryableProfileLookupException;
                break;
            }

            Thread.sleep(MIGRATION_REMOTE_LOOKUP_DELAY_MS);
        }

        synchronized (this) {
            if (!updatedNameCache.equals(loadedNameCache)) {
                saveWhitelist(new ArrayList<>(loadedUuidList), updatedNameCache);
            }
        }

        return new MigrationResult(
                missingUuidStrings.size(),
                migratedCount,
                onlineResolvedCount,
                failureMessage != null,
                retryableFailure,
                failureMessage
        );
    }

    private String getDisplayName(String uuidString) {
        String knownPlayerName = getKnownPlayerName(uuidString);
        return knownPlayerName != null ? knownPlayerName : uuidString;
    }

    private String getKnownPlayerName(String uuidString) {
        String cachedName = loadedNameCache.get(uuidString);
        if (isValidPlayerName(cachedName)) {
            return cachedName;
        }

        return getOnlinePlayerName(uuidString).orElse(null);
    }

    private Optional<String> getOnlinePlayerName(String uuidString) {
        try {
            return MineManiaVelocity.getInstance()
                    .getProxyServer()
                    .getPlayer(UUID.fromString(uuidString))
                    .map(Player::getUsername);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private synchronized void saveWhitelist(List<String> uuidList, Map<String, String> nameCache) {
        Map<String, String> mergedNameCache = new LinkedHashMap<>(loadedNameCache);
        mergedNameCache.putAll(nameCache);
        mergedNameCache.entrySet().removeIf(entry -> !uuidList.contains(entry.getKey()) || !isValidPlayerName(entry.getValue()));

        whiteListConfig.set(WHITELIST_KEY, uuidList);
        whiteListConfig.set(CACHED_NAMES_KEY, serialiseNameCache(uuidList, mergedNameCache));
        whiteListConfig.save();
        reloadWhitelist();
    }

    private List<String> serialiseNameCache(List<String> uuidList, Map<String, String> nameCache) {
        return nameCache.entrySet().stream()
                .filter(entry -> uuidList.contains(entry.getKey()))
                .filter(entry -> isValidPlayerName(entry.getValue()))
                .map(entry -> entry.getKey() + NAME_CACHE_SEPARATOR + entry.getValue())
                .toList();
    }

    private Map<String, String> parseNameCache(List<String> rawNameCacheEntries) {
        Map<String, String> parsedNameCache = new LinkedHashMap<>();
        if (rawNameCacheEntries == null) {
            return parsedNameCache;
        }

        for (String entry : rawNameCacheEntries) {
            if (entry == null) {
                continue;
            }

            int separatorIndex = entry.indexOf(NAME_CACHE_SEPARATOR);
            if (separatorIndex <= 0 || separatorIndex >= entry.length() - 1) {
                continue;
            }

            String uuidString = entry.substring(0, separatorIndex);
            String playerName = entry.substring(separatorIndex + 1);

            if (loadedUuidList.contains(uuidString) && isValidPlayerName(playerName)) {
                parsedNameCache.put(uuidString, playerName);
            }
        }

        return parsedNameCache;
    }

    private boolean isValidPlayerName(String playerName) {
        return playerName != null && !playerName.isBlank();
    }

    public synchronized boolean check(Player player) {
        return loadedUuidList.contains(player.getUniqueId().toString());
    }

    public synchronized void reloadWhitelist() {
        whiteListConfig.load();
        enabled = whiteListConfig.getBoolean("enabled");
        String configuredMessage = whiteListConfig.getString("message");
        message = configuredMessage == null || configuredMessage.isBlank()
                ? "&cYou are not whitelisted on this network."
                : configuredMessage;

        List<String> configuredWhitelist = whiteListConfig.getListString(WHITELIST_KEY);
        loadedUuidList = configuredWhitelist == null
                ? new ArrayList<>()
                : new ArrayList<>(configuredWhitelist);
        loadedNameCache = parseNameCache(whiteListConfig.getListString(CACHED_NAMES_KEY));
    }

    public synchronized boolean isWhitelisted(UUID uuid) {
        return loadedUuidList.contains(uuid.toString());
    }

    public synchronized List<String> getLoadedUuidList() {
        return Collections.unmodifiableList(loadedUuidList);
    }

    public static final class MigrationResult {

        private final int totalMissingCount;
        private final int migratedCount;
        private final int onlineResolvedCount;
        private final boolean stoppedEarly;
        private final boolean retryableFailure;
        private final String failureMessage;

        public MigrationResult(int totalMissingCount, int migratedCount, int onlineResolvedCount, boolean stoppedEarly, boolean retryableFailure, String failureMessage) {
            this.totalMissingCount = totalMissingCount;
            this.migratedCount = migratedCount;
            this.onlineResolvedCount = onlineResolvedCount;
            this.stoppedEarly = stoppedEarly;
            this.retryableFailure = retryableFailure;
            this.failureMessage = failureMessage;
        }

        public int getTotalMissingCount() {
            return totalMissingCount;
        }

        public int getMigratedCount() {
            return migratedCount;
        }

        public int getOnlineResolvedCount() {
            return onlineResolvedCount;
        }

        public boolean isStoppedEarly() {
            return stoppedEarly;
        }

        public boolean isRetryableFailure() {
            return retryableFailure;
        }

        public String getFailureMessage() {
            return failureMessage;
        }
    }
}
