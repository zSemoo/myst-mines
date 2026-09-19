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
        public String mine;
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
        if (mine == null) return false;
        String key = mine.getName().toLowerCase();
        if (running(key) != null) {
            by.tell("<red>" + mine.getName() + " already has an event running. <gray>/mine event stop " + mine.getName());
            return false;
        }
        String path = "events." + kind.name().toLowerCase() + ".";
        if (seconds <= 0) seconds = cfg.getInt(path + "seconds", 300);

        Active a = new Active();
        a.kind = kind;
        a.mine = key;
        a.endsAt = System.currentTimeMillis() + seconds * 1000L;
        a.dropMultiplier = cfg.getDouble(path + "drop-multiplier", 1);
        a.xpMultiplier = cfg.getDouble(path + "xp-multiplier", 1);
        running.put(key, a);

        switch (kind) {
            case PARTY -> paint(mine, cfg.getString(path + "block", "minecraft:diamond_block"), 100, a);
            case GOLDEN_VEIN -> paint(mine, cfg.getString(path + "block", "minecraft:gold_block"),
                    cfg.getDouble(path + "percent", 12), a);
            case RUSH -> {
                a.previousDelay = mine.getController().getResetDelay();
                mine.getController().setResetDelay(cfg.getInt(path + "reset-delay", 30));
                mine.reset(plugin);
            }
            case DOUBLE_DROP -> { /* nothing visual — that's the point */ }
            case METEOR -> dropMeteor(mine, a, path);
        }

        announce(kind, mine, seconds, path);
        return true;
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
                    if (percent >= 100) {
                        blocks.clear();
                        blocks.add(new me.catalysmrl.catamines.mine.components.composition.CataMineBlock(block, 100));
                    } else {
                        // Squeeze the mine's own blocks down to make room for
                        // the vein; stop() puts every chance back.
                        blocks.forEach(b -> b.setChance(b.getChance() * (100 - percent) / 100));
                        blocks.add(new me.catalysmrl.catamines.mine.components.composition.CataMineBlock(block, percent));
                    }
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

    public void stop(String mine) {
        Active a = running.remove(mine.toLowerCase());
        if (a == null) return;
        CataMine m = plugin.getMineManager().getMine(a.mine).orElse(null);
        if (m == null) return;
        if (a.kind == Kind.RUSH && a.previousDelay >= 0) m.getController().setResetDelay(a.previousDelay);
        // Put back exactly what each composition held, so a party or a vein
        // leaves nothing behind and nothing has to be reloaded from disk.
        a.saved.forEach((comp, before) -> {
            comp.getBlocks().clear();
            comp.getBlocks().addAll(before);
        });
        a.savedChances.forEach(me.catalysmrl.catamines.mine.components.composition.CataMineBlock::setChance);
        m.reset(plugin);
        Bukkit.broadcast(MM.deserialize(cfg.getString("events." + a.kind.name().toLowerCase() + ".end-broadcast",
                "<gray>The {kind} in <white>{mine}<gray> is over.")
                .replace("{kind}", pretty(a.kind)).replace("{mine}", a.mine)));
    }

    /** Ticked every second. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Active a : new ArrayList<>(running.values()))
            if (a.endsAt <= now) stop(a.mine);
    }

    // ------------------------------------------------------------------ chrome

    private void announce(Kind kind, CataMine mine, int seconds, String path) {
        String broadcast = cfg.getString(path + "broadcast",
                "<gradient:#ffd166:#ff8c00>✦ {kind} in {mine} for {minutes} minutes!</gradient>");
        Bukkit.broadcast(MM.deserialize(broadcast
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
