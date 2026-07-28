package com.github.minemaniauk.velocity.minemaniavelocity.commands;

import com.github.minemaniauk.velocity.minemaniavelocity.MinecraftProfile;
import com.github.minemaniauk.velocity.minemaniavelocity.MinecraftProfileService;
import com.github.minemaniauk.velocity.minemaniavelocity.MineManiaVelocity;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class GwhitelistCommand implements SimpleCommand {

    private final MinecraftProfileService profileService;

    public GwhitelistCommand(MinecraftProfileService profileService){
        this.profileService = profileService;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length < 1) {
            sendUsage(invocation);
            return;
        }

        switch (args[0]) {
            case "add":
                if (args.length != 2) {
                    sendUsage(invocation);
                    return;
                }

                String addUsername = args[1];
                try {
                    UUID uuid = resolveUuid(invocation, addUsername);
                    if (uuid == null) {
                        return;
                    }

                    if (MineManiaVelocity.getInstance().getWhitelistManager().isWhitelisted(uuid)) {
                        invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c&l> &f%s &cis already whitelisted".formatted(addUsername)));
                        return;
                    }

                    MineManiaVelocity.getInstance().getWhitelistManager().add(uuid);
                    invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&7&l> &7Successfully &aadded &f%s &7to the whitelist".formatted(addUsername)));
                } catch (IOException | InterruptedException e) {
                    sendProfileServiceError(invocation, e, "Something went wrong resolving the player profile for %s".formatted(addUsername));
                    return;
                }

                break;
            case "list":
                try {
                    List<String> whitelistedPlayers = MineManiaVelocity.getInstance().getWhitelistManager().getAllPlayers();

                    if (whitelistedPlayers.isEmpty()) {
                        invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&7&l> &7There are no whitelisted players."));
                        return;
                    }

                    String output = String.join(", ", whitelistedPlayers);
                    invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&7&l> &7Whitelisted players are: &f" + output));
                } catch (IOException | InterruptedException e) {
                    sendProfileServiceError(invocation, e, "Something went wrong listing whitelisted players");
                    return;
                }
                break;
            case "remove":
                if (args.length != 2) {
                    sendUsage(invocation);
                    return;
                }

                String removeUsername = args[1];
                try {
                    UUID uuid = resolveUuid(invocation, removeUsername);
                    if (uuid == null) {
                        return;
                    }

                    if (!MineManiaVelocity.getInstance().getWhitelistManager().isWhitelisted(uuid)) {
                        invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c&l> &f%s &cis not whitelisted".formatted(removeUsername)));
                        return;
                    }

                    MineManiaVelocity.getInstance().getWhitelistManager().remove(uuid);
                    invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&7&l> &aSuccessfully &cremoved &f%s &7from the whitelist".formatted(removeUsername)));
                } catch (IOException | InterruptedException e) {
                    sendProfileServiceError(invocation, e, "Something went wrong resolving the player profile for %s".formatted(removeUsername));
                    return;
                }
                break;
            case "reload":
                MineManiaVelocity.getInstance().getWhitelistManager().reloadWhitelist();
                invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&7&l> &aReloaded &7whitelist"));
                break;
            default:
                sendUsage(invocation);
                break;
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 0) {
            return List.of("add", "list", "remove", "reload");
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return List.of("add", "list", "remove", "reload" ).stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);

            try {
                List<String> whitelistedPlayers = MineManiaVelocity.getInstance().getWhitelistManager().getAllPlayers();
                return whitelistedPlayers.stream()
                        .filter(s -> prefix.isBlank() || s.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
            } catch (IOException | InterruptedException e) {
                MineManiaVelocity.getInstance().getLogger().atError().setCause(e).log("Something went wrong tab completing whitelisted players");
                return List.of();
            }
        }

        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("minemaniavelocity.manage.whitelist");
    }

    private void sendUsage(Invocation invocation) {
        invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c&l> &cUsage: /gwhitelist <add|list|remove|reload> [username]"));
    }

    private UUID resolveUuid(Invocation invocation, String username) throws IOException, InterruptedException {
        MinecraftProfile profile = profileService.findByName(username);
        if (profile == null) {
            invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c&l> &cUnable to find a Mojang profile for &f%s&c.".formatted(username)));
            return null;
        }
        return profile.getUniqueId();
    }

    private void sendProfileServiceError(Invocation invocation, Exception exception, String logMessage) {
        invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c&l> &cSomething went wrong. Check console for errors"));
        MineManiaVelocity.getInstance().getLogger().atError().setCause(exception).log(logMessage);
    }
}
