package me.exnfachjan.twitchRandomizer.command;

import me.exnfachjan.twitchRandomizer.twitch.TwitchIntegrationManager;
import me.exnfachjan.twitchRandomizer.twitch.DonationsManager;
import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class ConfigCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "twitchrandomizer.config";

    private final TwitchRandomizer plugin;

    public ConfigCommand(TwitchRandomizer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(ChatColor.RED + "Dir fehlt die Berechtigung: " + PERM);
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "get" -> {
                if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "Benutzung: /trconfig get <pfad>"); return true; }
                String path = args[1];
                Object val = plugin.getConfig().get(path);
                sender.sendMessage(ChatColor.AQUA + path + ChatColor.GRAY + " = " + ChatColor.WHITE + val);
            }
            case "set" -> {
                if (args.length < 3) { sender.sendMessage(ChatColor.YELLOW + "Benutzung: /trconfig set <pfad> <wert>"); return true; }
                String path = args[1];
                String valueRaw = join(args, 2);
                Object parsed = parseValue(valueRaw);
                plugin.getConfig().set(path, parsed);
                plugin.saveConfig();
                plugin.applyDynamicConfig();
                sender.sendMessage(ChatColor.GREEN + "Gesetzt: " + ChatColor.AQUA + path + ChatColor.GRAY + " = " + ChatColor.WHITE + parsed);
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.applyDynamicConfig();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded and applied.");
            }
            case "apply" -> {
                plugin.applyDynamicConfig();
                sender.sendMessage(ChatColor.GREEN + "Aktuelle Config angewendet.");
            }
            case "twitch" -> { return handleTwitch(sender, Arrays.copyOfRange(args, 1, args.length)); }
            case "donations", "don" -> { return handleDonations(sender, Arrays.copyOfRange(args, 1, args.length)); }
            case "weights" -> { return handleWeights(sender, Arrays.copyOfRange(args, 1, args.length)); }
            default -> sendUsage(sender);
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /trconfig donations <reload|status>
    // ─────────────────────────────────────────────────────────────────────────
    private boolean handleDonations(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /trconfig donations <reload|status>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                DonationsManager don = plugin.getDonations();
                if (don == null) {
                    sender.sendMessage(ChatColor.RED + "Donations integration not available.");
                    return true;
                }
                don.applyConfig();
                sender.sendMessage(ChatColor.GREEN + "donations.yml reloaded and reconnected.");
                sender.sendMessage(ChatColor.GRAY + "Check server log for [SE:...] / [Tipeee] messages.");
            }
            case "status" -> {
                DonationsManager don = plugin.getDonations();
                java.io.File donFile = new java.io.File(plugin.getDataFolder(), "donations.yml");

                sender.sendMessage(ChatColor.GOLD + "=== Donations Status ===");
                if (don != null) {
                    sender.sendMessage(ChatColor.GRAY + "Euro/Event: " + ChatColor.WHITE + don.getEuroPerEvent() + "€");
                    sender.sendMessage(ChatColor.GRAY + "Bits/Event: " + ChatColor.WHITE + don.getBitsPerEvent() + " Bits");
                    sender.sendMessage(ChatColor.GRAY + "Events/Sub: " + ChatColor.WHITE + don.getEventsPerSub());
                    sender.sendMessage(ChatColor.GRAY + "StreamElements: " + (don.getSeEnabled() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
                    sender.sendMessage(ChatColor.GRAY + "Tipeeestream:   " + (don.getTipeeeEnabled() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
                }
                sender.sendMessage(ChatColor.GRAY + "Config file: "
                        + (donFile.exists() ? ChatColor.GREEN + "donations.yml found" : ChatColor.RED + "donations.yml missing!"));
                sender.sendMessage(ChatColor.GRAY + "Edit donations.yml then run: " + ChatColor.WHITE + "/trconfig donations reload");
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "Usage: /trconfig donations <reload|status>");
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /trconfig twitch <set|get|reconnect>
    // ─────────────────────────────────────────────────────────────────────────
    private boolean handleTwitch(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Benutzung: /trconfig twitch <set|get|reconnect> ...");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        FileConfiguration cfg = plugin.getConfig();

        switch (sub) {
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.YELLOW + "Benutzung: /trconfig twitch set <channels|token|interval|debug|subs|bits_enabled> <wert>");
                    return true;
                }
                String key = args[1].toLowerCase(Locale.ROOT);
                String value = join(args, 2);
                switch (key) {
                    case "channels" -> {
                        String[] split = value.split("[,;\\s\\n]+");
                        List<String> channelList = Arrays.stream(split).map(String::trim).filter(s -> !s.isEmpty()).toList();
                        cfg.set("twitch.channels", channelList);
                        cfg.set("twitch.channel", null);
                        plugin.saveConfig();
                        plugin.applyDynamicConfig();
                        sender.sendMessage(ChatColor.GREEN + "Twitch-Channels gespeichert: " + String.join(", ", channelList));
                        return true;
                    }
                    case "channel"                              -> cfg.set("twitch.channel", value);
                    case "token", "oauth", "oauth_token"        -> cfg.set("twitch.oauth_token", value);
                    case "interval"                             -> cfg.set("twitch.trigger_interval_seconds", parseDouble(value, 1.0));
                    case "chat_trigger"                         -> cfg.set("twitch.chat_trigger.enabled", parseBoolean(value));
                    case "debug"                                -> cfg.set("twitch.debug", parseBoolean(value));
                    case "subs"                                 -> cfg.set("twitch.triggers.subscriptions.enabled", parseBoolean(value));
                    case "chat_test"                            -> cfg.set("twitch.triggers.chat_test.enabled", parseBoolean(value));
                    case "bits_enabled"                         -> cfg.set("twitch.triggers.bits.enabled", parseBoolean(value));
                    default -> { sender.sendMessage(ChatColor.RED + "Unbekanntes Feld: " + key); return true; }
                }
                plugin.saveConfig();
                plugin.applyDynamicConfig();
                sender.sendMessage(ChatColor.GREEN + "Twitch-Setting aktualisiert: " + key);
            }
            case "get" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Benutzung: /trconfig twitch get <channels|token|interval|debug|subs|bits_enabled>");
                    return true;
                }
                String key = args[1].toLowerCase(Locale.ROOT);
                if (key.equals("channels")) {
                    List<String> channels = cfg.getStringList("twitch.channels");
                    if (channels == null || channels.isEmpty()) {
                        String fallback = cfg.getString("twitch.channel", "");
                        if (fallback != null && !fallback.isBlank()) channels = List.of(fallback);
                    }
                    sender.sendMessage(ChatColor.AQUA + "twitch.channels" + ChatColor.GRAY + " = " + ChatColor.WHITE + String.join(", ", channels));
                } else {
                    String path = switch (key) {
                        case "token", "oauth", "oauth_token" -> "twitch.oauth_token";
                        case "interval"                      -> "twitch.trigger_interval_seconds";
                        case "debug"                         -> "twitch.debug";
                        case "subs"                          -> "twitch.triggers.subscriptions.enabled";
                        case "chat_test"                     -> "twitch.triggers.chat_test.enabled";
                        case "bits_enabled"                  -> "twitch.triggers.bits.enabled";
                        default                              -> null;
                    };
                    if (path == null) { sender.sendMessage(ChatColor.RED + "Unbekanntes Feld: " + key); return true; }
                    sender.sendMessage(ChatColor.AQUA + path + ChatColor.GRAY + " = " + ChatColor.WHITE + cfg.get(path));
                }
            }
            case "reconnect" -> {
                TwitchIntegrationManager tim = plugin.getTwitch();
                if (tim == null) { sender.sendMessage(ChatColor.RED + "Twitch-Integration nicht verfügbar."); return true; }
                tim.applyConfig();
                sender.sendMessage(ChatColor.GREEN + "Twitch (re)konfiguriert.");
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "Benutzung: /trconfig twitch <set|get|reconnect> ...");
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /trconfig weights <show|set <key> <value>>
    // ─────────────────────────────────────────────────────────────────────────
    private boolean handleWeights(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Benutzung: /trconfig weights set <key> <gewicht> | show");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("show")) {
            sender.sendMessage(ChatColor.GOLD + "Aktuelle Event-Gewichte:");
            for (String key : RandomEventCommand.EVENT_KEYS_ORDER) {
                int w = plugin.getConfig().getInt("events.weights." + key, 0);
                sender.sendMessage(ChatColor.YELLOW + "- " + key + ": " + ChatColor.WHITE + w);
            }
            return true;
        }
        if (!sub.equals("set") || args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Benutzung: /trconfig weights set <key> <gewicht>");
            return true;
        }
        String key = args[1];
        int val = parseInt(args[2], -1);
        if (val < 0) { sender.sendMessage(ChatColor.RED + "Ungültige Zahl: " + args[2]); return true; }
        plugin.getConfig().set("events.weights." + key, val);
        plugin.saveConfig();
        plugin.applyDynamicConfig();
        sender.sendMessage(ChatColor.GREEN + "Gewicht gesetzt: " + key + " = " + val);
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Usage + Tab-Completion
    // ─────────────────────────────────────────────────────────────────────────

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Usage:");
        sender.sendMessage(ChatColor.GRAY + "  /trconfig reload                   " + ChatColor.WHITE + "– Reload config.yml & reconnect all");
        sender.sendMessage(ChatColor.GRAY + "  /trconfig donations reload          " + ChatColor.WHITE + "– Reload donations.yml & reconnect SE+Tipeee");
        sender.sendMessage(ChatColor.GRAY + "  /trconfig donations status          " + ChatColor.WHITE + "– Show donations info");
        sender.sendMessage(ChatColor.GRAY + "  /trconfig twitch reconnect          " + ChatColor.WHITE + "– Reconnect Twitch");
        sender.sendMessage(ChatColor.GRAY + "  /trconfig twitch set <key> <value>");
        sender.sendMessage(ChatColor.GRAY + "  /trconfig weights <show|set <key> <weight>>");
        sender.sendMessage(ChatColor.GRAY + "  /trconfig get <path>               " + ChatColor.WHITE + "– Read any config value");
        sender.sendMessage(ChatColor.GRAY + "  /trconfig set <path> <value>       " + ChatColor.WHITE + "– Set any config value");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (!sender.hasPermission(PERM)) return Collections.emptyList();
        if (args.length == 1) {
            return prefixFilter(args[0], List.of("get", "set", "reload", "apply", "twitch", "donations", "weights"));
        }
        if (args.length >= 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "twitch" -> {
                    if (args.length == 2) return prefixFilter(args[1], List.of("set", "get", "reconnect"));
                    if (args.length == 3 && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("get"))) {
                        return prefixFilter(args[2], List.of("channels", "token", "interval", "debug", "subs", "chat_test", "bits_enabled"));
                    }
                }
                case "donations", "don" -> {
                    if (args.length == 2) return prefixFilter(args[1], List.of("reload", "status"));
                }
                case "weights" -> {
                    if (args.length == 2) return prefixFilter(args[1], List.of("show", "set"));
                    if (args.length == 3 && args[1].equalsIgnoreCase("set")) {
                        return prefixFilter(args[2], RandomEventCommand.EVENT_KEYS_ORDER);
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    private List<String> prefixFilter(String prefix, java.util.Collection<String> options) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }

    private Object parseValue(String raw) {
        String s = raw.trim();
        if (s.equalsIgnoreCase("true")) return true;
        if (s.equalsIgnoreCase("false")) return false;
        try {
            if (s.contains(".") || s.contains(",")) return Double.parseDouble(s.replace(",", "."));
            return Integer.parseInt(s);
        } catch (NumberFormatException ignore) { return raw; }
    }

    private int parseInt(String raw, int def) {
        try { return Integer.parseInt(raw.trim()); } catch (Exception e) { return def; }
    }

    private double parseDouble(String raw, double def) {
        try { return Double.parseDouble(raw.trim().replace(',', '.')); } catch (Exception e) { return def; }
    }

    private boolean parseBoolean(String raw) {
        String s = raw.trim();
        return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("on") || s.equals("1");
    }

    private String join(String[] arr, int fromIdx) {
        StringBuilder sb = new StringBuilder();
        for (int i = fromIdx; i < arr.length; i++) {
            if (i > fromIdx) sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }
}