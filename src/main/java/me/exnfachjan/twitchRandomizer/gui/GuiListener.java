package me.exnfachjan.twitchRandomizer.gui;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuiListener implements Listener {

    private final TwitchRandomizer plugin;
    private final ConfigGui gui;

    private static final String TOKEN_URL = "https://twitchtokengenerator.com/quick/1KRFjxyoNE";

    private enum EditType { CHANNEL, TOKEN, SE_TOKEN }

    private final Map<UUID, EditType> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingSince = new ConcurrentHashMap<>();
    private final NamespacedKey keySecureBook;

    public GuiListener(TwitchRandomizer plugin, ConfigGui gui) {
        this.plugin = plugin;
        this.gui = gui;
        this.keySecureBook = new NamespacedKey(plugin, "secure_input");
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!gui.isOurInventory(top)) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;

        ConfigGui.MenuType type = gui.getMenuType(clicked);
        String action = gui.getAction(clicked);
        String path   = gui.getPath(clicked);
        String extra  = gui.getExtra(clicked);
        if (action == null) return;

        HumanEntity he = e.getWhoClicked();
        if (!(he instanceof Player p)) return;

        boolean hasGUIAll = p.hasPermission("twitchrandomizer.gui"); // Super-Node

        // frühe Navigation/Close
        switch (action) {
            case "noop" -> { return; }
            case "close" -> {
                doAutoSave();
                p.closeInventory();
                return;
            }
        }

        if (!hasGUIAll && !p.hasPermission("twitchrandomizer.gui.use")) {
            p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.gui.use");
            return;
        }

        switch (action) {
            case "back_main" -> { doAutoSave(); gui.openMain(p); }
            case "open_trigger" -> gui.openTrigger(p);
            case "open_debug"   -> gui.openDebug(p);
            case "open_weights" -> gui.openWeights(p);
            case "open_misc"    -> gui.openMisc(p);
            case "open_lang"    -> gui.openLanguage(p);

            case "timer_start" -> {
                if (!(hasGUIAll || p.hasPermission("twitchrandomizer.timer.start"))) {
                    p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.timer.start");
                    return;
                }
                try { plugin.getTimerManager().start(); } catch (Throwable ignored) {}
                refreshMenu(type, p);
            }
            case "timer_stop" -> {
                if (!(hasGUIAll || p.hasPermission("twitchrandomizer.timer.stop"))) {
                    p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.timer.stop");
                    return;
                }
                try { plugin.getTimerManager().stop(); } catch (Throwable ignored) {}
                refreshMenu(type, p);
            }
            case "timer_reset" -> {
                if (!(hasGUIAll || p.hasPermission("twitchrandomizer.timer.reset"))) {
                    p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.timer.reset");
                    return;
                }
                try { plugin.getTimerManager().reset(); } catch (Throwable ignored) {}
                refreshMenu(type, p);
            }

            case "save_reconnect" -> {
                if (!(hasGUIAll || p.hasPermission("twitchrandomizer.admin.save"))) {
                    p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.admin.save");
                    return;
                }
                try { plugin.saveConfig(); } catch (Throwable ignored) {}
                try { plugin.applyDynamicConfig(); } catch (Throwable ignored) {}
                p.sendMessage(plugin.getMessages().tr(p, "commands.saved_and_reconfigured"));
            }

            case "toggle" -> {
                if (!(hasGUIAll || p.hasPermission("twitchrandomizer.admin.edit"))) {
                    p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.admin.edit");
                    return;
                }
                FileConfiguration cfg = plugin.getConfig();
                boolean cur = cfg.getBoolean(path, false);
                cfg.set(path, !cur);
                try { plugin.saveConfig(); } catch (Throwable ignored) {}
                try { plugin.applyDynamicConfig(); } catch (Throwable ignored) {}
                refreshMenu(type, p);
                p.sendMessage(ChatColor.GREEN + "Umschalter: " + ChatColor.AQUA + path + ChatColor.GRAY + " = " + ChatColor.WHITE + !cur);
            }

            case "adjust_int_bits" -> {
                if (!(hasGUIAll || p.hasPermission("twitchrandomizer.admin.edit"))) {
                    p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.admin.edit");
                    return;
                }
                int step  = e.isShiftClick() ? 100 : 1;
                int delta = e.isRightClick() ? +step : -step;
                FileConfiguration cfg = plugin.getConfig();
                int cur = Math.max(1, cfg.getInt(path, 500));
                int val = Math.max(1, cur + delta);
                cfg.set(path, val);
                try { plugin.saveConfig(); } catch (Throwable ignored) {}
                try { plugin.applyDynamicConfig(); } catch (Throwable ignored) {}
                refreshMenu(type, p);
                p.sendMessage(ChatColor.GREEN + "Bits/Trigger: " + ChatColor.WHITE + cur + ChatColor.GRAY + " -> " + ChatColor.AQUA + val);
            }

            case "adjust_double_interval" -> {
                if (!(hasGUIAll || p.hasPermission("twitchrandomizer.admin.edit"))) {
                    p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.admin.edit");
                    return;
                }
                double step  = e.isShiftClick() ? 1.0 : 0.5;
                double delta = e.isRightClick() ? +step : -step;
                FileConfiguration cfg = plugin.getConfig();
                double cur = cfg.getDouble(path, 1.0);
                double val = Math.max(0.05, cur + delta);
                cfg.set(path, val);
                try { plugin.saveConfig(); } catch (Throwable ignored) {}
                try { plugin.applyDynamicConfig(); } catch (Throwable ignored) {}
                refreshMenu(type, p);
                p.sendMessage(ChatColor.GREEN + "Intervall: " + ChatColor.WHITE + String.format("%.2f", cur) + "s" + ChatColor.GRAY + " -> " + ChatColor.AQUA + String.format("%.2f", val) + "s");
            }

            case "adjust_double_donations" -> {
                if (!(hasGUIAll || p.hasPermission("twitchrandomizer.admin.edit"))) {
                    p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.admin.edit");
                    return;
                }
                double step  = e.isShiftClick() ? 1.0 : 0.5;
                double delta = e.isRightClick() ? +step : -step;
                FileConfiguration cfg = plugin.getConfig();
                double cur = cfg.getDouble(path, 5.0);
                double val = Math.max(0.5, cur + delta);
                // Auf 1 Nachkommastelle runden
                val = Math.round(val * 10.0) / 10.0;
                cfg.set(path, val);
                try { plugin.saveConfig(); } catch (Throwable ignored) {}
                try { plugin.applyDynamicConfig(); } catch (Throwable ignored) {}
                refreshMenu(type, p);
                p.sendMessage(ChatColor.GREEN + "Donation/Trigger: " + ChatColor.WHITE + cur + ChatColor.GRAY + " -> " + ChatColor.AQUA + val);
            }

            case "weight_adjust" -> {
                if (!(hasGUIAll || p.hasPermission("twitchrandomizer.admin.edit"))) {
                    p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.admin.edit");
                    return;
                }
                int step = e.isShiftClick() ? 10 : 1;
                int delta = e.isRightClick() ? +step : -step;
                FileConfiguration cfg = plugin.getConfig();
                int cur = Math.max(0, cfg.getInt(path, 0));
                int val = Math.max(0, cur + delta);
                cfg.set(path, val);
                try { plugin.saveConfig(); } catch (Throwable ignored) {}
                try { plugin.applyDynamicConfig(); } catch (Throwable ignored) {}
                refreshMenu(type, p);
            }

            case "reset_weights_defaults" -> {
                if (!(hasGUIAll || p.hasPermission("twitchrandomizer.admin.edit"))) {
                    p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.admin.edit");
                    return;
                }
                Map<String, Integer> defaults = plugin.getDefaultWeights();
                for (Map.Entry<String, Integer> entry : defaults.entrySet()) {
                    plugin.getConfig().set("events.weights." + entry.getKey(), entry.getValue());
                }
                plugin.saveConfig();
                plugin.applyDynamicConfig();
                p.sendMessage(ChatColor.GREEN + "Alle Gewichte wurden auf Standardwerte zurückgesetzt!");
                gui.openWeights(p);
            }

            case "misc_deaths_adjust" -> {
                if (!(hasGUIAll || p.hasPermission("twitchrandomizer.admin.edit"))) {
                    p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.admin.edit");
                    return;
                }
                if (e.isShiftClick()) {
                    plugin.getDeathCounter().clear();
                } else if (e.isRightClick()) {
                    plugin.getDeathCounter().increment();
                } else {
                    plugin.getDeathCounter().set(plugin.getDeathCounter().get() - 1);
                }
                plugin.getDeathCounter().broadcastActionbar();
                refreshMenu(type, p);
            }

            case "misc_reset_confirm" -> {
                if (!(hasGUIAll || p.hasPermission("twitchrandomizer.reset"))) {
                    p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.reset");
                    return;
                }
                p.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () ->
                        Bukkit.dispatchCommand(p, "reset"));
            }

            case "lang_set" -> {
                if (extra != null) {
                    plugin.getMessages().setPlayerLanguage(p, extra);
                    plugin.getMessages().savePlayerLocales();
                }
                refreshMenu(type, p);
            }

            case "edit_twitch" -> {
                if (!(hasGUIAll || p.hasPermission("twitchrandomizer.admin.twitch"))) {
                    p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.admin.twitch");
                    return;
                }

                EditType edit;
                if (e.isShiftClick() && e.isRightClick()) {
                    edit = EditType.SE_TOKEN;
                } else if (e.isRightClick()) {
                    edit = EditType.TOKEN;
                } else {
                    edit = EditType.CHANNEL;
                }

                p.closeInventory();
                p.sendMessage(plugin.getMessages().tr(p, "gui.book.chat.warn_sensitive"));

                String chatKey = switch (edit) {
                    case CHANNEL  -> "gui.book.chat.received_channel";
                    case TOKEN    -> "gui.book.chat.received_token";
                    case SE_TOKEN -> "gui.book.chat.received_se_token";
                };
                p.sendMessage(plugin.getMessages().tr(p, chatKey));

                pending.put(p.getUniqueId(), edit);
                pendingSince.put(p.getUniqueId(), System.currentTimeMillis());
                giveSecureBook(p, edit);
            }
        }
    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!gui.isOurInventory(top)) return;
        doAutoSave();
    }

    private void doAutoSave() {
        try { plugin.saveConfig(); } catch (Throwable ignored) {}
        try { plugin.applyDynamicConfig(); } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onPlayerEditBook(PlayerEditBookEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        EditType mode = pending.get(id);
        if (mode == null) return;

        boolean guiAll = p.hasPermission("twitchrandomizer.gui");
        if (!(guiAll || p.hasPermission("twitchrandomizer.admin.twitch"))) {
            e.setCancelled(true);
            pending.remove(id);
            pendingSince.remove(id);
            p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.admin.twitch");
            return;
        }

        String text = "";
        BookMeta newMeta = e.getNewBookMeta();
        if (newMeta != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= newMeta.getPageCount(); i++) {
                String page = newMeta.getPage(i);
                if (page != null) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(page.trim());
                }
            }
            text = sb.toString();
            if ((text == null || text.isEmpty()) && newMeta.getTitle() != null) {
                text = newMeta.getTitle().trim();
            }
        }

        e.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> removeSecureBooks(p));

        pending.remove(id);
        pendingSince.remove(id);

        if (text == null || text.isEmpty()) {
            p.sendMessage(plugin.getMessages().tr(p, "gui.book.chat.cancelled"));
            Bukkit.getScheduler().runTask(plugin, () -> gui.openMain(p));
            return;
        }

        FileConfiguration cfg = plugin.getConfig();

        switch (mode) {
            case CHANNEL -> {
                String input = text.trim().replace(" ", "");
                String[] split = input.split("[,;\\n]+");
                java.util.List<String> channels = new java.util.ArrayList<>();
                for (String s : split) {
                    if (!s.isBlank()) {
                        String ch = s;
                        if (ch.startsWith("#")) ch = ch.substring(1);
                        channels.add(ch);
                    }
                }
                if (!channels.isEmpty()) {
                    cfg.set("twitch.channels", channels);
                    cfg.set("twitch.channel", null);
                    p.sendMessage(plugin.getMessages().tr(p, "gui.book.chat.saved_channel_list", Map.of("channels", String.join(", ", channels))));
                } else {
                    p.sendMessage(plugin.getMessages().tr(p, "gui.book.chat.no_valid_channels"));
                }
            }
            case TOKEN -> {
                String token = normalizeToken(text);
                cfg.set("twitch.oauth_token", token);
                p.sendMessage(plugin.getMessages().tr(p, "gui.book.chat.saved_token"));
            }
            case SE_TOKEN -> {
                String jwt = text.trim();
                // Anführungszeichen entfernen falls vorhanden
                if (jwt.length() >= 2 && jwt.startsWith("\"") && jwt.endsWith("\"")) {
                    jwt = jwt.substring(1, jwt.length() - 1).trim();
                }
                cfg.set("streamelements.jwt_token", jwt);
                p.sendMessage(plugin.getMessages().tr(p, "gui.book.chat.saved_se_token"));
            }
        }

        try { plugin.saveConfig(); } catch (Throwable ignored) {}
        try { plugin.applyDynamicConfig(); } catch (Throwable ignored) {}

        Bukkit.getScheduler().runTask(plugin, () -> gui.openMain(p));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!pending.containsKey(p.getUniqueId())) return;

        var from = e.getFrom();
        var to = e.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        boolean removed = removeSecureBooks(p);
        pending.remove(p.getUniqueId());
        pendingSince.remove(p.getUniqueId());

        if (removed) {
            p.sendMessage(plugin.getMessages().tr(p, "gui.book.chat.cancelled"));
        }
    }

    private boolean removeSecureBooks(Player p) {
        boolean removed = false;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it == null || !it.hasItemMeta()) continue;
            ItemMeta im = it.getItemMeta();
            String v = im.getPersistentDataContainer().get(keySecureBook, PersistentDataType.STRING);
            if (v != null) {
                p.getInventory().setItem(i, null);
                removed = true;
            }
        }
        ItemStack main = p.getInventory().getItemInMainHand();
        if (main != null && main.hasItemMeta()) {
            ItemMeta im = main.getItemMeta();
            String v = im.getPersistentDataContainer().get(keySecureBook, PersistentDataType.STRING);
            if (v != null) { p.getInventory().setItemInMainHand(null); removed = true; }
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        if (off != null && off.hasItemMeta()) {
            ItemMeta im = off.getItemMeta();
            String v = im.getPersistentDataContainer().get(keySecureBook, PersistentDataType.STRING);
            if (v != null) { p.getInventory().setItemInOffHand(null); removed = true; }
        }
        return removed;
    }

    private void refreshMenu(ConfigGui.MenuType type, Player p) {
        if (type == null) { gui.openMain(p); return; }
        switch (type) {
            case MAIN     -> gui.openMain(p);
            case TRIGGER  -> gui.openTrigger(p);
            case DEBUG    -> gui.openDebug(p);
            case WEIGHTS  -> gui.openWeights(p);
            case MISC     -> gui.openMisc(p);
            case LANGUAGE -> gui.openLanguage(p);
        }
    }

    private void giveSecureBook(Player p, EditType type) {
        ItemStack book = new ItemStack(org.bukkit.Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            try { meta.setTitle(plugin.getMessages().tr(p, "gui.book.title")); } catch (Throwable ignored) {}
            meta.setAuthor("TwitchRandomizer");

            String pageText = switch (type) {
                case CHANNEL  -> plugin.getMessages().tr(p, "gui.book.page.channel");
                case TOKEN    -> plugin.getMessages().tr(p, "gui.book.page.token.text",
                        java.util.Map.of("url", TOKEN_URL));
                case SE_TOKEN -> plugin.getMessages().tr(p, "gui.book.page.se_token.text");
            };
            meta.addPage(pageText);

            meta.getPersistentDataContainer().set(keySecureBook, PersistentDataType.STRING, type.name());
            book.setItemMeta(meta);
        }

        java.util.Map<Integer, org.bukkit.inventory.ItemStack> left = p.getInventory().addItem(book);
        if (!left.isEmpty()) {
            p.getWorld().dropItemNaturally(p.getLocation(), book);
            p.sendMessage(plugin.getMessages().tr(p, "gui.book.chat.inventory_full"));
        }
    }

    @SuppressWarnings("unused")
    private String normalizeToken(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.length() >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))) {
            t = t.substring(1, t.length() - 1).trim();
        }
        if (t.toLowerCase(java.util.Locale.ROOT).startsWith("oauth:")) {
            t = t.substring("oauth:".length());
        }
        return t;
    }
}