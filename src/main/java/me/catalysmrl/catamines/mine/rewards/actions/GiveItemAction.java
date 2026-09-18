package me.catalysmrl.catamines.mine.rewards.actions;

import me.catalysmrl.catamines.api.rewards.Action;
import me.catalysmrl.catamines.api.rewards.RewardContext;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class GiveItemAction implements Action {
    @Override
    public void execute(RewardContext context, Entity target, Map<String, String> args) {
        if (!(target instanceof Player)) return;
        Player player = (Player) target;
        String matStr = args.get("i");
        if (matStr != null) {
            try {
                Material mat = Material.valueOf(matStr.toUpperCase());
                int amount = 1;
                if (args.containsKey("a")) amount = Integer.parseInt(args.get("a"));
                player.getInventory().addItem(new ItemStack(mat, amount));
            } catch (Exception ignored) {}
        }
    }
}
