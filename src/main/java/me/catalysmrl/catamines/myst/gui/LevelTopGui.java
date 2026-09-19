package me.catalysmrl.catamines.myst.gui;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.myst.level.MineLevels;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * /level top — the mining leaderboard.
 *
 * The level is in the NAME of every tile, not buried in the lore, so a
 * glance across the row reads as a ranking. Podium for the first three,
 * ranks 4–17 below, your own placing pinned at the bottom.
 */
public class LevelTopGui extends MystGui {

    private static final int[] PODIUM = {12, 13, 14};
    private static final int[] REST = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int ME = 49, BACK = 45;

    public LevelTopGui(CataMines plugin) {
        super(plugin);
        create("<dark_gray>Mining <dark_gray>» <gold>best of", 6);
    }

    private String label(MineLevels levels, MineLevels.Profile prof, String prefix) {
        return prefix + " <white>" + prof.name + " <dark_gray>lv" + prof.level
                + (prof.prestige > 0 ? " <gradient:#e08cff:#7de2ff>" + levels.stars(prof) + "</gradient>" : "");
    }

    @Override
    protected void build(Player viewer) {
        inventory.clear();
        MineLevels levels = plugin.getMineLevels();
        List<MineLevels.Profile> top = levels.top(PODIUM.length + REST.length);

        if (top.isEmpty())
            inventory.setItem(22, item(Material.BARRIER, "<gray>Nobody yet", List.of("<gray>Mine something.")));

        String[] place = {"<#ffd166>1st", "<#d0d0d0>2nd", "<#cd7f32>3rd"};
        for (int i = 0; i < PODIUM.length && i < top.size(); i++) {
            MineLevels.Profile prof = top.get(i);
            inventory.setItem(PODIUM[i], head(levels.offline(prof), label(levels, prof, place[i]),
                    List.of(levels.bar(levels.progress(prof)),
                            "<gray>Blocks mined: <white>" + prof.blocks)));
        }
        for (int i = PODIUM.length; i < top.size() && i - PODIUM.length < REST.length; i++) {
            MineLevels.Profile prof = top.get(i);
            inventory.setItem(REST[i - PODIUM.length], head(levels.offline(prof), label(levels, prof, "<gray>#" + (i + 1)),
                    List.of("<gray>Blocks mined: <white>" + prof.blocks)));
        }

        MineLevels.Profile mine = levels.profile(viewer);
        int myPlace = levels.placeOf(viewer.getUniqueId());
        List<String> lore = new ArrayList<>();
        lore.add(myPlace > 0 ? "<gray>You're <white>#" + myPlace : "<gray>Not on the board yet.");
        lore.add(levels.bar(levels.progress(mine)));
        inventory.setItem(ME, head(viewer, label(levels, mine, "<gradient:#ffd166:#ff8c00>You</gradient>"), lore));

        inventory.setItem(BACK, item(Material.ARROW, "<gray>Back", List.of()));
        fill(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Override
    public void onClick(Player p, InventoryClickEvent e) {
        if (e.getSlot() == BACK) new LevelGui(plugin, p.getUniqueId()).open(p);
    }
}
