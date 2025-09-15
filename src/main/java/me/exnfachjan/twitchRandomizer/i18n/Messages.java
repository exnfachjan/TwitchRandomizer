package me.exnfachjan.twitchRandomizer.i18n;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class Messages {
    public enum Mode { AUTO, MANUAL }

    private final Plugin plugin;
    private final Map<UUID, String> store = new HashMap<>();
    private java.io.File playerLocalesFile;

    private final Map<String, Object> de = new LinkedHashMap<>();
    private final Map<String, Object> en = new LinkedHashMap<>();

    private Mode mode = Mode.AUTO;
    private String defaultLang = "en";

    public Messages(Plugin plugin) {
        this.plugin = plugin;
        try {
            String m = String.valueOf(plugin.getConfig().getString("language.mode", "auto")).toLowerCase(Locale.ROOT);
            this.mode = "manual".equals(m) ? Mode.MANUAL : Mode.AUTO;
            String def = String.valueOf(plugin.getConfig().getString("language.default", "en"));
            String norm = normalizeLang(def);
            this.defaultLang = norm != null ? norm : "en";
        } catch (Throwable ignored) { }

        de.clear();
        de.put("commands.saved_and_reconfigured", "&aKonfiguration gespeichert und (re)konfiguriert.");
        de.put("commands.randomevent.triggered", "&aRandom-Event ausgelöst.");
        de.put("commands.randomevent.triggered_by", "&aRandom-Event ausgelöst von &f{user}&a.");
        de.put("commands.randomevent.unknown_event", "&cUnbekanntes Event: &f{key}");
        de.put("commands.randomevent.weights_reloaded", "&aEvent-Gewichte neu geladen.");
        de.put("commands.randomevent.cooldown_active", "&eWartezeit aktiv: &f{seconds}s &everbleibend.");
        de.put("commands.randomevent.no_permission", "&cDir fehlt die Berechtigung: &f{perm}");
        de.put("events.damage.half_heart.by", "{user} zieht dir ein halbes Herz ab!");
        de.put("events.damage.half_heart.solo", "Du verlierst ein halbes Herz!");
        de.put("events.spawn.by", "{user} spawnt {amount}x {entity} bei dir!");
        de.put("events.spawn.solo", "Es spawnen {amount}x {entity} bei dir!");
        de.put("events.ignite.short.by", "{user} zündet dich kurz an!");
        de.put("events.ignite.short.solo", "Du wirst kurz angezündet!");
        de.put("events.fire.by", "{user} setzt dich für {seconds}s in Brand!");
        de.put("events.fire.solo", "Du wirst für {seconds}s in Brand gesetzt!");
        de.put("events.give.item.by", "{user} gibt dir {amount}x {item}!");
        de.put("events.give.item.solo", "Du bekommst {amount}x {item}!");
        de.put("events.inventory.cleared.by", "{user} leert dein Inventar!");
        de.put("events.inventory.cleared.solo", "Dein Inventar wurde geleert!");
        de.put("events.teleport.random.by", "{user} teleportiert dich zufällig!");
        de.put("events.teleport.random.solo", "Du wurdest zufällig teleportiert!");
        de.put("events.potion.applied.by", "{user} gibt dir {effect} für {seconds}s!");
        de.put("events.potion.applied.solo", "Du erhältst {effect} für {seconds}s!");
        de.put("events.inv_shuffle.by", "{user} hat dein Inventar gemischt!");
        de.put("events.inv_shuffle.solo", "Dein Inventar wurde gemischt!");
        de.put("events.hot_potato.start_by", "{user} hat dir die heiße Kartoffel gegeben! ({seconds}s)");
        de.put("events.hot_potato.start", "Heiße Kartoffel für {seconds}s!");
        de.put("events.hot_potato.end", "Die heiße Kartoffel ist vorbei.");
        de.put("events.no_crafting.start_by", "{user} blockiert {seconds}s dein Crafting!");
        de.put("events.no_crafting.start", "Crafting {seconds}s blockiert!");
        de.put("events.no_crafting.blocked", "Crafting ist derzeit blockiert!");
        de.put("events.no_crafting.end", "Du kannst wieder craften.");
        de.put("events.safe_creepers.explode_by", "{user} hat eine Explosion ausgelöst!");
        de.put("events.safe_creepers.explode", "Eine Explosion wurde ausgelöst!");
        de.put("events.floor_is_lava.start_by", "{user} hat den Boden in Lava verwandelt! ({seconds}s)");
        de.put("events.floor_is_lava.start", "Boden ist Lava für {seconds}s!");
        de.put("events.floor_is_lava.end", "Boden ist wieder sicher.");
        de.put("events.nasa_call.by", "{user} schickt dich auf eine Reise ins All!");
        de.put("events.nasa_call.solo", "Du wirst in die Luft geschleudert!");
        de.put("events.slippery_ground.start_by", "{user} macht den Boden rutschig! ({seconds}s)");
        de.put("events.slippery_ground.start", "Der Boden ist rutschig für {seconds}s!");
        de.put("events.slippery_ground.end", "Der Boden ist wieder normal.");
        de.put("events.hell_is_calling.by", "{user} entfesselt die Hölle – Feuerbälle regnen herab!");
        de.put("events.hell_is_calling.solo", "Feuerbälle regnen auf dich nieder!");
        de.put("actionbar.timer.running_prefix", "⏱ {time}");
        de.put("actionbar.timer.paused", "TIMER PAUSIERT");
        de.put("actionbar.queue_label", "Queue");
        de.put("actionbar.next_label", "Nächstes");
        de.put("actionbar.next_none", "–");
        de.put("actionbar.deaths_label", "Versuche");
        de.put("gui.weights.paper_name", "&d{key}");
        de.put("gui.weights.paper_lore", java.util.List.of("&7Gewicht: &f{weight}", "&7Links/Rechts: &f±1", "&7Shift + Klick: &f±10"));
        de.put("gui.titles.main", "&3TwitchRandomizer");
        de.put("gui.titles.trigger", "&3Trigger");
        de.put("gui.titles.debug", "&3Debug Menü");
        de.put("gui.titles.weights", "&3Event-Gewichte");
        de.put("gui.titles.misc", "&3Misc");
        de.put("gui.titles.reset_confirm", "&3Misc - Server reset");
        de.put("gui.titles.ui_language", "&3UI-Sprache");
        de.put("gui.common.back", "&eZurück");
        de.put("gui.common.save_reconnect_name", "&eSpeichern & (Re)verbinden");
        de.put("gui.common.save_reconnect_lore", java.util.List.of("&7Änderungen übernehmen"));
        de.put("gui.main.twitch_info_name", "&6Twitch-Info");
        de.put("gui.main.twitch_info_lore", java.util.List.of("&7Channel: &f{channel}", "&7Token: &f{token_masked}", "&4Achtung: sensible Daten!", "&7Links: &fChannel setzen", "&7Rechts: &fToken setzen"));
        de.put("gui.main.misc_name", "&6Misc");
        de.put("gui.main.misc_lore", java.util.List.of("&7Diverse Einstellungen", "&7z. B. 'Spectator after death' & Deathcounter"));
        de.put("gui.main.debug_name", "&dDebug Menü");
        de.put("gui.main.debug_lore", java.util.List.of("&7Testtrigger verwalten:", "&7!test, !gift, !giftbomb"));
        de.put("gui.main.trigger_name", "&bTrigger");
        de.put("gui.main.trigger_lore", java.util.List.of("&7Subs/Bits, Bits pro Trigger,", "&7Intervall anpassen"));
        de.put("gui.main.weights_name", "&dEvent-Gewichte");
        de.put("gui.main.weights_lore", java.util.List.of("&7Öffne Untermenü, um Gewichte", "&7pro Event anzupassen"));
        de.put("gui.main.ui_language_name", "&6UI-Sprache/Ui-Language");
        de.put("gui.main.ui_language_lore", java.util.List.of("&7Setze die Plugin-UI-Sprache nur für dich", "&7Überschreibt die Client-Sprache"));
        de.put("gui.main.timer_start", "&aTimer START");
        de.put("gui.main.timer_start_lore", java.util.List.of("&7Startet den Timer"));
        de.put("gui.main.timer_stop", "&6Timer STOP");
        de.put("gui.main.timer_stop_lore", java.util.List.of("&7Pausiert den Timer"));
        de.put("gui.main.timer_reset", "&cTimer RESET");
        de.put("gui.main.timer_reset_lore", java.util.List.of("&7Setzt auf 00:00:00"));
        de.put("gui.main.close", "&cSchließen");
        de.put("gui.trigger.subs_toggle", "Subscriptions");
        de.put("gui.trigger.bits_toggle", "Bits (Cheer)");
        de.put("gui.trigger.bits_per_trigger_name", "&bBits pro Trigger: &f{value}");
        de.put("gui.trigger.bits_per_trigger_lore", java.util.List.of("&7Links/Rechts: &f±1", "&7Shift + Klick: &f±100"));
        de.put("gui.trigger.interval_name", "&bIntervall: &f{seconds}s");
        de.put("gui.trigger.interval_lore", java.util.List.of("&7Links/Rechts: &f±0.5s", "&7Shift + Klick: &f±1.0s"));
        de.put("gui.debug.test_toggle", "!test erlaubt");
        de.put("gui.debug.gift_toggle", "!gift Simulation");
        de.put("gui.debug.giftbomb_toggle", "!giftbomb Simulation");
        de.put("gui.misc.spectator_toggle", "Spectator after death");
        de.put("gui.misc.deaths_name", "&bDeaths: &f{value}");
        de.put("gui.misc.deaths_lore", java.util.List.of("&7Zählt alle Tode (persistent).", "&7Shift + Klick: &fauf 0 zurücksetzen"));
        de.put("gui.misc.reset_button_name", "&cServer reset");
        de.put("gui.misc.reset_button_lore", java.util.List.of("&7Öffnet Bestätigung", "&7führt '/reset confirm' aus"));
        de.put("gui.lang.de_name", "&fDeutsch");
        de.put("gui.lang.de_lore", java.util.List.of("&7UI auf Deutsch umstellen"));
        de.put("gui.lang.en_name", "&fEnglisch");
        de.put("gui.lang.en_lore", java.util.List.of("&7UI auf Englisch umstellen"));
        de.put("gui.lang.current_prefix", "&eAktuell: &f{lang}");
        de.put("gui.reset_confirm.cancel", "&cAbbrechen");
        de.put("gui.reset_confirm.confirm", "&aReset bestätigen");
        de.put("gui.book.title", "Twitch Eingabe");
        de.put("gui.book.page.channel", "Ersetze diesen Text mit deinem Twitch Username");
        de.put("gui.book.page.token.text", "Ersetzte diesen Text mit den Access Token deines Bots, welchen du unter {url} erhalten kannst.");
        de.put("gui.book.page.token.link_text", "Token-Generator öffnen");
        de.put("gui.book.page.reward", "Schreibe auf die erste Seite den EXAKTEN Namen deiner Kanalpunkte-Belohnung und signiere das Buch.");
        de.put("gui.book.chat.warn_sensitive", "§6[Hinweis] §eAchte auf sensible Daten (Channel/Token).");
        de.put("gui.book.chat.received_channel", "§7Du hast ein §fBuch & Feder§7 erhalten. Ersetze den Text in §fZeile 1§7 durch deinen §fTwitch-Nutzernamen§7 und klicke §fFertig§7.");
        de.put("gui.book.chat.received_token", "§7Du hast ein §fBuch & Feder§7 erhalten. Ersetze den Text in §fZeile 1§7 durch dein §fAccess Token§7 und klicke §fFertig§7.");
        de.put("gui.book.chat.inventory_full", "§7Inventar voll – Buch wurde auf den Boden gedroppt.");
        de.put("gui.book.chat.cancelled", "§eEingabe abgebrochen (leer).");
        de.put("gui.book.chat.saved_channel", "§aChannel gesetzt und (re)konfiguriert.");
        de.put("gui.book.chat.saved_token", "§aToken gesetzt und (re)konfiguriert.");
        de.put("toggles.on_prefix", "&aAn: &f{label}");
        de.put("toggles.off_prefix", "&8Aus: &f{label}");
        de.put("reset.confirm", "Bitte bestätige mit §e{command}");
        de.put("reset.confirm_prompt", "Bitte bestätige innerhalb von {seconds}s mit §e/{command}");
        de.put("reset.confirm_already_pending", "Es läuft bereits ein Reset-Dialog (von {requester}). Bestätige mit §e/{command} (§e{seconds}s§7 übrig).");
        de.put("reset.confirm_no_pending", "Kein Reset-Dialog aktiv. Starte mit §e/{command0}.");
        de.put("reset.confirm_ok", "§aReset wird ausgeführt...");
        de.put("server.reset.kick", "§cServer-Reset von §e{requester}§7. Bitte später erneut verbinden.");
        de.put("title.reset.line1", "§cServer-Reset");
        de.put("title.reset.line2", "Bitte später erneut verbinden");
        de.put("bossbar.floor_is_lava", "Der Boden ist Lava");
        de.put("bossbar.slippery_ground", "Achtung Rutschgefahr");

        en.clear();
        en.put("commands.saved_and_reconfigured", "&aConfiguration saved and (re)configured.");
        en.put("commands.randomevent.triggered", "&aRandom event triggered.");
        en.put("commands.randomevent.triggered_by", "&aRandom event triggered by &f{user}&a.");
        en.put("commands.randomevent.unknown_event", "&cUnknown event: &f{key}");
        en.put("commands.randomevent.weights_reloaded", "&aEvent weights reloaded.");
        en.put("commands.randomevent.cooldown_active", "&eCooldown active: &f{seconds}s &eleft.");
        en.put("commands.randomevent.no_permission", "&cYou lack permission: &f{perm}");
        en.put("events.damage.half_heart.by", "{user} takes half a heart from you!");
        en.put("events.damage.half_heart.solo", "You lose half a heart!");
        en.put("events.spawn.by", "{user} spawns {amount}x {entity} at you!");
        en.put("events.spawn.solo", "{amount}x {entity} spawn at you!");
        en.put("events.ignite.short.by", "{user} sets you on fire briefly!");
        en.put("events.ignite.short.solo", "You are set on fire briefly!");
        en.put("events.fire.by", "{user} sets you on fire for {seconds}s!");
        en.put("events.fire.solo", "You are set on fire for {seconds}s!");
        en.put("events.give.item.by", "{user} gives you {amount}x {item}!");
        en.put("events.give.item.solo", "You get {amount}x {item}!");
        en.put("events.inventory.cleared.by", "{user} cleared your inventory!");
        en.put("events.inventory.cleared.solo", "Your inventory was cleared!");
        en.put("events.teleport.random.by", "{user} teleported you randomly!");
        en.put("events.teleport.random.solo", "You were randomly teleported!");
        en.put("events.potion.applied.by", "{user} gives you {effect} for {seconds}s!");
        en.put("events.potion.applied.solo", "You receive {effect} for {seconds}s!");
        en.put("events.inv_shuffle.by", "{user} shuffled your inventory!");
        en.put("events.inv_shuffle.solo", "Your inventory was shuffled!");
        en.put("events.hot_potato.start_by", "{user} gave you the hot potato! ({seconds}s)");
        en.put("events.hot_potato.start", "Hot Potato for {seconds}s!");
        en.put("events.hot_potato.end", "Hot Potato ended.");
        en.put("events.no_crafting.start_by", "{user} blocks your crafting for {seconds}s!");
        en.put("events.no_crafting.start", "Crafting blocked for {seconds}s!");
        en.put("events.no_crafting.blocked", "Crafting is currently blocked!");
        en.put("events.no_crafting.end", "You can craft again.");
        en.put("events.safe_creepers.explode_by", "{user} caused an explosion!");
        en.put("events.safe_creepers.explode", "An explosion was triggered!");
        en.put("events.floor_is_lava.start_by", "{user} turned the floor into lava! ({seconds}s)");
        en.put("events.floor_is_lava.start", "Floor is lava for {seconds}s!");
        en.put("events.floor_is_lava.end", "The floor is safe again.");
        en.put("events.nasa_call.by", "{user} is calling NASA – you’re going to space!");
        en.put("events.nasa_call.solo", "You are launched sky high!");
        en.put("events.slippery_ground.start_by", "{user} made the ground slippery! ({seconds}s)");
        en.put("events.slippery_ground.start", "The ground is slippery for {seconds}s!");
        en.put("events.slippery_ground.end", "The ground is normal again.");
        en.put("events.hell_is_calling.by", "{user} calls hell – fireballs rain down!");
        en.put("events.hell_is_calling.solo", "Fireballs rain down on you!");
        en.put("actionbar.timer.running_prefix", "⏱ {time}");
        en.put("actionbar.timer.paused", "TIMER PAUSED");
        en.put("actionbar.queue_label", "Queue");
        en.put("actionbar.next_label", "Next");
        en.put("actionbar.next_none", "–");
        en.put("actionbar.deaths_label", "Trys");
        en.put("gui.weights.paper_name", "&d{key}");
        en.put("gui.weights.paper_lore", java.util.List.of("&7Weight: &f{weight}", "&7Left/Right: &f±1", "&7Shift + Click: &f±10"));
        en.put("gui.titles.main", "&3TwitchRandomizer");
        en.put("gui.titles.trigger", "&3Trigger");
        en.put("gui.titles.debug", "&3Debug Menu");
        en.put("gui.titles.weights", "&3Event Weights");
        en.put("gui.titles.misc", "&3Misc");
        en.put("gui.titles.reset_confirm", "&3Misc - Server reset");
        en.put("gui.titles.ui_language", "&3UI Language");
        en.put("gui.common.back", "&eBack");
        en.put("gui.common.save_reconnect_name", "&eSave & (Re)connect");
        en.put("gui.common.save_reconnect_lore", java.util.List.of("&7Apply changes"));
        en.put("gui.main.twitch_info_name", "&6Twitch Info");
        en.put("gui.main.twitch_info_lore", java.util.List.of("&7Channel: &f{channel}", "&7Token: &f{token_masked}", "&4Warning: sensitive data!", "&7Left: &fSet channel", "&7Right: &fSet token"));
        en.put("gui.main.misc_name", "&6Misc");
        en.put("gui.main.misc_lore", java.util.List.of("&7Various settings", "&7e.g. 'Spectator after death' & Death counter"));
        en.put("gui.main.debug_name", "&dDebug Menu");
        en.put("gui.main.debug_lore", java.util.List.of("&7Manage test triggers:", "&7!test, !gift, !giftbomb"));
        en.put("gui.main.trigger_name", "&bTriggers");
        en.put("gui.main.trigger_lore", java.util.List.of("&7Subs/Bits, Bits per trigger,", "&7Adjust interval"));
        en.put("gui.main.weights_name", "&dEvent Weights");
        en.put("gui.main.weights_lore", java.util.List.of("&7Open submenu to adjust", "&7per-event weights"));
        en.put("gui.main.ui_language_name", "&6UI Language");
        en.put("gui.main.ui_language_lore", java.util.List.of("&7Set plugin UI language for you", "&7Overrides client language"));
        en.put("gui.main.timer_start", "&aTimer START");
        en.put("gui.main.timer_start_lore", java.util.List.of("&7Start the timer"));
        en.put("gui.main.timer_stop", "&6Timer STOP");
        en.put("gui.main.timer_stop_lore", java.util.List.of("&7Pause the timer"));
        en.put("gui.main.timer_reset", "&cTimer RESET");
        en.put("gui.main.timer_reset_lore", java.util.List.of("&7Reset to 00:00:00"));
        en.put("gui.main.close", "&cClose");
        en.put("gui.trigger.subs_toggle", "Subscriptions");
        en.put("gui.trigger.bits_toggle", "Bits (Cheer)");
        en.put("gui.trigger.bits_per_trigger_name", "&bBits per Trigger: &f{value}");
        en.put("gui.trigger.bits_per_trigger_lore", java.util.List.of("&7Left/Right: &f±1", "&7Shift + Click: &f±100"));
        en.put("gui.trigger.interval_name", "&bInterval: &f{seconds}s");
        en.put("gui.trigger.interval_lore", java.util.List.of("&7Left/Right: &f±0.5s", "&7Shift + Click: &f±1.0s"));
        en.put("gui.debug.test_toggle", "!test allowed");
        en.put("gui.debug.gift_toggle", "!gift simulation");
        en.put("gui.debug.giftbomb_toggle", "!giftbomb simulation");
        en.put("gui.misc.spectator_toggle", "Spectator after death");
        en.put("gui.misc.deaths_name", "&bDeaths: &f{value}");
        en.put("gui.misc.deaths_lore", java.util.List.of("&7Counts all deaths (persistent).", "&7Shift + Click: &freset to 0"));
        en.put("gui.misc.reset_button_name", "&cServer reset");
        en.put("gui.misc.reset_button_lore", java.util.List.of("&7Open confirmation", "&7executes '/reset confirm'"));
        en.put("gui.lang.de_name", "&fDeutsch");
        en.put("gui.lang.de_lore", java.util.List.of("&7Switch UI to German"));
        en.put("gui.lang.en_name", "&fEnglish");
        en.put("gui.lang.en_lore", java.util.List.of("&7Switch UI to English"));
        en.put("gui.lang.current_prefix", "&eCurrent: &f{lang}");
        en.put("gui.reset_confirm.cancel", "&cCancel");
        en.put("gui.reset_confirm.confirm", "&aConfirm reset");
        en.put("gui.book.title", "Twitch Input");
        en.put("gui.book.page.channel", "Replace this text with your Twitch username");
        en.put("gui.book.page.token.text", "Replace this text with your bot's access token, which you can obtain at {url}");
        en.put("gui.book.page.token.link_text", "Open Token Generator");
        en.put("gui.book.chat.warn_sensitive", "§6[Notice] §eBe careful with sensitive data (channel/token).");
        en.put("gui.book.chat.received_channel", "§7You received a §fBook & Quill§7. Replace the text in §fline 1§7 with your §fTwitch username§7 and click §fDone§7.");
        en.put("gui.book.chat.received_token", "§7You received a §fBook & Quill§7. Replace the text in §fline 1§7 with your §faccess token§7 and click §fDone§7.");
        en.put("gui.book.chat.inventory_full", "§7Inventory full – dropped the book on the ground.");
        en.put("gui.book.chat.cancelled", "§eInput cancelled (empty).");
        en.put("gui.book.chat.saved_channel", "§aChannel set and (re)configured.");
        en.put("gui.book.chat.saved_token", "§aToken set and (re)configured.");
        en.put("toggles.on_prefix", "&aOn: &f{label}");
        en.put("toggles.off_prefix", "&8Off: &f{label}");
        en.put("reset.confirm", "Please confirm using §e{command}");
        en.put("reset.confirm_prompt", "Please confirm within {seconds}s using §e/{command}");
        en.put("reset.confirm_already_pending", "A reset dialog is already pending (by {requester}). Confirm with §e/{command} (§e{seconds}s§7 left).");
        en.put("reset.confirm_no_pending", "No reset dialog active. Start with §e/{command0}.");
        en.put("reset.confirm_ok", "§aExecuting reset...");
        en.put("server.reset.kick", "§cServer reset requested by §e{requester}§7. Please reconnect later.");
        en.put("title.reset.line1", "§cServer Reset");
        en.put("title.reset.line2", "Please reconnect later");
        en.put("bossbar.floor_is_lava", "The Floor is Lava");
        en.put("bossbar.slippery_ground", "Caution Slippery");
    }

    // Re-reads mode/default + loads per-player overrides (texts stay embedded)
    public void load() {
        try {
            String m = String.valueOf(plugin.getConfig().getString("language.mode", "auto")).toLowerCase(java.util.Locale.ROOT);
            this.mode = "manual".equals(m) ? Mode.MANUAL : Mode.AUTO;
            String def = String.valueOf(plugin.getConfig().getString("language.default", "en"));
            String norm = normalizeLang(def);
            this.defaultLang = norm != null ? norm : "en";
        } catch (Throwable ignored) { }

        try {
            // Player-Locale aus config.yml -> player_locales.<uuid>
            org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
            store.clear();
            if (cfg.isConfigurationSection("player_locales")) {
                org.bukkit.configuration.ConfigurationSection sec = cfg.getConfigurationSection("player_locales");
                for (String key : sec.getKeys(false)) {
                    try {
                        java.util.UUID id = java.util.UUID.fromString(key);
                        String lang = sec.getString(key, null);
                        String n = normalizeLang(lang);
                        if (n != null) store.put(id, n);
                    } catch (IllegalArgumentException ignored) { }
                }
            }
        } catch (Throwable ignored) { }
    }

    public void savePlayerLocales() {
        try {
            org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
            // alte Sektion leeren, dann neu schreiben
            cfg.set("player_locales", null);
            for (var e : store.entrySet()) {
                cfg.set("player_locales." + e.getKey().toString(), e.getValue());
            }
            plugin.saveConfig();
        } catch (Throwable ignored) { }
    }

    // --- Public API ---
    public String tr(Player p, String key) {
        String lang = resolveLang(p);
        Object val = rawFor(lang, key);
        return colorize(val == null ? key : String.valueOf(val));
    }

    public String tr(Player p, String key, Map<String, String> placeholders) {
        return colorize(apply(tr(p, key), placeholders));
    }

    public List<String> trList(Player p, String key) {
        Object val = rawFor(resolveLang(p), key);
        List<String> out = new ArrayList<>();
        if (val instanceof List<?> list) {
            for (Object o : list) out.add(colorize(String.valueOf(o)));
        } else {
            out.add(tr(p, key));
        }
        return out;
    }

    public List<String> trList(Player p, String key, Map<String, String> placeholders) {
        List<String> base = trList(p, key);
        List<String> out = new ArrayList<>(base.size());
        for (String line : base) out.add(colorize(apply(line, placeholders)));
        return out;
    }

    public void setPlayerLanguage(Player p, String lang) {
        String n = normalizeLang(lang);
        if (n == null) store.remove(p.getUniqueId());
        else store.put(p.getUniqueId(), n);
    }
    public void clearPlayerLanguage(Player p) { store.remove(p.getUniqueId()); }
    public String currentLanguage(Player p) { return resolveLang(p); }

    // --- Internals ---
    private Object rawFor(String lang, String key) {
        Object val = getMap(lang).get(key);
        if (val == null) val = getMap("de".equals(lang) ? "en" : "de").get(key);
        return val;
    }
    private Map<String, Object> getMap(String lang) { return "de".equals(lang) ? de : en; }

    private String resolveLang(Player p) {
        // 1) Per-Player Override hat IMMER Vorrang (egal ob AUTO oder MANUAL)
        String override = store.get(p.getUniqueId());
        if (override != null) return override;

        // 2) MANUAL -> nutze defaultLang
        if (mode == Mode.MANUAL) {
            return defaultLang;
        }

        // 3) AUTO -> vom Client ableiten, sonst fallback
        try {
            String tag = p.locale().toLanguageTag();
            String l = tag == null ? "" : tag.toLowerCase(Locale.ROOT);
            return l.startsWith("de") ? "de" : "en";
        } catch (Throwable ignored) {
            return defaultLang;
        }
    }

    private String normalizeLang(String lang) {
        if (lang == null) return null;
        String l = lang.trim().toLowerCase(Locale.ROOT);
        if (l.startsWith("de")) return "de";
        if (l.startsWith("en")) return "en";
        return null;
    }

    private String apply(String src, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) return src;
        String out = src;
        for (var e : placeholders.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return out;
    }

    private String colorize(String s) {
        try { return ChatColor.translateAlternateColorCodes('&', s); } catch (Throwable ignored) { return s; }
    }
}