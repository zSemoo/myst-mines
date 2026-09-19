package me.catalysmrl.catamines.myst.event;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.Duration;
import java.util.*;

/**
 * Things that happen to a mine.
 *
 * All five run the same way: pick a mine, start it, it ends on its own.
 * What they change is deliberately narrow, because a mine event that alters
 * five things at once is impossible to tune:
 *
 *   PARTY        every block becomes one rich block for the duration
 *   GOLDEN_VEIN  a seam of a valuable block is drawn through the mine
 *   RUSH         the reset timer collapses, so it refills as fast as it empties
 *   DOUBLE_DROP  drops and mining XP are multiplied; nothing looks different
 *   METEOR       a single prize block lands, and the first to break it wins
 *
 * Only one event runs per mine at a time. Everything is announced, and
 * everything ends cleanly — a mine left mid-party after a restart resets
 * itself back to its own composition on the next natural reset.
 */
public class MineEvents {

    public enum Kind { PARTY, GOLDEN_VEIN, RUSH, DOUBLE_DROP, METEOR }

    public static class Active {
        public Kind kind;
        /** The map key: the mine's name, lowercased. */
        public String mine;
        /** The mine's name as it really is, for looking it up again. */
        public String realName;
        public long endsAt;
        public double dropMultiplier = 1, xpMultiplier = 1;
        /** For METEOR: where the prize block is, and whether it's been claimed. */
        public Location meteor;
        public boolean claimed;
        /** Restores the mine's own reset delay when a RUSH ends. */
        public int previousDelay = -1;
        /**
         * The compositions we repainted: the block list as it was, and each
         * block's own chance. CataMineBlock has no getter for its block
         * string, so chances are restored on the original objects rather
         * than rebuilt from scratch.
         */
        public final Map<me.catalysmrl.catamines.mine.components.composition.CataMineComposition,
                List<me.catalysmrl.catamines.mine.components.composition.CataMineBlock>> saved = new HashMap<>();
        public final Map<me.catalysmrl.catamines.mine.components.composition.CataMineBlock, Double> savedChances = new HashMap<>();
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CataMines plugin;
    private final Map<String, Active> running = new HashMap<>();
    private YamlConfiguration cfg;

    public MineEvents(CataMines plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "events.yml");
        if (!f.exists()) plugin.saveResource("events.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
    }

    public Active running(String mine) {
        if (mine == null) return null;
        Active a = running.get(mine.toLowerCase());
        return a != null && a.endsAt > System.currentTimeMillis() ? a : null;
    }

    public Collection<Active> all() { return running.values(); }

    /** What drops should be multiplied by in this mine right now. */
    public double dropMultiplier(CataMine mine) {
        Active a = mine == null ? null : running(mine.getName());
        return a == null ? 1 : a.dropMultiplier;
    }

    /** What mining XP should be multiplied by in this mine right now. */
    public double xpMultiplier(CataMine mine) {
        Active a = mine == null ? null : running(mine.getName());
        return a == null ? 1 : a.xpMultiplier;
    }

    // ------------------------------------------------------------------ starting

    public boolean start(Kind kind, CataMine mine, int seconds, CommandSenderLike by) {
        return start(kind, mine, seconds, null, by);
    }

    /** With a block override, for a party or vein of something other than the config's default. */
    public boolean start(Kind kind, CataMine mine, int seconds, String blockOverride, CommandSenderLike by) {
        if (mine == null) return false;
        String key = mine.getName().toLowerCase();
        if (running(key) != null) {
            by.tell("<red>" + mine.getName() + " already has an event running. <gray>/mine event stop " + mine.getName());
            return false;
        }
        // An entry that has expired but hasn't been cleaned up yet still owns
        // this mine's original blocks. Ending it properly first means its
        // composition is restored rather than overwritten and lost — which is
        // how a mine ended up permanently diamond.
        if (running.containsKey(key)) stop(key);

        // The mine's file is the only copy of its real composition that a
        // paint can't scribble over, so take one before touching anything.
        // It survives a crash, a restart, or a save mid-event.
        if (!backup(mine)) {
            by.tell("<red>Couldn't back up " + mine.getName() + " — not starting an event on it.");
            return false;
        }
        String path = "events." + kind.name().toLowerCase() + ".";
        if (seconds <= 0) seconds = cfg.getInt(path + "seconds", 300);

        Active a = new Active();
        a.kind = kind;
        a.mine = key;
        a.realName = mine.getName();
        a.endsAt = System.currentTimeMillis() + seconds * 1000L;
        a.dropMultiplier = cfg.getDouble(path + "drop-multiplier", 1);
        a.xpMultiplier = cfg.getDouble(path + "xp-multiplier", 1);
        running.put(key, a);

        switch (kind) {
            case PARTY -> paint(mine, blockOf(blockOverride, cfg.getString(path + "block", "minecraft:diamond_block")), 100, a);
            case GOLDEN_VEIN -> paint(mine, blockOf(blockOverride, cfg.getString(path + "block", "minecraft:gold_block")),
                    cfg.getDouble(path + "percent", 12), a);
            case RUSH -> {
                a.previousDelay = mine.getController().getResetDelay();
                mine.getController().setResetDelay(cfg.getInt(path + "reset-delay", 30));
                mine.reset(plugin);
            }
            case DOUBLE_DROP -> { /* nothing visual — that's the point */ }
            case METEOR -> dropMeteor(mine, a, path);
        }

        announce(kind, mine, seconds, path, blockOf(blockOverride,
                cfg.getString(path + "block", kind == Kind.GOLDEN_VEIN ? "minecraft:gold_block" : "minecraft:diamond_block")));
        return true;
    }

    /** A mine by name, ignoring case. */
    private CataMine findMine(String name) {
        if (name == null) return null;
        var exact = plugin.getMineManager().getMine(name).orElse(null);
        if (exact != null) return exact;
        for (CataMine m : plugin.getMineManager().getMines())
            if (m.getName().equalsIgnoreCase(name)) return m;
        return null;
    }

    /** Where a mine's pre-event file is kept. */
    private java.io.File backupFile(String mineName) {
        java.io.File dir = new java.io.File(plugin.getDataFolder(), "mines/event-backups");
        if (!dir.exists() && !dir.mkdirs()) return null;
        return new java.io.File(dir, mineName.toLowerCase(Locale.ROOT) + ".yml");
    }

    /** Copies the mine's file aside. False if it couldn't be done. */
    private boolean backup(CataMine mine) {
        try {
            java.io.File source = new java.io.File(plugin.getDataFolder(), "mines/" + mine.getName() + ".yml");
            if (!source.isFile()) {
                // saveMine writes it if it somehow isn't there yet
                plugin.getMineManager().saveMine(mine);
                if (!source.isFile()) return false;
            }
            java.io.File target = backupFile(mine.getName());
            if (target == null) return false;
            java.nio.file.Files.copy(source.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().severe("Couldn't back up " + mine.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    /**
     * Puts a mine back exactly as it was before its event.
     *
     * The backup is copied over the mine's file, the mine is re-read from
     * disk, and it resets. Nothing here depends on what's in memory, so it
     * works after a crash, a restart, or a save taken mid-event.
     */
    public boolean restoreFromBackup(String mineName) {
        java.io.File backup = backupFile(mineName);
        if (backup == null || !backup.isFile()) return false;
        try {
            java.io.File target = new java.io.File(plugin.getDataFolder(), "mines/" + mineName + ".yml");
            // match the real file's capitalisation if it differs
            java.io.File dir = new java.io.File(plugin.getDataFolder(), "mines");
            java.io.File[] siblings = dir.listFiles();
            if (siblings != null) for (java.io.File f : siblings)
                if (f.getName().equalsIgnoreCase(mineName + ".yml")) target = f;

            java.nio.file.Files.copy(backup.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String realName = target.getName().replaceAll("(?i)\\.yml$", "");
            var fresh = plugin.getMineManager().reloadMine(realName);
            fresh.ifPresent(m -> m.reset(plugin));
            //noinspection ResultOfMethodCallIgnored
            backup.delete();
            plugin.getLogger().info("Restored " + realName + " from its pre-event backup.");
            return fresh.isPresent();
        } catch (Exception ex) {
            plugin.getLogger().severe("Couldn't restore " + mineName + ": " + ex.getMessage());
            return false;
        }
    }

    /** On startup: any backup left lying around means an event never ended. */
    public void restoreOrphans() {
        java.io.File dir = new java.io.File(plugin.getDataFolder(), "mines/event-backups");
        java.io.File[] left = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (left == null || left.length == 0) return;
        for (java.io.File f : left) {
            String name = f.getName().replaceAll("(?i)\\.yml$", "");
            plugin.getLogger().warning("An event on '" + name + "' never finished — restoring it.");
            restoreFromBackup(name);
        }
    }

    /** "sponge" -> "minecraft:sponge"; null -> the default. */
    private static String blockOf(String override, String def) {
        if (override == null || override.isBlank()) return def;
        String b = override.toLowerCase(Locale.ROOT);
        return b.contains(":") ? b : "minecraft:" + b;
    }

    /**
     * Repaints the mine.
     *
     * A party replaces the whole composition; a golden vein mixes one rich
     * block in at a percentage. Either way the mine's own composition is
     * untouched on disk — the next natural reset after the event puts it
     * back, so nothing has to be undone by hand.
     */
    private void paint(CataMine mine, String block, double percent, Active a) {
        try {
            // Every composition of every region, not just the current one: a
            // reset refills from the UPCOMING composition, so painting only the
            // current one changed nothing visible — which is why a party did
            // nothing at all.
            mine.getRegionManager().getChoices().forEach(region ->
                region.getCompositionManager().getChoices().forEach(comp -> {
                    if (a.saved.containsKey(comp)) return;
                    var blocks = comp.getBlocks();
                    // The mine's own blocks are kept so stop() can put them
                    // back exactly; nothing is read from or written to disk.
                    a.saved.put(comp, new ArrayList<>(blocks));
                    blocks.forEach(b -> a.savedChances.put(b, b.getChance()));
                    List<me.catalysmrl.catamines.mine.components.composition.CataMineBlock> painted = new ArrayList<>();
                    if (percent >= 100) {
                        painted.add(new me.catalysmrl.catamines.mine.components.composition.CataMineBlock(block, 100));
                    } else {
                        // Squeeze the mine's own blocks down to make room for
                        // the vein; stop() puts every chance back.
                        blocks.forEach(b -> b.setChance(b.getChance() * (100 - percent) / 100));
                        painted.addAll(blocks);
                        painted.add(new me.catalysmrl.catamines.mine.components.composition.CataMineBlock(block, percent));
                    }
                    // setBlocks rebuilds the reset pattern; editing the list
                    // in place does not, which is why a party reset to
                    // exactly what it was before.
                    comp.setBlocks(painted);
                }));
            mine.reset(plugin);
        } catch (Exception ex) {
            plugin.getLogger().warning("Couldn't repaint " + mine.getName() + ": " + ex.getMessage());
        }
    }

    /** Puts one prize block somewhere in the mine and tells everyone roughly where. */
    private void dropMeteor(CataMine mine, Active a, String path) {
        try {
            var region = mine.getRegionManager().getChoices().get(0);
            Location at = randomIn(region);
            if (at == null) return;
            Material block = Material.matchMaterial(cfg.getString(path + "block", "ANCIENT_DEBRIS"));
            at.getBlock().setType(block == null ? Material.ANCIENT_DEBRIS : block, false);
            a.meteor = at;
            World w = at.getWorld();
            if (w != null) {
                w.strikeLightningEffect(at);
                w.spawnParticle(Particle.EXPLOSION, at.clone().add(0.5, 1, 0.5), 12, 0.5, 0.5, 0.5, 0);
                w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.7f);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Couldn't drop a meteor in " + mine.getName() + ": " + ex.getMessage());
        }
    }

    private Location randomIn(me.catalysmrl.catamines.mine.components.region.CataMineRegion region) {
        try {
            var sel = (me.catalysmrl.catamines.mine.components.region.impl.SelectionRegion) region;
            var r = sel.getRegion();
            var min = r.getMinimumPoint();
            var max = r.getMaximumPoint();
            World w = Bukkit.getWorld(r.getWorld().getName());
            if (w == null) return null;
            Random rnd = new Random();
            for (int tries = 0; tries < 40; tries++) {
                int x = min.x() + rnd.nextInt(Math.max(1, max.x() - min.x() + 1));
                int y = min.y() + rnd.nextInt(Math.max(1, max.y() - min.y() + 1));
                int z = min.z() + rnd.nextInt(Math.max(1, max.z() - min.z() + 1));
                Location at = new Location(w, x, y, z);
                if (!at.getBlock().getType().isAir()) return at;
            }
        } catch (ClassCastException | NullPointerException ignored) { }
        return null;
    }

    /** Called when a block is broken, to see if it was the meteor. */
    public void checkMeteor(Player p, Location broken) {
        for (Active a : running.values()) {
            if (a.kind != Kind.METEOR || a.claimed || a.meteor == null) continue;
            if (a.meteor.getBlockX() != broken.getBlockX() || a.meteor.getBlockY() != broken.getBlockY()
                    || a.meteor.getBlockZ() != broken.getBlockZ()) continue;
            a.claimed = true;
            a.endsAt = 0;
            for (String cmd : cfg.getStringList("events.meteor.reward-commands"))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
            Bukkit.broadcast(MM.deserialize(cfg.getString("events.meteor.claimed-broadcast",
                    "<gradient:#ffd166:#ff4040>☄ {player} reached the meteor first.</gradient>")
                    .replace("{player}", p.getName()).replace("{mine}", a.mine)));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            return;
        }
    }

    // ------------------------------------------------------------------ ending

    public boolean stop(String mine) {
        if (mine == null) return false;
        String key = mine.toLowerCase(Locale.ROOT);
        Active a = running.remove(key);
        if (a == null) {
            // Try the mine's real name, in case they typed a different case
            // or a partial one.
            // Exact, ignoring case only. A prefix match here meant stopping
            // "vig" could remove "vigwood" instead.
            for (String candidate : new ArrayList<>(running.keySet()))
                if (candidate.equalsIgnoreCase(key)) { a = running.remove(candidate); break; }
        }
        if (a == null) return false;
        // By its real name, and case-insensitively. This used to look the
        // mine up by the LOWERCASED map key with an exact-match getMine(),
        // so any mine whose name had a capital in it — Vig, VigWood, Knight
        // — failed here: the entry was already removed, stop() reported
        // nothing running, and the mine was left painted for good. Mines
        // with all-lowercase names were unaffected, which is why duke alone
        // behaved.
        CataMine m = findMine(a.realName != null ? a.realName : a.mine);
        if (m == null) {
            plugin.getLogger().warning("Event " + a.kind + " was running in '"
                    + (a.realName != null ? a.realName : a.mine) + "' but that mine can't be found; couldn't restore it.");
            return false;
        }
        if (a.kind == Kind.RUSH && a.previousDelay >= 0) m.getController().setResetDelay(a.previousDelay);
        // Restore from the file first; memory is only the fallback.
        if (restoreFromBackup(a.realName != null ? a.realName : a.mine)) {
            Bukkit.broadcast(MM.deserialize(cfg.getString("events." + a.kind.name().toLowerCase() + ".end-broadcast",
                    "<gray>The {kind} in <white>{mine}<gray> is over.")
                    .replace("{kind}", pretty(a.kind)).replace("{mine}", a.mine)));
            return true;
        }
        // Put back exactly what each composition held, so a party or a vein
        // leaves nothing behind and nothing has to be reloaded from disk.
        a.savedChances.forEach(me.catalysmrl.catamines.mine.components.composition.CataMineBlock::setChance);
        a.saved.forEach((comp, before) -> comp.setBlocks(new ArrayList<>(before)));
        m.reset(plugin);
        plugin.getLogger().info("Event " + a.kind + " ended in " + a.mine + "; composition restored ("
                + a.saved.size() + " composition(s)).");
        Bukkit.broadcast(MM.deserialize(cfg.getString("events." + a.kind.name().toLowerCase() + ".end-broadcast",
                "<gray>The {kind} in <white>{mine}<gray> is over.")
                .replace("{kind}", pretty(a.kind)).replace("{mine}", a.mine)));
        return true;
    }

    /** Every mine with an event running, for the command's feedback. */
    public List<String> runningMines() {
        List<String> out = new ArrayList<>();
        for (Active a : running.values()) out.add(a.realName != null ? a.realName : a.mine);
        return out;
    }

    /** Ends every running event, restoring each mine. Used before reload and shutdown. */
    public void stopAll() {
        for (Active a : new ArrayList<>(running.values())) stop(a.mine);
    }

    /**
     * Runs something with the mine's ORIGINAL composition in place.
     *
     * A save during a party would otherwise write the painted blocks to disk
     * as the mine's real composition, and the originals would be gone for
     * good. So a save swaps the originals back in, writes, and re-applies
     * the event — the file on disk never sees a party.
     */
    public void withOriginals(CataMine mine, Runnable action) {
        Active a = mine == null ? null : running(mine.getName());
        if (a == null || a.saved.isEmpty()) { action.run(); return; }
        // snapshot what the event currently has in place
        Map<me.catalysmrl.catamines.mine.components.composition.CataMineComposition,
                List<me.catalysmrl.catamines.mine.components.composition.CataMineBlock>> painted = new HashMap<>();
        a.saved.forEach((comp, before) -> {
            painted.put(comp, new ArrayList<>(comp.getBlocks()));
            comp.setBlocks(new ArrayList<>(before));
        });
        a.savedChances.forEach(me.catalysmrl.catamines.mine.components.composition.CataMineBlock::setChance);
        try {
            action.run();
        } finally {
            painted.forEach((comp, during) -> comp.setBlocks(new ArrayList<>(during)));
            // the squeezed chances of a vein have to come back too
            if (a.kind == Kind.GOLDEN_VEIN) {
                double percent = cfg.getDouble("events.golden_vein.percent", 12);
                a.savedChances.forEach((b, original) -> b.setChance(original * (100 - percent) / 100));
            }
        }
    }

    /** Ticked every second. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Active a : new ArrayList<>(running.values()))
            if (a.endsAt <= now) stop(a.mine);
    }

    // ------------------------------------------------------------------ chrome

    private void announce(Kind kind, CataMine mine, int seconds, String path, String block) {
        String blockName = block.replace("minecraft:", "").replace('_', ' ');
        String broadcast = cfg.getString(path + "broadcast",
                "<gradient:#ffd166:#ff8c00>✦ {kind} in {mine} for {minutes} minutes!</gradient>");
        Bukkit.broadcast(MM.deserialize(broadcast
                .replace("{block}", blockName)
                .replace("{kind}", pretty(kind))
                .replace("{mine}", mine.getDisplayName() == null ? mine.getName() : mine.getDisplayName())
                .replace("{minutes}", String.valueOf(Math.max(1, seconds / 60)))
                .replace("{seconds}", String.valueOf(seconds))));

        String titleRaw = cfg.getString(path + "title", "<gradient:#ffd166:#ff8c00><bold>{kind}</bold></gradient>");
        String subRaw = cfg.getString(path + "subtitle", "<gray>{mine} — get in there.");
        for (Player p : Bukkit.getOnlinePlayers())
            p.showTitle(Title.title(
                    MM.deserialize(titleRaw.replace("{kind}", pretty(kind)).replace("{mine}", mine.getName())),
                    MM.deserialize(subRaw.replace("{kind}", pretty(kind)).replace("{mine}", mine.getName())),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(500))));
    }

    public static String pretty(Kind kind) {
        String[] parts = kind.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    /** Lets the command layer talk back without depending on Bukkit's sender. */
    public interface CommandSenderLike {
        void tell(String miniMessage);
    }
}
