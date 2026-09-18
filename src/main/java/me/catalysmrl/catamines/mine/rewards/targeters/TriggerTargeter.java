package me.catalysmrl.catamines.mine.rewards.targeters;

import me.catalysmrl.catamines.api.rewards.RewardContext;
import me.catalysmrl.catamines.api.rewards.Targeter;
import org.bukkit.entity.Entity;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class TriggerTargeter implements Targeter {
    @Override
    public Collection<Entity> getTargets(RewardContext context, Map<String, String> args) {
        if (context.getPlayer() != null) {
            return Collections.singletonList(context.getPlayer());
        }
        return Collections.emptyList();
    }
}
