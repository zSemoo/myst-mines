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
 * /level — where you are and what's next.
 *
 * The head in the middle is you, with the bar and the numbers. Below it, the
 * upcoming milestone levels and what each one pays, read straight out of
 * levelling.yml so the menu can never disagree with the rewards.
 */
public class LevelGui extends MystGui {

    private final UUID subject;
    private static final int ME = 13, TOP = 26, PRESTIGE = 18, NEXT_ROW_START = 28;

    public LevelGui(CataMines plugin, UUID subject) {
        super(plugin);
        this.subject = subject;
        create("<dark_gray>Mining <dark_gray>» <gold>your level", 5);
    }

    @Override
    protected void build(Player viewer) {
        inventory.clear();
        MineLevels levels = plugin.getMineLevels();
        MineLevels.Profile prof = levels.profile(subject);
        var owner = Bukkit.getOfflinePlayer(subject);

        long need = levels.xpForNext(prof.level);
        int place = levels.placeOf(subject);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Every block you break in a mine counts.");
        lore.add("");
        String stars = levels.stars(prof);
        if (!stars.isEmpty()) lore.add("<gradient:#e08cff:#7de2ff>" + stars + "</gradient> <gray>prestige " + prof.prestige);
        String title = levels.titleFor(prof.level);
        if (title != null) lore.add("<gray>Title: <white>" + title);
        lore.add("<gray>Level <white>" + prof.level + "<dark_gray>/" + levels.maxLevel());
        lore.add(levels.bar(levels.progress(prof)));
        lore.add("<gray>XP: <white>" + (long) prof.xp + "<dark_gray>/" + need);
        lore.add("<gray>Blocks mined: <white>" + prof.blocks);
        if (place > 0) lore.add("<gray>Placed: <white>#" + place);
        int daily = levels.dailyLeft(prof);
        lore.add(daily > 0 ? "<gold>Daily bonus: <white>" + daily + " <gray>blocks left" : "<dark_gray>Daily bonus used up.");
        inventory.setItem(ME, head(owner,
                "<gradient:#ffd166:#ff8c00>" + (owner.getName() == null ? "You" : owner.getName()) + "</gradient>", lore));

        inventory.setItem(TOP, item(Material.GOLD_INGOT, "<gold>Leaderboard",
                List.of("<gray>Who's put the most hours in.", "", "<yellow>Click <gray>to see it")));
        boolean canPrestige = prof.level >= levels.maxLevel();
        inventory.setItem(PRESTIGE, item(canPrestige ? Material.NETHER_STAR : Material.GRAY_DYE,
                (canPrestige ? "<gradient:#e08cff:#7de2ff>" : "<dark_gray>") + "Prestige" + (canPrestige ? "</gradient>" : ""),
                List.of("<gray>At level " + levels.maxLevel() + ", go round again:",
                        "<gray>back to 1, a permanent <white>★<gray>, a faster climb.",
                        "", canPrestige ? "<yellow>Click <gray>to prestige" : "<dark_gray>Not yet.")));

        int every = levels.milestoneEvery();
        int shown = 0;
        int next = every <= 0 ? -1 : ((prof.level / every) + 1) * every;
        for (int slot = NEXT_ROW_START; shown < 5 && next > 0 && next <= levels.maxLevel(); slot++, next += every) {
            List<String> ml = new ArrayList<>();
            ml.add("<gray>Reach level <white>" + next + "<gray>.");
            List<String> rewards = levels.rewardBlurb(next);
            if (!rewards.isEmpty()) {
                ml.add("");
                ml.add("<gray>Pays:");
                ml.addAll(rewards);
            }
            boolean reached = prof.level >= next;
            inventory.setItem(slot, item(reached ? Material.LIME_DYE : Material.GRAY_DYE,
                    (reached ? "<green>" : "<white>") + "Level " + next + (reached ? " <dark_gray>(done)" : ""), ml));
            shown++;
        }

        fill(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Override
    public void onClick(Player p, InventoryClickEvent e) {
        if (e.getSlot() == TOP) new LevelTopGui(plugin).open(p);
        if (e.getSlot() == PRESTIGE) {
            p.closeInventory();
            p.performCommand("level prestige");
        }
    }
}
