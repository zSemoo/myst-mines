package me.catalysmrl.catamines.api.rewards;

import org.bukkit.entity.Entity;

import java.util.Collection;
import java.util.Map;

public class CompiledTargeter {

    private final Targeter targeter;
    private final Map<String, String> args;

    public CompiledTargeter(Targeter targeter, Map<String, String> args) {
        this.targeter = targeter;
        this.args = args;
    }

    public Collection<Entity> getTargets(RewardContext context) {
        return targeter.getTargets(context, args);
    }

    public Targeter getTargeter() {
        return targeter;
    }
}
