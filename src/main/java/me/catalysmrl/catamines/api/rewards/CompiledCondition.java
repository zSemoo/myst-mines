package me.catalysmrl.catamines.api.rewards;

import org.bukkit.entity.Entity;

import java.util.Map;

public class CompiledCondition {

    private final Condition condition;
    private final Map<String, String> args;
    private final boolean inverted;
    private final String raw;

    public CompiledCondition(Condition condition, Map<String, String> args, boolean inverted, String raw) {
        this.condition = condition;
        this.args = args;
        this.inverted = inverted;
        this.raw = raw;
    }

    public String getRaw() {
        return raw;
    }

    public boolean evaluate(RewardContext context, Entity target) {
        boolean result = condition.evaluate(context, target, args);
        return inverted ? !result : result;
    }
}
