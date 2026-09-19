package me.catalysmrl.catamines.myst.gui;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.mine.components.composition.CataMineBlock;
import me.catalysmrl.catamines.mine.components.composition.CataMineComposition;
import me.catalysmrl.catamines.mine.components.manager.controller.CataMineController;
import me.catalysmrl.catamines.mine.components.region.impl.SelectionRegion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * One mine, editable.
 *
 * The blocks and their percentages across the top; a working set of
 * controls below. Percentages are edited in place — left-click +5, right
 * -5, shift for ±1, drop (Q) to remove — and the total is shown at all
 * times, green at exactly 100 and red otherwise, because a composition that
 * doesn't add up is the single commonest way a mine goes wrong.
 *
 * Adding a block: hold it and click the empty "add" slot. Everything saves
 * to the mine's file the moment it changes.
 */
public class MineEditGui extends MystGui {

    private final CataMine mine;
    private static final int[] BLOCK_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
    private static final int TOTAL = 4, ADD = 27,
            TELEPORT = 37, COUNTDOWN = 38, ANNOUNCE = 39, MOVE_PLAYERS = 40, DELAY = 41, ENABLED = 42, RESET = 43, GATE = 44, BACK = 45;

    /** Players who asked to see this mine's countdown on their action bar. */
    private static final Set<UUID> watching = new HashSet<>();
    private static final Map<UUID, String> watchingMine = new HashMap<>();

    public MineEditGui(CataMines plugin, CataMine mine) {
        super(plugin);
        this.mine = mine;
        create("<dark_gray>Mine <dark_gray>» <white>" + mine.getName(), 6);
    }

    // ------------------------------------------------------------------ the composition

    private CataMineComposition composition() {
        var region = mine.getRegionManager().getChoices().isEmpty() ? null : mine.getRegionManager().getChoices().get(0);
        if (region == null) return null;
        var comps = region.getCompositionManager().getChoices();
        return comps.isEmpty() ? null : comps.get(0);
    }

    private double total(CataMineComposition comp) {
        double t = 0;
        for (CataMineBlock b : comp.getBlocks()) t += b.getChance();
        return t;
    }

    private static String pretty(CataMineBlock b) {
        String raw = b.getBaseBlock() == null ? "?" : b.getBaseBlock().toString();
        String s = raw.split("\\[")[0].replace("minecraft:", "").replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static Material iconOf(CataMineBlock b) {
        if (b.getBaseBlock() == null) return Material.STONE;
        Material m = Material.matchMaterial(b.getBaseBlock().toString().split("\\[")[0].replace("minecraft:", ""));
        return m == null || !m.isItem() ? Material.STONE : m;
    }

    // ------------------------------------------------------------------ build

    @Override
    protected void build(Player viewer) {
        inventory.clear();
        CataMineComposition comp = composition();
        CataMineController c = mine.getController();

        if (comp != null) {
            double total = total(comp);
            boolean ok = Math.abs(total - 100) < 0.01;
            inventory.setItem(TOTAL, item(ok ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                    (ok ? "<green>" : "<red>") + "Total: " + fmt(total) + "%",
                    List.of(ok ? "<gray>Adds up. The mine will reset cleanly."
                               : "<gray>Needs to be exactly 100%.",
                            "", "<dark_gray>Left +5  Right -5  Shift ±1  Q removes")));

            int i = 0;
            for (CataMineBlock b : comp.getBlocks()) {
                if (i >= BLOCK_SLOTS.length) break;
                ItemStack it = item(iconOf(b), "<white>" + pretty(b),
                        List.of("<gray>Chance: <white>" + fmt(b.getChance()) + "%",
                                "", "<yellow>Left <gray>+5   <yellow>Right <gray>-5",
                                "<yellow>Shift <gray>±1   <yellow>Q <gray>remove"));
                it.setAmount(Math.max(1, Math.min(64, (int) Math.round(b.getChance()))));
                inventory.setItem(BLOCK_SLOTS[i++], it);
            }
        }

        inventory.setItem(ADD, item(Material.LIME_DYE, "<green>Add a block",
                List.of("<gray>Click any block in your inventory below",
                        "<gray>and it's added at 0% — then raise it.")));

        inventory.setItem(TELEPORT, item(Material.ENDER_PEARL, "<aqua>Teleport to the mine", List.of()));

        boolean watchingThis = watching.contains(viewer.getUniqueId()) && mine.getName().equals(watchingMine.get(viewer.getUniqueId()));
        inventory.setItem(COUNTDOWN, item(watchingThis ? Material.CLOCK : Material.GRAY_DYE,
                (watchingThis ? "<green>" : "<gray>") + "Reset countdown on your screen",
                List.of("<gray>Shows this mine's time to reset on your action bar.",
                        "", watchingThis ? "<yellow>Click <gray>to hide it" : "<yellow>Click <gray>to show it")));

        boolean announces = mine.getFlags().isWarnGlobal();
        inventory.setItem(ANNOUNCE, item(announces ? Material.BELL : Material.GRAY_DYE,
                (announces ? "<green>" : "<gray>") + "Announce resets to everyone",
                List.of("<gray>Whether the reset warning goes server-wide",
                        "<gray>or only to people inside the mine.",
                        "", "<yellow>Click <gray>to " + (announces ? "keep it local" : "announce it"))));

        boolean moves = mine.getFlags().isTeleportPlayers();
        var resetPoint = mine.getFlags().getResetTeleportLocation();
        var normalPoint = mine.getFlags().getTeleportLocation();
        List<String> moveLore = new ArrayList<>();
        moveLore.add("<gray>Whether people inside are moved out when it resets.");
        moveLore.add(moves ? "<green>On." : "<red>Off — anyone inside gets buried in the refill.");
        moveLore.add("");
        if (resetPoint != null) moveLore.add("<gray>To: <white>the reset point <dark_gray>(/cm setresetteleport)");
        else if (normalPoint != null) moveLore.add("<gray>To: <white>the mine's teleport point <dark_gray>(no reset point set)");
        else moveLore.add("<red>No teleport point set — nothing to move them to.");
        moveLore.add("");
        moveLore.add("<yellow>Click <gray>to " + (moves ? "leave them where they are" : "move them out"));
        inventory.setItem(MOVE_PLAYERS, item(moves ? Material.ENDER_EYE : Material.GRAY_DYE,
                (moves ? "<green>" : "<gray>") + "Move players out on reset", moveLore));

        inventory.setItem(DELAY, item(Material.REPEATER, "<gold>Reset every " + c.getResetDelay() + "s",
                List.of("<gray>Mode: <white>" + c.getResetMode().name().toLowerCase().replace('_', ' '),
                        "", "<yellow>Left <gray>+30s   <yellow>Right <gray>-30s",
                        "<yellow>Shift <gray>±5s")));

        boolean stopped = mine.getFlags().isStopped();
        inventory.setItem(ENABLED, item(stopped ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
                stopped ? "<red>Disabled" : "<green>Enabled",
                List.of(stopped ? "<gray>The mine won't reset until it's enabled." : "<gray>Resetting on schedule.",
                        "", "<yellow>Click <gray>to " + (stopped ? "enable" : "disable"))));

        inventory.setItem(RESET, item(Material.TNT, "<red>Reset now", List.of("<gray>Refills it immediately.")));

        int gate = plugin.getMineLevels().gateFor(mine.getName());
        inventory.setItem(GATE, item(gate > 0 ? Material.IRON_BARS : Material.OAK_DOOR,
                gate > 0 ? "<gold>Opens at mining level " + gate : "<green>Open to everyone",
                List.of("<gray>Below the level you can walk in and look;",
                        "<gray>you can't break anything.",
                        "<dark_gray>Prestiged players pass every gate.",
                        "", "<yellow>Left <gray>+5   <yellow>Right <gray>-5",
                        "<yellow>Shift <gray>±1   <yellow>Q <gray>removes the gate")));
        inventory.setItem(BACK, item(Material.ARROW, "<gray>Back to all mines", List.of()));
        fill(Material.BLACK_STAINED_GLASS_PANE);
    }

    private static String fmt(double d) { return d == Math.floor(d) ? String.valueOf((int) d) : String.format("%.1f", d); }

    // ------------------------------------------------------------------ clicks

    @Override
    public void onClick(Player p, InventoryClickEvent e) {
        int slot = e.getSlot();
        CataMineComposition comp = composition();

        // a block's chance
        for (int i = 0; i < BLOCK_SLOTS.length; i++) {
            if (BLOCK_SLOTS[i] != slot || comp == null || i >= comp.getBlocks().size()) continue;
            CataMineBlock b = comp.getBlocks().get(i);
            if (e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) {
                comp.getBlocks().remove(i);
            } else {
                double step = e.isShiftClick() ? 1 : 5;
                if (e.isRightClick()) step = -step;
                b.setChance(Math.max(0, Math.min(100, b.getChance() + step)));
            }
            // The reset uses a precomputed pattern, not the list — without
            // this, an edit shows in the menu and changes nothing in the mine.
            comp.refreshPattern();
            save(p);
            refresh(p);
            return;
        }

        switch (slot) {
            case ADD -> p.sendMessage(MM.deserialize("<gray>Click a block in your inventory to add it."));
            case TELEPORT -> {
                p.closeInventory();
                var at = mine.getFlags().getTeleportLocation();
                if (at != null) { p.teleport(at); return; }
                centre().ifPresent(p::teleport);
            }
            case COUNTDOWN -> {
                boolean on = watching.contains(p.getUniqueId()) && mine.getName().equals(watchingMine.get(p.getUniqueId()));
                if (on) { watching.remove(p.getUniqueId()); watchingMine.remove(p.getUniqueId()); }
                else { watching.add(p.getUniqueId()); watchingMine.put(p.getUniqueId(), mine.getName()); }
                refresh(p);
            }
            case ANNOUNCE -> {
                mine.getFlags().setWarnGlobal(!mine.getFlags().isWarnGlobal());
                save(p);
                refresh(p);
            }
            case MOVE_PLAYERS -> {
                mine.getFlags().setTeleportPlayers(!mine.getFlags().isTeleportPlayers());
                save(p);
                refresh(p);
            }
            case DELAY -> {
                int step = e.isShiftClick() ? 5 : 30;
                if (e.isRightClick()) step = -step;
                CataMineController c = mine.getController();
                c.setResetDelay(Math.max(5, c.getResetDelay() + step));   // never zero: a zero delay spins
                save(p);
                refresh(p);
            }
            case ENABLED -> {
                mine.getFlags().setStopped(!mine.getFlags().isStopped());
                save(p);
                refresh(p);
            }
            case RESET -> {
                mine.reset(plugin);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.3f);
                p.sendMessage(MM.deserialize("<green>Reset <white>" + mine.getName() + "<green>."));
            }
            case GATE -> {
                int gate = plugin.getMineLevels().gateFor(mine.getName());
                if (e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) gate = 0;
                else {
                    int step = e.isShiftClick() ? 1 : 5;
                    if (e.isRightClick()) step = -step;
                    gate = Math.max(0, Math.min(plugin.getMineLevels().maxLevel(), gate + step));
                }
                plugin.getMineLevels().setGate(mine.getName(), gate);
                refresh(p);
            }
            case BACK -> new MineGui(plugin).open(p);
            default -> { }
        }
    }

    /** Clicking a block in your own inventory adds it to the composition. */
    @Override
    public void onOwnInventoryClick(Player p, InventoryClickEvent e) {
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        if (!clicked.getType().isBlock()) {
            p.sendMessage(MM.deserialize("<red>That isn't a block."));
            return;
        }
        CataMineComposition comp = composition();
        if (comp == null) return;
        if (comp.getBlocks().size() >= BLOCK_SLOTS.length) {
            p.sendMessage(MM.deserialize("<red>The mine already has " + BLOCK_SLOTS.length + " blocks."));
            return;
        }
        try {
            // addBlock replaces any existing entry for the same block, and
            // rebuilds the pattern; the item stays in their inventory.
            comp.addBlock(new CataMineBlock("minecraft:" + clicked.getType().name().toLowerCase(), 0));
            save(p);
            p.playSound(p.getLocation(), Sound.BLOCK_STONE_PLACE, 0.8f, 1.2f);
        } catch (Exception ex) {
            p.sendMessage(MM.deserialize("<red>Couldn't add that: " + ex.getMessage()));
        }
        refresh(p);
    }

    private Optional<org.bukkit.Location> centre() {
        try {
            var region = mine.getRegionManager().getChoices().get(0);
            if (!(region instanceof SelectionRegion sel)) return Optional.empty();
            var r = sel.getRegion();
            World w = Bukkit.getWorld(r.getWorld().getName());
            if (w == null) return Optional.empty();
            var min = r.getMinimumPoint(); var max = r.getMaximumPoint();
            return Optional.of(new org.bukkit.Location(w, (min.x() + max.x()) / 2 + 0.5, max.y() + 2, (min.z() + max.z()) / 2 + 0.5));
        } catch (RuntimeException ex) { return Optional.empty(); }
    }

    private void save(Player p) {
        try { plugin.getMineManager().saveMine(mine); }
        catch (Exception ex) { p.sendMessage(MM.deserialize("<red>Couldn't save: " + ex.getMessage())); }
    }

    // ------------------------------------------------------------------ the countdown

    /** Inside the mine's box, grown by `pad` blocks on every side. */
    private static boolean nearMine(Player p, CataMine mine, int pad) {
        try {
            var region = mine.getRegionManager().getChoices().get(0);
            if (!(region instanceof SelectionRegion sel)) return false;
            var r = sel.getRegion();
            if (!p.getWorld().getName().equals(r.getWorld().getName())) return false;
            var min = r.getMinimumPoint(); var max = r.getMaximumPoint();
            var l = p.getLocation();
            return l.getBlockX() >= min.x() - pad && l.getBlockX() <= max.x() + pad
                    && l.getBlockY() >= min.y() - pad && l.getBlockY() <= max.y() + pad
                    && l.getBlockZ() >= min.z() - pad && l.getBlockZ() <= max.z() + pad;
        } catch (RuntimeException ex) { return false; }
    }

    /** Ticked every second: anyone watching a mine sees its timer. */
    public static void tickCountdowns(CataMines plugin) {
        if (watching.isEmpty()) return;
        for (UUID id : new ArrayList<>(watching)) {
            Player p = Bukkit.getPlayer(id);
            String name = watchingMine.get(id);
            if (p == null || name == null) { watching.remove(id); continue; }
            CataMine mine = plugin.getMineManager().getMine(name).orElse(null);
            if (mine == null) { watching.remove(id); continue; }
            // Only shown while you're at the mine — a block inside its
            // bounds counts, so standing on the rim still shows it.
            if (!nearMine(p, mine, 1)) continue;
            CataMineController c = mine.getController();
            String text = mine.getFlags().isStopped() ? "<red>" + name + " is disabled"
                    : "<gold>" + name + " <gray>resets in <white>" + Math.max(0, c.getCountdown()) + "s";
            p.sendActionBar(MM.deserialize(text));
        }
    }
}
