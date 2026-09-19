package me.catalysmrl.catamines.managers;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.api.serialization.DeserializationException;
import me.catalysmrl.catamines.managers.blockmanagers.BlockApplicator;
import me.catalysmrl.catamines.managers.blockmanagers.BukkitBlockApplicationManager;
import me.catalysmrl.catamines.managers.blockmanagers.FastAsyncBlockApplicationManager;
import me.catalysmrl.catamines.mine.components.region.CataMineRegion;
import me.catalysmrl.catamines.mine.mines.AdvancedCataMine;
import me.catalysmrl.catamines.utils.helper.CompatibilityProvider;
import me.catalysmrl.catamines.utils.message.Message;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import me.catalysmrl.catamines.api.events.CataMineBlockBreakEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MineManager {

    private final Path minesPath;
    private final Path schematicsPath;

    private final CataMines plugin;
    private BukkitTask minesTask;
    private BlockApplicator blockApplicator;

    private final List<CataMine> mines = new ArrayList<>();

    public MineManager(CataMines plugin) {
        this.plugin = plugin;
        minesPath = plugin.getDataFolder().toPath().resolve("mines");
        schematicsPath = plugin.getDataFolder().toPath().resolve("schematics");
        try {
            createDirectoriesIfNotExists(minesPath);
            createDirectoriesIfNotExists(schematicsPath);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create mines and/or schematics directory");
            plugin.getLogger().severe(e.getMessage());
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> loadMinesFromFolder(minesPath), 2L);
        start();
    }

    public void start() {
        initBlockApplicator();
        initMineTask();
    }

    /**
     * Reloads every mine from disk.
     *
     * Mines hold live state — a running controller, a region with a
     * composition mid-rotation, a queued block application — so this can't
     * just re-read the files on top of what's there. The running tasks are
     * stopped, the current mines are saved (so nothing in memory is lost),
     * the list is emptied, and everything is read fresh and started again.
     *
     * Returns how many mines came back, or -1 if the folder couldn't be read.
     */
    public int reload() {
        // Events end first, so what gets saved is every mine's own blocks.
        if (plugin.getMineEvents() != null) plugin.getMineEvents().stopAll();
        // Save first: a reload shouldn't cost anyone a mine they just edited
        // in game but hadn't saved.
        for (CataMine mine : mines) {
            try { saveMine(mine); } catch (IOException e) {
                plugin.getLogger().warning("Couldn't save " + mine.getName() + " before reloading: " + e.getMessage());
            }
        }
        if (blockApplicator != null) blockApplicator.cancel();
        if (minesTask != null) minesTask.cancel();
        mines.clear();

        try {
            loadMinesFromFolder(minesPath);
        } catch (Exception e) {
            plugin.getLogger().severe("Couldn't reload the mines folder: " + e.getMessage());
            start();                                  // get the tasks running again regardless
            return -1;
        }
        start();
        return mines.size();
    }

    public void shutDown() {
        if (plugin.getMineEvents() != null) plugin.getMineEvents().stopAll();
        blockApplicator.cancel();
        minesTask.cancel();

        for (CataMine mine : mines) {
            try {
                saveMine(mine);
            } catch (IOException e) {
                plugin.getLogger().severe(
                        Message.MINE_SAVE_EXCEPTION.format(plugin.getServer().getConsoleSender(), mine.getName()));
                plugin.getLogger().severe(e.getMessage());
            }
        }
    }

    /**
     * Initializes the BlockApplicationManager. It's responsible for Block efficient
     * Block manipulation as well as balancing workload.
     */
    private void initBlockApplicator() {
        Logger logger = plugin.getLogger();

        logger.info("Starting BlockApplicationManager...");

        if (blockApplicator != null) {
            logger.warning("BlockApplicationManager already running. Cancelling and running new manager.");
            blockApplicator.cancel();
        }

        if (CompatibilityProvider.isFaweEnabled()) {
            logger.info("Initializing FastAsyncBlockApplicationManager...");
            blockApplicator = new FastAsyncBlockApplicationManager(plugin);
        } else {
            logger.info("Initializing BukkitBlockApplicationManager...");
            blockApplicator = new BukkitBlockApplicationManager(plugin);
        }

        logger.info("Done initializing BlockApplicationManager.");

        logger.info("Starting BlockApplicationManager...");
        blockApplicator.start();
        logger.info("BlockApplicationManager started.");
    }

    /**
     * The mine task that ticks every mine once per second.
     */
    private void initMineTask() {
        Logger logger = plugin.getLogger();

        logger.info("Starting mine task...");

        if (minesTask != null) {
            logger.warning("Mine task already running. Overriding old mine task.");
            minesTask.cancel();
        }

        this.minesTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            for (CataMine cataMine : mines) {
                cataMine.tick();
            }

        }, 0L, 20L);
    }

    /**
     * Queues this region for reset. Resetting means filling the region with blocks
     * configured by the region.
     *
     * @param region the mine to reset
     */
    public void resetRegion(CataMineRegion region) {
        blockApplicator.queueForReset(region);
    }

    /**
     * Attempts to load all mines from a directory and returns it as a
     * List of CataMines. If the directory does not exist, is not a folder or
     * does not contain any files ending with '.yml', then an empty
     * ArrayList is returned. Otherwise, attempts to load mines from all files
     * ending with '.yml'. Note that only direct children files of the folder are
     * affected. Another folder inside the folder will be ignored.
     *
     * @param folder the path to load the mines from
     * @return A list of successfully loaded mines of direct children paths
     */
    public List<CataMine> getMinesFromFolder(Path folder) {
        Objects.requireNonNull(folder);
        List<CataMine> cataMines = new ArrayList<>();

        if (!Files.isDirectory(folder)) {
            plugin.getLogger().severe("Path is not a directory: " + folder);
            return cataMines;
        }

        try (Stream<Path> stream = Files.list(folder)) {
            cataMines = stream
                    .map(this::deserializeCataMineFromYaml)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed loading directory: " + folder);
        }

        for (CataMine mine : cataMines) {
            var controller = mine.getController();
            if (controller.getResetMode() == me.catalysmrl.catamines.mine.components.manager.controller.CataMineController.ResetMode.TIME
                    && controller.getResetDelay() <= 0)
                plugin.getLogger().warning("Mine '" + mine.getName() + "' resets on TIME with a delay of "
                        + controller.getResetDelay() + " — it will reset continuously. Set a reset-delay, or use PERCENTAGE mode.");
        }
        plugin.getLogger().info("Loaded " + cataMines.size() + " mines");
        return cataMines;
    }

    private Optional<CataMine> deserializeCataMineFromYaml(Path path) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(path.toFile());
        try {
            return deserializeCataMine(cfg);
        } catch (RuntimeException e) {
            // Name the file. Without this a bad value anywhere in the folder
            // produces a stack trace that says nothing about which mine it
            // came from, and the rest of the folder silently never loads.
            plugin.getLogger().severe("Couldn't load " + path.getFileName() + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<CataMine> deserializeCataMine(ConfigurationSection section) {
        CataMine mine = null;
        try {
            mine = AdvancedCataMine.deserialize(plugin, section);
        } catch (DeserializationException e) {
            // ignore
            e.printStackTrace();
        }
        return Optional.ofNullable(mine);
    }

    /**
     * Loads all mines of folder into this MineManager
     * {@link #getMinesFromFolder(Path)}
     *
     * @param folder the folder to load the mines from
     */
    public void loadMinesFromFolder(Path folder) {
        mines.clear();
        mines.addAll(getMinesFromFolder(folder));
    }

    /**
     * Fires CataMineBlockBreakEvent when a block is broken inside a mine.
     *
     * Upstream left this empty, which meant the event never fired and
     * everything listening for it — levels, pickaxe souls, contribution
     * boards, weekly challenges — silently did nothing. This is the missing
     * body: find the mine and region for the block, work out which entry of
     * the composition it matches, and fire.
     */
    public void callBlockBreak(BlockBreakEvent event) {
        org.bukkit.Location at = event.getBlock().getLocation();
        CataMine mine = getMineAtLocation(at).orElse(null);
        if (mine == null) return;
        CataMineRegion region = getRegionAtLocation(mine, at).orElse(null);
        if (region == null) return;

        var composition = region.getCompositionManager().getCurrent().orElse(null);
        // Which block of the composition was this? Matched by material, since
        // that's all the broken block can tell us. Null when it isn't one of
        // them (someone's own placed block, a fossil, an event's paint).
        me.catalysmrl.catamines.mine.components.composition.CataMineBlock which = null;
        if (composition != null) {
            String broken = event.getBlock().getType().name().toLowerCase(java.util.Locale.ROOT);
            for (var candidate : composition.getBlocks()) {
                if (candidate.getBaseBlock() == null) continue;
                String name = candidate.getBaseBlock().toString().split("\\[")[0]
                        .replace("minecraft:", "").toLowerCase(java.util.Locale.ROOT);
                if (name.equals(broken)) { which = candidate; break; }
            }
        }

        CataMineBlockBreakEvent mineEvent =
                new CataMineBlockBreakEvent(mine, region, composition, which, event);
        plugin.getServer().getPluginManager().callEvent(mineEvent);
        if (mineEvent.isCancelled()) event.setCancelled(true);
    }

    public void callBlockPlace(BlockPlaceEvent event) {

    }

    /**
     * Returns the mine with matching ID (name). Returns null if not present
     *
     * @param id ID or name of the mine
     * @return the mine if found, otherwise null
     */
    /**
     * Re-reads one mine from its file and swaps it into the list.
     *
     * Used when an event ends: the mine's file on disk is the only copy of
     * its real composition that can't be scribbled over by a paint, so
     * restoring means reading it back rather than trusting memory.
     */
    public Optional<CataMine> reloadMine(String name) {
        Path file = minesPath.resolve(name + ".yml");
        if (!Files.isRegularFile(file)) {
            // the file may be named with different case than the mine
            try (var stream = Files.list(minesPath)) {
                file = stream.filter(p -> p.getFileName().toString().equalsIgnoreCase(name + ".yml"))
                        .findFirst().orElse(null);
            } catch (IOException e) { file = null; }
            if (file == null) return Optional.empty();
        }
        Optional<CataMine> loaded = deserializeCataMineFromYaml(file);
        loaded.ifPresent(fresh -> {
            mines.removeIf(m -> m.getName().equalsIgnoreCase(name));
            mines.add(fresh);
        });
        return loaded;
    }

    /**
     * A mine by name.
     *
     * Exact match first, then ignoring case. Mine names are typed by hand in
     * commands and stored lowercased in a few places, and an exact-only
     * lookup meant anything with a capital letter in its name silently
     * "didn't exist".
     */
    public Optional<CataMine> getMine(String id) {
        if (id == null) return Optional.empty();
        Optional<CataMine> exact = mines.stream()
                .filter(cataMine -> cataMine.getName().equals(id))
                .findFirst();
        if (exact.isPresent()) return exact;
        return mines.stream()
                .filter(cataMine -> cataMine.getName().equalsIgnoreCase(id))
                .findFirst();
    }

    public Optional<CataMine> getMineAtLocation(org.bukkit.Location location) {
        for (CataMine mine : mines) {
            for (CataMineRegion region : mine.getRegionManager().getChoices()) {
                if (region.contains(location)) {
                    return Optional.of(mine);
                }
            }
        }
        return Optional.empty();
    }
    
    public Optional<CataMineRegion> getRegionAtLocation(CataMine mine, org.bukkit.Location location) {
        for (CataMineRegion region : mine.getRegionManager().getChoices()) {
            if (region.contains(location)) return Optional.of(region);
        }
        return Optional.empty();
    }

    /**
     * Returns the list containing all registered mines.
     *
     * @return the registered mines
     */
    public List<CataMine> getMines() {
        return mines;
    }

    /**
     * Returns a list of every mine name that is registered.
     *
     * @return a list of mine IDs
     */
    public List<String> getMineList() {
        return mines.stream().map(CataMine::getName).collect(Collectors.toList());
    }

    /**
     * Returns true if a mine with matching ID (name) is registered.
     *
     * @param id ID or name of the mine
     * @return true if mine with matching ID is registered
     */
    public boolean containsMine(String id) {
        return mines.stream().anyMatch(mine -> mine.getName().equals(id));
    }

    /**
     * {@link #containsMine(String)}
     *
     * @param mine the cata mine
     * @return true if a mine with matching ID is registered
     */
    public boolean containsMine(CataMine mine) {
        return containsMine(mine.getName());
    }

    /**
     * Registers a mine
     *
     * @param mine the mine to register
     * @throws IllegalArgumentException if the mine is already registered
     */
    public void registerMine(CataMine mine) {
        if (containsMine(mine))
            throw new IllegalArgumentException();
        mines.add(mine);
    }

    public void deleteMine(CataMine cataMine) throws IOException {
        mines.remove(cataMine);

        Files.deleteIfExists(plugin.getDataFolder().toPath().resolve("mines").resolve(cataMine.getName() + ".yml"));
    }

    public void saveMine(CataMine mine) throws IOException {
        Path file = plugin.getDataFolder().toPath().resolve("mines").resolve(mine.getName() + ".yml");
        FileConfiguration fileCfg = new YamlConfiguration();

        // Never write a running event's blocks to disk as if they were the
        // mine's own — see MineEvents.withOriginals.
        if (plugin.getMineEvents() != null) plugin.getMineEvents().withOriginals(mine, () -> mine.serialize(fileCfg));
        else mine.serialize(fileCfg);

        fileCfg.save(file.toFile());
    }

    public Path getMinesPath() {
        return minesPath;
    }

    private static void createDirectoriesIfNotExists(Path path) throws IOException {
        if (Files.exists(path) && (Files.isDirectory(path) || Files.isSymbolicLink(path))) {
            return;
        }

        try {
            Files.createDirectories(path);
        } catch (FileAlreadyExistsException e) {
            // ignore
        }
    }
}
