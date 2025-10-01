# TwitchRandomizer

TwitchRandomizer is a powerful Minecraft (Paper 1.21.8+) plugin that brings interactive, randomized challenges to your server... all controlled by your Twitch viewers! It seamlessly integrates your Minecraft gameplay with Twitch chat, letting your audience influence and trigger in-game events in real time.

---

## 📌 Key Features

- **Twitch Chat Integration:**  
  Twitch viewers can trigger random events by subscribing or donating bits to your channel!

- **Play this Challenge with your Twitch Friends:**
  The Plugin can read subscriptions and bits from multiple Twitch accounts. So you can play this challenge with your Twitch friends!
***Shared Chat might not work properly!***

- **Event Weighting and Customization:**  
  Adjust the likelihood of each event — make some more common, some rare, or disable them entirely. Easily reload or adjust settings on the fly.

- **Death Counter and Statistics:**  
  Built-in death tracking and player statistics, perfect for challenge runs and community competitions.

- **Queue System:**  
  Viewer-triggered events can be queued and managed, ensuring smooth pacing and no event spam. Even after a seed reset, the queue will still be the same!

- **Graphical Interface (GUI):**  
  Tweak plugin settings, manage event weights, and configure behaviors directly from an in-game GUI. Perfect for easy setup without any OP permissions!

- **Session and World Reset:**  
  Reset your seed after a death if you want a REAL challenge!

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

## 🎉 Example Events

- **spawn_mobs:** Spawn mobs near the player.
- **potion:** Apply random potion effects.
- **give_item:** Give or remove random items.
- **clear_inventory:** Empty player inventories.
- **teleport:** Teleport players to random locations.
- **damage_half_heart:** Deal half a heart of damage.
- **floor_is_lava:** Turns the floor beneath you to lava.
- ...and many more!

---

## 🛠 Getting Started

You can configure the plugin through the config.yml or without OP permissions directly through the GUI in-game.

1. **Install:**  
   Place `TwitchRandomizer.jar` into your server's plugins folder.

2. **Configure Twitch Integration:**  
    - Open the GUI with `/trgui`; in the top left, you'll find the "twitch-info" book.
    - Insert your `Broadcaster Username` by left-clicking on the book, then confirm with `done`.
    - Insert your `Bot OAuth` by right-clicking on the book, then confirm with `done`.  
      You can get your OAuth token here: https://twitchtokengenerator.com/quick/1KRFjxyoNE  
      Make sure the following scopes are active: `bits.read`, `chat:read`, `channel:read:subscriptions` and `user:read:subscriptions`.

3. **Reload/Restart:**  
    If you use the GUI, you don't need to restart the server.

4. **Set up your trigger**  
    You can test your connection. With the test triggers enabled (find them in the `Debug Menu`), you can simulate events like a sub (`!test` or `!gift`) or a sub gift bomb (`!giftbomb`).

    **MAKE SURE YOUR TIMER IS RUNNING! OTHERWISE, YOU CAN'T ADD EVENTS TO THE QUEUE THROUGH TWITCH!**

    If you want to add some events to the queue, you can simply use the command `/queue add <AMOUNT> <Username>`. This also works when the timer isn't running!

---

## 🔍 Permissions for non-OP users (LuckPerms required)
| Permission | Description |
| --- | --- |
| twitchrandomizer.gui | Full GUI access (super-node) |
| twitchrandomizer.gui.use | Use GUI (basic) |
| twitchrandomizer.admin.save | Save & hot-reload config |
| twitchrandomizer.admin.edit | Edit values via GUI |
| twitchrandomizer.admin.twitch | Set Twitch channel/token |
| twitchrandomizer.timer.<start/stop/reset> | Timer control |
| twitchrandomizer.reset  | Reset flow |

The GUI uses sensible permission checks for each action.

## 📎 Requirements

- Minecraft Paper server 1.21.8+
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