package me.catalysmrl.catamines.mine.rewards.targeters;

import me.catalysmrl.catamines.api.rewards.RewardContext;
import me.catalysmrl.catamines.api.rewards.Targeter;
import org.bukkit.entity.Entity;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class PlayersInRadiusTargeter implements Targeter {
    @Override
    public Collection<Entity> getTargets(RewardContext context, Map<String, String> args) {
        if (context.getLocation() == null) return Collections.emptyList();

        double radius = 10.0;
        try {
            if (args.containsKey("r")) {
                radius = Double.parseDouble(args.get("r"));
            }
        } catch (NumberFormatException ignored) {}

        double finalRadius = radius;
        return context.getLocation().getWorld().getNearbyEntities(context.getLocation(), finalRadius, finalRadius, finalRadius)
                .stream().filter(e -> e instanceof org.bukkit.entity.Player).collect(Collectors.toList());
    }
}
