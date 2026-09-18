package me.catalysmrl.catamines.api.rewards;

import java.util.HashMap;
import java.util.Map;

public class RewardManager {

    private final Map<String, Action> actionRegistry = new HashMap<>();
    private final Map<String, Condition> conditionRegistry = new HashMap<>();
    private final Map<String, Targeter> targeterRegistry = new HashMap<>();

    public void registerAction(String id, Action action) {
        actionRegistry.put(id.toLowerCase(), action);
    }

    public Action getAction(String id) {
        return actionRegistry.get(id.toLowerCase());
    }

    public void registerCondition(String id, Condition condition) {
        conditionRegistry.put(id.toLowerCase(), condition);
    }

    public Condition getCondition(String id) {
        return conditionRegistry.get(id.toLowerCase());
    }

    public void registerTargeter(String id, Targeter targeter) {
        targeterRegistry.put(id.toLowerCase(), targeter);
    }

    public Targeter getTargeter(String id) {
        return targeterRegistry.get(id.toLowerCase());
    }

}
