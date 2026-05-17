# Ghost of You

A Minecraft Forge 1.20.1 mod that records your last minutes of life and spawns a looping ghost at the place you died.

## Features

- Automatically records the last 5 minutes (configurable) of each player's movement
- Spawns a translucent ghost entity at the death location upon death
- Ghost plays back in an infinite loop; multiple ghosts accumulate over time
- Craft a **Ghost Banisher** to permanently remove ghosts
- **Ghost Essence** is dropped when a ghost is banished — use it to craft a **Memorial Block**
- Full Forge config with server-side and client-side options

## Requirements

- Minecraft 1.20.1
- Forge 47.3.0+
- Java 17

## Building

```bash
./gradlew build
```

The output JAR will be at `build/libs/ghostofyou-<version>.jar`.

## Running

```bash
# Client
./gradlew runClient

# Server
./gradlew runServer
```

## Configuration

### Server-side (`ghostofyou-common.toml`)

| Key | Default | Description |
|-----|---------|-------------|
| `enableRecording` | `true` | Toggle player recording on/off |
| `enableGhostSpawning` | `true` | Toggle ghost spawning on death |
| `recordingDurationSeconds` | `300` | How many seconds to keep in the buffer |
| `sampleIntervalTicks` | `4` | Record one frame every N ticks |
| `maxGhostsPerChunk` | `5` | FIFO cap per chunk |
| `maxGhostsPerPlayer` | `50` | FIFO cap per player |

### Client-side (`ghostofyou-client.toml`)

| Key | Default | Description |
|-----|---------|-------------|
| `ghostTransparency` | `0.6` | Ghost alpha (0.1 – 1.0) |
| `ghostGlowing` | `true` | Show outline glow |
| `renderDistance` | `48` | Render distance in blocks |

## Admin Commands (requires permission level 2)

| Command | Description |
|---------|-------------|
| `/ghostofyou remove player <player>` | Delete all ghosts of a player |
| `/ghostofyou remove all` | Delete all ghosts in current dimension |
| `/ghostofyou remove nearby <radius>` | Delete ghosts within radius |
| `/ghostofyou list` | List ghosts with coordinates |
| `/ghostofyou pause <true\|false>` | Toggle global playback pause |
| `/ghostofyou stats` | Show recording buffer stats |

## License

MIT — see [LICENSE](LICENSE).
