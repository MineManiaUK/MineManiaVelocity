package com.github.minemaniauk.velocity.minemaniavelocity.MinecraftProfileService;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MinecraftProfileService {

    private static final String NAME_LOOKUP_URL = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
    private static final String UUID_LOOKUP_URL = "https://api.minecraftservices.com/minecraft/profile/lookup/";
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([A-Za-z0-9_]{1,16})\"");
    private static final int MAX_RETRIES = 3;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    public MinecraftProfile findByName(String name) throws IOException, InterruptedException {
        return lookupProfile(NAME_LOOKUP_URL + URLEncoder.encode(name, StandardCharsets.UTF_8));
    }

    public List<MinecraftProfile> findAllByName(Iterable<String> names) throws IOException, InterruptedException {
        List<MinecraftProfile> profiles = new ArrayList<>();
        for (String name : names) {
            MinecraftProfile profile = findByName(name);
            if (profile != null) {
                profiles.add(profile);
            }
        }
        return List.copyOf(profiles);
    }

    public MinecraftProfile findByUuid(UUID uuid) throws IOException, InterruptedException {
        return lookupProfile(UUID_LOOKUP_URL + uuid.toString().replace("-", ""));
    }

    public List<MinecraftProfile> findAllByUuid(Iterable<UUID> uuids) throws IOException, InterruptedException {
        List<MinecraftProfile> profiles = new ArrayList<>();
        for (UUID uuid : uuids) {
            MinecraftProfile profile = findByUuid(uuid);
            if (profile != null) {
                profiles.add(profile);
            }
        }
        return List.copyOf(profiles);
    }

    private MinecraftProfile lookupProfile(String url) throws IOException, InterruptedException {
        IOException failure = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int statusCode = response.statusCode();

            if (statusCode == 404) {
                return null;
            }

            if (statusCode == 429 || statusCode >= 500) {
                failure = new RetryableProfileLookupException(statusCode, url);
                Thread.sleep(250L * (attempt + 1));
                continue;
            }

            if (statusCode != 200) {
                throw new IOException("Profile lookup failed with HTTP " + statusCode + " for " + url);
            }

            return parseProfile(response.body());
        }

        throw failure == null ? new IOException("Profile lookup failed for " + url) : failure;
    }

    private MinecraftProfile parseProfile(String responseBody) throws IOException {
        Matcher idMatcher = ID_PATTERN.matcher(responseBody);
        Matcher nameMatcher = NAME_PATTERN.matcher(responseBody);

        if (!idMatcher.find() || !nameMatcher.find()) {
            throw new IOException("Profile lookup returned an unexpected response: " + responseBody);
        }

        String rawUuid = idMatcher.group(1);
        String playerName = nameMatcher.group(1);
        String dashedUuid = rawUuid.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"
        );

        return new MinecraftProfile(UUID.fromString(dashedUuid), playerName);
    }

    public static class RetryableProfileLookupException extends IOException {

        private final int statusCode;

        public RetryableProfileLookupException(int statusCode, String url) {
            super("Profile lookup failed with HTTP " + statusCode + " for " + url);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
