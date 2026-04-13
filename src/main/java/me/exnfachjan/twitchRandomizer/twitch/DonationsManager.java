package me.exnfachjan.twitchRandomizer.twitch;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Central manager for all donation integrations.
 * Reads/writes donations.yml and delegates to
 * StreamElementsIntegrationManager and TipeeeStreamIntegrationManager.
 *
 * Universal currency:
 *   Standard: 5€ = 500 Bits = 1 Sub = 1 Event
 *   Minimum hardcoded: 1€ / 100 Bits
 *   euroPerEvent is the single configurable value.
 *   bitsPerEvent  = euroPerEvent * 100  (min 100)
 *   eventsPerSub  = ceil(5.0 / euroPerEvent)
 */
public class DonationsManager {

    public static final double MIN_EURO_PER_EVENT = 1.0;
    public static final int    MIN_BITS_PER_EVENT  = 100;
    public static final double SUB_VALUE_EURO      = 5.0;

    private static final String DONATIONS_FILE = "donations.yml";

    private final JavaPlugin plugin;
    private final File donationsFile;
    private final StreamElementsIntegrationManager seManager;
    private final TipeeeStreamIntegrationManager tipeeeManager;

    public DonationsManager(JavaPlugin plugin,
                            StreamElementsIntegrationManager seManager,
                            TipeeeStreamIntegrationManager tipeeeManager) {
        this.plugin = plugin;
        this.donationsFile = new File(plugin.getDataFolder(), DONATIONS_FILE);
        this.seManager = seManager;
        this.tipeeeManager = tipeeeManager;
        ensureFileExists();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    public void start() {
        DonationsConfig cfg = loadFile();
        boolean debug = plugin.getConfig().getBoolean("twitch.debug", false);

        double euro = Math.max(MIN_EURO_PER_EVENT, cfg.euroPerEvent);

        seManager.start(cfg.seEnabled, cfg.seAccounts, euro, debug);
        tipeeeManager.start(cfg.tipeeeEnabled, cfg.tipeeeApiKey, euro, debug);
    }

    public void stop() {
        seManager.stop();
        tipeeeManager.stop();
    }

    public void applyConfig() {
        DonationsConfig cfg = loadFile();
        boolean debug = plugin.getConfig().getBoolean("twitch.debug", false);
        double euro = Math.max(MIN_EURO_PER_EVENT, cfg.euroPerEvent);

        seManager.applyConfig(cfg.seEnabled, cfg.seAccounts, euro, debug);
        tipeeeManager.applyConfig(cfg.tipeeeEnabled, cfg.tipeeeApiKey, euro, debug);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Getters (read live from file for GUI)
    // ─────────────────────────────────────────────────────────────────────────

    public double getEuroPerEvent() {
        return Math.max(MIN_EURO_PER_EVENT, loadFile().euroPerEvent);
    }

    /** Bits per event derived from euroPerEvent. Min 100. */
    public int getBitsPerEvent() {
        return Math.max(MIN_BITS_PER_EVENT, (int) Math.round(getEuroPerEvent() * 100.0));
    }

    /** Events per sub = ceil(5.0 / euroPerEvent). Min 1. */
    public int getEventsPerSub() {
        double euro = getEuroPerEvent();
        return Math.max(1, (int) Math.ceil(SUB_VALUE_EURO / euro));
    }

    public boolean getSeEnabled() { return loadFile().seEnabled; }
    public boolean getTipeeeEnabled() { return loadFile().tipeeeEnabled; }

    // ─────────────────────────────────────────────────────────────────────────
    // Setters (write to donations.yml)
    // ─────────────────────────────────────────────────────────────────────────

    public void setEuroPerEvent(double value) {
        double clamped = Math.max(MIN_EURO_PER_EVENT, Math.round(value * 10.0) / 10.0);
        rewriteFileLine("euro_per_event:", "euro_per_event: " + clamped);
        // also update both managers live
        boolean debug = plugin.getConfig().getBoolean("twitch.debug", false);
        seManager.applyConfig(getSeEnabled(), loadFile().seAccounts, clamped, debug);
        tipeeeManager.applyConfig(getTipeeeEnabled(), loadFile().tipeeeApiKey, clamped, debug);
    }

    public void setSeEnabled(boolean value) {
        rewriteFileLine("se_enabled:", "se_enabled: " + value);
        applyConfig();
    }

    public void setTipeeeEnabled(boolean value) {
        rewriteFileLine("tipeee_enabled:", "tipeee_enabled: " + value);
        applyConfig();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File I/O
    // ─────────────────────────────────────────────────────────────────────────

    private DonationsConfig loadFile() {
        DonationsConfig result = new DonationsConfig();
        if (!donationsFile.exists()) { ensureFileExists(); return result; }
        try {
            List<String> lines = Files.readAllLines(donationsFile.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                String t = line.trim();
                if (t.startsWith("#") || t.isBlank()) continue;

                if (t.startsWith("euro_per_event:")) {
                    result.euroPerEvent = parseDouble(t.substring("euro_per_event:".length()), 5.0);
                } else if (t.startsWith("se_enabled:")) {
                    result.seEnabled = parseBool(t.substring("se_enabled:".length()));
                } else if (t.startsWith("se_accounts:")) {
                    result.seAccounts = parseString(t.substring("se_accounts:".length()));
                } else if (t.startsWith("tipeee_enabled:")) {
                    result.tipeeeEnabled = parseBool(t.substring("tipeee_enabled:".length()));
                } else if (t.startsWith("tipeee_api_key:")) {
                    result.tipeeeApiKey = parseString(t.substring("tipeee_api_key:".length()));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Donations] Fehler beim Lesen von donations.yml: " + e.getMessage());
        }
        return result;
    }

    private void rewriteFileLine(String linePrefix, String newLine) {
        if (!donationsFile.exists()) ensureFileExists();
        try {
            List<String> lines = Files.readAllLines(donationsFile.toPath(), StandardCharsets.UTF_8);
            List<String> out = new ArrayList<>();
            boolean replaced = false;
            for (String line : lines) {
                if (line.trim().startsWith(linePrefix) && !line.trim().startsWith("#")) {
                    out.add(newLine);
                    replaced = true;
                } else {
                    out.add(line);
                }
            }
            if (!replaced) out.add(newLine);
            Files.write(donationsFile.toPath(), out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("[Donations] Konnte donations.yml nicht schreiben: " + e.getMessage());
        }
    }

    private void ensureFileExists() {
        if (donationsFile.exists()) return;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(donationsFile), StandardCharsets.UTF_8))) {
            pw.println("# ═══════════════════════════════════════════════════════════");
            pw.println("#  TwitchRandomizer – Donations Configuration");
            pw.println("#  Edit this file manually, then run /trconfig donations reload");
            pw.println("# ═══════════════════════════════════════════════════════════");
            pw.println();
            pw.println("# Universal currency value: how much €/event is needed to trigger one event.");
            pw.println("# Minimum: 1.0 (hardcoded). Standard: 5.0");
            pw.println("# Bits per event  = euro_per_event * 100  (min 100 bits)");
            pw.println("# Events per sub  = ceil(5.0 / euro_per_event)");
            pw.println("# Example: euro_per_event=5.0 → 500 bits = 1 sub = 5€ = 1 event");
            pw.println("# Example: euro_per_event=1.0 → 100 bits = 1 sub worth 5 events = 1€ = 1 event");
            pw.println("euro_per_event: 5.0");
            pw.println();
            pw.println("# ─────────────────────────────────────────────────────────────");
            pw.println("#  STREAMELEMENTS");
            pw.println("#  JWT Token: https://streamelements.com/dashboard/account/channels");
            pw.println("#  Format: \"Channel:JWT\"  |  Multiple: \"Ch1:JWT1;Ch2:JWT2\"");
            pw.println("# ─────────────────────────────────────────────────────────────");
            pw.println("se_enabled: false");
            pw.println("se_accounts: \"YOUR_CHANNEL:YOUR_JWT_TOKEN\"");
            pw.println();
            pw.println("# ─────────────────────────────────────────────────────────────");
            pw.println("#  TIPEEESTREAM");
            pw.println("#  API Key: https://tipeeestream.com/dashboard/stream");
            pw.println("# ─────────────────────────────────────────────────────────────");
            pw.println("tipeee_enabled: false");
            pw.println("tipeee_api_key: \"YOUR_TIPEEESTREAM_APIKEY\"");
            plugin.getLogger().info("[Donations] donations.yml erstellt. Bitte konfigurieren.");
        } catch (Exception e) {
            plugin.getLogger().warning("[Donations] Konnte donations.yml nicht erstellen: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parse helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean parseBool(String s) { return "true".equalsIgnoreCase(s.trim()); }
    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim().replace(",", ".")); } catch (Exception e) { return def; }
    }
    private String parseString(String s) {
        String t = s.trim();
        if (t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
        else if (t.startsWith("'") && t.endsWith("'")) t = t.substring(1, t.length() - 1);
        return t;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Config POJO
    // ─────────────────────────────────────────────────────────────────────────

    private static class DonationsConfig {
        double euroPerEvent = 5.0;
        boolean seEnabled = false;
        String seAccounts = "";
        boolean tipeeeEnabled = false;
        String tipeeeApiKey = "";
    }
}