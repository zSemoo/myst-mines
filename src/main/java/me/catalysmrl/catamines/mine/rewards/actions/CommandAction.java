package me.catalysmrl.catamines.mine.rewards.actions;

import me.catalysmrl.catamines.api.rewards.Action;
import me.catalysmrl.catamines.api.rewards.RewardContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;

public class CommandAction implements Action {
    @Override
    public void execute(RewardContext context, Entity target, Map<String, String> args) {
        String cmd = args.get("c");
        if (cmd != null && target instanceof Player) {
            cmd = cmd.replace("<target>", target.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }
}
