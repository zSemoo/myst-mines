package me.catalysmrl.catamines.mine.rewards.conditions;

import me.catalysmrl.catamines.api.rewards.Condition;
import me.catalysmrl.catamines.api.rewards.RewardContext;
import org.bukkit.entity.Entity;

import java.util.Map;

public class PermissionCondition implements Condition {
    @Override
    public boolean evaluate(RewardContext context, Entity target, Map<String, String> args) {
        if (target == null) return false;
        String p = args.get("p");
        return p != null && target.hasPermission(p);
    }
}
