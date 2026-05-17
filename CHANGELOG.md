# Changelog

## [0.1.0] - 2024-01-01

### Added
- Initial release
- Player movement recording with off-heap circular buffer (28 bytes/frame)
- Ghost entity spawned at death location, loops recording
- Ghost Banisher item (craftable) — 3-second channeling to permanently remove a ghost
- Ghost Essence item — dropped by banished ghosts, used to craft Memorial Block
- Memorial Block — decorative block crafted from Ghost Essence + Stone
- LOD-based tick scheduling (near/mid/far/frozen zones)
- Per-chunk and per-player ghost caps with FIFO eviction
- Forge Config: server-side common config + client-side rendering config
- Admin commands: remove, list, pause, stats
- Russian (ru_RU) and English (en_US) localizations
- GitHub Actions CI/CD workflows
