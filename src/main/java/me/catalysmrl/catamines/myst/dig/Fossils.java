package me.catalysmrl.catamines.myst.dig;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.mine.components.region.impl.SelectionRegion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.Duration;
import java.util.*;

/**
 * Fossils, and the last block out of a mine.
 *
 * A FOSSIL is a small buried cluster — bone and amethyst by default — placed
 * somewhere in the mine on reset. Break any part of it and the clock starts:
 * dig the rest out within a few seconds and it pays; break the wrong thing
 * or run out of time and it crumbles to gravel. So it isn't "find the rare
 * block", it's "spot it, then handle it carefully", which is a different
 * and better feeling.
 *
 * The LAST BLOCK is whoever empties a mine before it resets. A small
 * jackpot, announced, and it gives the end of a reset a shape it didn't
 * have — people race the timer instead of drifting off when it thins out.
 */
public class Fossils {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** One buried cluster, waiting to be found. */
    public static class Fossil {
        public String mine;
        public final Set<String> blocks = new HashSet<>();   // "x;y;z"
        public UUID digger;                                  // whoever started it
        public long deadline;
        public int broken;
        public boolean crumbled;
    }

    private final CataMines plugin;
    private final Map<String, Fossil> byMine = new HashMap<>();
    private YamlConfiguration cfg;

    public Fossils(CataMines plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "digging.yml");
        if (!f.exists()) plugin.saveResource("digging.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
    }

    private static String key(Block b) { return b.getX() + ";" + b.getY() + ";" + b.getZ(); }

    // ------------------------------------------------------------------ placing

    /** Called after a mine resets. */
    public void onReset(CataMine mine) {
        byMine.remove(mine.getName().toLowerCase());
        if (!cfg.getBoolean("fossils.enabled", true)) return;
        if (Math.random() > cfg.getDouble("fossils.chance-per-reset", 0.35)) return;

        Location at = randomIn(mine);
        if (at == null) return;

        Material shell = Material.matchMaterial(cfg.getString("fossils.shell-block", "BONE_BLOCK"));
        Material heart = Material.matchMaterial(cfg.getString("fossils.heart-block", "AMETHYST_BLOCK"));
        if (shell == null) shell = Material.BONE_BLOCK;
        if (heart == null) heart = Material.AMETHYST_BLOCK;

        Fossil fossil = new Fossil();
        fossil.mine = mine.getName().toLowerCase();

        // A little cross of shell around one heart: small enough to be
        // missed, distinctive enough to spot if you're paying attention.
        int size = Math.max(1, cfg.getInt("fossils.size", 2));
        Random r = new Random();
        for (int dx = -size; dx <= size; dx++)
            for (int dy = -size; dy <= size; dy++)
                for (int dz = -size; dz <= size; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > size) continue;
                    Block b = at.getWorld().getBlockAt(at.getBlockX() + dx, at.getBlockY() + dy, at.getBlockZ() + dz);
                    if (b.getType().isAir()) continue;
                    boolean centre = dx == 0 && dy == 0 && dz == 0;
                    b.setType(centre ? heart : shell, false);
                    fossil.blocks.add(key(b));
                }
        if (fossil.blocks.isEmpty()) return;
        byMine.put(fossil.mine, fossil);

        if (cfg.getBoolean("fossils.announce-placed", true))
            Bukkit.broadcast(MM.deserialize(cfg.getString("fossils.placed-broadcast",
                    "<gray>Something old is buried in <white>{mine}<gray>.")
                    .replace("{mine}", mine.getDisplayName() == null ? mine.getName() : mine.getDisplayName())));
    }

    private Location randomIn(CataMine mine) {
        try {
            var region = mine.getRegionManager().getChoices().get(0);
            if (!(region instanceof SelectionRegion sel)) return null;
            var r = sel.getRegion();
            World w = Bukkit.getWorld(r.getWorld().getName());
            if (w == null) return null;
            var min = r.getMinimumPoint();
            var max = r.getMaximumPoint();
            Random rnd = new Random();
            int pad = cfg.getInt("fossils.size", 2) + 1;
            for (int tries = 0; tries < 30; tries++) {
                int x = min.x() + pad + rnd.nextInt(Math.max(1, max.x() - min.x() - pad * 2 + 1));
                int y = min.y() + pad + rnd.nextInt(Math.max(1, max.y() - min.y() - pad * 2 + 1));
                int z = min.z() + pad + rnd.nextInt(Math.max(1, max.z() - min.z() - pad * 2 + 1));
                Location at = new Location(w, x, y, z);
                if (!at.getBlock().getType().isAir()) return at;
            }
        } catch (IndexOutOfBoundsException | NullPointerException ignored) { }
        return null;
    }

    // ------------------------------------------------------------------ digging

    /** Returns true if this block was part of a fossil. */
    public boolean onBreak(Player p, Block block) {
        Fossil fossil = null;
        for (Fossil f : byMine.values()) if (f.blocks.contains(key(block))) { fossil = f; break; }
        if (fossil == null) return false;
        if (fossil.crumbled) return false;

        if (fossil.digger == null) {
            fossil.digger = p.getUniqueId();
            fossil.deadline = System.currentTimeMillis() + cfg.getInt("fossils.seconds", 25) * 1000L;
            p.showTitle(Title.title(
                    MM.deserialize(cfg.getString("fossils.found-title", "<gradient:#ffd166:#ff8c00><bold>FOSSIL</bold></gradient>")),
                    MM.deserialize(cfg.getString("fossils.found-subtitle", "<gray>Dig it out — carefully, and quickly.")),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(400))));
            p.playSound(p.getLocation(), Sound.BLOCK_BONE_BLOCK_BREAK, 1f, 0.7f);
        } else if (!fossil.digger.equals(p.getUniqueId()) && cfg.getBoolean("fossils.finder-only", true)) {
            // Someone else's find; let them have it rather than stealing it.
            return false;
        }

        fossil.broken++;
        block.getWorld().spawnParticle(Particle.CRIT, block.getLocation().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, 0);

        if (fossil.broken >= fossil.blocks.size()) {
            complete(p, fossil);
            byMine.remove(fossil.mine);
        }
        return true;
    }

    private void complete(Player p, Fossil fossil) {
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 60, 0.6, 1, 0.6, 0.1);
        for (String cmd : cfg.getStringList("fossils.reward-commands"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
        double xp = cfg.getDouble("fossils.xp", 500);
        if (xp > 0) plugin.getMineLevels().give(p, xp, false);
        Bukkit.broadcast(MM.deserialize(cfg.getString("fossils.claimed-broadcast",
                "<gradient:#ffd166:#ff8c00>✦ {player} excavated a fossil from {mine}.</gradient>")
                .replace("{player}", p.getName()).replace("{mine}", fossil.mine)));
    }

    /** Ticked: fossils that ran out of time fall apart. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Fossil f : new ArrayList<>(byMine.values())) {
            if (f.digger == null || f.crumbled || f.deadline > now) continue;
            f.crumbled = true;
            Player p = Bukkit.getPlayer(f.digger);
            World w = p == null ? null : p.getWorld();
            for (String k : f.blocks) {
                String[] parts = k.split(";");
                if (w == null) break;
                Block b = w.getBlockAt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                if (!b.getType().isAir()) b.setType(Material.GRAVEL, false);
            }
            if (p != null) {
                p.sendMessage(MM.deserialize(cfg.getString("fossils.crumbled",
                        "<gray>The fossil crumbles. <dark_gray>Too slow.")));
                p.playSound(p.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 1f, 0.6f);
            }
            byMine.remove(f.mine);
        }
    }

    /** How long the finder has left, for the action bar. */
    public long secondsLeft(Player p) {
        for (Fossil f : byMine.values())
            if (p.getUniqueId().equals(f.digger) && !f.crumbled)
                return Math.max(0, (f.deadline - System.currentTimeMillis()) / 1000);
        return -1;
    }

    // ------------------------------------------------------------------ last block

    /**
     * Whoever takes the last block out of a mine before it resets.
     *
     * Checked cheaply: only once a mine is nearly empty, and only every few
     * breaks, because counting air in a big region on every swing would be
     * madness.
     */
    private final Map<String, Integer> sinceCheck = new HashMap<>();

    public void checkLastBlock(Player p, CataMine mine) {
        if (!cfg.getBoolean("last-block.enabled", true)) return;
        String key = mine.getName().toLowerCase();
        int n = sinceCheck.merge(key, 1, Integer::sum);
        if (n % cfg.getInt("last-block.check-every", 20) != 0) return;
        try {
            var region = mine.getRegionManager().getChoices().get(0);
            if (!(region instanceof SelectionRegion sel)) return;
            var r = sel.getRegion();
            World w = Bukkit.getWorld(r.getWorld().getName());
            if (w == null) return;
            var min = r.getMinimumPoint();
            var max = r.getMaximumPoint();
            long solid = 0;
            long limit = cfg.getInt("last-block.blocks-left", 3);
            for (int x = min.x(); x <= max.x() && solid <= limit; x++)
                for (int y = min.y(); y <= max.y() && solid <= limit; y++)
                    for (int z = min.z(); z <= max.z() && solid <= limit; z++)
                        if (!w.getBlockAt(x, y, z).getType().isAir()) solid++;
            if (solid > limit) return;
            sinceCheck.remove(key);
            for (String cmd : cfg.getStringList("last-block.reward-commands"))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
            double xp = cfg.getDouble("last-block.xp", 250);
            if (xp > 0) plugin.getMineLevels().give(p, xp, false);
            Bukkit.broadcast(MM.deserialize(cfg.getString("last-block.broadcast",
                    "<gradient:#8cff9e:#1fbf5a>✦ {player} emptied {mine} — last block bonus.</gradient>")
                    .replace("{player}", p.getName()).replace("{mine}", mine.getName())));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        } catch (IndexOutOfBoundsException | NullPointerException ignored) { }
    }
}
