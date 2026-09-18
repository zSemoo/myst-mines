package me.catalysmrl.catamines.mine.rewards.actions;

import me.catalysmrl.catamines.api.rewards.Action;
import me.catalysmrl.catamines.api.rewards.RewardContext;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;

public class ActionBarAction implements Action {
    @Override
    public void execute(RewardContext context, Entity target, Map<String, String> args) {
        if (!(target instanceof Player)) return;
        String m = args.get("m");
        if (m != null) {
            String triggerName = context.getPlayer() != null ? context.getPlayer().getName() : "Console";
            m = m.replace("<trigger.name>", triggerName);
            m = ChatColor.translateAlternateColorCodes('&', m);
            ((Player) target).spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(m));
        }
    }
}
