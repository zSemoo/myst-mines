package me.catalysmrl.catamines.api.rewards;

import org.bukkit.entity.Entity;

import java.util.Collection;
import java.util.Map;

public interface Targeter {

    /**
     * Gets a collection of entities that this targeter resolved
     * from the given context and arguments.
     *
     * @param context the execution context
     * @param args the parsed arguments for the targeter
     * @return a collection of entity targets
     */
    Collection<Entity> getTargets(RewardContext context, Map<String, String> args);

}
