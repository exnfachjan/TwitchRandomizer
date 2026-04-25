# Update Notes: v1.6.0 → v1.7.0

## New Events

### `structure_teleport` ✨
Teleports all players to a random vanilla structure in any dimension (Overworld, Nether, End). Stronghold is the rarest target. Deduplication prevents landing at the same structure twice in a row. Default weight: 3.

### `hunger_clubs` ✨
Permanently gains or removes 1–2 hunger drumsticks from the player's maximum food bar. The change persists via PersistentDataContainer and is reset when the world is reset. Default weight: 5.

### `player_size` ✨
Randomly shrinks or grows the player for 15–60 seconds. A boss bar counts down the remaining duration. The player always returns to normal scale when the timer expires. Default weight: 7.

---

## Bug Fixes & Improvements

### NASA Call — uninterrupted flight arc
The `nasa_call` event no longer teleports the player to Y=300 before applying velocity. Instead, a per-tick task phases the player through any solid blocks below Y=300 so the full flight arc from ground level is visible and uninterrupted. The player now launches from their current position and rises naturally through terrain and structures.

### TNT Rain — 5-second countdown
A title-screen warning ("Geh in Deckung!") now appears 5 seconds before TNT minecarts begin to fall, giving players time to react.

### Inventory Shuffle — armor/offhand fix
Items stored in armor slots and the offhand slot no longer vanish when the `inv_shuffle` event fires.

---

## Infrastructure Changes

### Single data file (`data.db`)
The five separate session files (`timer.yml`, `queue.txt`, `player_locales.yml`, `session.yml`, `stats.yml`) have been replaced by a single SQLite database at `plugins/TwitchRandomizer/data.db`. SQLite's binary format prevents accidental manual edits. All existing data is migrated automatically on the first start after the update — no manual action required.

Your plugin folder now looks like this:
```
plugins/TwitchRandomizer/
├── config.yml      ← main configuration
├── donations.yml   ← StreamElements & Tipeeestream credentials
└── data.db         ← all runtime data (SQLite, binary)
```

### Auto config migration
When you drop in a new version of the JAR, any config keys that are new in that version are automatically added to your existing `config.yml` with their default values. You no longer need to manually edit `config.yml` after an update — just restart the server.
