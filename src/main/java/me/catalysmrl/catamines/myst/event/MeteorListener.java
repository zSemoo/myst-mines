package me.catalysmrl.catamines.myst.event;

import me.catalysmrl.catamines.CataMines;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Watches for the meteor being broken.
 *
 * A plain BlockBreakEvent rather than the mine's own event, because the
 * prize block is placed on top of whatever the composition put there and
 * might not be part of it.
 */
public class MeteorListener implements Listener {

    private final CataMines plugin;

    public MeteorListener(CataMines plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        plugin.getMineEvents().checkMeteor(e.getPlayer(), e.getBlock().getLocation());
    }
}
