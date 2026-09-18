package me.catalysmrl.catamines.api.rewards.parser;

import me.catalysmrl.catamines.api.rewards.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RewardParser {

    private final RewardManager manager;

    public RewardParser(RewardManager manager) {
        this.manager = manager;
    }

    public static class ParsedComponent {
        public final String id;
        public final Map<String, String> args;

        public ParsedComponent(String id, Map<String, String> args) {
            this.id = id;
            this.args = args;
        }
    }

    public static ParsedComponent parseComponent(String input) {
        input = input.trim();
        int bracketIndex = input.indexOf('{');
        if (bracketIndex == -1) {
            return new ParsedComponent(input, new HashMap<>());
        }

        String id = input.substring(0, bracketIndex);
        String argsString = input.substring(bracketIndex + 1);
        if (argsString.endsWith("}")) {
            argsString = argsString.substring(0, argsString.length() - 1);
        }

        Map<String, String> args = new HashMap<>();
        boolean inQuotes = false;
        StringBuilder currentKey = new StringBuilder();
        StringBuilder currentValue = new StringBuilder();
        boolean parsingKey = true;

        for (int i = 0; i < argsString.length(); i++) {
            char c = argsString.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (!inQuotes) {
                if (c == '=') {
                    parsingKey = false;
                    continue;
                } else if (c == ',') {
                    args.put(currentKey.toString().trim(), currentValue.toString().trim());
                    currentKey.setLength(0);
                    currentValue.setLength(0);
                    parsingKey = true;
                    continue;
                }
            }

            if (parsingKey) {
                currentKey.append(c);
            } else {
                currentValue.append(c);
            }
        }

        if (currentKey.length() > 0) {
            args.put(currentKey.toString().trim(), currentValue.toString().trim());
        }

        return new ParsedComponent(id, args);
    }

    public static List<String> splitActionLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            }
            if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    public RewardActionLine parseActionLine(String input) throws IllegalArgumentException {
        List<String> parts = splitActionLine(input);
        if (parts.isEmpty()) throw new IllegalArgumentException("Empty action line");

        ParsedComponent actionComp = parseComponent(parts.get(0));
        Action action = manager.getAction(actionComp.id);
        if (action == null) throw new IllegalArgumentException("Unknown action: " + actionComp.id);

        Targeter baseTriggerTargeter = manager.getTargeter("trigger");
        CompiledTargeter targeter = null;
        if (baseTriggerTargeter != null) {
            targeter = new CompiledTargeter(baseTriggerTargeter, new HashMap<>());
        }
        
        List<CompiledCondition> targetConditions = new ArrayList<>();

        for (int i = 1; i < parts.size(); i++) {
            String part = parts.get(i);
            if (part.startsWith("@")) {
                ParsedComponent targetComp = parseComponent(part.substring(1));
                Targeter t = manager.getTargeter(targetComp.id);
                if (t == null) throw new IllegalArgumentException("Unknown targeter: " + targetComp.id);
                targeter = new CompiledTargeter(t, targetComp.args);
            } else if (part.startsWith("~")) {
                boolean inverted = false;
                String condStr = part.substring(1);
                if (condStr.startsWith("!")) {
                    inverted = true;
                    condStr = condStr.substring(1);
                }
                ParsedComponent condComp = parseComponent(condStr);
                Condition c = manager.getCondition(condComp.id);
                if (c == null) throw new IllegalArgumentException("Unknown target condition: " + condComp.id);
                targetConditions.add(new CompiledCondition(c, condComp.args, inverted, condStr));
            }
        }

        if (targeter == null) {
            throw new IllegalArgumentException("No targeter specified and no 'trigger' default targeter registered");
        }

        return new RewardActionLine(action, actionComp.args, targeter, targetConditions, input);
    }

    public CompiledCondition parseCondition(String input) throws IllegalArgumentException {
        boolean inverted = false;
        String baseInput = input;
        if (baseInput.startsWith("!")) {
            inverted = true;
            baseInput = baseInput.substring(1);
        }

        ParsedComponent comp = parseComponent(baseInput);
        Condition c = manager.getCondition(comp.id);
        if (c == null) throw new IllegalArgumentException("Unknown condition: " + comp.id);

        return new CompiledCondition(c, comp.args, inverted, input);
    }
}
