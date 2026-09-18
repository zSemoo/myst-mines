package me.catalysmrl.catamines.myst.level;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.events.CataMineBlockBreakEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.File;
import java.time.Duration;
import java.util.*;

/**
 * Mining levels.
 *
 * One level per player rather than one per mine: a mining career, not
 * fourteen separate grinds. Every block broken in a mine pays XP — per block
 * type where the config says so, otherwise per mine, otherwise the default —
 * and the curve is `base x level^exponent`, so early levels come quickly and
 * the late ones are a project.
 *
 * Levelling up throws a full-screen title. Milestone levels (every N) are
 * announced server-wide and can run commands, which is where crate keys,
 * tags and unlocks hang. Nothing here touches drops or sell prices: a level
 * is status plus whatever its milestone hands over, which keeps the whole
 * thing tunable without compounding into something you can't undo.
 */
public class MineLevels implements Listener {

    public static class Profile {
        public final UUID id;
        public String name = "?";
        public int level = 1;
        public double xp;
        public long blocks;
        public Profile(UUID id) { this.id = id; }
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CataMines plugin;
    private final Map<UUID, Profile> profiles = new HashMap<>();
    private final File file;
    private YamlConfiguration cfg;
    private boolean dirty;

    public MineLevels(CataMines plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "levels.yml");
        reload();
        load();
    }

    // ------------------------------------------------------------------ config

    public void reload() {
        File conf = new File(plugin.getDataFolder(), "levelling.yml");
        if (!conf.exists()) plugin.saveResource("levelling.yml", false);
        cfg = YamlConfiguration.loadConfiguration(conf);
    }

    public boolean enabled() { return cfg.getBoolean("enabled", true); }
    public int maxLevel() { return cfg.getInt("max-level", 100); }

    /** XP needed to get from this level to the next. */
    public long xpForNext(int level) {
        double base = cfg.getDouble("curve.base", 100);
        double exponent = cfg.getDouble("curve.exponent", 1.4);
        return Math.round(base * Math.pow(level, exponent));
    }

    /** What one block is worth: its own value, else the mine's, else the default. */
    public double xpFor(String mine, String block) {
        ConfigurationSection perBlock = cfg.getConfigurationSection("xp.blocks");
        if (perBlock != null) {
            String key = block.toLowerCase().replace("minecraft:", "");
            if (perBlock.contains(key)) return perBlock.getDouble(key);
        }
        ConfigurationSection perMine = cfg.getConfigurationSection("xp.mines");
        if (perMine != null && mine != null && perMine.contains(mine.toLowerCase()))
            return perMine.getDouble(mine.toLowerCase());
        return cfg.getDouble("xp.default", 1);
    }

    // ------------------------------------------------------------------ profiles

    public Profile profile(UUID id) { return profiles.computeIfAbsent(id, Profile::new); }

    public Profile profile(Player p) {
        Profile prof = profile(p.getUniqueId());
        prof.name = p.getName();
        return prof;
    }

    public Collection<Profile> all() { return profiles.values(); }

    /** Everyone, best first. */
    public List<Profile> top(int limit) {
        List<Profile> all = new ArrayList<>(profiles.values());
        all.sort((a, b) -> a.level != b.level ? Integer.compare(b.level, a.level) : Double.compare(b.xp, a.xp));
        return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
    }

    public int placeOf(UUID id) {
        List<Profile> all = top(Integer.MAX_VALUE);
        for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(id)) return i + 1;
        return -1;
    }

    /** How far through the current level, 0..1. */
    public double progress(Profile p) {
        long need = xpForNext(p.level);
        return need <= 0 ? 1 : Math.max(0, Math.min(1, p.xp / (double) need));
    }

    // ------------------------------------------------------------------ earning

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMineBreak(CataMineBlockBreakEvent e) {
        if (!enabled()) return;
        Player p = e.getBlockBreakEvent().getPlayer();
        if (p == null || p.hasPermission("mystmines.level.exempt")) return;
        String block = e.getBlockBreakEvent().getBlock().getType().name();
        double xp = xpFor(e.getCataMine() == null ? null : e.getCataMine().getName(), block);
        give(p, xp * plugin.getMineEvents().xpMultiplier(e.getCataMine()), true);
    }

    /** Adds XP and handles any levels it crosses. */
    public void give(Player p, double amount, boolean countBlock) {
        if (amount <= 0) return;
        Profile prof = profile(p);
        if (countBlock) prof.blocks++;
        prof.xp += amount;
        dirty = true;

        while (prof.level < maxLevel() && prof.xp >= xpForNext(prof.level)) {
            prof.xp -= xpForNext(prof.level);
            prof.level++;
            celebrate(p, prof);
        }
        if (cfg.getBoolean("action-bar", true) && countBlock)
            p.sendActionBar(MM.deserialize(cfg.getString("messages.action-bar",
                            "<gray>Level <white>{level} <dark_gray>{bar} <gray>{xp}<dark_gray>/{next}")
                    .replace("{level}", String.valueOf(prof.level))
                    .replace("{bar}", bar(progress(prof)))
                    .replace("{xp}", String.valueOf((long) prof.xp))
                    .replace("{next}", String.valueOf(xpForNext(prof.level)))));
    }

    /** The bit everyone actually plays for. */
    private void celebrate(Player p, Profile prof) {
        int every = cfg.getInt("milestone-every", 10);
        boolean milestone = every > 0 && prof.level % every == 0;

        String titleRaw = cfg.getString(milestone ? "messages.milestone-title" : "messages.level-title",
                "<gradient:#ffd166:#ff8c00><bold>LEVEL {level}</bold></gradient>");
        String subRaw = cfg.getString(milestone ? "messages.milestone-subtitle" : "messages.level-subtitle",
                "<gray>Keep swinging.");

        Component title = MM.deserialize(titleRaw.replace("{level}", String.valueOf(prof.level)));
        Component sub = MM.deserialize(subRaw.replace("{level}", String.valueOf(prof.level)));
        p.showTitle(Title.title(title, sub, Title.Times.times(
                Duration.ofMillis(cfg.getInt("title.fade-in-ms", 250)),
                Duration.ofMillis(cfg.getInt("title.stay-ms", 2200)),
                Duration.ofMillis(cfg.getInt("title.fade-out-ms", 500)))));

        playSound(p, cfg.getString(milestone ? "sounds.milestone" : "sounds.level-up", "entity.player.levelup"),
                milestone ? 1.2f : 1f);

        if (milestone) {
            if (cfg.getBoolean("milestone-firework", true))
                p.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, p.getLocation().add(0, 1, 0), 60, 0.6, 1, 0.6, 0.15);
            String broadcast = cfg.getString("messages.milestone-broadcast",
                    "<gradient:#ffd166:#ff8c00>✦ {player} reached mining level {level}.</gradient>");
            Bukkit.broadcast(MM.deserialize(broadcast
                    .replace("{player}", p.getName()).replace("{level}", String.valueOf(prof.level))));
        }

        // Rewards: this exact level first, then the milestone list, then the
        // every-level list. Commands run as console, with {player} and {level}.
        runCommands(p, prof.level, "rewards.levels." + prof.level);
        if (milestone) runCommands(p, prof.level, "rewards.milestone");
        runCommands(p, prof.level, "rewards.every-level");
        save();
    }

    private void playSound(Player p, String name, float pitch) {
        try {
            p.playSound(p.getLocation(), Sound.valueOf(name.toUpperCase(Locale.ROOT).replace('.', '_')), 1f, pitch);
        } catch (IllegalArgumentException ignored) { }
    }

    private void runCommands(Player p, int level, String path) {
        for (String cmd : cfg.getStringList(path))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("{player}", p.getName()).replace("{level}", String.valueOf(level)));
    }

    public int milestoneEvery() { return cfg.getInt("milestone-every", 10); }

    /**
     * A human sentence per reward command, for the menu.
     *
     * Commands are opaque, so levelling.yml carries a `reward-names` map
     * from level to a line of text; anything without one is described by
     * how many commands run, which is at least honest.
     */
    public List<String> rewardBlurb(int level) {
        List<String> out = new ArrayList<>();
        for (String line : cfg.getStringList("reward-names." + level)) out.add("<white>• " + line);
        if (out.isEmpty()) {
            int n = cfg.getStringList("rewards.levels." + level).size();
            if (n > 0) out.add("<dark_gray>• " + n + " reward" + (n == 1 ? "" : "s"));
        }
        return out;
    }

    public String bar(double fraction) {
        int width = cfg.getInt("bar-width", 20);
        int filled = (int) Math.round(width * Math.max(0, Math.min(1, fraction)));
        StringBuilder sb = new StringBuilder(cfg.getString("bar-filled-colour", "<green>"));
        for (int i = 0; i < width; i++) {
            if (i == filled) sb.append(cfg.getString("bar-empty-colour", "<dark_gray>"));
            sb.append(cfg.getString("bar-char", "|"));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ admin

    public void setLevel(Player p, int level) {
        Profile prof = profile(p);
        prof.level = Math.max(1, Math.min(maxLevel(), level));
        prof.xp = 0;
        dirty = true;
        save();
    }

    public void reset(UUID id) {
        profiles.remove(id);
        dirty = true;
        save();
    }

    // ------------------------------------------------------------------ storage

    public void save() {
        if (!dirty) return;
        YamlConfiguration y = new YamlConfiguration();
        for (Profile p : profiles.values()) {
            String b = "players." + p.id;
            y.set(b + ".name", p.name);
            y.set(b + ".level", p.level);
            y.set(b + ".xp", p.xp);
            y.set(b + ".blocks", p.blocks);
        }
        try { y.save(file); dirty = false; } catch (Exception ex) {
            plugin.getLogger().severe("Couldn't save levels.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("players");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            try {
                Profile p = new Profile(UUID.fromString(id));
                p.name = root.getString(id + ".name", "?");
                p.level = root.getInt(id + ".level", 1);
                p.xp = root.getDouble(id + ".xp");
                p.blocks = root.getLong(id + ".blocks");
                profiles.put(p.id, p);
            } catch (IllegalArgumentException ignored) { }
        }
        plugin.getLogger().info("Mining levels: " + profiles.size() + " player(s) on record.");
    }

    public OfflinePlayer offline(Profile p) { return Bukkit.getOfflinePlayer(p.id); }
}
