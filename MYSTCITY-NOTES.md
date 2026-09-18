# MystMines — a fork of CataMines 3.0

CataMines stopped receiving updates, so this is a fork rather than a
rewrite: the world-edit-backed resets, compositions and reward system are
Catalysm's and work well. Everything added for MystCity lives under
`me.catalysmrl.catamines.myst.*` so it stays separable and the upstream code
can still be diffed against the original if it ever comes back.

CataMines is GPL-3, so this fork is too. The original licence and copyright
stay in place.

## Added

### Mining levels (`levelling.yml`, `/level`)
One level per player rather than one per mine — a mining career, not
fourteen separate grinds. Every block broken in a mine pays XP: per block
type, else per mine, else a default. Curve is `base x level^exponent`.

Levelling up throws a full-screen **LEVEL {n}** title. Every tenth level is a
milestone: different colours, a firework, a server broadcast, and its own
rewards. Levels deliberately do **not** touch drops or sell prices — a small
permanent boost per level compounds into something you can't tune later,
whereas a crate key at level 25 can be changed any time.

- `/level` — a menu: your bar, your numbers, your placing, and the next five
  milestones with what each pays (read from `reward-names` so the menu can
  never disagree with the actual rewards)
- `/level top` — podium for the top three, heads below, your own placing
  pinned at the bottom
- `/level set|give|reset|check|reload` — staff

### Mine events (`events.yml`, `/mine`)
Each changes one thing on purpose; an event that alters five things at once
can't be tuned.

- `party` — the whole mine becomes one rich block
- `vein` — a seam of a valuable block mixed in at a percentage
- `rush` — the reset delay collapses, so it refills as fast as it empties
- `double` — drops and mining XP multiplied, nothing looks different
- `meteor` — one prize block lands, first to break it takes the reward

When an event ends, the mine's own block chances and reset delay are put
back exactly as they were and it resets. Nothing is written to disk during
an event, so a restart mid-event costs at most one reset.

## Config migration
The fourteen live mines were CataMines **2.x** format
(`de.c4t4lysm...CuboidCataMine`) and 3.0 uses a completely different
structure. They're converted in `mines-converted/`: regions, compositions,
reset mode and delay, countdown, warn settings and teleport points all
carried over.

Two things that didn't survive, because 3.0 has no equivalent:
- `replaceMode` on **duke**
- the per-mine `warnSeconds` list — 3.0 takes a single threshold, so the
  largest was kept

## Still worth doing
- Per-player instanced mines
- Mine contribution boards (who broke most this reset)
- Prestige-gated mines, tying into MystDrift's prestige checks
- A boss bar showing how full a mine is before its reset
