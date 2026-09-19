package me.catalysmrl.catamines.myst.dig;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.mine.components.region.impl.SelectionRegion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Whoever takes the last block out of a mine before it resets.
 *
 * A small jackpot, announced. It gives the end of a reset a shape it didn't
 * have — people race the timer instead of drifting off when it thins out.
 * Checked cheaply: only every N breaks, and the count stops early the moment
 * it's clear the mine isn't empty yet.
 */
public class LastBlock {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CataMines plugin;
    private final Map<String, Integer> sinceCheck = new HashMap<>();
    private final Map<String, Long> lastPaid = new HashMap<>();
    private YamlConfiguration cfg;

    public LastBlock(CataMines plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "digging.yml");
        if (!f.exists()) plugin.saveResource("digging.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
    }

    public void check(Player p, CataMine mine) {
        if (!cfg.getBoolean("last-block.enabled", true)) return;
        String key = mine.getName().toLowerCase();
        int n = sinceCheck.merge(key, 1, Integer::sum);
        if (n % cfg.getInt("last-block.check-every", 20) != 0) return;
        long now = System.currentTimeMillis();
        if (now - lastPaid.getOrDefault(key, 0L) < cfg.getInt("last-block.minimum-seconds-between", 60) * 1000L) return;
        try {
            var region = mine.getRegionManager().getCurrent().orElse(null);
            if (!(region instanceof SelectionRegion sel)) return;
            var r = sel.getRegion();
            World w = Bukkit.getWorld(r.getWorld().getName());
            if (w == null) return;
            var min = r.getMinimumPoint();
            var max = r.getMaximumPoint();
            long limit = cfg.getInt("last-block.blocks-left", 3);
            long solid = 0;
            for (int x = min.x(); x <= max.x() && solid <= limit; x++)
                for (int y = min.y(); y <= max.y() && solid <= limit; y++)
                    for (int z = min.z(); z <= max.z() && solid <= limit; z++)
                        if (!w.getBlockAt(x, y, z).getType().isAir()) solid++;
            if (solid > limit) return;
            sinceCheck.remove(key);
            lastPaid.put(key, now);
            for (String cmd : cfg.getStringList("last-block.reward-commands"))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
            double xp = cfg.getDouble("last-block.xp", 250);
            if (xp > 0) plugin.getMineLevels().give(p, xp, false);
            Bukkit.broadcast(MM.deserialize(cfg.getString("last-block.broadcast",
                    "<gradient:#8cff9e:#1fbf5a>✦ {player} emptied {mine} — last block bonus.</gradient>")
                    .replace("{player}", p.getName()).replace("{mine}", mine.getName())));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        } catch (RuntimeException ignored) { }
    }
}
