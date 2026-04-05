# TwitchRandomizer

TwitchRandomizer is a powerful Minecraft (Paper 1.21+) plugin that brings interactive, randomized challenges to your server... all controlled by your Twitch viewers! It seamlessly integrates your Minecraft gameplay with Twitch chat, letting your audience influence and trigger in-game events in real time.

---

## 📌 Key Features

- **Twitch Chat Integration:**  
  Twitch viewers can trigger random events by subscribing or donating bits to your channel!

- **Play this Challenge with your Twitch Friends:**  
  The plugin can read subscriptions and bits from multiple Twitch accounts. So you can play this challenge with your Twitch friends!  
  **_Shared Chat might not work properly!_**

- **Twitch Role Colors:**  
  Viewer names appear color-coded in Minecraft chat — Broadcaster (red), Moderator (green), VIP (pink), and regular viewers (white).

- **Event Weighting and Customization:**  
  Adjust the likelihood of each event — make some more common, some rare, or disable them entirely. Easily reload or adjust settings on the fly. Reset all weights to defaults with one click.

- **Death Counter and Statistics:**  
  Built-in death tracking and player statistics, perfect for challenge runs and community competitions.

- **Queue System:**  
  Viewer-triggered events can be queued and managed, ensuring smooth pacing and no event spam. The queue persists across server restarts and world resets!

- **Pause System:**  
  Timer and queue automatically pause when a player is in the death screen or when all players are in spectator mode. Fully configurable.

- **Graphical Interface (GUI):**  
  Tweak plugin settings, manage event weights, control the timer, and configure behaviors directly from an in-game GUI. Perfect for easy setup without any OP permissions!

- **Multi-Language Support:**  
  Full German and English UI with automatic client language detection. Players can override their language in the GUI.

- **Session and World Reset:**  
  Reset your seed after a death if you want a REAL challenge! Supports BungeeCord fallback servers.

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

| Event                 | Description                                                                       |
| --------------------- | --------------------------------------------------------------------------------- |
| **spawn_mobs**        | Spawn random mobs near the player (hostile mobs chase you!)                       |
| **potion**            | Apply a random potion effect                                                      |
| **give_item**         | Give a random item                                                                |
| **clear_inventory**   | Clear random slots from the player's inventory                                    |
| **teleport**          | Teleport to a random location (safe Y-level)                                      |
| **damage_half_heart** | Take heart damage                                                                 |
| **fire**              | Set the player on fire                                                            |
| **inv_shuffle**       | Shuffle the entire inventory                                                      |
| **hot_potato**        | A fast zombie chases you — if it catches you, it explodes!                        |
| **no_crafting**       | Block crafting for a random duration (with boss bar countdown)                    |
| **safe_creepers**     | 6 Creepers surround you clock-style and explode (no damage, no block destruction) |
| **floor_is_lava**     | The floor beneath you turns to magma blocks (with block restore)                  |
| **nasa_call**         | Launch the player sky-high                                                        |
| **slippery_ground**   | The ground turns to packed ice (with block restore)                               |
| **hell_is_calling**   | Homing fireballs rain down on you                                                 |
| **tnt_rain**          | TNT Minecarts rain from the sky                                                   |
| **anvil_rain**        | Anvils fall from the sky                                                          |
| **skyblock**          | All surrounding chunks get deleted — instant Skyblock!                            |
| **fake_totem**        | Receive a Totem of Undying that doesn't actually work                             |

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

3. **Reload/Restart:**  
   If you use the GUI, you don't need to restart the server.

4. **Set up your trigger**  
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

## 🗝 Credits

Built by exnfachjan with the help of AI (Copilot and ChatGPT).  
Thanks to the Paper/Spigot communities and Twitch4J for solid foundations.

---

## 🔒 License

MIT License — see [LICENSE](LICENSE) for details.

---

## ❤️ Support

Do you like my plugin? Feel free to support my work: https://streamelements.com/exnfachjan/tip
