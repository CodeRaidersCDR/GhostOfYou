# Ghost of You — Implementation Plan

## Architecture

```
SERVER:
  PlayerTickHandler
    → per-player PlayerRecorder (CircularFrameBuffer, ActionEventLog)
  
  On LivingDeathEvent (ServerPlayer):
    → RecordingSerializer → GZIP-compressed NBT
    → GhostEntity spawned at death location
    → FIFO cap enforcement
  
  ServerTickHandler (every tick):
    → LOD logic per GhostEntity
    → PlaybackController.tick() applies delta frames

CLIENT:
  GhostEntityRenderer
    → extends LivingEntityRenderer<GhostEntity, PlayerModel<GhostEntity>>
    → AlphaMultiBufferSource wraps MultiBufferSource to apply ghostTransparency
    → RenderType.entityTranslucent
  GhostFeatureRenderer — glow outline layer
```

## File Order (creation sequence)

1. Build files: gradle.properties, settings.gradle, build.gradle, gradlew
2. Resources: mods.toml, pack.mcmeta, lang, models, recipes, loot tables
3. Main class + config
4. recording/ package
5. entity/ package
6. item/ + block/ packages
7. network/ + event/ + command/ packages
8. client/ package
9. util/ package
10. .github/workflows/

## Performance Notes

- CircularFrameBuffer uses ByteBuffer.allocateDirect → off-heap, no GC
- Frame.read/write use absolute position addressing → no buffer position mutation
- PlaybackController uses ByteBuffer.wrap (heap) for read-only playback
- ServerTickHandler checks chunk loading before ticking ghosts
- LOD distances: 32 / 64 / 128 blocks
