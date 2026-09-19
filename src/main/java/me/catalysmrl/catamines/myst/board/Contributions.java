package me.catalysmrl.catamines.myst.board;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

/**
 * Who dug the most out of a mine this reset.
 *
 * Counts are per mine and wiped when it resets — that's what makes it a
 * race rather than a lifetime total nobody new can touch. The top few are
 * paid when the reset lands, and the standings are readable at any time, so
 * you can see you're two hundred blocks behind with a minute left and
 * decide whether to fight for it.
 */
public class Contributions {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CataMines plugin;
    /** mine -> player -> blocks this reset */
    private final Map<String, Map<UUID, Integer>> counts = new HashMap<>();
    /** all-time, for the board */
    private final Map<String, Map<UUID, Long>> lifetime = new HashMap<>();
    private YamlConfiguration cfg;
    private final File file;
    private boolean dirty;

    public Contributions(CataMines plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "contributions.yml");
        reload();
        load();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "digging.yml");
        if (!f.exists()) plugin.saveResource("digging.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
    }

    public void record(Player p, CataMine mine) {
        if (!cfg.getBoolean("contributions.enabled", true)) return;
        String key = mine.getName().toLowerCase();
        counts.computeIfAbsent(key, k -> new HashMap<>()).merge(p.getUniqueId(), 1, Integer::sum);
        lifetime.computeIfAbsent(key, k -> new HashMap<>()).merge(p.getUniqueId(), 1L, Long::sum);
        dirty = true;
    }

    public List<Map.Entry<UUID, Integer>> standings(String mine) {
        List<Map.Entry<UUID, Integer>> out = new ArrayList<>(counts.getOrDefault(mine.toLowerCase(), Map.of()).entrySet());
        out.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return out;
    }

    public List<Map.Entry<UUID, Long>> lifetimeStandings(String mine) {
        List<Map.Entry<UUID, Long>> out = new ArrayList<>(lifetime.getOrDefault(mine.toLowerCase(), Map.of()).entrySet());
        out.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return out;
    }

    public int blocksThisReset(String mine, UUID player) {
        return counts.getOrDefault(mine.toLowerCase(), Map.of()).getOrDefault(player, 0);
    }

    /** Called when a mine resets: pay the top few, then wipe the slate. */
    public void onReset(CataMine mine) {
        String key = mine.getName().toLowerCase();
        List<Map.Entry<UUID, Integer>> top = standings(key);
        counts.remove(key);
        if (top.isEmpty() || !cfg.getBoolean("contributions.pay-on-reset", true)) return;

        int minBlocks = cfg.getInt("contributions.minimum-blocks", 50);
        int places = cfg.getInt("contributions.paid-places", 3);
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < Math.min(places, top.size()); i++) {
            var entry = top.get(i);
            if (entry.getValue() < minBlocks) break;
            Player p = Bukkit.getPlayer(entry.getKey());
            for (String cmd : cfg.getStringList("contributions.rewards." + (i + 1)))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        cmd.replace("{player}", p != null ? p.getName() : Bukkit.getOfflinePlayer(entry.getKey()).getName())
                           .replace("{blocks}", String.valueOf(entry.getValue())));
            if (p != null) p.sendMessage(MM.deserialize(cfg.getString("contributions.paid",
                            "<gradient:#ffd166:#ff8c00>✦ #{place} in {mine}</gradient> <gray>— {blocks} blocks.")
                    .replace("{place}", String.valueOf(i + 1)).replace("{mine}", mine.getName())
                    .replace("{blocks}", String.valueOf(entry.getValue()))));
            if (i > 0) names.append("<gray>, ");
            names.append("<white>").append(p != null ? p.getName() : "?");
        }
        if (cfg.getBoolean("contributions.announce", true) && !names.isEmpty())
            Bukkit.broadcast(MM.deserialize(cfg.getString("contributions.broadcast",
                    "<gray>Top of <white>{mine}<gray> this reset: {names}<gray>.")
                    .replace("{mine}", mine.getName()).replace("{names}", names.toString())));
        save();
    }

    // ------------------------------------------------------------------ storage

    public void save() {
        if (!dirty) return;
        YamlConfiguration y = new YamlConfiguration();
        lifetime.forEach((mine, players) ->
                players.forEach((id, n) -> y.set("lifetime." + mine + "." + id, n)));
        try { y.save(file); dirty = false; } catch (Exception ex) {
            plugin.getLogger().severe("Couldn't save contributions.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        var root = y.getConfigurationSection("lifetime");
        if (root == null) return;
        for (String mine : root.getKeys(false)) {
            var sec = root.getConfigurationSection(mine);
            if (sec == null) continue;
            Map<UUID, Long> m = new HashMap<>();
            for (String id : sec.getKeys(false)) {
                try { m.put(UUID.fromString(id), sec.getLong(id)); } catch (IllegalArgumentException ignored) { }
            }
            lifetime.put(mine, m);
        }
    }
}
