package me.exnfachjan.twitchRandomizer.config;

import me.exnfachjan.twitchRandomizer.data.DataStore;

public class SessionConfig {

    private final DataStore store;

    public SessionConfig(DataStore store) {
        this.store = store;
    }

    public boolean getBoolean(String path, boolean def) {
        return store.getSessionBoolean(path, def);
    }

    public boolean getBoolean(String path) {
        return store.getSessionBoolean(path, false);
    }

    public String getString(String path, String def) {
        return store.getSessionString(path, def);
    }

    public void set(String path, Object value) {
        store.setSession(path, value);
        store.saveAsync();
    }

    public void clear() {
        store.clearSession();
        store.saveAsync();
    }

    public void save(boolean async) {
        if (async) store.saveAsync(); else store.save();
    }
}
