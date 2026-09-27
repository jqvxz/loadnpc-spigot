## LoadNPC

A lightweight Spigot plugin that spawns armor stand NPCs which keep chunks loaded, just like a real player would. Built for Minecraft 26.2.

## What it does

- Spawn visible, glowing armor stand NPCs that force-load surrounding chunks
- Optional timers so NPCs auto-remove after a set duration
- Each NPC gets a unique ID for easy management
- Config-based access control (no permissions plugin needed)
- NPCs persist through server restarts

## Commands

| Command | Description |
|---|---|
| `/loadnpc spawn [time]` | Spawn an NPC (e.g. `10m`, `1h`, `2h30m`, or empty for permanent) |
| `/loadnpc kill [id]` | Kill an NPC by ID or by looking at it |
| `/loadnpc list` | List your NPCs |
| `/loadnpc list all` | List all NPCs |
| `/loadnpc id` | Get the ID of the NPC you're looking at |

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
cp target/LoadNPC-1.0.0.jar /path/to/server/plugins/
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
  Alex:
    max-npcs: 2
```
