package me.catalysmrl.catamines.myst.dig;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.events.CataMineBlockBreakEvent;
import me.catalysmrl.catamines.api.events.CataMineResetEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.entity.Player;

/**
 * The one listener that feeds fossils, contributions, challenges, pickaxe
 * souls and the last-block bonus.
 *
 * Everything hangs off the mine's own break event so none of it fires
 * outside a mine, with fossils on the plain break event as well because a
 * fossil block is placed on top of the composition and might not belong to
 * it.
 */
public class DigListener implements Listener {

    private final CataMines plugin;

    public DigListener(CataMines plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMineBreak(CataMineBlockBreakEvent e) {
        Player p = e.getBlockBreakEvent().getPlayer();
        if (p == null || e.getCataMine() == null) return;
        String block = e.getBlockBreakEvent().getBlock().getType().name();

        plugin.getContributions().record(p, e.getCataMine());
        plugin.getMineChallenges().record(p, e.getCataMine(), block);

        var pick = p.getInventory().getItemInMainHand();
        if (plugin.getPickaxeSouls().hasSoul(pick))
            plugin.getPickaxeSouls().onBreak(p, pick,
                    plugin.getMineLevels().xpFor(e.getCataMine().getName(), block));

        plugin.getFossils().checkLastBlock(p, e.getCataMine());
    }

    /** Fossil blocks sit on top of the composition, so this is the plain event. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        plugin.getFossils().onBreak(e.getPlayer(), e.getBlock());
    }

    /** On reset: pay the board, then bury a new fossil. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onReset(CataMineResetEvent e) {
        plugin.getContributions().onReset(e.getCataMine());
        // a tick later, so the fossil is placed into the refilled mine
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.getFossils().onReset(e.getCataMine()), 5L);
    }
}
