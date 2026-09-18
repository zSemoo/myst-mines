package me.catalysmrl.catamines.api.rewards;

import org.bukkit.entity.Entity;

import java.util.Map;

public interface Action {

    /**
     * Executes the action on the given target.
     *
     * @param context the execution context
     * @param target the specific target to execute upon
     * @param args the parsed arguments for this action
     */
    void execute(RewardContext context, Entity target, Map<String, String> args);

}
