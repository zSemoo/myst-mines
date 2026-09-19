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
        /** Times they've gone round the ladder. */
        public int prestige;
        /** For the daily bonus: which day it was last counted, and how many blocks so far that day. */
        public String bonusDay = "";
        public int bonusBlocks;
        /** The tag currently held for their level, so it can be swapped rather than stacked. */
        public String heldTag = "";
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
    public String message(String key, String def) { return cfg.getString("messages." + key, def); }
    public int gateFor(String mine) {
        var gates = cfg.getConfigurationSection("gates");
        if (gates == null || mine == null) return 0;
        for (String k : gates.getKeys(false)) if (k.equalsIgnoreCase(mine)) return gates.getInt(k);
        return 0;
    }
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
        all.sort((a, b) -> a.prestige != b.prestige ? Integer.compare(b.prestige, a.prestige)
                : a.level != b.level ? Integer.compare(b.level, a.level) : Double.compare(b.xp, a.xp));
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
        if (countBlock) {
            prof.blocks++;
            amount *= dailyMultiplier(prof);
        }
        // Each prestige makes the next climb a little quicker.
        amount *= 1 + prof.prestige * cfg.getDouble("prestige.xp-bonus-per-prestige", 0.05);
        prof.xp += amount;
        dirty = true;

        // The level-1 title is earned by showing up, not by levelling.
        if (prof.heldTag.isBlank()) updateTitle(p, prof);
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

        // Every level goes out server-wide (cheap to switch off); milestones
        // get their own, louder line on top.
        if (cfg.getBoolean("announce-every-level", true) && !milestone)
            Bukkit.broadcast(MM.deserialize(cfg.getString("messages.level-broadcast",
                    "<gray>{player} reached mining level <white>{level}<gray>.")
                    .replace("{player}", p.getName()).replace("{level}", String.valueOf(prof.level))));
        updateTitle(p, prof);
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

    // ------------------------------------------------------------------ daily bonus

    /**
     * The first N blocks of the day pay extra. Cheap, and it gets people
     * into a mine every day, which is the whole point of it.
     */
    private double dailyMultiplier(Profile prof) {
        if (!cfg.getBoolean("daily.enabled", true)) return 1;
        String today = java.time.LocalDate.now().toString();
        if (!today.equals(prof.bonusDay)) {
            prof.bonusDay = today;
            prof.bonusBlocks = 0;
            Player p = Bukkit.getPlayer(prof.id);
            if (p != null) p.sendMessage(MM.deserialize(cfg.getString("messages.daily-start",
                            "<gradient:#ffd166:#ff8c00>✦ Daily bonus:</gradient> <gray>your first {blocks} blocks today pay {times}× xp.")
                    .replace("{blocks}", String.valueOf(cfg.getInt("daily.blocks", 100)))
                    .replace("{times}", String.valueOf(cfg.getDouble("daily.multiplier", 3)))));
        }
        int limit = cfg.getInt("daily.blocks", 100);
        if (prof.bonusBlocks >= limit) return 1;
        prof.bonusBlocks++;
        if (prof.bonusBlocks == limit) {
            Player p = Bukkit.getPlayer(prof.id);
            if (p != null) p.sendMessage(MM.deserialize(cfg.getString("messages.daily-done",
                    "<gray>Daily bonus used up. <dark_gray>Back tomorrow.")));
        }
        return cfg.getDouble("daily.multiplier", 3);
    }

    /** Blocks of daily bonus left today, for the menu. */
    public int dailyLeft(Profile prof) {
        String today = java.time.LocalDate.now().toString();
        int limit = cfg.getInt("daily.blocks", 100);
        return today.equals(prof.bonusDay) ? Math.max(0, limit - prof.bonusBlocks) : limit;
    }

    // ------------------------------------------------------------------ titles

    /**
     * The title for a level, from `titles:` — the highest threshold reached.
     * Handed over as a DeluxeTags permission, and the previous one taken
     * away, so a player holds exactly one mining tag at a time.
     */
    public String titleFor(int level) {
        ConfigurationSection t = cfg.getConfigurationSection("titles");
        if (t == null) return null;
        String best = null;
        int bestAt = -1;
        for (String k : t.getKeys(false)) {
            int at;
            try { at = Integer.parseInt(k); } catch (NumberFormatException e) { continue; }
            if (level >= at && at > bestAt) { bestAt = at; best = t.getString(k); }
        }
        return best;
    }

    private void updateTitle(Player p, Profile prof) {
        if (!cfg.getBoolean("titles-enabled", true)) return;
        String tag = titleFor(prof.level);
        if (tag == null || tag.equals(prof.heldTag)) return;
        String perm = cfg.getString("title-permission", "deluxetags.tag.{tag}");
        if (!prof.heldTag.isBlank())
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cfg.getString("title-revoke-command", "lp user {player} permission unset {permission}")
                            .replace("{player}", p.getName()).replace("{permission}", perm.replace("{tag}", prof.heldTag)));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                cfg.getString("title-grant-command", "lp user {player} permission set {permission} true")
                        .replace("{player}", p.getName()).replace("{permission}", perm.replace("{tag}", tag)));
        prof.heldTag = tag;
        dirty = true;
        p.sendMessage(MM.deserialize(cfg.getString("messages.title-earned",
                "<gradient:#ffd166:#ff8c00>✦ New title:</gradient> <white>{tag}</white> <dark_gray>— /tags to wear it")
                .replace("{tag}", tag)));
    }

    // ------------------------------------------------------------------ prestige

    /** Goes round again: back to level 1, a star for good, and a quicker climb. */
    public boolean prestige(Player p) {
        Profile prof = profile(p);
        int max = maxLevel();
        int maxPrestige = cfg.getInt("prestige.max", 10);
        if (prof.level < max) {
            p.sendMessage(MM.deserialize(cfg.getString("messages.prestige-not-yet",
                    "<red>You need level {max} to prestige. <gray>You're level {level}.")
                    .replace("{max}", String.valueOf(max)).replace("{level}", String.valueOf(prof.level))));
            return false;
        }
        if (prof.prestige >= maxPrestige) {
            p.sendMessage(MM.deserialize(cfg.getString("messages.prestige-maxed", "<gray>That's as far as it goes.")));
            return false;
        }
        prof.prestige++;
        prof.level = 1;
        prof.xp = 0;
        dirty = true;
        save();
        p.showTitle(Title.title(
                MM.deserialize(cfg.getString("messages.prestige-title", "<gradient:#e08cff:#7de2ff><bold>PRESTIGE {prestige}</bold></gradient>")
                        .replace("{prestige}", String.valueOf(prof.prestige))),
                MM.deserialize(cfg.getString("messages.prestige-subtitle", "<gray>Back to one. Faster this time.")),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3000), Duration.ofMillis(600))));
        playSound(p, cfg.getString("sounds.prestige", "ui.toast.challenge_complete"), 0.8f);
        p.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, p.getLocation().add(0, 1, 0), 120, 0.8, 1.5, 0.8, 0.1);
        Bukkit.broadcast(MM.deserialize(cfg.getString("messages.prestige-broadcast",
                "<gradient:#e08cff:#7de2ff>★ {player} has prestiged their mining — prestige {prestige}.</gradient>")
                .replace("{player}", p.getName()).replace("{prestige}", String.valueOf(prof.prestige))));
        for (String cmd : cfg.getStringList("prestige.commands"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("{player}", p.getName()).replace("{prestige}", String.valueOf(prof.prestige)));
        updateTitle(p, prof);
        return true;
    }

    /** "★★" for the menus and placeholders. */
    public String stars(Profile prof) {
        String star = cfg.getString("prestige.star", "★");
        return star.repeat(Math.max(0, prof.prestige));
    }

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
            y.set(b + ".prestige", p.prestige);
            y.set(b + ".bonus-day", p.bonusDay);
            y.set(b + ".bonus-blocks", p.bonusBlocks);
            y.set(b + ".held-tag", p.heldTag);
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
                p.prestige = root.getInt(id + ".prestige", 0);
                p.bonusDay = root.getString(id + ".bonus-day", "");
                p.bonusBlocks = root.getInt(id + ".bonus-blocks", 0);
                p.heldTag = root.getString(id + ".held-tag", "");
                profiles.put(p.id, p);
            } catch (IllegalArgumentException ignored) { }
        }
        plugin.getLogger().info("Mining levels: " + profiles.size() + " player(s) on record.");
    }

    public OfflinePlayer offline(Profile p) { return Bukkit.getOfflinePlayer(p.id); }
}
