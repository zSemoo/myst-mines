package me.catalysmrl.catamines.myst.dig;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.events.CataMineBlockBreakEvent;
import me.catalysmrl.catamines.api.events.CataMineResetEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * The one listener feeding contributions, challenges, pickaxe souls and the
 * last-block bonus. Everything hangs off the mine's own break event, so none
 * of it fires outside a mine.
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
        if (plugin.getPickaxeSouls().hasSoul(pick)) {
            plugin.getPickaxeSouls().onBreak(p, pick, plugin.getMineLevels().xpFor(e.getCataMine().getName(), block));
            // getItemInMainHand() can hand back a copy on newer Paper, in
            // which case edits to it never reach the inventory — which is
            // exactly why soul progress looked stuck. Put it back explicitly.
            p.getInventory().setItemInMainHand(pick);
        }

        plugin.getLastBlock().check(p, e.getCataMine());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReset(CataMineResetEvent e) {
        plugin.getContributions().onReset(e.getCataMine());
    }
}
