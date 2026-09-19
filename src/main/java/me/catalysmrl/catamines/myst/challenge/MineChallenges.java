package me.catalysmrl.catamines.myst.challenge;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * Weekly challenges, per mine.
 *
 * Each mine can carry a list of challenges; one is picked per week, seeded
 * by the week number so every player on the server sees the same one and
 * they rotate predictably. Progress is per player and resets with the week.
 *
 * Challenges are deliberately mine-specific — "500 in peasant" is a
 * different ask from "50 emerald in king" — which gives the lower mines a
 * reason to exist after you've outgrown them.
 */
public class MineChallenges {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public record Challenge(String id, String name, String mine, String block, int amount, List<String> rewards) { }

    private final CataMines plugin;
    private final File file;
    private YamlConfiguration cfg;
    /** player -> challenge id -> progress */
    private final Map<UUID, Map<String, Integer>> progress = new HashMap<>();
    private final Map<UUID, Set<String>> claimed = new HashMap<>();
    private String week = "";
    private boolean dirty;

    public MineChallenges(CataMines plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "challenge-progress.yml");
        reload();
        load();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "challenges.yml");
        if (!f.exists()) plugin.saveResource("challenges.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
    }

    private String thisWeek() {
        LocalDate now = LocalDate.now();
        return now.getYear() + "-w" + now.get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    /** The challenge running in a mine this week, or null. */
    public Challenge forMine(String mine) {
        ConfigurationSection sec = cfg.getConfigurationSection("challenges." + mine.toLowerCase());
        if (sec == null) return null;
        List<String> ids = new ArrayList<>(sec.getKeys(false));
        if (ids.isEmpty()) return null;
        // seeded by the week, so everyone sees the same one and it rotates
        int index = Math.floorMod(thisWeek().hashCode() + mine.toLowerCase().hashCode(), ids.size());
        String id = ids.get(index);
        return new Challenge(mine.toLowerCase() + ":" + id,
                sec.getString(id + ".name", id),
                mine.toLowerCase(),
                sec.getString(id + ".block", "any"),
                sec.getInt(id + ".amount", 100),
                sec.getStringList(id + ".rewards"));
    }

    public List<Challenge> allThisWeek() {
        List<Challenge> out = new ArrayList<>();
        ConfigurationSection root = cfg.getConfigurationSection("challenges");
        if (root == null) return out;
        for (String mine : root.getKeys(false)) {
            Challenge c = forMine(mine);
            if (c != null) out.add(c);
        }
        return out;
    }

    public int progress(Player p, Challenge c) {
        rollWeek();
        return progress.getOrDefault(p.getUniqueId(), Map.of()).getOrDefault(c.id(), 0);
    }

    public boolean claimed(Player p, Challenge c) {
        return claimed.getOrDefault(p.getUniqueId(), Set.of()).contains(c.id());
    }

    /** Wipes everyone's progress when the week turns. */
    private void rollWeek() {
        String now = thisWeek();
        if (now.equals(week)) return;
        week = now;
        progress.clear();
        claimed.clear();
        dirty = true;
        save();
    }

    public void record(Player p, CataMine mine, String block) {
        if (!cfg.getBoolean("enabled", true)) return;
        rollWeek();
        Challenge c = forMine(mine.getName());
        if (c == null || claimed(p, c)) return;
        if (!c.block().equalsIgnoreCase("any")
                && !block.toLowerCase().endsWith(c.block().toLowerCase().replace("minecraft:", ""))) return;

        Map<String, Integer> mine_ = progress.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        int now = mine_.merge(c.id(), 1, Integer::sum);
        dirty = true;

        if (now == c.amount()) {
            claimed.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(c.id());
            for (String cmd : c.rewards())
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.sendMessage(MM.deserialize(cfg.getString("messages.done",
                    "<gradient:#8cff9e:#1fbf5a>✦ Challenge done:</gradient> <white>{name}")
                    .replace("{name}", c.name())));
            if (cfg.getBoolean("announce", true))
                Bukkit.broadcast(MM.deserialize(cfg.getString("messages.broadcast",
                        "<gray>{player} finished this week's <white>{name}<gray>.")
                        .replace("{player}", p.getName()).replace("{name}", c.name())));
            save();
        } else if (now % Math.max(1, c.amount() / 4) == 0) {
            p.sendActionBar(MM.deserialize(cfg.getString("messages.progress",
                    "<gray>{name} <dark_gray>{progress}/{amount}")
                    .replace("{name}", c.name()).replace("{progress}", String.valueOf(now))
                    .replace("{amount}", String.valueOf(c.amount()))));
        }
    }

    // ------------------------------------------------------------------ storage

    public void save() {
        if (!dirty) return;
        YamlConfiguration y = new YamlConfiguration();
        y.set("week", week);
        progress.forEach((id, m) -> m.forEach((c, n) -> y.set("progress." + id + "." + c.replace(":", "__"), n)));
        claimed.forEach((id, set) -> y.set("claimed." + id, new ArrayList<>(set)));
        try { y.save(file); dirty = false; } catch (Exception ex) {
            plugin.getLogger().severe("Couldn't save challenge-progress.yml: " + ex.getMessage());
        }
    }

    private void load() {
        week = thisWeek();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (!thisWeek().equals(y.getString("week", ""))) return;    // stale week, start clean
        var pr = y.getConfigurationSection("progress");
        if (pr != null) for (String id : pr.getKeys(false)) {
            try {
                UUID u = UUID.fromString(id);
                Map<String, Integer> m = new HashMap<>();
                var sec = pr.getConfigurationSection(id);
                if (sec != null) for (String c : sec.getKeys(false)) m.put(c.replace("__", ":"), sec.getInt(c));
                progress.put(u, m);
            } catch (IllegalArgumentException ignored) { }
        }
        var cl = y.getConfigurationSection("claimed");
        if (cl != null) for (String id : cl.getKeys(false)) {
            try { claimed.put(UUID.fromString(id), new HashSet<>(cl.getStringList(id))); }
            catch (IllegalArgumentException ignored) { }
        }
    }
}
