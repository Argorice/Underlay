# Underlay (archived)

**A decorative bottom layer for any block — carpets under chests, snow under fences, floors that stay whole.**

*Minecraft 1.21.1 · NeoForge · No longer developed*

---

## Use this instead

> ### [**Underlay by Dooji**](https://github.com/dooji2/underlay)
> [Modrinth](https://modrinth.com/mod/underlay) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/underlay)
>
> Same idea, years of polish, 8.7M+ downloads, and it runs on Fabric, Forge and NeoForge. If you came here looking for a mod, that's the one to install.

I started this without checking whether it already existed. It did — under the same name, no less. Rather than ship a second mod into a solved problem, I'm archiving this one and pointing at the original.

The code stays public because parts of it may be useful to someone.

---

## What was here

Placing a covering into an occupied cell, a settings screen, and:

- **Chunk-attachment storage.** Layers live in a per-chunk data attachment — no block entities, nothing ticking, saved and loaded with the chunk itself. Serialized format is versioned, and registry ids of coverings from uninstalled mods are preserved rather than dropped, so removing a mod never corrupts a chunk.
- **Baked into the section mesh** via `AddSectionGeometryEvent` rather than drawn per frame. Render cost is paid once on section rebuild.
- **Real collision.** Carpets give the usual 1/16 step, snow the vanilla stepped heights.
- **Snow stacking** 1→8, becoming a real snow block when a drift fills an open cell.
- **Absorbing real carpets** — placing a block onto a carpet turns it into an underlay instead of leaving the block floating.
- **Create contraptions** carry their coverings through assembly and disassembly, rotation included.
- **Tag-driven** carriers and coverings, with an in-game screen for per-block overrides on top of the tags.

## Honest comparison

Where this one went further:

| | This | Dooji's |
|---|---|---|
| Storage | Per-chunk attachment | Global map per dimension, custom persistence |
| Collision | Real, per covering | Visual only |
| Snow stacking | 1→8, promotes to full block | — |
| Create contraptions | Carried through assembly | — |
| Settings UI | In-game screen, grouped by mod | JSON config |

Where Dooji's is ahead — and it matters more than the list above:

- **Sodium compatibility.** Sodium replaces the chunk meshing path, and without a dedicated patch, layers simply don't render for most players. Dooji's has that patch; this doesn't.
- **Jade, WorldEdit, minecarts, structure templates.** The kind of integration you only discover through years of bug reports.
- **Fabric, Forge and NeoForge**, across many Minecraft versions.

A cleaner storage model doesn't beat working in the packs people actually play. That's the honest summary.

## Credits

The concept, the name, and the mod worth installing all belong to [Dooji](https://github.com/dooji2). No affiliation, no endorsement — just a pointer to the better option.

## License

LGPL-3.0. Take whatever's useful.
