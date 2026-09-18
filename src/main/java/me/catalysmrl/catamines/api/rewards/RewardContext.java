package me.catalysmrl.catamines.api.rewards;

import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.mine.components.composition.CataMineBlock;
import me.catalysmrl.catamines.mine.components.composition.CataMineComposition;
import me.catalysmrl.catamines.mine.components.region.CataMineRegion;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class RewardContext {

    private final String trigger;
    private Player player;
    private Location location;
    private CataMine mine;
    private CataMineRegion region;
    private CataMineComposition composition;
    private CataMineBlock block;

    private final Map<String, Object> variables = new HashMap<>();

    public RewardContext(String trigger) {
        this.trigger = trigger;
    }

    public String getTrigger() {
        return trigger;
    }

    public Player getPlayer() {
        return player;
    }

    public RewardContext setPlayer(Player player) {
        this.player = player;
        return this;
    }

    public Location getLocation() {
        return location;
    }

    public RewardContext setLocation(Location location) {
        this.location = location;
        return this;
    }

    public CataMine getMine() {
        return mine;
    }

    public RewardContext setMine(CataMine mine) {
        this.mine = mine;
        return this;
    }

    public CataMineRegion getRegion() {
        return region;
    }

    public RewardContext setRegion(CataMineRegion region) {
        this.region = region;
        return this;
    }

    public CataMineComposition getComposition() {
        return composition;
    }

    public RewardContext setComposition(CataMineComposition composition) {
        this.composition = composition;
        return this;
    }

    public CataMineBlock getBlock() {
        return block;
    }

    public RewardContext setBlock(CataMineBlock block) {
        this.block = block;
        return this;
    }

    public Object getVariable(String key) {
        return variables.get(key);
    }

    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }
}
