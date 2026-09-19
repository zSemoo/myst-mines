package me.catalysmrl.catamines.myst.level;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Level-gated mines.
 *
 * `gates.<mine>: <level>` in levelling.yml. Below the level you can walk in,
 * you can look, you just can't break anything — and you're told once every
 * few seconds what it would take, rather than on every swing. The rank
 * ladder and the level ladder pull in different directions, which is the
 * point: a low-rank player who mines a lot gets somewhere rank alone
 * wouldn't take them.
 */
public class MineGateListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CataMines plugin;
    private final Map<UUID, Long> lastTold = new HashMap<>();

    public MineGateListener(CataMines plugin) { this.plugin = plugin; }

    private int gateFor(CataMine mine) { return plugin.getMineLevels().gateFor(mine.getName()); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("mystmines.gate.bypass")) return;
        CataMine mine = plugin.getMineManager().getMineAtLocation(e.getBlock().getLocation()).orElse(null);
        if (mine == null) return;
        int gate = gateFor(mine);
        if (gate <= 0) return;
        MineLevels.Profile prof = plugin.getMineLevels().profile(p);
        // Prestige counts as having been there: someone on their second climb
        // isn't locked out of mines they cleared the first time.
        if (prof.level >= gate || prof.prestige > 0) return;
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        if (now - lastTold.getOrDefault(p.getUniqueId(), 0L) < 3000) return;
        lastTold.put(p.getUniqueId(), now);
        p.sendMessage(MM.deserialize(plugin.getMineLevels().message("gate-denied",
                        "<red>This mine opens at mining level <white>{gate}<red>. <gray>You're level {level}.")
                .replace("{gate}", String.valueOf(gate)).replace("{level}", String.valueOf(prof.level))
                .replace("{mine}", mine.getName())));
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
    }
}
