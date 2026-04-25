package me.exnfachjan.twitchRandomizer.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/**
 * SQLite-backed runtime data store.
 * All session data (timer, queue, player locales, session state, run stats)
 * lives in a single data.db file. SQLite is a binary format – it cannot be
 * casually edited in a text editor, providing natural protection against
 * accidental manual changes.
 *
 * The table schema is a flat key-value store:
 *   CREATE TABLE kv (key TEXT PRIMARY KEY, value TEXT NOT NULL)
 *
 * Keys mirror the former YAML paths, e.g. "timer.elapsed_seconds", "queue",
 * "player_locales.<uuid>", "session.<key>", "runs.<runKey>.<field>".
 */
public class DataStore {

    private final JavaPlugin plugin;
    private Connection conn;

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        try {
            Class.forName("org.sqlite.JDBC");
            String url = "jdbc:sqlite:" + new File(plugin.getDataFolder(), "data.db").getAbsolutePath();
            conn = DriverManager.getConnection(url);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("CREATE TABLE IF NOT EXISTS kv (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)");
            }
            plugin.getLogger().info("[DataStore] SQLite database ready.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("[DataStore] SQLite JDBC driver not found! Data will not be persisted.");
        } catch (SQLException e) {
            plugin.getLogger().severe("[DataStore] Failed to open database: " + e.getMessage());
        }
        migrateOldFiles();
    }

    public synchronized void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                plugin.getLogger().info("[DataStore] Database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[DataStore] Failed to close database: " + e.getMessage());
        }
    }

    // Kept for API compatibility; SQLite writes are immediate – no explicit flush needed.
    public void save() {}
    public void saveAsync() {}

    // ── Core KV helpers ───────────────────────────────────────────────────────

    private synchronized void set(String key, String value) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO kv(key, value) VALUES(?,?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().warning("[DataStore] set(" + key + ") failed: " + e.getMessage());
        }
    }

    private synchronized String get(String key, String def) {
        if (conn == null) return def;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT value FROM kv WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[DataStore] get(" + key + ") failed: " + e.getMessage());
        }
        return def;
    }

    private synchronized Map<String, String> getByPrefix(String prefix) {
        Map<String, String> result = new LinkedHashMap<>();
        if (conn == null) return result;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT key, value FROM kv WHERE key LIKE ? ESCAPE '\\'")) {
            ps.setString(1, escapeLike(prefix) + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[DataStore] getByPrefix(" + prefix + ") failed: " + e.getMessage());
        }
        return result;
    }

    private synchronized void deleteByPrefix(String prefix) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM kv WHERE key LIKE ? ESCAPE '\\'")) {
            ps.setString(1, escapeLike(prefix) + "%");
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().warning("[DataStore] deleteByPrefix(" + prefix + ") failed: " + e.getMessage());
        }
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    public long getTimerElapsed() {
        try { return Long.parseLong(get("timer.elapsed_seconds", "0")); }
        catch (NumberFormatException e) { return 0L; }
    }

    public synchronized void setTimerState(long elapsed, boolean running) {
        set("timer.elapsed_seconds", String.valueOf(elapsed));
        set("timer.running",         String.valueOf(running));
    }

    // ── Queue ─────────────────────────────────────────────────────────────────

    public List<String> getQueue() {
        String blob = get("queue", "");
        if (blob.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(blob.split("\n", -1)));
    }

    public synchronized void setQueue(List<String> queue) {
        set("queue", String.join("\n", queue));
    }

    // ── Player locales ────────────────────────────────────────────────────────

    public Map<UUID, String> getPlayerLocales() {
        Map<String, String> rows = getByPrefix("player_locales.");
        Map<UUID, String> result = new HashMap<>();
        for (Map.Entry<String, String> e : rows.entrySet()) {
            String uuidStr = e.getKey().substring("player_locales.".length());
            try { result.put(UUID.fromString(uuidStr), e.getValue()); }
            catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    public synchronized void setPlayerLocales(Map<UUID, String> locales) {
        if (conn == null) return;
        deleteByPrefix("player_locales.");
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO kv(key, value) VALUES(?,?)")) {
                for (Map.Entry<UUID, String> e : locales.entrySet()) {
                    ps.setString(1, "player_locales." + e.getKey());
                    ps.setString(2, e.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            plugin.getLogger().warning("[DataStore] setPlayerLocales failed: " + e.getMessage());
            try { conn.rollback(); } catch (SQLException ignored) {}
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    // ── Session ───────────────────────────────────────────────────────────────

    public boolean getSessionBoolean(String path, boolean def) {
        String v = get("session." + path, null);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    public String getSessionString(String path, String def) {
        String v = get("session." + path, null);
        return v == null ? def : v;
    }

    public void setSession(String path, Object value) {
        set("session." + path, value == null ? "" : value.toString());
    }

    public void clearSession() {
        deleteByPrefix("session.");
    }

    // ── Dragon-kill run stats ─────────────────────────────────────────────────

    public void setRunData(String path, Object value) {
        set("runs." + path, value == null ? "" : value.toString());
    }

    // ── Migration from previous formats ──────────────────────────────────────

    private void migrateOldFiles() {
        File dir = plugin.getDataFolder();
        if (!dir.exists()) return;
        boolean any = false;

        // data.yml (previous YAML-based DataStore from plugin version that had it)
        File dataYml = new File(dir, "data.yml");
        if (dataYml.exists()) {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(dataYml);
            migrateYamlFlat(c);
            dataYml.delete();
            any = true;
            plugin.getLogger().info("[DataStore] Migrated data.yml → data.db");
        }

        // timer.yml
        File oldTimer = new File(dir, "timer.yml");
        if (oldTimer.exists()) {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(oldTimer);
            set("timer.elapsed_seconds", String.valueOf(c.getLong("elapsedSeconds", 0L)));
            set("timer.running", "false");
            oldTimer.delete();
            any = true;
            plugin.getLogger().info("[DataStore] Migrated timer.yml → data.db");
        }

        // queue.txt
        File oldQueue = new File(dir, "queue.txt");
        if (oldQueue.exists()) {
            List<String> lines = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(oldQueue), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) lines.add(line);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[DataStore] Could not migrate queue.txt: " + e.getMessage());
            }
            set("queue", String.join("\n", lines));
            oldQueue.delete();
            any = true;
            plugin.getLogger().info("[DataStore] Migrated queue.txt → data.db");
        }

        // player_locales.yml
        File oldLocales = new File(dir, "player_locales.yml");
        if (oldLocales.exists()) {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(oldLocales);
            for (String key : c.getKeys(false)) {
                set("player_locales." + key, c.getString(key, "en"));
            }
            oldLocales.delete();
            any = true;
            plugin.getLogger().info("[DataStore] Migrated player_locales.yml → data.db");
        }

        // session.yml
        File oldSession = new File(dir, "session.yml");
        if (oldSession.exists()) {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(oldSession);
            migrateYamlSection(c, "session", "session.");
            oldSession.delete();
            any = true;
            plugin.getLogger().info("[DataStore] Migrated session.yml → data.db");
        }

        // stats.yml
        File oldStats = new File(dir, "stats.yml");
        if (oldStats.exists()) {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(oldStats);
            migrateYamlSection(c, null, "runs.");
            oldStats.delete();
            any = true;
            plugin.getLogger().info("[DataStore] Migrated stats.yml → data.db");
        }

        if (any) plugin.getLogger().info("[DataStore] Migration complete → data.db");
    }

    /** Copies every leaf path from a flat YAML config into SQLite with the same key. */
    private void migrateYamlFlat(YamlConfiguration yaml) {
        for (String path : yaml.getKeys(true)) {
            if (yaml.isConfigurationSection(path)) continue;
            if (path.equals("__checksum")) continue;
            Object v = yaml.get(path);
            if (v instanceof List<?> list) {
                // queue stored as YAML list
                List<String> strings = new ArrayList<>();
                for (Object item : list) if (item != null) strings.add(item.toString());
                set(path, String.join("\n", strings));
            } else {
                set(path, v == null ? "" : v.toString());
            }
        }
    }

    /** Copies a section (or all keys) of a YAML config into SQLite under the given db prefix. */
    private void migrateYamlSection(YamlConfiguration yaml, String section, String dbPrefix) {
        for (String path : yaml.getKeys(true)) {
            if (yaml.isConfigurationSection(path)) continue;
            if (section != null && !path.startsWith(section)) continue;
            Object v = yaml.get(path);
            // strip the section prefix from the path if needed to avoid double prefixing
            String subPath = (section != null) ? path.substring(section.length()).replaceFirst("^\\.", "") : path;
            set(dbPrefix + subPath, v == null ? "" : v.toString());
        }
    }
}
