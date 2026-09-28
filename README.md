## LoadNPC

A lightweight Spigot plugin that spawns armor stand NPCs which keep chunks loaded, just like a real player would. Built for Minecraft 26.2.

## What it does

- Spawn player-like NPCs with custom skin, clothing, and natural poses that force-load surrounding chunks
- Optional timers so NPCs auto-remove after a set duration
- Each NPC gets a unique ID for easy management
- Config-based access control or standard Bukkit/LuckPerms permissions
- NPCs persist safely across server restarts with chunk ticket management

## Commands

| Command | Description |
|---|---|
| `/loadnpc spawn [time]` | Spawn a player NPC (e.g. `10m`, `1h`, `2h30m`, or empty for permanent) |
| `/loadnpc kill [id]` | Kill an NPC by ID or by looking at it |
| `/loadnpc list [all]` | List your NPCs (or all NPCs) |
| `/loadnpc id` | Get the ID of the NPC you're looking at |
| `/loadnpc info [id]` | View detailed chunk, coordinates, and timer info |
| `/loadnpc tp <id>` | Teleport to an NPC |
| `/loadnpc extend <id> <time>` | Extend the duration of an active NPC |
| `/loadnpc clear <player\|all>` | Bulk-remove NPCs for a player or server-wide |
| `/loadnpc reload` | Reload the configuration (alias: `/load-npc reload`) |

## Installation

### Requirements
1. Java 21+ installed.
2. A Spigot 26.2 server.

### Building from source
```bash
# Clone the repository
git clone https://github.com/jqvxz/loadnpc-spigot.git
cd loadnpc-spigot

# Build the plugin
./mvnw.cmd clean package

# Copy the JAR to your server
cp target/loadnpc-1.2.1.jar /path/to/server/plugins/
```

### Fast installation
1. Go to [releases](https://github.com/jqvxz/loadnpc-spigot/releases) and download the latest `LoadNPC.jar` file.
2. Drop it into your server's `plugins/` folder and restart.

## Configuration

Edit `plugins/LoadNPC/config.yml` to configure chunk radius, per-player NPC limits, and access control.

To use config-based access instead of permissions, set `use-config-access: true` and define players:

```yaml
use-config-access: true

players:
  Steve:
    max-npcs: 5
    can-kill-others: true
    can-list-all: true
    can-reload: true
  Alex:
    max-npcs: 2
```
