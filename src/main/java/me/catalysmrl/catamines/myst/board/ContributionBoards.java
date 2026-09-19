package me.catalysmrl.catamines.myst.board;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.events.CataMineBlockBreakEvent;
import me.catalysmrl.catamines.api.events.CataMineResetEvent;
import me.catalysmrl.catamines.api.mine.CataMine;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.File;
import java.util.*;

/**
 * Who did the work this reset.
 *
 * Every mine keeps a count per player of blocks broken since its last
 * refill. When it resets, the board closes: the top three are announced,
 * the winner gets `winner-commands`, and whoever broke the LAST block before
 * the reset takes a small jackpot of their own. Then the counts clear and
 * the next reset starts from nothing — so a new player can win one, which
 * an all-time board never lets them do.
 *
 * A hologram per mine (placed with /mine board <mine>) shows the live top
 * three, refreshed every few seconds.
 */
public class ContributionBoards implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CataMines plugin;
    /** mine -> player -> blocks this reset */
    private final Map<String, Map<UUID, Integer>> counts = new HashMap<>();
    /** mine -> who broke the most recent block, so "last block" can be paid */
    private final Map<String, UUID> lastBreaker = new HashMap<>();
    /** mine -> how many blocks the mine started the reset with (for the empty check) */
    private final Map<String, Long> volumes = new HashMap<>();
    private final Map<String, Location> boards = new HashMap<>();
    private final Map<String, TextDisplay> displays = new HashMap<>();
    private YamlConfiguration cfg;

    public ContributionBoards(CataMines plugin) {
        this.plugin = plugin;
        reload();
        loadBoards();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "boards.yml");
        if (!f.exists()) plugin.saveResource("boards.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
    }

    private Map<UUID, Integer> countsFor(String mine) {
        return counts.computeIfAbsent(mine.toLowerCase(), k -> new HashMap<>());
    }

    public int broken(String mine, UUID player) { return countsFor(mine).getOrDefault(player, 0); }

    /** Top N for a mine this reset. */
    public List<Map.Entry<UUID, Integer>> top(String mine, int n) {
        List<Map.Entry<UUID, Integer>> all = new ArrayList<>(countsFor(mine).entrySet());
        all.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return all.size() > n ? all.subList(0, n) : all;
    }

    // ------------------------------------------------------------------ counting

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(CataMineBlockBreakEvent e) {
        Player p = e.getBlockBreakEvent().getPlayer();
        if (p == null || e.getCataMine() == null) return;
        String mine = e.getCataMine().getName().toLowerCase();
        countsFor(mine).merge(p.getUniqueId(), 1, Integer::sum);
        lastBreaker.put(mine, p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReset(CataMineResetEvent e) {
        CataMine mine = e.getCataMine();
        String key = mine.getName().toLowerCase();
        Map<UUID, Integer> board = countsFor(key);
        if (!board.isEmpty()) close(mine, board);
        board.clear();
        lastBreaker.remove(key);
    }

    /** The board closes: winners named, rewards paid. */
    private void close(CataMine mine, Map<UUID, Integer> board) {
        List<Map.Entry<UUID, Integer>> top = top(mine.getName(), 3);
        int total = board.values().stream().mapToInt(Integer::intValue).sum();
        int minimum = cfg.getInt("minimum-blocks-to-count", 50);
        if (total < minimum) return;                       // nobody really mined it; don't spam

        StringBuilder names = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            var entry = top.get(i);
            String name = Optional.ofNullable(Bukkit.getOfflinePlayer(entry.getKey()).getName()).orElse("?");
            names.append(i > 0 ? "<gray>, " : "").append("<white>").append(name)
                    .append(" <dark_gray>(").append(entry.getValue()).append(")");
        }
        Bukkit.broadcast(MM.deserialize(cfg.getString("messages.closed",
                "<gradient:#ffd166:#ff8c00>⛏ {mine} reset</gradient> <gray>— top diggers: {top}")
                .replace("{mine}", mine.getDisplayName()).replace("{top}", names.toString())
                .replace("{total}", String.valueOf(total))));

        // the winner
        if (!top.isEmpty()) {
            Player winner = Bukkit.getPlayer(top.get(0).getKey());
            for (String cmd : cfg.getStringList("winner-commands"))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd
                        .replace("{player}", Optional.ofNullable(Bukkit.getOfflinePlayer(top.get(0).getKey()).getName()).orElse(""))
                        .replace("{mine}", mine.getName()).replace("{blocks}", String.valueOf(top.get(0).getValue())));
            if (winner != null) {
                winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                winner.sendMessage(MM.deserialize(cfg.getString("messages.you-won",
                        "<gradient:#ffd166:#ff8c00>✦ You out-dug everyone in {mine} this reset.</gradient>")
                        .replace("{mine}", mine.getDisplayName())));
            }
        }

        // the last block
        UUID last = lastBreaker.get(mine.getName().toLowerCase());
        if (last != null && cfg.getBoolean("last-block.enabled", true)) {
            String name = Optional.ofNullable(Bukkit.getOfflinePlayer(last).getName()).orElse("");
            for (String cmd : cfg.getStringList("last-block.commands"))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", name).replace("{mine}", mine.getName()));
            Bukkit.broadcast(MM.deserialize(cfg.getString("messages.last-block",
                    "<gradient:#8cff9e:#1fbf5a>⛏ {player} broke the last block in {mine}.</gradient>")
                    .replace("{player}", name).replace("{mine}", mine.getDisplayName())));
        }
    }

    // ------------------------------------------------------------------ holograms

    public void placeBoard(CataMine mine, Location at) {
        removeBoard(mine.getName());
        boards.put(mine.getName().toLowerCase(), at);
        saveBoards();
        refresh(mine.getName());
    }

    public boolean removeBoard(String mine) {
        String key = mine.toLowerCase();
        TextDisplay d = displays.remove(key);
        if (d != null) d.remove();
        boolean had = boards.remove(key) != null;
        if (had) saveBoards();
        return had;
    }

    public void refresh(String mineName) {
        String key = mineName.toLowerCase();
        Location at = boards.get(key);
        if (at == null || at.getWorld() == null || !at.isChunkLoaded()) return;
        TextDisplay d = displays.get(key);
        if (d == null || !d.isValid()) {
            // sweep any stray copy left by a previous run before spawning
            for (var e : at.getWorld().getNearbyEntities(at, 1, 2, 1))
                if (e instanceof TextDisplay && e.getPersistentDataContainer().has(
                        new org.bukkit.NamespacedKey(plugin, "board"), org.bukkit.persistence.PersistentDataType.STRING)) e.remove();
            d = at.getWorld().spawn(at, TextDisplay.class, td -> {
                td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                td.setShadowed(true);
                td.setDefaultBackground(false);
                td.setPersistent(false);
                td.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "board"),
                        org.bukkit.persistence.PersistentDataType.STRING, key);
            });
            displays.put(key, d);
        }
        CataMine mine = plugin.getMineManager().getMine(mineName).orElse(null);
        String display = mine == null ? mineName : mine.getDisplayName();
        StringBuilder sb = new StringBuilder(cfg.getString("hologram.header",
                "<gradient:#ffd166:#ff8c00><bold>{mine}</bold></gradient> <gray>this reset").replace("{mine}", display));
        List<Map.Entry<UUID, Integer>> top = top(mineName, cfg.getInt("hologram.rows", 3));
        String[] medal = {"<#ffd166>1.", "<#d0d0d0>2.", "<#cd7f32>3.", "<gray>4.", "<gray>5."};
        if (top.isEmpty()) sb.append("\n").append(cfg.getString("hologram.empty", "<dark_gray>Nobody yet. Be first."));
        for (int i = 0; i < top.size(); i++) {
            var e = top.get(i);
            String name = Optional.ofNullable(Bukkit.getOfflinePlayer(e.getKey()).getName()).orElse("?");
            sb.append("\n").append(i < medal.length ? medal[i] : "<gray>" + (i + 1) + ".")
                    .append(" <white>").append(name).append(" <dark_gray>").append(e.getValue());
        }
        d.text(MM.deserialize(sb.toString()));
    }

    /** Every few seconds. */
    public void tick() { for (String mine : new ArrayList<>(boards.keySet())) refresh(mine); }

    public void shutdown() { for (TextDisplay d : displays.values()) d.remove(); displays.clear(); }

    private File boardsFile() { return new File(plugin.getDataFolder(), "boards-placed.yml"); }

    private void saveBoards() {
        YamlConfiguration y = new YamlConfiguration();
        boards.forEach((mine, at) -> {
            y.set(mine + ".world", at.getWorld().getName());
            y.set(mine + ".x", at.getX()); y.set(mine + ".y", at.getY()); y.set(mine + ".z", at.getZ());
        });
        try { y.save(boardsFile()); } catch (Exception ex) { plugin.getLogger().warning("Couldn't save boards: " + ex.getMessage()); }
    }

    private void loadBoards() {
        if (!boardsFile().exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(boardsFile());
        for (String mine : y.getKeys(false)) {
            var w = Bukkit.getWorld(y.getString(mine + ".world", ""));
            if (w == null) continue;
            boards.put(mine, new Location(w, y.getDouble(mine + ".x"), y.getDouble(mine + ".y"), y.getDouble(mine + ".z")));
        }
    }
}
