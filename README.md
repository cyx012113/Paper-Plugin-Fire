# 🔥 FirePlugin

**Version 2.0.0** – A lightweight, high‑performance server utility plugin for Paper 1.21.11 (and similar versions).  
It provides world‑based economy reset, block protection, building restrictions, advanced world rules, anti‑spam, a broadcast credit system, and a clickable invite system.

---

## ✨ Features

- **Economy reset** – Automatically clear players' Vault balances in specified worlds (supports regex/wildcard).
- **Block protection** – Only blocks placed by players can be broken, burned, or destroyed by explosions.
- **Build restriction** – Prevent block placement in chosen worlds.
- **World rules** – Per‑world options:
  - Disable PvP
  - Make players invincible
  - Unlimited saturation (no hunger loss)
  - Void respawn (teleport back to world spawn instead of dying)
- **Anti‑spam** – Limit chat messages per time interval.
- **Broadcast system** – Players spend credits to send server‑wide messages. Credits are stored per player and can be managed by admins.
- **Invite system** – Send clickable invites to individuals or everyone. Invited players click the message to teleport directly to the inviter's world.
- **All world rules support regex/wildcard patterns** (e.g. `lobby*`, `pvp-*`, `*`).
- **Bypass permissions** – Each restriction has its own permission node.
- **Efficient listener‑based design** – No performance‑heavy persistent storage for placed blocks.

---

## 📥 Installation

1. Download the latest `fire-2.0.0.jar`.
2. Place it in your server’s `plugins/` folder.
3. Restart the server.
4. Configure `plugins/fire/config.yml` to your needs (see below).
5. Use `/fireadmin reload` to reload the configuration at any time.

---

## 🔧 Commands

| Command & Aliases                                            | Permission             | Description                                                  |
| ------------------------------------------------------------ | ---------------------- | ------------------------------------------------------------ |
| `/broadcast <message>` = `/bc <message>`                     | `fire.broadcast.use`   | Send a broadcast (consumes 1 credit).                        |
| `/broadcastadmin add <player> <amount>` = `/bcadmin add ...` | `fire.broadcast.admin` | Add broadcast credits to a player.                           |
| `/broadcastadmin set <player> <amount>`                      | `fire.broadcast.admin` | Set a player's credits to a specific amount.                 |
| `/broadcastadmin clear <player>`                             | `fire.broadcast.admin` | Reset a player's credits to 0.                               |
| `/broadcastadmin check <player>`                             | `fire.broadcast.admin` | Show a player's remaining credits.                           |
| `/invite <player\|all> [world]` = `/inv ...`                 | `fire.invite.send`     | Send an invite to a player or everyone. Optional world name. |
| `/invite accept <inviter>`                                   | `fire.invite.join`     | Accept an invite and teleport to the inviter's world.        |
| `/fireadmin reload` = `/fadmin reload`                       | `fire.admin`           | Reload the plugin configuration.                             |

---

## 🔐 Permissions

| Node                   | Default | Description                                            |
| ---------------------- | ------- | ------------------------------------------------------ |
| `fire.bypass.clear`    | `op`    | Prevents economy reset in reset worlds.                |
| `fire.bypass.block`    | `op`    | Allows breaking any block (bypasses block protection). |
| `fire.bypass.build`    | `op`    | Allows placing blocks in `no-build-worlds`.            |
| `fire.broadcast.use`   | `true`  | Allows using `/broadcast`.                             |
| `fire.broadcast.admin` | `op`    | Allows managing broadcast credits.                     |
| `fire.invite.send`     | `true`  | Allows sending invites.                                |
| `fire.invite.join`     | `true`  | Allows accepting invites.                              |
| `fire.admin`           | `op`    | Allows reloading the plugin.                           |

---

## ⚙️ Configuration (`config.yml`)

```yaml
# Worlds where player balances are cleared (regex/wildcard)
economy-reset-worlds:
  - "Lobby"
  - "lobby_*"
economy-reset-interval-ticks: 1200 # 20 ticks = 1 sec

# Worlds where only player‑placed blocks can be broken/burned/exploded
block-protection-worlds:
  - "*"

# Worlds where block placement is forbidden (requires fire.bypass.build)
no-build-worlds:
  - "Lobby"
  - "hub*"

# Worlds where PvP is disabled
pvp-disabled-worlds:
  - "Lobby"

# Worlds where players take no damage (invincible)
invincible-worlds:
  - "Lobby"

# Worlds where hunger never decreases
saturate-worlds:
  - "Lobby"

# Worlds where falling into the void teleports you to world spawn
void-respawn-worlds:
  - "*"

# Anti‑spam settings
anti-spam:
  interval-seconds: 3
  max-messages: 2

# Broadcast system
broadcast:
  default-credits: 3 # Starting credits per player

# Invite cooldown (seconds)
invite:
  cooldown-seconds: 10
```

> **Note:** Patterns support `*` wildcard and regular expressions. Example: `"lobby*"` matches `lobby`, `lobby_1`, `lobby_world`, etc.

---

## 🛠 Building from source

1. Clone or download the source code.
2. Ensure you have Java 21 and Maven installed.
3. Run `mvn clean package`.
4. The compiled JAR will be in `target/fire-2.0.0.jar`.

---

## 📄 License

This project is licensed under the **CC BY-SA 4.0** license.  
You are free to share, adapt, and use it, provided you give appropriate credit and distribute derivatives under the same license.

---

## 💬 Support

For issues or suggestions, please contact the plugin author: `cyx012113` (Discord / GitHub).  
Pull requests and contributions are welcome under the same license.
