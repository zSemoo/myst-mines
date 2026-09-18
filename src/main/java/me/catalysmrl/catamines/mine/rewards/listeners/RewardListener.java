package me.catalysmrl.catamines.mine.rewards.listeners;

import com.sk89q.worldedit.world.block.BaseBlock;
import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.api.rewards.Reward;
import me.catalysmrl.catamines.api.rewards.RewardContext;
import me.catalysmrl.catamines.mine.components.composition.CataMineBlock;
import me.catalysmrl.catamines.mine.components.composition.CataMineComposition;
import me.catalysmrl.catamines.mine.components.region.CataMineRegion;
import me.catalysmrl.catamines.utils.worldedit.BaseBlockParser;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.*;

public class RewardListener implements Listener {

    private final CataMines plugin;

    public RewardListener(CataMines plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Optional<CataMine> mineOpt = plugin.getMineManager().getMineAtLocation(event.getBlock().getLocation());
        if (!mineOpt.isPresent()) return;

        CataMine mine = mineOpt.get();
        Optional<CataMineRegion> regionOpt = plugin.getMineManager().getRegionAtLocation(mine, event.getBlock().getLocation());
        if (!regionOpt.isPresent()) return;

        CataMineRegion region = regionOpt.get();
        Optional<CataMineComposition> compOpt = region.getCompositionManager().getCurrent();
        if (!compOpt.isPresent()) return;

        CataMineComposition composition = compOpt.get();

        // Find matching block type
        CataMineBlock matchedBlock = null;
        try {
            BaseBlock baseBlockBroken = BaseBlockParser.parse(event.getBlock().getType().name());
            for (CataMineBlock mb : composition.getBlocks()) {
                if (mb.getBaseBlock() != null && mb.getBaseBlock().equals(baseBlockBroken)) {
                    matchedBlock = mb;
                    break;
                }
            }
        } catch (Exception ignored) {}

        // 1. Gather & Override system
        RewardContext context = new RewardContext("BLOCK_BREAK")
                .setPlayer(event.getPlayer())
                .setLocation(event.getBlock().getLocation())
                .setMine(mine)
                .setRegion(region)
                .setComposition(composition)
                .setBlock(matchedBlock);

        // Collect all rewards from the 4 scopes
        List<Reward> collected = new ArrayList<>();
        if (matchedBlock != null) collected.addAll(matchedBlock.getRewards());
        collected.addAll(composition.getRewards());
        collected.addAll(region.getRewards());
        collected.addAll(mine.getRewards());

        Set<String> suppressedGroups = new HashSet<>();

        // 2. Evaluate
        for (Reward reward : collected) {
            // Check if group is suppressed by a smaller scoped reward
            if (reward.getGroup() != null && suppressedGroups.contains(reward.getGroup())) {
                continue;
            }

            if (reward.canTrigger(context)) {
                suppressedGroups.addAll(reward.getOverrides());
                reward.execute(context);
            }
        }
    }
}
