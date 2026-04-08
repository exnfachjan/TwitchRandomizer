package me.exnfachjan.twitchRandomizer.command;

import me.exnfachjan.twitchRandomizer.twitch.TwitchIntegrationManager;
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
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Benutzung: /trconfig get <pfad>");
                    return true;
                }
                String path = args[1];
                Object val = plugin.getConfig().get(path);
                sender.sendMessage(ChatColor.AQUA + path + ChatColor.GRAY + " = " + ChatColor.WHITE + String.valueOf(val));
            }
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.YELLOW + "Benutzung: /trconfig set <pfad> <wert>");
                    return true;
                }
                String path = args[1];
                String valueRaw = join(args, 2);
                Object parsed = parseValue(valueRaw);
                plugin.getConfig().set(path, parsed);
                plugin.saveConfig();
                plugin.applyDynamicConfig();
                sender.sendMessage(ChatColor.GREEN + "Gesetzt: " + ChatColor.AQUA + path + ChatColor.GRAY + " = " + ChatColor.WHITE + parsed);
            }
            case "reload" -> {
                // Liest die config.yml von Disk neu ein und wendet alles an
                plugin.reloadConfig();
                plugin.applyDynamicConfig();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded and applied (Twitch + SE + Weights).");
            }
            case "apply" -> {
                plugin.applyDynamicConfig();
                sender.sendMessage(ChatColor.GREEN + "Aktuelle Config angewendet.");
            }
            case "twitch" -> {
                return handleTwitch(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            case "se" -> {
                return handleSE(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            case "weights" -> {
                return handleWeights(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /trconfig se <reload|status>
    // ─────────────────────────────────────────────────────────────────────────
    private boolean handleSE(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /trconfig se <reload|status>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                // Liest streamelements.yml direkt von Disk (nicht plugin.reloadConfig()!)
                var se = plugin.getStreamElements();
                if (se == null) {
                    sender.sendMessage(ChatColor.RED + "StreamElements integration not available.");
                    return true;
                }
                se.applyConfig();
                sender.sendMessage(ChatColor.GREEN + "streamelements.yml reloaded and SE reconnected.");
                sender.sendMessage(ChatColor.GRAY + "Check server log for [SE:...] messages.");
            }
            case "status" -> {
                boolean enabled = plugin.getConfig().getBoolean("streamelements.enabled", false);
                double amount = plugin.getConfig().getDouble("streamelements.triggers.tips.amount_per_trigger", 5.0);
                java.io.File seFile = new java.io.File(plugin.getDataFolder(), "streamelements.yml");

                sender.sendMessage(ChatColor.GOLD + "=== StreamElements Status ===");
                sender.sendMessage(ChatColor.GRAY + "Enabled: " + (enabled ? ChatColor.GREEN + "yes" : ChatColor.RED + "no"));
                sender.sendMessage(ChatColor.GRAY + "Amount per trigger: " + ChatColor.WHITE + amount);
                sender.sendMessage(ChatColor.GRAY + "Config file: " + ChatColor.WHITE
                        + (seFile.exists() ? ChatColor.GREEN + "streamelements.yml found" : ChatColor.RED + "streamelements.yml missing!"));
                sender.sendMessage(ChatColor.GRAY + "To change tokens: edit " + ChatColor.WHITE + "streamelements.yml"
                        + ChatColor.GRAY + " then run " + ChatColor.WHITE + "/trconfig se reload");
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "Usage: /trconfig se <reload|status>");
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
                    sender.sendMessage(ChatColor.YELLOW + "Benutzung: /trconfig twitch set <channels|token|interval|debug|subs|bits_enabled|bits_per_trigger> <wert>");
                    return true;
                }
                String key = args[1].toLowerCase(Locale.ROOT);
                String value = join(args, 2);
                switch (key) {
                    case "channels" -> {
                        String[] split = value.split("[,;\\s\\n]+");
                        List<String> channelList = Arrays.stream(split)
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .toList();
                        cfg.set("twitch.channels", channelList);
                        cfg.set("twitch.channel", null);
                        plugin.saveConfig();
                        plugin.applyDynamicConfig();
                        sender.sendMessage(ChatColor.GREEN + "Twitch-Channels gespeichert: " + String.join(", ", channelList));
                        return true;
                    }
                    case "channel" -> cfg.set("twitch.channel", value);
                    case "token", "oauth", "oauth_token" -> cfg.set("twitch.oauth_token", value);
                    case "interval" -> cfg.set("twitch.trigger_interval_seconds", parseDouble(value, 1.0));
                    case "chat_trigger" -> cfg.set("twitch.chat_trigger.enabled", parseBoolean(value));
                    case "debug" -> cfg.set("twitch.debug", parseBoolean(value));
                    case "subs" -> cfg.set("twitch.triggers.subscriptions.enabled", parseBoolean(value));
                    case "chat_test" -> cfg.set("twitch.triggers.chat_test.enabled", parseBoolean(value));
                    case "bits_enabled" -> cfg.set("twitch.triggers.bits.enabled", parseBoolean(value));
                    case "bits_per_trigger" -> cfg.set("twitch.triggers.bits.bits_per_trigger", parseInt(value, 500));
                    default -> {
                        sender.sendMessage(ChatColor.RED + "Unbekanntes Feld: " + key);
                        return true;
                    }
                }
                plugin.saveConfig();
                plugin.applyDynamicConfig();
                sender.sendMessage(ChatColor.GREEN + "Twitch-Setting aktualisiert: " + key);
            }
            case "get" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Benutzung: /trconfig twitch get <channels|token|interval|debug|subs|bits_enabled|bits_per_trigger>");
                    return true;
                }
                String key = args[1].toLowerCase(Locale.ROOT);
                switch (key) {
                    case "channels" -> {
                        List<String> channels = cfg.getStringList("twitch.channels");
                        if (channels == null || channels.isEmpty()) {
                            String fallback = cfg.getString("twitch.channel", "");
                            if (fallback != null && !fallback.isBlank()) channels = List.of(fallback);
                        }
                        sender.sendMessage(ChatColor.AQUA + "twitch.channels" + ChatColor.GRAY + " = " + ChatColor.WHITE + String.join(", ", channels));
                    }
                    default -> {
                        String path = switch (key) {
                            case "token", "oauth", "oauth_token" -> "twitch.oauth_token";
                            case "interval" -> "twitch.trigger_interval_seconds";
                            case "debug" -> "twitch.debug";
                            case "subs" -> "twitch.triggers.subscriptions.enabled";
                            case "chat_test" -> "twitch.triggers.chat_test.enabled";
                            case "bits_enabled" -> "twitch.triggers.bits.enabled";
                            case "bits_per_trigger" -> "twitch.triggers.bits.bits_per_trigger";
                            default -> null;
                        };
                        if (path == null) {
                            sender.sendMessage(ChatColor.RED + "Unbekanntes Feld: " + key);
                            return true;
                        }
                        Object val = cfg.get(path);
                        sender.sendMessage(ChatColor.AQUA + path + ChatColor.GRAY + " = " + ChatColor.WHITE + String.valueOf(val));
                    }
                }
            }
            case "reconnect" -> {
                TwitchIntegrationManager tim = plugin.getTwitch();
                if (tim == null) {
                    sender.sendMessage(ChatColor.RED + "Twitch-Integration nicht verfügbar.");
                    return true;
                }
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
        if (val < 0) {
            sender.sendMessage(ChatColor.RED + "Ungültige Zahl: " + args[2]);
            return true;
        }
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
        sender.sendMessage(ChatColor.GRAY + "  /trconfig reload" + ChatColor.WHITE + "               – Reload config.yml from disk & reconnect all");
        sender.sendMessage(ChatColor.GRAY + "  /trconfig se reload" + ChatColor.WHITE + "            – Reload config.yml & reconnect SE only");
        sender.sendMessage(ChatColor.GRAY + "  /trconfig se status" + ChatColor.WHITE + "            – Show SE connection info");
        sender.sendMessage(ChatColor.GRAY + "  /trconfig twitch reconnect" + ChatColor.WHITE + "     – Reconnect Twitch");
        sender.sendMessage(ChatColor.GRAY + "  /trconfig twitch set <key> <value>");
        sender.sendMessage(ChatColor.GRAY + "  /trconfig weights <show|set <key> <weight>>");
        sender.sendMessage(ChatColor.GRAY + "  /trconfig get <path>" + ChatColor.WHITE + "           – Read any config value");
        sender.sendMessage(ChatColor.GRAY + "  /trconfig set <path> <value>" + ChatColor.WHITE + "   – Set any config value");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (!sender.hasPermission(PERM)) return Collections.emptyList();
        if (args.length == 1) {
            return prefixFilter(args[0], List.of("get", "set", "reload", "apply", "twitch", "se", "weights"));
        }
        if (args.length >= 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "twitch" -> {
                    if (args.length == 2) return prefixFilter(args[1], List.of("set", "get", "reconnect"));
                    if (args.length == 3 && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("get"))) {
                        return prefixFilter(args[2], List.of("channels", "token", "interval", "debug",
                                "subs", "chat_test", "bits_enabled", "bits_per_trigger"));
                    }
                }
                case "se" -> {
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

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Object parseValue(String raw) {
        String s = raw.trim();
        if (s.equalsIgnoreCase("true")) return true;
        if (s.equalsIgnoreCase("false")) return false;
        try {
            if (s.contains(".") || s.contains(",")) {
                return Double.parseDouble(s.replace(",", "."));
            }
            return Integer.parseInt(s);
        } catch (NumberFormatException ignore) {
            return raw;
        }
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