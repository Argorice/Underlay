# Underlay

**A decorative bottom layer for any block. Carpets continue under chests, snow drifts around fences, your floor stays whole.**

*Minecraft 1.21.1 · NeoForge*

---

Ever built a nice carpeted hall, placed a chest, and got a hole in your floor?
In vanilla, a block cell can only hold one block — so every anvil, door, fence
or machine punches a gap into your carpet.

Underlay fixes that. It lets a thin covering — a carpet, moss, or a snow
layer — share a cell with the block standing on it. Purely visual with a touch
of real collision, no ticking block entities, no impact on redstone or game
mechanics.

## What you can do

- **Lay a carpet under things.** Click a carpet on the free part of the floor
  around an anvil (or any non-full block), and the carpet slides into its
  cell. Chests, beds, doors, fences, flower pots, machines, furniture — if the
  covering would be visible, it can go under it.
- **Break it like a carpet.** Aim at the visible part of the covering — it
  highlights like a real block — and left-click. It pops off as an item.
- **Build on it.** Place blocks onto a carpet and they stand flush on top of
  it. Placing a block onto a *real* carpet block absorbs the carpet into an
  underlay automatically — no more blocks floating one cell above your rugs.
- **Stack snow.** Snow layers pile up 1→8 just like vanilla. A drift that
  reaches full height in an open cell becomes a real snow block.
- **Walk on it.** Coverings have their real collision: carpets give the usual
  1/16 step, snow gives the vanilla stepped heights.
- Carpets need support, exactly like the real thing: break the block below and
  the covering drops.

## Works with your mods

Nothing is hardcoded. Which blocks accept a covering and which items count as
coverings is driven by data tags, so any mod works out of the box or with a
tiny datapack. By default, *any* block that leaves the bottom of its cell
visible is a valid carrier — Create machines, Supplementaries decor, Quark
chests, furniture mods, all of it.

There's also an in-game settings screen (default key `U`, or via the mod
list): blocks grouped by mod, search, per-block toggles. Your choices are
overrides on top of the tags, so mod updates never wipe them.

Create contraptions carry their coverings along: assemble a glued machine and
the carpets under its blocks travel with it, coming back in the right cells on
disassembly — rotation included.

## Configuration

Server config (`config/underlay-server.toml`): master switch, item drops,
snow melting, the "any non-full block is a carrier" rule, layer collision.
Client config: render distance for layers, biome tint. Tag overrides from the
settings screen live in `config/underlay-overrides.json`.

For datapack authors, three tags control everything:

```
underlay:allows_underlay   blocks that can stand on a covering
underlay:denies_underlay   blacklist, wins over everything
underlay:coverings         items that can be laid down (item tag)
```

There is also a small Java API (`dev.argorice.underlay.UnderlayAPI`) for mod
developers who want programmatic access.

## Known issues

- Create Aeronautics / Sable airships assemble through their own physics
  pipeline, and coverings don't travel with them yet. Regular Create
  contraptions (bearings, pistons, gantries, trains) work fine.
- A full-height snow drift packed around a block (a buried fence) stays
  visual, so torches and other support-needing blocks can't be placed on it.
  Free-standing drifts turn into real snow and behave normally.
- Coverings inside a flying contraption aren't rendered mid-flight; they
  reappear on disassembly.

## Building from source

JDK 21, then `./gradlew build` — the jar lands in `build/libs/`.

## License

MIT. Do whatever you like with it — just credit **Argorice**.
