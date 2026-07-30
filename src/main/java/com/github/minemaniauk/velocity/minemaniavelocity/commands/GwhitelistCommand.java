package com.github.minemaniauk.velocity.minemaniavelocity.commands;

import com.github.minemaniauk.velocity.minemaniavelocity.MineManiaVelocity;
import com.github.minemaniauk.velocity.minemaniavelocity.WhitelistManager.AddResult;
import com.github.minemaniauk.velocity.minemaniavelocity.WhitelistManager.RemoveResult;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class GwhitelistCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length < 1) {
            sendUsage(invocation);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add":
                String addUsername = getUsernameArgument(args);
                if (addUsername == null) {
                    sendUsage(invocation);
                    return;
                }

                AddResult addResult = MineManiaVelocity.getInstance().getWhitelistManager().addPlayer(addUsername);
                switch (addResult) {
                    case ADDED_PENDING -> invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                            "&7&l> &7Added &f%s &7to the whitelist. Their UUID will be linked when they join.".formatted(addUsername)
                    ));
                    case ALREADY_PENDING -> invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                            "&e&l> &f%s &eis already waiting for their first join.".formatted(addUsername)
                    ));
                    case ALREADY_WHITELISTED -> invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                            "&c&l> &f%s &cis already whitelisted".formatted(addUsername)
                    ));
                    case INVALID_NAME -> sendUsage(invocation);
                }
                break;
            case "list":
                List<String> whitelistedPlayers = MineManiaVelocity.getInstance().getWhitelistManager().getAllPlayers();

                if (whitelistedPlayers.isEmpty()) {
                    invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&7&l> &7There are no whitelist entries."));
                    return;
                }

                String output = String.join(", ", whitelistedPlayers);
                invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&7&l> &7Whitelist entries are: &f" + output));
                break;
            case "remove":
                String removeUsername = getUsernameArgument(args);
                if (removeUsername == null) {
                    sendUsage(invocation);
                    return;
                }

                RemoveResult removeResult = MineManiaVelocity.getInstance().getWhitelistManager().removePlayer(removeUsername);
                if (!removeResult.wasRemoved()) {
                    invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                            "&c&l> &f%s &cis not on the whitelist".formatted(removeUsername)
                    ));
                    return;
                }

                invocation.source().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                        removeResult.wasPending()
                                ? "&7&l> &aSuccessfully &cremoved &f%s &7from the pending whitelist".formatted(removeResult.getDisplayName())
                                : "&7&l> &aSuccessfully &cremoved &f%s &7from the whitelist".formatted(removeResult.getDisplayName())
                ));
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
        List<String> subcommands = getAvailableSubcommands();

        if (args.length == 0) {
            return subcommands;
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return subcommands.stream()
                    .filter(subcommand -> subcommand.startsWith(prefix))
                    .toList();
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("remove")) {
            String prefix = getUsernameArgument(args);
            String lowerCasePrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);

            return MineManiaVelocity.getInstance().getWhitelistManager().getRemovalSuggestions().stream()
                    .filter(playerName -> lowerCasePrefix.isBlank() || playerName.toLowerCase(Locale.ROOT).startsWith(lowerCasePrefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
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

    private String getUsernameArgument(String[] args) {
        if (args.length < 2) {
            return null;
        }

        String username = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        return username.isBlank() ? null : username;
    }

    private List<String> getAvailableSubcommands() {
        return List.of("add", "list", "remove", "reload");
    }
}
