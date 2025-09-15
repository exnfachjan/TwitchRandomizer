package me.exnfachjan.twitchRandomizer.gui;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.command.RandomEventCommand;
import me.exnfachjan.twitchRandomizer.i18n.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class ConfigGui {

    public enum MenuType { MAIN, WEIGHTS, TRIGGER, DEBUG, MISC, LANGUAGE }

    private final TwitchRandomizer plugin;
    private final Messages i18n;
    private final org.bukkit.NamespacedKey keyMenu;
    private final org.bukkit.NamespacedKey keyAction;
    private final org.bukkit.NamespacedKey keyPath;
    private final org.bukkit.NamespacedKey keyExtra;

    public ConfigGui(TwitchRandomizer plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getMessages();
        this.keyMenu = new org.bukkit.NamespacedKey(plugin, "tr_menu");
        this.keyAction = new org.bukkit.NamespacedKey(plugin, "tr_action");
        this.keyPath = new org.bukkit.NamespacedKey(plugin, "tr_path");
        this.keyExtra = new org.bukkit.NamespacedKey(plugin, "tr_extra");
    }

    // Mapping of event keys to unique/fitting icons (avoid duplicates)
    private static final Map<String, Material> EVENT_ICON = Map.ofEntries(
            Map.entry("spawn_mobs", Material.SPAWNER),
            Map.entry("potion", Material.POTION),
            Map.entry("give_item", Material.CHEST),
            Map.entry("clear_inventory", Material.BARRIER),
            Map.entry("teleport", Material.ENDER_PEARL),
            Map.entry("damage_half_heart", Material.GOLDEN_APPLE),
            Map.entry("fire", Material.FLINT_AND_STEEL),
            Map.entry("inv_shuffle", Material.SHULKER_BOX),
            Map.entry("hot_potato", Material.BAKED_POTATO),
            Map.entry("no_crafting", Material.CRAFTING_TABLE),
            Map.entry("safe_creepers", Material.CREEPER_HEAD),
            Map.entry("floor_is_lava", Material.MAGMA_BLOCK),
            Map.entry("nasa_call", Material.FIREWORK_ROCKET),
            Map.entry("slippery_ground", Material.PACKED_ICE),
            Map.entry("hell_is_calling", Material.FIRE_CHARGE)
    );

    public void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(p, 27, i18n.tr(p, "gui.titles.main"));
        FileConfiguration cfg = plugin.getConfig();

        String channel = String.valueOf(cfg.getString("twitch.channel", ""));
        String token = String.valueOf(cfg.getString("twitch.oauth_token", ""));
        Map<String, String> ph = new HashMap<>();
        ph.put("channel", channel.isBlank() ? "(leer/empty)" : channel);
        ph.put("token_masked", token.isBlank() ? "(leer/empty)" : mask(token));

        inv.setItem(0, tag(MenuType.MAIN,
                item(Material.BOOK,
                        i18n.tr(p, "gui.main.twitch_info_name"),
                        i18n.trList(p, "gui.main.twitch_info_lore", ph)),
                "edit_twitch", null, null));

        inv.setItem(4, tag(MenuType.MAIN,
                item(Material.CHEST,
                        i18n.tr(p, "gui.main.misc_name"),
                        i18n.trList(p, "gui.main.misc_lore")),
                "open_misc", null, null));

        inv.setItem(8, tag(MenuType.MAIN,
                item(Material.KNOWLEDGE_BOOK,
                        i18n.tr(p, "gui.main.ui_language_name"),
                        i18n.trList(p, "gui.main.ui_language_lore")),
                "open_lang", null, null));

        inv.setItem(11, tag(MenuType.MAIN,
                item(Material.COMMAND_BLOCK,
                        i18n.tr(p, "gui.main.debug_name"),
                        i18n.trList(p, "gui.main.debug_lore")),
                "open_debug", null, null));

        inv.setItem(13, tag(MenuType.MAIN,
                item(Material.GOLD_INGOT,
                        i18n.tr(p, "gui.main.trigger_name"),
                        i18n.trList(p, "gui.main.trigger_lore")),
                "open_trigger", null, null));

        inv.setItem(15, tag(MenuType.MAIN,
                item(Material.PAPER,
                        i18n.tr(p, "gui.main.weights_name"),
                        i18n.trList(p, "gui.main.weights_lore")),
                "open_weights", null, null));

        inv.setItem(18, tag(MenuType.MAIN,
                item(Material.REPEATER,
                        i18n.tr(p, "gui.common.save_reconnect_name"),
                        i18n.trList(p, "gui.common.save_reconnect_lore")),
                "save_reconnect", null, null));

        inv.setItem(21, tag(MenuType.MAIN,
                item(Material.LIME_DYE,
                        i18n.tr(p, "gui.main.timer_start"),
                        i18n.trList(p, "gui.main.timer_start_lore")),
                "timer_start", null, null));
        inv.setItem(22, tag(MenuType.MAIN,
                item(Material.ORANGE_DYE,
                        i18n.tr(p, "gui.main.timer_stop"),
                        i18n.trList(p, "gui.main.timer_stop_lore")),
                "timer_stop", null, null));
        inv.setItem(23, tag(MenuType.MAIN,
                item(Material.RED_DYE,
                        i18n.tr(p, "gui.main.timer_reset"),
                        i18n.trList(p, "gui.main.timer_reset_lore")),
                "timer_reset", null, null));

        inv.setItem(26, tag(MenuType.MAIN,
                item(Material.BARRIER,
                        i18n.tr(p, "gui.main.close"), null),
                "close", null, null));

        p.openInventory(inv);
    }

    public void openTrigger(Player p) {
        Inventory inv = Bukkit.createInventory(p, 27, i18n.tr(p, "gui.titles.trigger"));
        FileConfiguration cfg = plugin.getConfig();

        // Save/Reload
        inv.setItem(18, tag(MenuType.TRIGGER,
                item(Material.REPEATER,
                        i18n.tr(p, "gui.common.save_reconnect_name"),
                        i18n.trList(p, "gui.common.save_reconnect_lore")),
                "save_reconnect", null, null));

        // Toggles
        inv.setItem(10, toggle(p, MenuType.TRIGGER, "twitch.triggers.subscriptions.enabled", i18n.tr(p, "gui.trigger.subs_toggle")));
        inv.setItem(11, toggle(p, MenuType.TRIGGER, "twitch.triggers.bits.enabled", i18n.tr(p, "gui.trigger.bits_toggle")));

        // Bits per trigger → Slot 15
        int bpt = Math.max(1, cfg.getInt("twitch.triggers.bits.bits_per_trigger", 500));
        Map<String, String> phBits = Map.of("value", String.valueOf(bpt));
        inv.setItem(15, tag(MenuType.TRIGGER,
                item(Material.EMERALD,
                        i18n.tr(p, "gui.trigger.bits_per_trigger_name", phBits),
                        i18n.trList(p, "gui.trigger.bits_per_trigger_lore")),
                "adjust_int_bits", "twitch.triggers.bits.bits_per_trigger", null));

        // Interval → Slot 16
        double seconds = cfg.getDouble("twitch.trigger_interval_seconds", 1.0);
        Map<String, String> phInt = Map.of("seconds", String.format(Locale.US, "%.2f", seconds));
        inv.setItem(16, tag(MenuType.TRIGGER,
                item(Material.CLOCK,
                        i18n.tr(p, "gui.trigger.interval_name", phInt),
                        i18n.trList(p, "gui.trigger.interval_lore")),
                "adjust_double_interval", "twitch.trigger_interval_seconds", null));
// Zurück
        inv.setItem(26, tag(MenuType.TRIGGER,
                item(Material.ARROW, i18n.tr(p, "gui.common.back"), null),
                "back_main", null, null));

        p.openInventory(inv);
    }

    public void openDebug(Player p) {
        Inventory inv = Bukkit.createInventory(p, 27, i18n.tr(p, "gui.titles.debug"));

        inv.setItem(18, tag(MenuType.DEBUG,
                item(Material.REPEATER,
                        i18n.tr(p, "gui.common.save_reconnect_name"),
                        i18n.trList(p, "gui.common.save_reconnect_lore")),
                "save_reconnect", null, null));

        inv.setItem(11, toggle(p, MenuType.DEBUG, "twitch.triggers.chat_test.enabled", i18n.tr(p, "gui.debug.test_toggle")));
        inv.setItem(13, toggle(p, MenuType.DEBUG, "twitch.triggers.sim_gift.enabled", i18n.tr(p, "gui.debug.gift_toggle")));
        inv.setItem(15, toggle(p, MenuType.DEBUG, "twitch.triggers.sim_giftbomb.enabled", i18n.tr(p, "gui.debug.giftbomb_toggle")));

        inv.setItem(26, tag(MenuType.DEBUG,
                item(Material.ARROW, i18n.tr(p, "gui.common.back"), null),
                "back_main", null, null));

        p.openInventory(inv);
    }

    public void openWeights(Player p) {
        int size = 54;
        Inventory inv = Bukkit.createInventory(p, size, i18n.tr(p, "gui.titles.weights"));
        FileConfiguration cfg = plugin.getConfig();

        int slot = 0;
        for (String key : RandomEventCommand.EVENT_KEYS_ORDER) {
            int w = Math.max(0, cfg.getInt("events.weights." + key, 0));
            Map<String, String> ph = Map.of("key", key, "weight", String.valueOf(w));
            Material icon = EVENT_ICON.getOrDefault(key, Material.PAPER);
            inv.setItem(slot++, tag(MenuType.WEIGHTS,
                    item(icon,
                            i18n.tr(p, "gui.weights.paper_name", ph),
                            i18n.trList(p, "gui.weights.paper_lore", ph)),
                    "weight_adjust", "events.weights." + key, key));
        }

        inv.setItem(45, tag(MenuType.WEIGHTS,
                item(Material.REPEATER,
                        i18n.tr(p, "gui.common.save_reconnect_name"),
                        i18n.trList(p, "gui.common.save_reconnect_lore")),
                "save_reconnect", null, null));

        inv.setItem(size - 1, tag(MenuType.WEIGHTS,
                item(Material.ARROW, i18n.tr(p, "gui.common.back"), null),
                "back_main", null, null));

        p.openInventory(inv);
    }

    public void openMisc(Player p) {
        Inventory inv = Bukkit.createInventory(p, 27, i18n.tr(p, "gui.titles.misc"));

        inv.setItem(18, tag(MenuType.MISC,
                item(Material.REPEATER,
                        i18n.tr(p, "gui.common.save_reconnect_name"),
                        i18n.trList(p, "gui.common.save_reconnect_lore")),
                "save_reconnect", null, null));

        inv.setItem(11, toggle(p, MenuType.MISC, "challenge.auto_spectator_on_death", i18n.tr(p, "gui.misc.spectator_toggle")));

        int deaths = plugin.getConfig().getInt("stats.deaths", 0);
        Map<String, String> ph = Map.of("value", String.valueOf(deaths));
        inv.setItem(13, tag(MenuType.MISC,
                item(Material.SKELETON_SKULL,
                        i18n.tr(p, "gui.misc.deaths_name", ph),
                        i18n.trList(p, "gui.misc.deaths_lore")),
                "death_counter", "stats.deaths", null));

        inv.setItem(15, tag(MenuType.MISC,
                item(Material.REDSTONE_BLOCK,
                        i18n.tr(p, "gui.misc.reset_button_name"),
                        i18n.trList(p, "gui.misc.reset_button_lore")),
                "open_reset_confirm", null, null));

        inv.setItem(26, tag(MenuType.MISC,
                item(Material.ARROW, i18n.tr(p, "gui.common.back"), null),
                "back_main", null, null));

        p.openInventory(inv);
    }

    public void openResetConfirm(Player p) {
        Inventory inv = Bukkit.createInventory(p, 27, i18n.tr(p, "gui.titles.reset_confirm"));

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ", null));
        }

        inv.setItem(11, tag(MenuType.MISC,
                item(Material.BARRIER, i18n.tr(p, "gui.reset_confirm.cancel"), null),
                "reset_cancel", null, null));

        inv.setItem(15, tag(MenuType.MISC,
                item(Material.TNT, i18n.tr(p, "gui.reset_confirm.confirm"), null),
                "reset_force", null, null));

        p.openInventory(inv);
    }

    public void openLanguage(Player p) {
        Inventory inv = Bukkit.createInventory(p, 27, i18n.tr(p, "gui.titles.ui_language"));

        String cur = i18n.currentLanguage(p);
        Map<String,String> phDe = Map.of("lang", "Deutsch");
        Map<String,String> phEn = Map.of("lang", "English");

        // Deutsch: Yellow Dye
        inv.setItem(11, tag(MenuType.LANGUAGE,
                item(Material.YELLOW_DYE,
                        i18n.tr(p, "gui.lang.de_name"),
                        mergeLore(i18n.trList(p, "gui.lang.de_lore"), i18n.tr(p, "gui.lang.current_prefix", phDe), cur.equals("de"))),
                "set_lang", null, "de"));

        // Englisch: Blue Dye
        inv.setItem(15, tag(MenuType.LANGUAGE,
                item(Material.BLUE_DYE,
                        i18n.tr(p, "gui.lang.en_name"),
                        mergeLore(i18n.trList(p, "gui.lang.en_lore"), i18n.tr(p, "gui.lang.current_prefix", phEn), cur.equals("en"))),
                "set_lang", null, "en"));

        inv.setItem(26, tag(MenuType.LANGUAGE,
                item(Material.ARROW, i18n.tr(p, "gui.common.back"), null),
                "back_main", null, null));

        p.openInventory(inv);
    }

    /* ===== Helpers ===== */

    private ItemStack toggle(Player p, MenuType menu, String path, String label) {
        boolean val = plugin.getConfig().getBoolean(path, false);
        Material mat = val ? Material.LIME_DYE : Material.GRAY_DYE;

        Map<String, String> ph = Map.of("label", label);
        String name = i18n.tr(p, val ? "toggles.on_prefix" : "toggles.off_prefix", ph);
        List<String> lore = List.of(ChatColor.GRAY + "Klick/Click to toggle");

        ItemStack it = item(mat, name, lore);
        return tag(menu, it, "toggle", path, null);
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(new ArrayList<>(lore));
        addFlag(meta, "HIDE_ATTRIBUTES");
        addFlag(meta, "HIDE_POTION_EFFECTS");
        addFlag(meta, "HIDE_ENCHANTS");
        it.setItemMeta(meta);
        return it;
    }

    private List<String> mergeLore(List<String> base, String currentLine, boolean isCurrent) {
        List<String> out = new ArrayList<>();
        if (base != null) out.addAll(base);
        if (isCurrent) out.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "✔ " + ChatColor.RESET + ChatColor.YELLOW + currentLine);
        return out;
    }

    private void addFlag(ItemMeta meta, String flagName) {
        try { meta.addItemFlags(ItemFlag.valueOf(flagName)); }
        catch (IllegalArgumentException ignored) {}
    }

    private ItemStack tag(MenuType type, ItemStack it, String action, String path, String extra) {
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(keyMenu, PersistentDataType.STRING, type.name());
        m.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        if (path != null) m.getPersistentDataContainer().set(keyPath, PersistentDataType.STRING, path);
        if (extra != null) m.getPersistentDataContainer().set(keyExtra, PersistentDataType.STRING, extra);
        it.setItemMeta(m);
        return it;
    }

    private String mask(String s) {
        if (s == null || s.isBlank()) return "(leer/empty)";
        int vis = Math.min(4, s.length());
        return "*".repeat(Math.max(0, s.length() - vis)) + s.substring(s.length() - vis);
    }

    /* ===== Methods expected by GuiListener ===== */

    public boolean isOurInventory(Inventory inv) {
        if (inv == null) return false;
        for (ItemStack it : inv.getContents()) {
            if (it == null) continue;
            ItemMeta m = it.getItemMeta();
            if (m == null) continue;
            if (m.getPersistentDataContainer().has(keyMenu, PersistentDataType.STRING)) {
                return true;
            }
        }
        return false;
    }

    public MenuType getMenuType(ItemStack it) {
        if (it == null) return null;
        ItemMeta m = it.getItemMeta();
        if (m == null) return null;
        String type = m.getPersistentDataContainer().get(keyMenu, PersistentDataType.STRING);
        if (type == null) return null;
        try { return MenuType.valueOf(type); }
        catch (Exception e) { return null; }
    }

    public String getAction(ItemStack it) {
        ItemMeta m = (it == null) ? null : it.getItemMeta();
        return m == null ? null : m.getPersistentDataContainer().get(keyAction, PersistentDataType.STRING);
    }

    public String getPath(ItemStack it) {
        ItemMeta m = (it == null) ? null : it.getItemMeta();
        return m == null ? null : m.getPersistentDataContainer().get(keyPath, PersistentDataType.STRING);
    }

    public String getExtra(ItemStack it) {
        ItemMeta m = (it == null) ? null : it.getItemMeta();
        return m == null ? null : m.getPersistentDataContainer().get(keyExtra, PersistentDataType.STRING);
    }
}