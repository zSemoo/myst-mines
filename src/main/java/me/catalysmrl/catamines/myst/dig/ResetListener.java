package me.catalysmrl.catamines.myst.dig;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.events.CataMineResetEvent;
import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.mine.components.region.impl.SelectionRegion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;

/**
 * What happens to the people inside a mine when it resets.
 *
 * Upstream 3.0 reads the `teleport-players`, `teleport-location`,
 * `reset-teleport-location` and `warn` flags from the file and then never
 * uses any of them — so a player standing in a mine at reset was simply
 * buried. This is the missing half:
 *
 *   - `teleport-players` on: everyone inside is moved to the reset point,
 *     else the mine's teleport point, else straight up above the mine
 *   - `warn` on: a countdown is shown at `warn-seconds` and again at
 *     10, 5, 3, 2, 1 — to the people inside, or to everyone if `warn-global`
 */
public class ResetListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CataMines plugin;
    /** mine -> last second a warning was sent, so each threshold fires once */
    private final Map<String, Integer> lastWarned = new HashMap<>();

    public ResetListener(CataMines plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------------ moving people out

    @EventHandler(priority = EventPriority.LOWEST)
    public void onReset(CataMineResetEvent e) {
        CataMine mine = e.getCataMine();
        if (mine == null || !mine.getFlags().isTeleportPlayers()) return;
        Location to = mine.getFlags().getResetTeleportLocation();
        if (to == null) to = mine.getFlags().getTeleportLocation();
        for (Player p : playersInside(mine)) {
            Location dest = to != null ? to : above(mine, p);
            if (dest == null) continue;
            p.teleport(dest);
            p.setFallDistance(0);
            p.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
        }
    }

    /** Straight up, to the block above the mine's top — the fallback with no points set. */
    private Location above(CataMine mine, Player p) {
        try {
            var region = mine.getRegionManager().getChoices().get(0);
            if (!(region instanceof SelectionRegion sel)) return null;
            int top = sel.getRegion().getMaximumPoint().y();
            Location l = p.getLocation().clone();
            l.setY(top + 1.5);
            return l;
        } catch (RuntimeException ex) { return null; }
    }

    private java.util.List<Player> playersInside(CataMine mine) {
        java.util.List<Player> out = new java.util.ArrayList<>();
        try {
            var region = mine.getRegionManager().getChoices().get(0);
            if (!(region instanceof SelectionRegion sel)) return out;
            var r = sel.getRegion();
            World w = Bukkit.getWorld(r.getWorld().getName());
            if (w == null) return out;
            var min = r.getMinimumPoint(); var max = r.getMaximumPoint();
            for (Player p : w.getPlayers()) {
                var l = p.getLocation();
                // one block of slack above, so someone standing on the surface counts
                if (l.getBlockX() >= min.x() && l.getBlockX() <= max.x()
                        && l.getBlockY() >= min.y() && l.getBlockY() <= max.y() + 1
                        && l.getBlockZ() >= min.z() && l.getBlockZ() <= max.z()) out.add(p);
            }
        } catch (RuntimeException ignored) { }
        return out;
    }

    // ------------------------------------------------------------------ the warning

    /** Ticked every second. */
    public void tick() {
        for (CataMine mine : plugin.getMineManager().getMines()) {
            var flags = mine.getFlags();
            if (!flags.isWarn() || flags.isStopped()) continue;
            int left = mine.getController().getCountdown();
            Integer threshold = flags.getWarnSeconds();
            boolean fire = (threshold != null && left == threshold) || left == 10 || left == 5 || left == 3 || left == 2 || left == 1;
            if (!fire) continue;
            String key = mine.getName().toLowerCase();
            if (lastWarned.getOrDefault(key, -1) == left) continue;
            lastWarned.put(key, left);

            String name = mine.getDisplayName() == null || mine.getDisplayName().isBlank() ? mine.getName() : mine.getDisplayName();
            var msg = MM.deserialize("<gold>" + name + " <gray>resets in <white>" + left + "s");
            var audience = flags.isWarnGlobal() ? Bukkit.getOnlinePlayers() : playersInside(mine);
            for (Player p : audience) {
                if (flags.isWarnHotbar()) p.sendActionBar(msg); else p.sendMessage(msg);
                if (left <= 3) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.8f + (3 - left) * 0.3f);
            }
        }
    }
}
