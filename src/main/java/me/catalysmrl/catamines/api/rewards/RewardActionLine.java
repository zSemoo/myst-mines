package me.catalysmrl.catamines.api.rewards;

import org.bukkit.entity.Entity;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RewardActionLine {

    private final Action action;
    private final Map<String, String> actionArgs;

    private final CompiledTargeter targeter;
    private final List<CompiledCondition> targetConditions;
    private final String raw;

    public RewardActionLine(Action action, Map<String, String> actionArgs, CompiledTargeter targeter, List<CompiledCondition> targetConditions, String raw) {
        this.action = action;
        this.actionArgs = actionArgs;
        this.targeter = targeter;
        this.targetConditions = targetConditions;
        this.raw = raw;
    }

    public String getRaw() {
        return raw;
    }

    public Action getAction() {
        return action;
    }

    public CompiledTargeter getTargeter() {
        return targeter;
    }

    public List<CompiledCondition> getConditions() {
        return targetConditions;
    }

    public void execute(RewardContext context) {
        Collection<Entity> targets = targeter.getTargets(context);

        if (targets == null || targets.isEmpty()) {
            return;
        }

        for (Entity target : targets) {
            boolean passes = true;
            for (CompiledCondition cond : targetConditions) {
                if (!cond.evaluate(context, target)) {
                    passes = false;
                    break;
                }
            }
            if (passes) {
                action.execute(context, target, actionArgs);
            }
        }
    }
}
