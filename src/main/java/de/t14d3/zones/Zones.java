package de.t14d3.zones;

import com.sk89q.worldedit.WorldEdit;
import de.t14d3.zones.commands.CommandExecutor;
import de.t14d3.zones.integrations.FAWEIntegration;
import de.t14d3.zones.integrations.PlaceholderAPI;
import de.t14d3.zones.integrations.WorldEditSession;
import de.t14d3.zones.listeners.ChunkEventListener;
import de.t14d3.zones.listeners.PlayerInteractListener;
import de.t14d3.zones.listeners.PlayerQuitListener;
import de.t14d3.zones.utils.Messages;
import de.t14d3.zones.utils.Types;
import de.t14d3.zones.utils.Utils;
import de.t14d3.zones.visuals.BeaconUtils;
import de.t14d3.zones.visuals.FindBossbar;
import de.t14d3.zones.visuals.ParticleHandler;
import it.unimi.dsi.fastutil.Pair;
import org.bukkit.Location;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Zones extends JavaPlugin {

    private RegionManager regionManager;
    private PermissionManager permissionManager;
    private BeaconUtils beaconUtils;
    private ParticleHandler particleHandler;
    public Map<UUID, Pair<Location, Location>> selection = new HashMap<>();
    public ConcurrentHashMap<UUID, BoundingBox> particles = new ConcurrentHashMap<>();
    private Types types;
    private Messages messages;
    private static Zones instance;
    private CommandExecutor commandExecutor;
    private PaperBootstrap bootstrap;
    private Utils utils;
    private FindBossbar findBossbar;

    public Zones(PaperBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public void onEnable() {
        instance = this;
        // Initialize PermissionManager first without RegionManager
        this.permissionManager = new PermissionManager();

        // Initialize RegionManager with PermissionManager
        this.regionManager = new RegionManager(this, permissionManager);

        // Set RegionManager in PermissionManager to complete dependency setup
        this.permissionManager.setRegionManager(regionManager);

        // Initialize utilities
        this.beaconUtils = new BeaconUtils(this);
        this.particleHandler = new ParticleHandler(this);
        particleHandler.particleScheduler();
        this.utils = new Utils(this);
        utils.populatePlayers();

        // Load regions from regions.yml
        regionManager.loadRegions();

        this.saveDefaultConfig();

        // Load messages from messages.yml
        File messagesFile = new File(getDataFolder(), "messages.properties");
        if (!messagesFile.exists()) {
            saveResource("messages.properties", false); // Copy default messages.yml from jar
        }
        Properties messagesConfig = new Properties();
        try {
            messagesConfig.load(new FileInputStream(messagesFile));
        } catch (IOException e) {
            getLogger().severe("Failed to load messages.properties");
        }

        messages = new Messages(messagesConfig, this);


        // Register listeners
        this.getServer().getPluginManager().registerEvents(new PlayerInteractListener(regionManager, permissionManager, this), this);
        this.getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        this.getServer().getPluginManager().registerEvents(new ChunkEventListener(this), this);

        // Populate Types
        types = new Types();
        types.populateTypes();

        // Register mode permissions
        for (Utils.Modes mode : Utils.Modes.values()) {
            getServer().getPluginManager().addPermission(new Permission("zones.mode." + mode.getName().toLowerCase() + ".main", PermissionDefault.OP));
            getServer().getPluginManager().addPermission(new Permission("zones.mode." + mode.getName().toLowerCase() + ".sub", PermissionDefault.OP));
        }


        this.commandExecutor = new CommandExecutor(this, regionManager);
        // Register saving task
        if (getSavingMode() == Utils.SavingModes.PERIODIC) {
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                regionManager.saveRegions();
                getLogger().info("Zones have been saved.");
            }, 20L, getConfig().getInt("zone-saving.period", 60) * 20L);
        }
        // Find bossbar
        this.findBossbar = new FindBossbar(this);


        // PlaceholderAPI integration
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderAPI(this).register();
            getLogger().info("PlaceholderAPI hooked!");
        }

        if (getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null) {
            new FAWEIntegration(this).register();
            getLogger().info("FAWE Integration enabled.");
        } else if (getServer().getPluginManager().getPlugin("WorldEdit") != null) {
            WorldEdit.getInstance().getEventBus().register(new WorldEditSession(this));
            getLogger().info("WorldEdit Integration enabled.");
        }
        getLogger().info("Zones plugin has been enabled! Loaded " + regionManager.loadedRegions.size() + " regions.");
    }

    @Override
    public void onDisable() {
        // Save regions to regions.yml before plugin shutdown
        regionManager.saveRegions();
        regionManager.regions().clear();
        regionManager.loadedRegions.clear();
        regionManager.regionCache.clear();
        permissionManager.invalidateInteractionCaches();
        permissionManager.invalidateCaches();
        getLogger().info("Zones plugin is disabling and regions are saved.");
    }

    // Getters
    public RegionManager getRegionManager() { return regionManager; }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public Messages getMessages() {
        return messages;
    }
    public BeaconUtils getBeaconUtils() { return beaconUtils; }
    public ParticleHandler getParticleHandler() {
        return particleHandler;
    }

    public Types getTypes() {
        return this.types;
    }

    public Utils getUtils() {
        return utils;
    }

    public Utils.SavingModes getSavingMode() {
        return Utils.SavingModes.fromString(this.getConfig().getString("zone-saving.mode", "MODIFIED"));
    }

    public static Zones getInstance() {
        return instance;
    }

    public CommandExecutor getCommandListener() {
        return commandExecutor;
    }

    public FindBossbar getFindBossbar() {
        return findBossbar;
    }
}
