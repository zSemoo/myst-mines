package me.catalysmrl.catamines.api.rewards;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Reward {

    private final String id;
    private final String trigger;
    private final String group;
    private final Set<String> overrides;

    private final List<CompiledCondition> conditions;
    private final List<RewardActionLine> actions;

    public Reward(String id, String trigger, String group, Set<String> overrides, List<CompiledCondition> conditions, List<RewardActionLine> actions) {
        this.id = id;
        this.trigger = trigger;
        this.group = group;
        this.overrides = overrides == null ? new HashSet<>() : overrides;
        this.conditions = conditions;
        this.actions = actions;
    }

    public String getId() {
        return id;
    }

    public String getTrigger() {
        return trigger;
    }

    public String getGroup() {
        return group;
    }

    public Set<String> getOverrides() {
        return overrides;
    }

    public boolean canTrigger(RewardContext context) {
        if (!context.getTrigger().equalsIgnoreCase(this.trigger)) {
            return false;
        }
        for (CompiledCondition condition : conditions) {
            // Evaluated on the player triggering the event
            if (!condition.evaluate(context, context.getPlayer())) {
                return false;
            }
        }
        return true;
    }

    public void execute(RewardContext context) {
        for (RewardActionLine action : actions) {
            action.execute(context);
        }
    }

    public void serialize(org.bukkit.configuration.ConfigurationSection section) {
        section.set("trigger", trigger);
        if (group != null && !group.isEmpty()) section.set("group", group);
        if (!overrides.isEmpty()) section.set("overrides", new java.util.ArrayList<>(overrides));
        
        if (!conditions.isEmpty()) {
            java.util.List<String> condStrings = new java.util.ArrayList<>();
            for (CompiledCondition c : conditions) condStrings.add(c.getRaw());
            section.set("conditions", condStrings);
        }
        
        if (!actions.isEmpty()) {
            java.util.List<String> actionStrings = new java.util.ArrayList<>();
            for (RewardActionLine a : actions) actionStrings.add(a.getRaw());
            section.set("actions", actionStrings);
        }
    }

    public static Reward deserialize(String id, org.bukkit.configuration.ConfigurationSection section) {
        String trigger = section.getString("trigger");
        String group = section.getString("group");
        Set<String> overrides = new HashSet<>(section.getStringList("overrides"));
        
        List<CompiledCondition> conditions = new java.util.ArrayList<>();
        me.catalysmrl.catamines.api.rewards.parser.RewardParser parser = me.catalysmrl.catamines.CataMines.getInstance().getRewardParser();
        
        if (section.contains("conditions")) {
            for (String condStr : section.getStringList("conditions")) {
                try {
                    conditions.add(parser.parseCondition(condStr));
                } catch (Exception e) {
                    me.catalysmrl.catamines.CataMines.getInstance().getLogger().warning("Failed to parse condition: " + condStr + " - " + e.getMessage());
                }
            }
        }
        
        List<RewardActionLine> actions = new java.util.ArrayList<>();
        if (section.contains("actions")) {
            for (String actStr : section.getStringList("actions")) {
                try {
                    actions.add(parser.parseActionLine(actStr));
                } catch (Exception e) {
                    me.catalysmrl.catamines.CataMines.getInstance().getLogger().warning("Failed to parse action: " + actStr + " - " + e.getMessage());
                }
            }
        }
        
        return new Reward(id, trigger, group, overrides, conditions, actions);
    }
}
