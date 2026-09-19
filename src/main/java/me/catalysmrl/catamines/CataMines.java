package me.catalysmrl.catamines;

import me.catalysmrl.catamines.command.CommandManager;
import me.catalysmrl.catamines.listeners.BlockListeners;
import me.catalysmrl.catamines.managers.MineManager;
import me.catalysmrl.catamines.utils.helper.CompatibilityProvider;
import me.catalysmrl.catamines.utils.message.LocaleBootstrap;
import me.catalysmrl.catamines.utils.placeholders.CataMinePlaceHolders;
import me.catalysmrl.catamines.api.rewards.RewardManager;
import me.catalysmrl.catamines.api.rewards.parser.RewardParser;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CataMines extends JavaPlugin {

    private static CataMines INSTANCE;

    public static CataMines getInstance() {
        return INSTANCE;
    }

    private MineManager mineManager;
    private me.catalysmrl.catamines.myst.level.MineLevels mineLevels;
    private me.catalysmrl.catamines.myst.event.MineEvents mineEvents;
    private me.catalysmrl.catamines.myst.board.Contributions contributions;
    private me.catalysmrl.catamines.myst.dig.LastBlock lastBlock;
    private me.catalysmrl.catamines.myst.challenge.MineChallenges mineChallenges;
    private me.catalysmrl.catamines.myst.pick.PickaxeSouls pickaxeSouls;
    private CommandManager commandManager;
    private RewardManager rewardManager;
    private RewardParser rewardParser;

    @Override
    public void onLoad() {
        INSTANCE = this;

        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        CompatibilityProvider.checkCompatibility();

        new LocaleBootstrap(this).init();

        mineManager = new MineManager(this);
        // --- MystCity additions: levelling and mine events
        mineEvents = new me.catalysmrl.catamines.myst.event.MineEvents(this);
        mineLevels = new me.catalysmrl.catamines.myst.level.MineLevels(this);
        contributions = new me.catalysmrl.catamines.myst.board.Contributions(this);
        lastBlock = new me.catalysmrl.catamines.myst.dig.LastBlock(this);
        mineChallenges = new me.catalysmrl.catamines.myst.challenge.MineChallenges(this);
        pickaxeSouls = new me.catalysmrl.catamines.myst.pick.PickaxeSouls(this);
        
        // Setup Reward Engine
        rewardManager = new RewardManager();
        rewardParser = new RewardParser(rewardManager);
        
        // Register default Handlers
        rewardManager.registerAction("actionbar", new me.catalysmrl.catamines.mine.rewards.actions.ActionBarAction());
        rewardManager.registerAction("command", new me.catalysmrl.catamines.mine.rewards.actions.CommandAction());
        rewardManager.registerAction("giveitem", new me.catalysmrl.catamines.mine.rewards.actions.GiveItemAction());
        
        rewardManager.registerTargeter("trigger", new me.catalysmrl.catamines.mine.rewards.targeters.TriggerTargeter());
        rewardManager.registerTargeter("playersinradius", new me.catalysmrl.catamines.mine.rewards.targeters.PlayersInRadiusTargeter());
        
        rewardManager.registerCondition("haspermission", new me.catalysmrl.catamines.mine.rewards.conditions.PermissionCondition());

        registerCommands();
        registerListeners();

        if (CompatibilityProvider.isPapiEnabled()) {
            new CataMinePlaceHolders(this).register();
        }

        setupMetrics();
    }

    @Override
    public void onDisable() {
        if (mineLevels != null) mineLevels.save();
        if (contributions != null) contributions.save();
        if (mineChallenges != null) mineChallenges.save();
        INSTANCE = null;
        commandManager = null;

        // Properly disable MineManager
        mineManager.shutDown();
        mineManager = null;
    }

    private void setupMetrics() {
        final Metrics metrics = new Metrics(this, 12889);
        metrics.addCustomChart(new SimplePie("we_implementation",
                () -> CompatibilityProvider.isFaweEnabled() ? "FastAsyncWorldEdit" : "WorldEdit"));

        metrics.addCustomChart(new SingleLineChart("mines", () -> mineManager.getMines().size()));
    }

    private void registerCommands() {
        commandManager = new CommandManager(this);

        // MystCity commands
        PluginCommand levelCommand = getCommand("level");
        if (levelCommand != null) {
            var exec = new me.catalysmrl.catamines.myst.level.LevelCommand(this);
            levelCommand.setExecutor(exec);
            levelCommand.setTabCompleter(exec);
        }
        PluginCommand pickCommand = getCommand("pick");
        if (pickCommand != null) {
            var exec = new me.catalysmrl.catamines.myst.pick.PickCommand(this);
            pickCommand.setExecutor(exec);
            pickCommand.setTabCompleter(exec);
        }
        PluginCommand mineCommand = getCommand("mine");
        if (mineCommand != null) {
            var exec = new me.catalysmrl.catamines.myst.event.MineEventCommand(this);
            mineCommand.setExecutor(exec);
            mineCommand.setTabCompleter(exec);
        }

        PluginCommand command = getCommand("catamines");
        if (command == null) {
            getLogger().severe("***************************************");
            getLogger().severe("Could not register commands. All plugin");
            getLogger().severe("functions may still work apart from commands");
            getLogger().severe("***************************************");
            return;
        }

        command.setExecutor(commandManager);
    }

    private void registerListeners() {
        getLogger().info("Registering listeners");
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new BlockListeners(mineManager), this);
        pm.registerEvents(mineLevels, this);
        pm.registerEvents(new me.catalysmrl.catamines.myst.level.MineGateListener(this), this);
        pm.registerEvents(new me.catalysmrl.catamines.myst.dig.DigListener(this), this);
        getServer().getScheduler().runTaskTimer(this, () -> {
            contributions.save();
            mineChallenges.save();
        }, 1200L, 1200L);
        pm.registerEvents(new me.catalysmrl.catamines.myst.gui.MystGui.Clicks(), this);
        pm.registerEvents(new me.catalysmrl.catamines.myst.event.MeteorListener(this), this);
        getServer().getScheduler().runTaskTimer(this, () -> mineEvents.tick(), 20L, 20L);
        getServer().getScheduler().runTaskTimer(this,
                () -> me.catalysmrl.catamines.myst.gui.MineEditGui.tickCountdowns(this), 40L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> mineLevels.save(), 600L, 1200L);
        pm.registerEvents(new me.catalysmrl.catamines.mine.rewards.listeners.RewardListener(this), this);
    }

    public me.catalysmrl.catamines.myst.level.MineLevels getMineLevels() { return mineLevels; }

    public me.catalysmrl.catamines.myst.event.MineEvents getMineEvents() { return mineEvents; }

    public me.catalysmrl.catamines.myst.board.Contributions getContributions() { return contributions; }

    public me.catalysmrl.catamines.myst.dig.LastBlock getLastBlock() { return lastBlock; }

    public me.catalysmrl.catamines.myst.challenge.MineChallenges getMineChallenges() { return mineChallenges; }

    public me.catalysmrl.catamines.myst.pick.PickaxeSouls getPickaxeSouls() { return pickaxeSouls; }

    public MineManager getMineManager() {
        return mineManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public RewardParser getRewardParser() {
        return rewardParser;
    }
}
