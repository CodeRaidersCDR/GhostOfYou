# Ghost of You

A Minecraft Forge 1.20.1 mod that records your last minutes of life and spawns a looping ghost at the place you died.

## Features

- Automatically records the last 5 minutes (configurable) of each player's movement
- Spawns a translucent ghost entity at the death location upon death
- Ghost plays back in an infinite loop; multiple ghosts accumulate over time
- **Ghost fully respects respawn/dimension changes** — each death produces a unique path with non-zero deltas
- Craft a **Ghost Banisher** to permanently remove ghosts
- **Dramatic banishment effect** — soul-fire spiral, imploding particle sphere, lightning bolt, and layered sounds
- **Ghost death animation** — ghost tilts forward and fades out over 2 seconds on banishment
- **Ghost Essence** is dropped when a ghost is banished, carrying owner name, death cause, killer, and lifetime data
- Right-click a **Ghost Essence** to restore 4 hearts and gain Resistance II + Regeneration II for a short time
- Brew a **Ghost Essence** with an **Awkward Potion** to get a **Potion of Ethereal Form** (Slow Falling + Speed II)
- Craft a **Memorial Block** and bind it with Ghost Essence to create a persistent tribute that buffs nearby players
- **Ghost Essence** can be found in dungeon and structure loot chests
- Full Forge config with server-side and client-side options

## Recipes

### Ghost Banisher
```
  G
GBG
  S
G = Ghast Tear, B = Ghost Essence, S = Blaze Rod
```

### Memorial Block
```
 S
SES
PPP
S = Soul Soil, E = Ghost Essence, P = Polished Blackstone
```

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

## Ghost Essence Loot

Ghost Essence can be found in the following structures:

| Structure | Chance |
|-----------|--------|
| Dungeon | 25% (1-2) |
| Stronghold corridor | 30% (1-2) |
| Stronghold crossing | 30% (1-2) |
| Abandoned mineshaft | 15% (1-2) |
| Woodland mansion | 40% (1-2) |
| Ancient city | 50% (1-3) |
| Bastion treasure | 35% (1-2) |
| End city treasure | 45% (1-3) |
| Buried treasure | 20% (1-2) |

## License

MIT — see [LICENSE](LICENSE).
