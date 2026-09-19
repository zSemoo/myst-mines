package me.catalysmrl.catamines.myst.gui;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.mine.components.manager.controller.CataMineController;
import me.catalysmrl.catamines.mine.components.region.impl.SelectionRegion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /cm gui — every mine on one screen.
 *
 * Upstream left this as a stub ("GUI is currently disabled in this
 * version"), so this is a fresh one built on the fork's own menu base.
 *
 * Each mine shows what it's made of, when it next resets, how full it is,
 * whether an event is running and who's leading its contribution board.
 * Staff can reset, stop and start it from here; everyone else can look and
 * teleport, which is most of what a GUI is for.
 */
public class MineGui extends MystGui {

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private final List<CataMine> shown = new ArrayList<>();

    public MineGui(CataMines plugin) {
        super(plugin);
        create("<dark_gray>Mines", 6);
    }

    @Override
    protected void build(Player viewer) {
        inventory.clear();
        shown.clear();

        boolean staff = viewer.hasPermission("catamines.admin") || viewer.hasPermission("catamines.gui.manage");
        int i = 0;
        for (CataMine mine : plugin.getMineManager().getMines()) {
            if (i >= SLOTS.length) break;
            shown.add(mine);
            inventory.setItem(SLOTS[i++], tile(viewer, mine, staff));
        }

        if (shown.isEmpty())
            inventory.setItem(22, item(Material.BARRIER, "<red>No mines",
                    List.of("<gray>Nothing in the mines folder loaded.", "<gray>Check the console.")));

        var levels = plugin.getMineLevels();
        var prof = levels.profile(viewer);
        inventory.setItem(4, head(viewer, "<gradient:#ffd166:#ff8c00>Your mining</gradient>",
                List.of("<gray>Level <white>" + prof.level + "<dark_gray>/" + levels.maxLevel(),
                        levels.bar(levels.progress(prof)),
                        "<gray>Blocks mined: <white>" + prof.blocks,
                        "", "<yellow>Click <gray>for /level")));

        fill(Material.BLACK_STAINED_GLASS_PANE);
    }

    /** One mine, with everything worth knowing at a glance. */
    private org.bukkit.inventory.ItemStack tile(Player viewer, CataMine mine, boolean staff) {
        List<String> lore = new ArrayList<>();

        // what it's made of
        var region = mine.getRegionManager().getCurrent().orElse(null);
        if (region != null) region.getCompositionManager().getCurrent().ifPresent(comp -> {
            lore.add("<gray>Made of:");
            comp.getBlocks().stream()
                    .sorted((a, b) -> Double.compare(b.getChance(), a.getChance()))
                    .limit(5)
                    .forEach(b -> lore.add("<dark_gray>• <white>" + Math.round(b.getChance()) + "% <gray>"
                            + prettyBlock(b.getBaseBlock() == null ? "?" : b.getBaseBlock().toString())));
        });

        // when it resets
        CataMineController c = mine.getController();
        lore.add("");
        lore.add("<gray>Resets: <white>" + c.getResetMode().name().toLowerCase().replace('_', ' ')
                + (c.getResetMode() != CataMineController.ResetMode.PERCENTAGE
                    ? " <dark_gray>every " + c.getResetDelay() + "s" : "")
                + (c.getResetMode() != CataMineController.ResetMode.TIME
                    ? " <dark_gray>at " + Math.round(c.getResetPercentage()) + "%" : ""));

        // how full it is
        double filled = fullness(mine);
        if (filled >= 0) {
            lore.add("<gray>" + plugin.getMineLevels().bar(filled) + " <white>" + Math.round(filled * 100) + "%");
        }

        // an event, if one is running
        var active = plugin.getMineEvents().running(mine.getName());
        if (active != null)
            lore.add("<gold>" + me.catalysmrl.catamines.myst.event.MineEvents.pretty(active.kind)
                    + " <dark_gray>" + Math.max(0, (active.endsAt - System.currentTimeMillis()) / 1000) + "s left");

        // who's winning this reset
        var standings = plugin.getContributions().standings(mine.getName());
        if (!standings.isEmpty()) {
            Map.Entry<UUID, Integer> top = standings.get(0);
            var name = Bukkit.getOfflinePlayer(top.getKey()).getName();
            lore.add("<gray>Leading: <white>" + (name == null ? "?" : name)
                    + " <dark_gray>(" + top.getValue() + " blocks)");
        }
        int mine_ = plugin.getContributions().blocksThisReset(mine.getName(), viewer.getUniqueId());
        if (mine_ > 0) lore.add("<gray>You: <white>" + mine_ + " <gray>blocks this reset");

        // a level gate, if it has one
        int gate = plugin.getMineLevels().gateFor(mine.getName());
        boolean locked = gate > 0 && plugin.getMineLevels().profile(viewer).level < gate
                && plugin.getMineLevels().profile(viewer).prestige == 0;
        if (gate > 0) lore.add((locked ? "<red>" : "<green>") + "Opens at mining level " + gate);

        // this week's challenge
        var challenge = plugin.getMineChallenges().forMine(mine.getName());
        if (challenge != null) {
            int done = plugin.getMineChallenges().progress(viewer, challenge);
            lore.add("<gray>This week: <white>" + challenge.name());
            lore.add("<dark_gray>  " + done + "/" + challenge.amount()
                    + (plugin.getMineChallenges().claimed(viewer, challenge) ? " <green>done" : ""));
        }

        lore.add("");
        if (staff) {
            lore.add("<yellow>Click <gray>to edit this mine");
            lore.add("<yellow>Shift-click <gray>to reset it now");
        } else lore.add("<yellow>Click <gray>to teleport");

        Material icon = locked ? Material.IRON_BARS : iconFor(mine);
        return item(icon, (locked ? "<dark_gray>" : "<white>")
                + (mine.getDisplayName() == null || mine.getDisplayName().isBlank()
                    ? mine.getName() : mine.getDisplayName()), lore);
    }

    /** How much of the mine is still standing, or -1 if it can't be measured cheaply. */
    private double fullness(CataMine mine) {
        try {
            var region = mine.getRegionManager().getCurrent().orElse(null);
            if (!(region instanceof SelectionRegion sel)) return -1;
            var r = sel.getRegion();
            World w = Bukkit.getWorld(r.getWorld().getName());
            if (w == null) return -1;
            var min = r.getMinimumPoint();
            var max = r.getMaximumPoint();
            long volume = (long) (max.x() - min.x() + 1) * (max.y() - min.y() + 1) * (max.z() - min.z() + 1);
            // Sampling rather than counting: a big mine is millions of blocks
            // and this runs every time the menu is drawn.
            int samples = 200;
            int solid = 0;
            var rnd = new java.util.Random();
            for (int i = 0; i < samples; i++) {
                int x = min.x() + rnd.nextInt(Math.max(1, max.x() - min.x() + 1));
                int y = min.y() + rnd.nextInt(Math.max(1, max.y() - min.y() + 1));
                int z = min.z() + rnd.nextInt(Math.max(1, max.z() - min.z() + 1));
                if (!w.getBlockAt(x, y, z).getType().isAir()) solid++;
            }
            return volume <= 0 ? -1 : solid / (double) samples;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private boolean isStopped(CataMine mine) { return mine.getFlags().isStopped(); }

    private Material iconFor(CataMine mine) {
        var region = mine.getRegionManager().getCurrent().orElse(null);
        if (region == null) return Material.STONE;
        var comp = region.getCompositionManager().getCurrent().orElse(null);
        if (comp == null || comp.getBlocks().isEmpty()) return Material.STONE;
        // the commonest block, so the icon looks like the mine
        var best = comp.getBlocks().stream().max((a, b) -> Double.compare(a.getChance(), b.getChance())).orElse(null);
        if (best == null || best.getBaseBlock() == null) return Material.STONE;
        Material m = Material.matchMaterial(best.getBaseBlock().toString().split("\\[")[0].replace("minecraft:", ""));
        return m == null || !m.isItem() ? Material.STONE : m;
    }

    private static String prettyBlock(String raw) {
        String s = raw.split("\\[")[0].replace("minecraft:", "").replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public void onClick(Player p, InventoryClickEvent e) {
        if (e.getSlot() == 4) { p.closeInventory(); p.performCommand("level"); return; }

        int index = -1;
        for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i] == e.getSlot()) index = i;
        if (index < 0 || index >= shown.size()) return;
        CataMine mine = shown.get(index);
        boolean staff = p.hasPermission("catamines.admin") || p.hasPermission("catamines.gui.manage");

        if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) {
            if (!staff) return;
            mine.reset(plugin);
            p.sendMessage(MM.deserialize("<green>Reset <white>" + mine.getName() + "<green>."));
            refresh(p);
            return;
        }
        if (e.getClick() == ClickType.MIDDLE) {
            if (!staff) return;
            boolean stopped = isStopped(mine);
            mine.getFlags().setStopped(!stopped);
            p.sendMessage(MM.deserialize(stopped
                    ? "<green>Started <white>" + mine.getName() + "<green>."
                    : "<yellow>Stopped <white>" + mine.getName() + "<yellow>."));
            refresh(p);
            return;
        }

        // Staff get the editor; everyone else gets the teleport.
        if (staff) { new MineEditGui(plugin, mine).open(p); return; }
        var flag = mine.getFlags().getTeleportLocation();
        if (flag != null) { p.closeInventory(); p.teleport(flag); return; }
        try {
            var region = mine.getRegionManager().getCurrent().orElse(null);
            if (!(region instanceof SelectionRegion sel)) return;
            var r = sel.getRegion();
            World w = Bukkit.getWorld(r.getWorld().getName());
            if (w == null) return;
            var min = r.getMinimumPoint();
            var max = r.getMaximumPoint();
            int x = (min.x() + max.x()) / 2, z = (min.z() + max.z()) / 2;
            p.closeInventory();
            p.teleport(new org.bukkit.Location(w, x + 0.5, max.y() + 2, z + 0.5));
        } catch (RuntimeException ignored) { }
    }
}
