package me.catalysmrl.catamines.myst.extra;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.events.CataMineBlockBreakEvent;
import me.catalysmrl.catamines.api.events.CataMineResetEvent;
import me.catalysmrl.catamines.api.mine.CataMine;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.io.File;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * Two things buried in the mines.
 *
 * FOSSILS — after a reset, a mine has a small chance of hiding a fossil: a
 * little shape of bone and amethyst, a few blocks across, sitting inside
 * the ore. It has to be dug out INTACT: break every fossil block and
 * nothing else touching it, and the last one pays. Break an ordinary block
 * that was holding it up and it crumbles — announced, so everyone nearby
 * knows what just happened.
 *
 * CHALLENGES — one per mine per week, rotated by the calendar week so every
 * server sees the same one: "break 500 blocks in peasant", "find 3 fossils
 * in king". Progress is per player, the week's winner is whoever finishes
 * first, and everyone who finishes at all gets the completion reward.
 */
public class MineExtras implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** One buried fossil. */
    private static class Fossil {
        String mine;
        final Set<Location> bones = new HashSet<>();
        final Set<Location> supports = new HashSet<>();   // ore touching it that must NOT be broken first
        final Set<Location> dug = new HashSet<>();
        boolean crumbled;
    }

    private final CataMines plugin;
    private final Map<String, Fossil> fossils = new HashMap<>();
    private final Random random = new Random();
    private YamlConfiguration cfg;

    // challenge progress: week -> mine -> player -> count; week -> mine -> first finisher
    private final Map<String, Map<String, Map<UUID, Integer>>> progress = new HashMap<>();
    private final Map<String, Map<String, UUID>> firstDone = new HashMap<>();
    private final Map<String, Map<String, Set<UUID>>> done = new HashMap<>();

    public MineExtras(CataMines plugin) {
        this.plugin = plugin;
        reload();
        loadProgress();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "extras.yml");
        if (!f.exists()) plugin.saveResource("extras.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
    }

    // ================================================================== fossils

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReset(CataMineResetEvent e) {
        if (!cfg.getBoolean("fossils.enabled", true)) return;
        String key = e.getCataMine().getName().toLowerCase();
        fossils.remove(key);
        // The refill happens right after this event; bury the fossil a moment later.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (random.nextDouble() > cfg.getDouble("fossils.chance-per-reset", 0.35)) return;
            bury(e.getCataMine());
        }, 40L);
    }

    private void bury(CataMine mine) {
        try {
            var region = mine.getRegionManager().getChoices().get(0);
            var sel = (me.catalysmrl.catamines.mine.components.region.impl.SelectionRegion) region;
            var r = sel.getRegion();
            var min = r.getMinimumPoint(); var max = r.getMaximumPoint();
            World w = Bukkit.getWorld(r.getWorld().getName());
            if (w == null) return;
            // a spot with room around it, not on the edge
            int cx = min.x() + 2 + random.nextInt(Math.max(1, max.x() - min.x() - 3));
            int cy = min.y() + 1 + random.nextInt(Math.max(1, max.y() - min.y() - 2));
            int cz = min.z() + 2 + random.nextInt(Math.max(1, max.z() - min.z() - 3));

            Fossil f = new Fossil();
            f.mine = mine.getName().toLowerCase();
            List<Material> bones = new ArrayList<>();
            for (String s : cfg.getStringList("fossils.blocks")) { Material m = Material.matchMaterial(s); if (m != null) bones.add(m); }
            if (bones.isEmpty()) bones.add(Material.BONE_BLOCK);

            // a little spine with ribs: a line of 4-6, two side stubs
            int len = 4 + random.nextInt(3);
            boolean alongX = random.nextBoolean();
            for (int i = 0; i < len; i++) {
                Location at = new Location(w, alongX ? cx + i : cx, cy, alongX ? cz : cz + i);
                if (!r.contains(com.sk89q.worldedit.math.BlockVector3.at(at.getBlockX(), at.getBlockY(), at.getBlockZ()))) break;
                at.getBlock().setType(bones.get(random.nextInt(bones.size())), false);
                f.bones.add(at);
                if (i % 2 == 1) for (int side = -1; side <= 1; side += 2) {
                    Location rib = new Location(w, alongX ? cx + i : cx + side, cy, alongX ? cz + side : cz + i);
                    if (!r.contains(com.sk89q.worldedit.math.BlockVector3.at(rib.getBlockX(), rib.getBlockY(), rib.getBlockZ()))) continue;
                    rib.getBlock().setType(bones.get(random.nextInt(bones.size())), false);
                    f.bones.add(rib);
                }
            }
            // what holds it: the blocks directly beneath each bone
            for (Location b : f.bones) {
                Location under = b.clone().subtract(0, 1, 0);
                if (!f.bones.contains(under) && !under.getBlock().getType().isAir()) f.supports.add(under);
            }
            fossils.put(f.mine, f);
            if (cfg.getBoolean("fossils.announce-buried", true))
                Bukkit.broadcast(MM.deserialize(cfg.getString("fossils.messages.buried",
                        "<gradient:#e8e0c8:#a89f80>☠ Something old is buried in {mine}.</gradient> <gray>Dig it out whole.")
                        .replace("{mine}", mine.getDisplayName())));
        } catch (Exception ex) {
            plugin.getLogger().warning("Couldn't bury a fossil in " + mine.getName() + ": " + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFossilBreak(BlockBreakEvent e) {
        Location at = e.getBlock().getLocation();
        for (Fossil f : fossils.values()) {
            if (f.crumbled) continue;
            if (f.supports.contains(at) && !f.dug.containsAll(f.bones)) {
                crumble(f, e.getPlayer());
                return;
            }
            if (f.bones.contains(at)) {
                f.dug.add(at);
                e.setDropItems(false);
                e.getPlayer().playSound(at, Sound.BLOCK_BONE_BLOCK_BREAK, 1f, 0.6f);
                if (f.dug.containsAll(f.bones)) excavated(f, e.getPlayer());
                else e.getPlayer().sendActionBar(MM.deserialize(cfg.getString("fossils.messages.progress",
                        "<#e8e0c8>Fossil: <white>{dug}<dark_gray>/{total} <gray>— mind what's holding it up")
                        .replace("{dug}", String.valueOf(f.dug.size())).replace("{total}", String.valueOf(f.bones.size()))));
                return;
            }
        }
    }

    private void crumble(Fossil f, Player by) {
        f.crumbled = true;
        for (Location b : f.bones) if (!f.dug.contains(b)) b.getBlock().setType(Material.GRAVEL, false);
        by.getWorld().playSound(by.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 1.4f, 0.5f);
        Bukkit.broadcast(MM.deserialize(cfg.getString("fossils.messages.crumbled",
                "<gray>The fossil in <white>{mine}<gray> crumbled. <dark_gray>{player} knocked out what was holding it.")
                .replace("{mine}", f.mine).replace("{player}", by.getName())));
        fossils.remove(f.mine);
    }

    private void excavated(Fossil f, Player by) {
        fossils.remove(f.mine);
        by.playSound(by.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.9f);
        by.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, by.getLocation().add(0, 1, 0), 60, 0.6, 1, 0.6, 0.2);
        for (String cmd : cfg.getStringList("fossils.reward-commands"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", by.getName()).replace("{mine}", f.mine));
        Bukkit.broadcast(MM.deserialize(cfg.getString("fossils.messages.excavated",
                "<gradient:#e8e0c8:#a89f80>☠ {player} dug a fossil out of {mine} in one piece.</gradient>")
                .replace("{player}", by.getName()).replace("{mine}", f.mine)));
        record(by, f.mine, "fossil", 1);
    }

    // ================================================================== challenges

    public record Challenge(String mine, String type, int target, String name) {}

    private static String week() {
        LocalDate d = LocalDate.now();
        return d.getYear() + "-w" + d.get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    /** This week's challenge for a mine, rotated by the week number. */
    public Challenge challengeFor(String mine) {
        ConfigurationSection list = cfg.getConfigurationSection("challenges." + mine.toLowerCase());
        if (list == null) list = cfg.getConfigurationSection("challenges.default");
        if (list == null) return null;
        List<String> keys = new ArrayList<>(list.getKeys(false));
        if (keys.isEmpty()) return null;
        int idx = Math.floorMod(LocalDate.now().get(WeekFields.ISO.weekOfWeekBasedYear()) + LocalDate.now().getYear(), keys.size());
        ConfigurationSection c = list.getConfigurationSection(keys.get(idx));
        if (c == null) return null;
        return new Challenge(mine.toLowerCase(), c.getString("type", "blocks"), c.getInt("target", 500),
                c.getString("name", keys.get(idx)));
    }

    public int progressOf(Player p, String mine) {
        return progress.getOrDefault(week(), Map.of()).getOrDefault(mine.toLowerCase(), Map.of()).getOrDefault(p.getUniqueId(), 0);
    }

    public boolean finished(Player p, String mine) {
        return done.getOrDefault(week(), Map.of()).getOrDefault(mine.toLowerCase(), Set.of()).contains(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChallengeBreak(CataMineBlockBreakEvent e) {
        Player p = e.getBlockBreakEvent().getPlayer();
        if (p == null || e.getCataMine() == null) return;
        record(p, e.getCataMine().getName(), "blocks", 1);
        String block = e.getBlockBreakEvent().getBlock().getType().name().toLowerCase();
        record(p, e.getCataMine().getName(), "block:" + block, 1);
    }

    /** Something countable happened in a mine. */
    public void record(Player p, String mine, String type, int amount) {
        Challenge c = challengeFor(mine);
        if (c == null || !c.type().equalsIgnoreCase(type)) return;
        if (finished(p, mine)) return;
        String w = week();
        Map<UUID, Integer> map = progress.computeIfAbsent(w, k -> new HashMap<>()).computeIfAbsent(c.mine(), k -> new HashMap<>());
        int now = map.merge(p.getUniqueId(), amount, Integer::sum);
        if (now >= c.target()) complete(p, c);
        else if (now % Math.max(1, c.target() / 4) == 0)
            p.sendActionBar(MM.deserialize(cfg.getString("challenges.messages.progress",
                    "<#7de2ff>{challenge}: <white>{now}<dark_gray>/{target}")
                    .replace("{challenge}", c.name()).replace("{now}", String.valueOf(now)).replace("{target}", String.valueOf(c.target()))));
        dirty = true;
    }

    private void complete(Player p, Challenge c) {
        String w = week();
        done.computeIfAbsent(w, k -> new HashMap<>()).computeIfAbsent(c.mine(), k -> new HashSet<>()).add(p.getUniqueId());
        boolean first = !firstDone.computeIfAbsent(w, k -> new HashMap<>()).containsKey(c.mine());
        if (first) firstDone.get(w).put(c.mine(), p.getUniqueId());
        for (String cmd : cfg.getStringList("challenges.complete-commands"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()).replace("{mine}", c.mine()));
        if (first) for (String cmd : cfg.getStringList("challenges.first-commands"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()).replace("{mine}", c.mine()));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
        Bukkit.broadcast(MM.deserialize(cfg.getString(first ? "challenges.messages.first" : "challenges.messages.complete",
                "<gradient:#7de2ff:#1fa9d6>✦ {player} finished this week's {mine} challenge.</gradient>")
                .replace("{player}", p.getName()).replace("{mine}", c.mine()).replace("{challenge}", c.name())));
        dirty = true;
        saveProgress();
    }

    // ------------------------------------------------------------------ storage

    private boolean dirty;
    private File progressFile() { return new File(plugin.getDataFolder(), "challenges-progress.yml"); }

    public void saveProgress() {
        if (!dirty) return;
        YamlConfiguration y = new YamlConfiguration();
        String w = week();       // only this week is worth keeping
        progress.getOrDefault(w, Map.of()).forEach((mine, map) ->
                map.forEach((id, n) -> y.set("progress." + w + "." + mine + "." + id, n)));
        done.getOrDefault(w, Map.of()).forEach((mine, set) ->
                y.set("done." + w + "." + mine, set.stream().map(UUID::toString).toList()));
        firstDone.getOrDefault(w, Map.of()).forEach((mine, id) -> y.set("first." + w + "." + mine, id.toString()));
        try { y.save(progressFile()); dirty = false; } catch (Exception ex) {
            plugin.getLogger().warning("Couldn't save challenge progress: " + ex.getMessage());
        }
    }

    private void loadProgress() {
        if (!progressFile().exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(progressFile());
        String w = week();
        ConfigurationSection pr = y.getConfigurationSection("progress." + w);
        if (pr != null) for (String mine : pr.getKeys(false)) {
            ConfigurationSection m = pr.getConfigurationSection(mine);
            if (m == null) continue;
            for (String id : m.getKeys(false))
                try { progress.computeIfAbsent(w, k -> new HashMap<>()).computeIfAbsent(mine, k -> new HashMap<>()).put(UUID.fromString(id), m.getInt(id)); }
                catch (IllegalArgumentException ignored) { }
        }
        ConfigurationSection dn = y.getConfigurationSection("done." + w);
        if (dn != null) for (String mine : dn.getKeys(false)) {
            Set<UUID> set = new HashSet<>();
            for (String id : dn.getStringList(mine)) try { set.add(UUID.fromString(id)); } catch (IllegalArgumentException ignored) { }
            done.computeIfAbsent(w, k -> new HashMap<>()).put(mine, set);
        }
        ConfigurationSection fi = y.getConfigurationSection("first." + w);
        if (fi != null) for (String mine : fi.getKeys(false))
            try { firstDone.computeIfAbsent(w, k -> new HashMap<>()).put(mine, UUID.fromString(fi.getString(mine))); }
            catch (Exception ignored) { }
    }
}
