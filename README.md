# 👻 Ghost of You

> A Minecraft mod that records your last minutes of life and spawns a looping ghost of yourself where you died.

**Loader:** Forge | **MC Version:** 1.20.1 | **Java:** 17

---

## What it does

When you die, the mod spawns a translucent "ghost" of yourself at the death location. The ghost loops the last 5 minutes of your actions forever — moving, jumping, mining, attacking. Over time your world becomes a museum of your own mistakes and adventures.

## Features

- 👻 Auto-spawned ghost on every death, with looped playback
- 🎮 Records movement, rotations, block breaks/places, attacks, item use
- 🔮 **Ghost Banisher** — craftable item to permanently remove a ghost
- ⚙️ Full in-game config (ModMenu compatible via Forge Config)
- 💾 Efficient binary recording (28 bytes/frame, ~10 KB per ghost on disk)
- ⚡ LOD system, frustum culling, chunk-aware ticking
- 🛠️ Admin commands: `/ghostofyou remove|list|pause`

## Status

🚧 **Pre-alpha** — in active development. See [PLAN.md](./PLAN.md) for the full roadmap.

## Build

```bash
./gradlew build
```

Output: `build/libs/ghostofyou-<version>.jar`

## License

MIT — see [LICENSE](./LICENSE)
