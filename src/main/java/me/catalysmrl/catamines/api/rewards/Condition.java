package me.catalysmrl.catamines.api.rewards;

import org.bukkit.entity.Entity;

import java.util.Map;

public interface Condition {

    /**
     * Evaluates whether this condition is met.
     *
     * @param context the execution context
     * @param target the specific target being evaluated (may be null if evaluating base trigger condition)
     * @param args the parsed arguments for this condition
     * @return true if met, false otherwise
     */
    boolean evaluate(RewardContext context, Entity target, Map<String, String> args);

}
