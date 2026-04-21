# TwitchRandomizer

[![Modrinth](https://img.shields.io/badge/Modrinth-exnfachjanTTV-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/user/exnfachjanTTV)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Version](https://img.shields.io/badge/version-1.6.0-blue)](https://github.com/exnfachjan/TwitchRandomizer)

TwitchRandomizer is a powerful Minecraft (Paper 1.21+) plugin that brings interactive, randomized challenges to your server — all controlled by your Twitch viewers! It seamlessly integrates your Minecraft gameplay with Twitch chat, letting your audience influence and trigger in-game events in real time.

---

## 📌 Key Features

- **Twitch Chat Integration:**  
  Twitch viewers can trigger random events by subscribing or donating bits to your channel!

- **Play this Challenge with your Twitch Friends:**  
  The plugin can read subscriptions and bits from multiple Twitch accounts — including StreamElements and Tipeeestream donations. So you can play this challenge with your Twitch friends!

- **StreamElements & Tipeeestream Donation Integration:**  
  Donations via StreamElements or Tipeeestream trigger in-game events. Both support **multiple streamer accounts simultaneously**. Configuration is done exclusively via `donations.yml` in the plugin folder.

- **Channel Name Display:**  
  When playing with multiple Twitch channels, the source channel is shown in brackets after every trigger — e.g. `exnfachjan (fuxelbau) deals 4 hearts`.

- **Twitch Role Colors:**  
  Viewer names appear color-coded in Minecraft chat — Broadcaster (red), Moderator (green), VIP (pink), Donation (aqua), and regular viewers (white).

- **Event Weighting and Customization:**  
  Adjust the likelihood of each event — make some more common, some rare, or disable them entirely. Easily reload or adjust settings on the fly. Reset all weights to defaults with one click.

- **Recent Event Cooldown:**  
  A built-in cooldown system reduces the chance of the same event appearing multiple times in a row, automatically — no configuration needed.

- **Synced Multi-Player Events:**  
  When playing with multiple players, all random values within an event (duration, item, mob type, etc.) are identical for every player. No more player 1 getting 10 seconds of Floor is Lava while player 2 gets 69.

- **Spectator After Death:**  
  When a player dies, all players are moved to Spectator mode simultaneously. The timer and queue pause until a new run begins. Configurable per-run as "Tries" (team lives) or classic "Deaths" counter.

- **Death Counter and Statistics:**  
  Built-in death tracking and player statistics, perfect for challenge runs and community competitions.

- **Queue System:**  
  Viewer-triggered events can be queued and managed, ensuring smooth pacing and no event spam. The queue persists across server restarts and world resets!

- **Pause System:**  
  Timer and queue automatically pause when a player is in the death screen or when all players are in spectator mode. Fully configurable.

- **Pause Aura:**  
  When the timer is paused, a spiral flame particle aura appears around all online players — a clear visual indicator that the challenge is on hold.

- **Graphical Interface (GUI):**  
  Tweak plugin settings, manage event weights, control the timer, and configure behaviors directly from an in-game GUI. Perfect for easy setup without any OP permissions!

- **Multi-Language Support:**  
  Full German and English UI with automatic client language detection. Players can override their language in the GUI.

- **Session and World Reset:**  
  Reset your seed after a death if you want a REAL challenge! Supports BungeeCord fallback servers.

- **Ender Dragon Stats:**  
  When the Ender Dragon is defeated, the plugin automatically broadcasts a full run summary (time, deaths, events triggered, subs, donations, bits) and saves it to `stats.yml` for review. Timer and queue are reset automatically.

- **Permanent Hearts Event:**  
  A new event that permanently changes a player's maximum hearts — up or down. The change persists across disconnects and is reset when the world is reset.

---

## ✔️ Main Commands

- `/trgui` — Open the graphical config GUI.

Optional commands (not necessary, because you can manage everything seamlessly from the GUI):

- `/timer` — Start, stop, pause, or reset the challenge timer.
- `/reset` — Initiate a world/session reset with confirmation.
- `/queue add <amount> <username>` — Manually add events to the queue.
- `/cleardeath` — Clear the death counter.
- `/trconfig` — Configure the plugin via command (tab completion supported).

---

## 🎉 Events

| Event                 | Description                                                                      |
| --------------------- | -------------------------------------------------------------------------------- |
| **spawn_mobs**        | Spawn random mobs near the player (hostile mobs chase you!)                      |
| **potion**            | Apply a random potion effect                                                     |
| **give_item**         | Give a random item                                                               |
| **clear_inventory**   | Clear random slots from the player's inventory                                   |
| **teleport**          | Teleport to a random location (safe Y-level)                                     |
| **damage_half_heart** | Take heart damage                                                                |
| **fire**              | Set the player on fire                                                           |
| **inv_shuffle**       | Shuffle the entire inventory                                                     |
| **hot_potato**        | A fast zombie (3× HP) chases you — if it catches you, it explodes!               |
| **no_crafting**       | Block crafting for a random duration (with boss bar countdown)                   |
| **safe_creepers**     | Creepers surround you clock-style and explode (no damage, no block destruction)  |
| **floor_is_lava**     | The floor beneath you turns to magma blocks (with block restore)                 |
| **nasa_call**         | Launch the player sky-high — flies through blocks!                               |
| **slippery_ground**   | The ground turns to packed ice (with block restore)                              |
| **hell_is_calling**   | Homing fireballs rain down on you                                                |
| **tnt_rain**          | TNT Minecarts rain from the sky                                                  |
| **anvil_rain**        | Anvils fall from the sky                                                         |
| **skyblock**          | All players are teleported together — surrounding chunks get deleted!            |
| **fake_totem**        | Receive a Totem of Undying — 50/50 chance it actually works!                     |
| **equipment_shuffle** | All tiered tools and armor in your inventory are randomly upgraded or downgraded |
| **permanent_hearts**  | Permanently gain or lose 1–2 maximum hearts (persists across disconnects)        |

---

## 🛠 Getting Started

You can configure the plugin through the config.yml or without OP permissions directly through the GUI in-game.

1. **Install:**  
   Place `TwitchRandomizer.jar` into your server's plugins folder.

2. **Configure Twitch Integration:**
   - Open the GUI with `/trgui`; in the top left, you'll find the "twitch-info" book.
   - Insert your `Broadcaster Username` by left-clicking on the book, then confirm with `done`.  
     You can enter multiple channels separated by commas, semicolons, or new lines.
   - Insert your `Bot OAuth` by right-clicking on the book, then confirm with `done`.  
     You can get your OAuth token here: https://twitchtokengenerator.com/quick/1KRFjxyoNE  
     Make sure the following scopes are active: `bits.read`, `chat:read`, `channel:read:subscriptions` and `user:read:subscriptions`.

3. **Configure StreamElements (optional):**  
   Edit `plugins/TwitchRandomizer/donations.yml` — this file is created automatically on first start and is **never overwritten** by the plugin.

   ```yaml
   se_enabled: true
   se_accounts: "YOUR_CHANNEL:YOUR_JWT_TOKEN"
   ```

   For multiple streamers, separate entries with a semicolon:

   ```yaml
   se_accounts: "Channel1:JWT1;Channel2:JWT2"
   ```

   Get your JWT token from: https://streamelements.com/dashboard/account/channels

4. **Configure Tipeeestream (optional):**  
   In the same `donations.yml`:

   ```yaml
   tipeee_enabled: true
   tipeee_accounts: "YOUR_CHANNEL:YOUR_APIKEY"
   ```

   Multiple accounts work the same way: `"Ch1:KEY1;Ch2:KEY2"`  
   Get your API key from: https://tipeeestream.com/dashboard/stream

   After editing `donations.yml`, apply it in-game with `/trconfig donations reload` — no server restart required.

5. **Reload/Restart:**  
   If you use the GUI, you don't need to restart the server.

6. **Set up your trigger**  
   You can test your connection. With the test triggers enabled (find them in the `Debug Menu`), you can simulate events like a sub (`!test`), a gift sub (`!gift`), or a sub gift bomb (`!giftbomb [N]`).

   **MAKE SURE YOUR TIMER IS RUNNING! OTHERWISE, YOU CAN'T ADD EVENTS TO THE QUEUE THROUGH TWITCH!**

   If you want to add some events to the queue, you can simply use the command `/queue add <AMOUNT> <Username>`. This also works when the timer isn't running!

---

## 🔍 Permissions for non-OP users (LuckPerms required)

| Permission                    | Description                     |
| ----------------------------- | ------------------------------- |
| twitchrandomizer.gui          | Full GUI access (super-node)    |
| twitchrandomizer.gui.use      | Use GUI (basic)                 |
| twitchrandomizer.admin.save   | Save & hot-reload config        |
| twitchrandomizer.admin.edit   | Edit values via GUI             |
| twitchrandomizer.admin.twitch | Set Twitch channel/token        |
| twitchrandomizer.timer.start  | Start the timer                 |
| twitchrandomizer.timer.stop   | Stop/pause the timer            |
| twitchrandomizer.timer.reset  | Reset the timer                 |
| twitchrandomizer.reset        | Reset flow                      |
| twitchrandomizer.reset.force  | Force reset (skip confirmation) |

The GUI uses sensible permission checks for each action.

## 📎 Requirements

- Minecraft Paper server 1.21+
- Java 21 or newer
- Twitch account (at least affiliate) and OAuth token for chat integration

---

## 📋 Changelog

### v1.6.0

- **New event: `permanent_hearts`** — Permanently gain or lose 1–2 maximum hearts (chosen randomly). The change persists across disconnects via PersistentDataContainer and is reset on world reset.
- **Fake Totem 50/50** — The `fake_totem` event now gives a real 50/50 chance: half the time it's a real totem, half the time it's fake. Both look identical in the inventory.
- **NASA Call through blocks** — The `nasa_call` event now detects when a player is blocked mid-flight and teleports them 1 block upward, allowing them to fly through ceilings.
- **Hot Potato 3× HP** — The Hot Potato zombie now spawns with 60 HP (3× the normal amount), making it much harder to kill before it explodes.
- **GUI Reset Confirmation** — The reset button in the Misc menu now opens a dedicated confirmation screen instead of running `/reset confirm` in chat.
- **Timer not reset on world reset** — The timer is no longer zeroed when the world is reset. It can only be reset manually via the Misc menu (same behavior as Deaths).
- **Ender Dragon Stats** — When the Ender Dragon is defeated, the plugin broadcasts a full run summary (time, deaths/tries, events triggered, total subs, donations in €, bits) and saves it to `stats.yml`. Timer is reset to 00:00:00, death counter is cleared, and the queue is emptied automatically.
- **Donation Stats Tracking** — StreamElements and Tipeeestream donations are now correctly tracked for the end-of-run stats. Test commands (`!test`, `!gift`, `!giftbomb`) are tracked as simulated subs.

### v1.5.1

- Various bug fixes and stability improvements.

### v1.5.0

- **Tipeeestream multi-account support** — `donations.yml` now uses `tipeee_accounts` with the same `"Channel:APIKEY;Channel2:APIKEY2"` format as StreamElements.
- **Channel tag display** — When playing with multiple Twitch channels, the source channel name is shown in brackets after every event trigger.
- **Synced multi-player events** — All random values (duration, item, mob type, potion effect, etc.) are now identical for every player in the same event dispatch.
- **Skyblock team fix** — All players are now teleported to a single meeting point before chunks are cleared.
- **Spectator-after-death team fix** — When a player dies, all online players are now moved to Spectator simultaneously.
- **Tries vs Deaths counter** — When Spectator-after-death is enabled, the counter is labeled "Tries/Versuche".
- **Euro/Event maximum** — The donation value per event is now capped at 5.0€.
- **Recent event cooldown** — A new anti-repeat algorithm reduces the weight of recently triggered events.
- **Queue pause on all-spectator** — Timer and queue now correctly pause as soon as all players are in Spectator mode.

### v1.4.0

- **New event: `equipment_shuffle`** — All tiered tools and armor in the inventory are randomly upgraded or downgraded by one tier.
- **StreamElements donation integration** — Donations via StreamElements now trigger in-game events.
- **Pause aura** — A spiral flame particle effect is displayed around all online players while the timer is paused.

---

## 🗝 Credits

Built by exnfachjan with the help of AI (Claude by Anthropic).  
Thanks to the Paper/Spigot communities and Twitch4J for solid foundations.

---

## 🔒 License

This project is licensed under the **GNU General Public License v3.0** —
see [LICENSE](LICENSE) for details.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

---

## ❤️ Support

Do you like my plugin? Feel free to support my work: https://streamelements.com/exnfachjan/tip
