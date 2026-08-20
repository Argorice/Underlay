# Changelog

## 0.6.0 — initial public release

- Coverings (carpets, moss, snow) can share a cell with any non-full block:
  chests, anvils, doors, fences, machines, furniture.
- Place by clicking a covering item on the free part of the carrier's face;
  break by left-clicking the covering itself — it targets like a real block.
- Blocks can be placed on top of coverings and stand flush on them; real
  carpet blocks are absorbed into an underlay when you build on them.
- Snow stacks 1→8 like vanilla; a full drift in an open cell becomes a real
  snow block. Optional melting in warm biomes / bright light.
- Real collision: carpets give the vanilla 1/16 step, snow its stepped heights.
- Support physics: breaking the block below drops the covering.
- Driven by data tags (`underlay:allows_underlay`, `underlay:denies_underlay`,
  `underlay:coverings`) with per-block overrides in an in-game settings screen
  (default key `U`).
- Create integration: contraptions carry their coverings and restore them on
  disassembly, rotations included.
- Server/client configs; small Java API for mod developers.
- Stored as chunk data — no block entities, nothing ticks, layers survive
  chunk unloads, restarts and `/reload`.
