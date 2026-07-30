package com.github.minemaniauk.velocity.minemaniavelocity;

import com.github.smuddgge.squishyconfiguration.ConfigurationFactory;
import com.github.smuddgge.squishyconfiguration.interfaces.Configuration;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class WhitelistManager {

    private static final String WHITELIST_KEY = "whitelist";
    private static final String CACHED_NAMES_KEY = "cached-names";
    private static final String PENDING_USERNAMES_KEY = "pending-usernames";
    private static final String NAME_CACHE_SEPARATOR = "=";

    private List<String> loadedUuidList = new ArrayList<>();
    private Map<String, String> loadedNameCache = new LinkedHashMap<>();
    private List<String> pendingUsernames = new ArrayList<>();

    private boolean enabled;
    private String message;
    private final Configuration whiteListConfig;

    public WhitelistManager(File dataDirectory) {
        whiteListConfig = ConfigurationFactory.YAML
                .create(dataDirectory, "whitelist")
                .setDefaultPath("whitelist.yml");
        reloadWhitelist();
    }

    @Subscribe
    public void onPlayerJoin(LoginEvent event) {
        Player player = event.getPlayer();
        rememberJoiningPlayer(player.getUniqueId(), player.getUsername());

        if (!enabled) return;

        if (!check(player)) {
            event.setResult(ResultedEvent.ComponentResult.denied(LegacyComponentSerializer.legacyAmpersand().deserialize(message)));
        }
    }

    public synchronized AddResult addPlayer(String playerName) {
        String sanitisedPlayerName = sanitisePlayerName(playerName);
        if (sanitisedPlayerName == null) {
            return AddResult.INVALID_NAME;
        }

        if (containsPendingUsername(sanitisedPlayerName)) {
            return AddResult.ALREADY_PENDING;
        }

        if (isKnownWhitelistedName(sanitisedPlayerName)) {
            return AddResult.ALREADY_WHITELISTED;
        }

        List<String> updatedPendingUsernames = new ArrayList<>(pendingUsernames);
        updatedPendingUsernames.add(sanitisedPlayerName);
        saveWhitelist(new ArrayList<>(loadedUuidList), new LinkedHashMap<>(loadedNameCache), updatedPendingUsernames);
        return AddResult.ADDED_PENDING;
    }

    public synchronized RemoveResult removePlayer(String playerIdentifier) {
        String sanitisedPlayerIdentifier = sanitisePlayerName(playerIdentifier);
        if (sanitisedPlayerIdentifier == null) {
            return RemoveResult.notFound();
        }

        Optional<String> pendingMatch = findPendingUsername(sanitisedPlayerIdentifier);
        if (pendingMatch.isPresent()) {
            List<String> updatedPendingUsernames = new ArrayList<>(pendingUsernames);
            updatedPendingUsernames.removeIf(entry -> entry.equalsIgnoreCase(sanitisedPlayerIdentifier));
            saveWhitelist(new ArrayList<>(loadedUuidList), new LinkedHashMap<>(loadedNameCache), updatedPendingUsernames);
            return RemoveResult.pending(pendingMatch.get());
        }

        Optional<String> uuidMatch = findWhitelistedUuidString(sanitisedPlayerIdentifier);
        if (uuidMatch.isEmpty()) {
            return RemoveResult.notFound();
        }

        String uuidString = uuidMatch.get();
        String removedEntry = getDisplayName(uuidString);
        List<String> updatedUuidList = new ArrayList<>(loadedUuidList);
        Map<String, String> updatedNameCache = new LinkedHashMap<>(loadedNameCache);
        updatedUuidList.remove(uuidString);
        updatedNameCache.remove(uuidString);
        saveWhitelist(updatedUuidList, updatedNameCache, new ArrayList<>(pendingUsernames));
        return RemoveResult.whitelisted(removedEntry);
    }

    public synchronized List<String> getAllPlayers() {
        List<String> allPlayers = new ArrayList<>();
        loadedUuidList.stream()
                .map(this::getDisplayName)
                .forEach(allPlayers::add);
        pendingUsernames.stream()
                .map(playerName -> playerName + " (pending)")
                .forEach(allPlayers::add);
        return List.copyOf(allPlayers);
    }

    public synchronized List<String> getRemovalSuggestions() {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        loadedUuidList.stream()
                .map(this::getDisplayName)
                .forEach(suggestions::add);
        suggestions.addAll(pendingUsernames);
        return List.copyOf(suggestions);
    }

    public synchronized void rememberPlayerName(UUID uuid, String playerName) {
        String sanitisedPlayerName = sanitisePlayerName(playerName);
        if (!isWhitelisted(uuid) || sanitisedPlayerName == null) {
            return;
        }

        String uuidString = uuid.toString();
        if (sanitisedPlayerName.equals(loadedNameCache.get(uuidString))) {
            return;
        }

        Map<String, String> updatedNameCache = new LinkedHashMap<>(loadedNameCache);
        updatedNameCache.put(uuidString, sanitisedPlayerName);
        saveWhitelist(new ArrayList<>(loadedUuidList), updatedNameCache, new ArrayList<>(pendingUsernames));
    }

    private synchronized void rememberJoiningPlayer(UUID uuid, String playerName) {
        String sanitisedPlayerName = sanitisePlayerName(playerName);
        if (sanitisedPlayerName == null) {
            return;
        }

        String uuidString = uuid.toString();
        boolean pendingMatch = containsPendingUsername(sanitisedPlayerName);
        boolean alreadyWhitelisted = loadedUuidList.contains(uuidString);
        boolean shouldAddUuid = pendingMatch && !alreadyWhitelisted;
        boolean shouldUpdateName = (alreadyWhitelisted || shouldAddUuid) && !sanitisedPlayerName.equals(loadedNameCache.get(uuidString));

        if (!pendingMatch && !shouldUpdateName) {
            return;
        }

        List<String> updatedUuidList = new ArrayList<>(loadedUuidList);
        Map<String, String> updatedNameCache = new LinkedHashMap<>(loadedNameCache);
        List<String> updatedPendingUsernames = new ArrayList<>(pendingUsernames);

        if (shouldAddUuid) {
            updatedUuidList.add(uuidString);
        }

        if (pendingMatch) {
            updatedPendingUsernames.removeIf(entry -> entry.equalsIgnoreCase(sanitisedPlayerName));
        }

        if (shouldUpdateName) {
            updatedNameCache.put(uuidString, sanitisedPlayerName);
        }

        saveWhitelist(updatedUuidList, updatedNameCache, updatedPendingUsernames);
    }

    private boolean isKnownWhitelistedName(String playerName) {
        return loadedUuidList.stream()
                .map(this::getKnownPlayerName)
                .anyMatch(knownPlayerName -> knownPlayerName != null && knownPlayerName.equalsIgnoreCase(playerName));
    }

    private Optional<String> findPendingUsername(String playerName) {
        return pendingUsernames.stream()
                .filter(knownPendingName -> knownPendingName.equalsIgnoreCase(playerName))
                .findFirst();
    }

    private boolean containsPendingUsername(String playerName) {
        return findPendingUsername(playerName).isPresent();
    }

    private Optional<String> findWhitelistedUuidString(String playerIdentifier) {
        Optional<String> directUuidMatch = loadedUuidList.stream()
                .filter(uuidString -> uuidString.equalsIgnoreCase(playerIdentifier))
                .findFirst();
        if (directUuidMatch.isPresent()) {
            return directUuidMatch;
        }

        return loadedUuidList.stream()
                .filter(uuidString -> {
                    String knownPlayerName = getKnownPlayerName(uuidString);
                    return knownPlayerName != null && knownPlayerName.equalsIgnoreCase(playerIdentifier);
                })
                .findFirst();
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

    private synchronized void saveWhitelist(List<String> uuidList, Map<String, String> nameCache, List<String> pendingUsernames) {
        Map<String, String> mergedNameCache = new LinkedHashMap<>(loadedNameCache);
        mergedNameCache.putAll(nameCache);
        mergedNameCache.entrySet().removeIf(entry -> !uuidList.contains(entry.getKey()) || !isValidPlayerName(entry.getValue()));

        List<String> normalisedPendingUsernames = normalisePendingUsernames(pendingUsernames, mergedNameCache);

        whiteListConfig.set(WHITELIST_KEY, uuidList);
        whiteListConfig.set(CACHED_NAMES_KEY, serialiseNameCache(uuidList, mergedNameCache));
        whiteListConfig.set(PENDING_USERNAMES_KEY, normalisedPendingUsernames);
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

    private List<String> normalisePendingUsernames(List<String> rawPendingUsernames, Map<String, String> nameCache) {
        List<String> normalisedPendingUsernames = new ArrayList<>();
        if (rawPendingUsernames == null) {
            return normalisedPendingUsernames;
        }

        for (String playerName : rawPendingUsernames) {
            String sanitisedPlayerName = sanitisePlayerName(playerName);
            if (sanitisedPlayerName == null) {
                continue;
            }

            if (containsIgnoreCase(normalisedPendingUsernames, sanitisedPlayerName)) {
                continue;
            }

            if (isCachedWhitelistedName(sanitisedPlayerName, nameCache)) {
                continue;
            }

            normalisedPendingUsernames.add(sanitisedPlayerName);
        }

        return normalisedPendingUsernames;
    }

    private boolean isCachedWhitelistedName(String playerName, Map<String, String> nameCache) {
        return nameCache.values().stream()
                .anyMatch(cachedName -> cachedName != null && cachedName.equalsIgnoreCase(playerName));
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(target));
    }

    private String sanitisePlayerName(String playerName) {
        if (playerName == null) {
            return null;
        }

        String sanitisedPlayerName = playerName.trim();
        return sanitisedPlayerName.isBlank() ? null : sanitisedPlayerName;
    }

    private boolean isValidPlayerName(String playerName) {
        return sanitisePlayerName(playerName) != null;
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
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
        List<String> migratedPendingUsernames = new ArrayList<>();
        boolean migratedLegacyNames = false;

        loadedUuidList = new ArrayList<>();
        if (configuredWhitelist != null) {
            for (String whitelistEntry : configuredWhitelist) {
                String sanitisedWhitelistEntry = sanitisePlayerName(whitelistEntry);
                if (sanitisedWhitelistEntry == null) {
                    continue;
                }

                if (isUuid(sanitisedWhitelistEntry)) {
                    loadedUuidList.add(sanitisedWhitelistEntry);
                    continue;
                }

                migratedPendingUsernames.add(sanitisedWhitelistEntry);
                migratedLegacyNames = true;
            }
        }

        loadedNameCache = parseNameCache(whiteListConfig.getListString(CACHED_NAMES_KEY));
        List<String> configuredPendingUsernames = whiteListConfig.getListString(PENDING_USERNAMES_KEY);
        if (configuredPendingUsernames != null) {
            migratedPendingUsernames.addAll(configuredPendingUsernames);
        }
        pendingUsernames = normalisePendingUsernames(migratedPendingUsernames, loadedNameCache);

        if (migratedLegacyNames) {
            whiteListConfig.set(WHITELIST_KEY, loadedUuidList);
            whiteListConfig.set(CACHED_NAMES_KEY, serialiseNameCache(loadedUuidList, loadedNameCache));
            whiteListConfig.set(PENDING_USERNAMES_KEY, pendingUsernames);
            whiteListConfig.save();
        }
    }

    public synchronized boolean isWhitelisted(UUID uuid) {
        return loadedUuidList.contains(uuid.toString());
    }

    public synchronized List<String> getLoadedUuidList() {
        return Collections.unmodifiableList(loadedUuidList);
    }

    public enum AddResult {
        ADDED_PENDING,
        ALREADY_PENDING,
        ALREADY_WHITELISTED,
        INVALID_NAME
    }

    public static final class RemoveResult {

        private final boolean removed;
        private final boolean pending;
        private final String displayName;

        private RemoveResult(boolean removed, boolean pending, String displayName) {
            this.removed = removed;
            this.pending = pending;
            this.displayName = displayName;
        }

        public static RemoveResult notFound() {
            return new RemoveResult(false, false, null);
        }

        public static RemoveResult pending(String displayName) {
            return new RemoveResult(true, true, displayName);
        }

        public static RemoveResult whitelisted(String displayName) {
            return new RemoveResult(true, false, displayName);
        }

        public boolean wasRemoved() {
            return removed;
        }

        public boolean wasPending() {
            return pending;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
