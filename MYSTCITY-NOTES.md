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

### Added in round two
- **Titles** — a DeluxeTags tag per level threshold (`titles:`), granted as a
  permission through LuckPerms and the previous one revoked, so a player holds
  exactly one mining tag. Make the tags in DeluxeTags first.
- **Level-gated mines** — `gates.<mine>: <level>`. You can walk in and look;
  you can't break anything. Prestiged players pass every gate.
- **Global announcement** on every level, not just milestones
  (`announce-every-level`).
- **Daily bonus** — the first 100 blocks each day pay 3× XP, announced when
  it starts and when it runs out. Shown in `/level`.
- **Prestige** — `/level prestige confirm` at max level: back to 1, a
  permanent ★, +5% XP per prestige, commands on `prestige.commands`. The
  leaderboard ranks prestige first.

### Added in round three
- **Contribution boards** (`boards.yml`) — per-reset counts, top three
  announced when the mine refills, winner paid, and whoever broke the LAST
  block paid too. `/mine board <mine>` places a live hologram of the top
  three. Needed a reset event upstream never had: `CataMineResetEvent`, fired
  from `AbstractCataMine.reset()`.
- **Pickaxe souls** (`souls.yml`) — a pick earns XP on the item and gains one
  permanent trait per soul level (fortune tick, self-repair, wielder xp, a
  rare key, money from stone). Lore is rebuilt from the PDC so it can't go
  stale.
- **Fossils** (`extras.yml`) — a bone-and-amethyst shape buried after a reset
  35% of the time; dig every bone out before touching what holds it up or it
  crumbles to gravel. Pays a legendary key.
- **Weekly challenges** (`extras.yml`) — one per mine, rotated by ISO week.
  `/mine challenges` shows them with your progress; first finisher gets extra.
- `SelectionRegion.getRegion()` added upstream so fossils and meteors can
  pick a spot inside a mine.

### Added in round three
- **Fossils** (`digging.yml`) — a buried bone-and-amethyst cluster placed on
  reset. Break part of it and a 25-second clock starts: get the rest out and
  it pays, run out and it crumbles to gravel. Not "find the rare block" but
  "spot it, then handle it".
- **Contribution boards** — who dug the most out of a mine *this reset*,
  wiped on every reset so it's a race rather than a lifetime total. Top three
  paid when the reset lands, lifetime totals kept for a board.
- **Pickaxe souls** (`souls.yml`, `/pick awaken`) — a pick earns its own XP
  and gains one permanent trait per threshold: Keen, Mending, Rich, Lucky,
  Old. Stored on the ITEM, so the pick is the thing people care about.
- **Last block bonus** — whoever empties a mine before it resets. Checked
  every 20 breaks rather than every swing, because counting a whole region
  each time would be madness.
- **Weekly challenges** (`challenges.yml`) — one per mine per week, seeded by
  the week number so everyone sees the same one. Mine-specific, which gives
  the lower mines a reason to exist after you've outgrown them.

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
