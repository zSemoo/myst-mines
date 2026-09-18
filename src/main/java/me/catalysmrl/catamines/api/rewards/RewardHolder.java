package me.catalysmrl.catamines.api.rewards;

import java.util.List;

public interface RewardHolder {
    
    /**
     * Gets all rewards registered for this holder.
     *
     * @return list of rewards
     */
    List<Reward> getRewards();

    /**
     * Adds a reward to this holder.
     *
     * @param reward the reward to add
     */
    void addReward(Reward reward);

    /**
     * Removes a reward from this holder.
     *
     * @param reward the reward to remove
     */
    void removeReward(Reward reward);

}
