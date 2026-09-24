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

### /cm gui
Upstream's was a stub ("GUI is currently disabled in this version", with a
TODO to port the legacy menu). Built fresh on the fork's menu base: every
mine on one screen showing what it's made of, its reset mode and timer, how
full it is (sampled, not counted — a big mine is millions of blocks), any
running event, who's leading its contribution board, its level gate and this
week's challenge. Click to teleport; staff shift-click to reset and
middle-click to stop or start (`catamines.gui.manage`).

### Cleanup, 2026-09-19
Two generations of the same features had ended up wired in together —
`ContributionBoards`/`Contributions`, `soul.PickaxeSouls`/`pick.PickaxeSouls`,
`MineExtras` with its own fossil — both registered, fighting over the same
events and the same item keys. That was the doubled fossil messages, the
stuck soul progress and the dead party. Consolidated onto the set whose
configs match; the old classes and `boards.yml`/`extras.yml` are gone.

- **Fossils removed** entirely. The last-block bonus, which lived in the same
  class, has its own home now (`dig/LastBlock`).
- **Pickaxe souls** — `getItemInMainHand()` can return a copy on newer
  Paper, so edits never reached the inventory. The edited stack is now set
  back into the hand explicitly.
- **Mine party** painted only the CURRENT composition, but a reset refills
  from the UPCOMING one, so nothing changed. Paints every composition of every
  region now, and restores them all on stop.
- **`/mine event stop <mine>`** works — `event` is accepted and skipped.
- **`/cm gui`** clicking a mine (as staff) opens a per-mine editor: blocks and
  percentages editable in place with the total shown green/red at 100%,
  add a block by holding it, teleport, a reset countdown on your action bar,
  the global-announce toggle, a reset delay editor (never below 5s), an
  enable/disable switch, and reset-now.
- **/level** redone as three rows: you / what's coming / where to go.
  **/level top** puts the level in every tile's name.
- Levels, contributions and challenges are all saved on shutdown.

`/cm setresetteleport` takes the mine name: `/cm setresetteleport king`. If
it's still refusing with the name given, paste what it says.

### The big one: CataMineBlockBreakEvent was never fired
`MineManager.callBlockBreak` was an empty method upstream, so the plugin's
own break event never fired — and everything listening for it (mining
levels, pickaxe souls, contribution boards, weekly challenges) silently did
nothing, which is why none of it appeared to work. Implemented: it finds the
mine and region for the broken block, matches it against the composition,
fires the event, and honours a cancel.

`/mine debug` reports mines loaded, each one's reset state, which mine you're
standing in, and your own level — so "nothing is happening" can be answered
in one command.

### Soul lore wiped other plugins' lore
`describe()` rewrote the pickaxe's lore wholesale, so a custom enchant's
lines were destroyed on awaken and again on every redraw — which also made a
newly applied enchant look like it hadn't applied, since it was added and
then wiped on the next swing. Each soul line now starts with a zero-width
space, so a redraw removes exactly its own lines and leaves everything else
where it was.

### Events could lose a mine's composition for good
Three separate faults, all ending in a mine stuck on diamond:

1. **Restoring relied on memory.** If the entry was replaced or the server
   went down mid-event, the original blocks were gone. A mine's file is now
   copied to `mines/event-backups/` BEFORE anything is painted, and ending an
   event restores from that file and re-reads the mine from disk. Nothing in
   the restore path depends on memory.
2. **Starting over an expired-but-uncleaned entry** overwrote the object
   holding the originals. It now ends the old one properly first.
3. **`stop()` matched by prefix**, so stopping "vig" could remove "vigwood"'s
   entry instead — which is exactly what the chat log showed. Exact match
   only, ignoring case.

`/mine restore <mine>` puts a mine back from its backup by hand, and any
backup still on disk at startup means an event never finished, so it's
restored automatically with a warning.

### Why events broke on some mines and not others
`Active.mine` held the map key — the mine's name LOWERCASED — and `stop()`
then looked the mine up with an exact-match `getMine()`. So any mine whose
name has a capital letter (Vig, VigWood, Knight) failed the lookup: the
entry had already been removed from the map, stop() returned false and
reported "no events running", and the mine was left painted permanently.
`duke` and the other all-lowercase mines were unaffected, which is exactly
the pattern that showed up in testing.

Fixed three ways: Active now also stores the mine's real name and uses it;
MineEvents looks up case-insensitively; and `MineManager.getMine` itself
falls back to a case-insensitive match, so every caller benefits.

### Reload
Upstream's `/cm reload` was a stub that printed "Not supported yet". It's
implemented now: running tasks stopped, current mines saved (so an in-game
edit isn't lost), the list cleared, everything read fresh from disk and
restarted. It reloads every added config too, so one command covers the lot.
`/level reload`, `/mine reload` and `/pick reload` still work for their own
files.

### Robustness fixes found in testing
- A bad `drop-type` threw during deserialization, which killed not just that
  mine but every mine after it in the folder — so one typo produced zero
  mines and a stack trace naming no file. Unknown values now warn and fall
  back to CUSTOM, and each file is loaded in its own try so one bad mine
  can't take the rest with it.
- **king.yml had `reset-delay: 0` on TIME mode** (it was 0 in the 2.x config
  too), so it reset every tick — which meant a fossil buried and announced
  every tick. Set to 600. The loader now warns on any TIME mine with a
  delay of 0 rather than letting it quietly hammer the server.
- Fossils and contribution payouts are rate-limited per mine
  (`minimum-seconds-between`), so a fast-resetting mine can't spam either.

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

### Pickaxe souls removed (2026-09-24)
`/pick awaken` and the whole `myst.pick` package are gone, with `souls.yml`
and the `mystmines.souls.*` permissions. Pickaxe progression is MystStats
now (Mining Speed, Mining Fortune, Breaking Power, reforges, stars, gems).
Old soul lore lines (the ones starting with a zero-width space) are stripped
by MystStats when it stamps a pickaxe. MystStats hooks this plugin's
`CataMineBlockBreakEvent` reflectively for Breaking Power gating and gem
drops - nothing here depends on MystStats.
