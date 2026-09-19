package me.catalysmrl.catamines.myst.gui;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.myst.level.MineLevels;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /level, laid out as three rows that each answer one question.
 *
 *   Row 1 — who you are: your head, level, bar, title, prestige.
 *   Row 2 — what's next: the coming milestones, left to right, with what
 *           each pays; the ones you've passed are green.
 *   Row 3 — where to go: the leaderboard, prestige, the daily bonus.
 */
public class LevelGui extends MystGui {

    private final UUID subject;
    private static final int ME = 4;
    private static final int[] MILESTONES = {19, 20, 21, 22, 23, 24, 25};
    private static final int TOP = 38, PRESTIGE = 40, DAILY = 42;

    public LevelGui(CataMines plugin, UUID subject) {
        super(plugin);
        this.subject = subject;
        create("<dark_gray>Mining <dark_gray>» <gold>your level", 6);
    }

    @Override
    protected void build(Player viewer) {
        inventory.clear();
        MineLevels levels = plugin.getMineLevels();
        MineLevels.Profile prof = levels.profile(subject);
        var owner = Bukkit.getOfflinePlayer(subject);
        String name = owner.getName() == null ? "You" : owner.getName();

        // ---- row 1: you
        List<String> me = new ArrayList<>();
        me.add("<gray>Level <white>" + prof.level + "<dark_gray>/" + levels.maxLevel());
        me.add(levels.bar(levels.progress(prof)) + " <dark_gray>" + (long) prof.xp + "/" + levels.xpForNext(prof.level));
        String title = levels.titleFor(prof.level);
        if (title != null) me.add("<gray>Title: <white>" + title);
        if (prof.prestige > 0) me.add("<gradient:#e08cff:#7de2ff>" + levels.stars(prof) + "</gradient> <gray>prestige " + prof.prestige);
        me.add("");
        me.add("<gray>Blocks mined: <white>" + prof.blocks);
        int place = levels.placeOf(subject);
        if (place > 0) me.add("<gray>Server rank: <white>#" + place);
        inventory.setItem(ME, head(owner, "<gradient:#ffd166:#ff8c00>" + name + "</gradient>", me));

        // ---- row 2: what's coming
        int every = levels.milestoneEvery();
        if (every > 0) {
            // start from the last milestone passed, so there's always one
            // green tile on the left to show the shape of it
            int start = Math.max(every, (prof.level / every) * every);
            int lvl = start;
            for (int slot : MILESTONES) {
                if (lvl > levels.maxLevel()) break;
                boolean reached = prof.level >= lvl;
                List<String> lore = new ArrayList<>();
                lore.add(reached ? "<green>Reached." : "<gray>Reach level <white>" + lvl + "<gray>.");
                List<String> rewards = levels.rewardBlurb(lvl);
                if (!rewards.isEmpty()) { lore.add(""); lore.add("<gray>Pays:"); lore.addAll(rewards); }
                inventory.setItem(slot, item(reached ? Material.LIME_STAINED_GLASS : Material.GRAY_STAINED_GLASS,
                        (reached ? "<green>" : "<white>") + "Level " + lvl, lore));
                lvl += every;
            }
        }

        // ---- row 3: where to go
        inventory.setItem(TOP, item(Material.GOLD_INGOT, "<gold>Leaderboard",
                List.of("<gray>Who's put the most hours in.", "", "<yellow>Click <gray>to open")));

        boolean canPrestige = prof.level >= levels.maxLevel();
        inventory.setItem(PRESTIGE, item(canPrestige ? Material.NETHER_STAR : Material.GRAY_DYE,
                (canPrestige ? "<gradient:#e08cff:#7de2ff>Prestige</gradient>" : "<dark_gray>Prestige"),
                List.of("<gray>At level " + levels.maxLevel() + ", go round again:",
                        "<gray>back to 1, a permanent <white>★<gray>, a faster climb.",
                        "", canPrestige ? "<yellow>Click <gray>to prestige" : "<dark_gray>Not yet.")));

        int daily = levels.dailyLeft(prof);
        inventory.setItem(DAILY, item(daily > 0 ? Material.SUNFLOWER : Material.GRAY_DYE,
                daily > 0 ? "<gold>Daily bonus" : "<dark_gray>Daily bonus",
                List.of(daily > 0 ? "<gray>Your next <white>" + daily + "<gray> blocks pay extra." : "<gray>Used up for today.",
                        "<dark_gray>Resets at midnight.")));

        fill(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Override
    public void onClick(Player p, InventoryClickEvent e) {
        switch (e.getSlot()) {
            case TOP -> new LevelTopGui(plugin).open(p);
            case PRESTIGE -> { p.closeInventory(); p.performCommand("level prestige"); }
            default -> { }
        }
    }
}
