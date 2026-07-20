package theLifesteal;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import theLifesteal.crafting.*;

import java.util.UUID;
import java.util.logging.Level;

public final class TheLifesteal extends JavaPlugin implements Listener {

    private static TheLifesteal instance;
    private ConfigManager configManager;
    private HeartManager heartManager;
    private DeathListener deathListener;
    private HeartUseListener heartUseListener;
    private CommandHandler commandHandler;
    private RecipeManager recipeManager;

    // Crafting system
    private RecipeBookItem recipeBookItem;
    private CraftingManager craftingManager;
    private CraftingGUI craftingGUI;
    private RecipeBookListener recipeBookListener;
    private CustomItemManager customItemManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Try to save resources that might not exist yet
        try {
            saveResource("recipes.yml", false);
        } catch (IllegalArgumentException e) {
            getLogger().info("No default recipes.yml found, will create when needed.");
        }

        try {
            saveResource("custom_items.yml", false);
        } catch (IllegalArgumentException e) {
            getLogger().info("No default custom_items.yml found, will create when needed.");
        }

        // Initialize managers in the correct order
        this.configManager = new ConfigManager(this);
        this.heartManager = new HeartManager(this);

        // Initialize custom item manager
        this.customItemManager = new CustomItemManager(this);

        // Register listeners
        this.deathListener = new DeathListener(this);
        this.heartUseListener = new HeartUseListener(this);
        getServer().getPluginManager().registerEvents(deathListener, this);
        getServer().getPluginManager().registerEvents(heartUseListener, this);
        getServer().getPluginManager().registerEvents(this, this);

        // Initialize crafting system
        initializeCraftingSystem();

        // Register commands with tab completion
        this.commandHandler = new CommandHandler(this, recipeBookItem, craftingGUI);
        registerCommands();

        // Register custom recipes from config
        this.recipeManager = new RecipeManager(this);
        recipeManager.registerRecipes();

        // Load saved crafting processes
        craftingManager.loadCraftingProcesses();


        // Player quit cleanup
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                java.util.UUID uuid = event.getPlayer().getUniqueId();
                // Clear GUI data
                if (craftingGUI != null) {
                    craftingGUI.removePlayer(uuid);
                }
                // Clear admin editor sessions
                if (craftingGUI != null && craftingGUI.getAdminGUI() != null) {
                    craftingGUI.getAdminGUI().cleanupPlayer(uuid);
                }
                // Also clear any recipe book death cache if needed (the recipe book listener does its own cleanup,
                // but we can also clear here just in case)
            }
        }, this);

        getLogger().log(Level.INFO, "§c❤ §aLifesteal Plugin v2.0 enabled for 1.21.11! §c❤");
        getLogger().log(Level.INFO, "§6⚒ §eCustom Crafting System loaded! §6⚒");
        getLogger().log(Level.INFO, "§d✨ §eCustom Items Manager loaded! §d✨");

    }

    private void initializeCraftingSystem() {
        NamespacedKey bookKey = new NamespacedKey(this, "recipe_book");

        this.recipeBookItem = new RecipeBookItem(bookKey, getConfig());
        this.craftingManager = new CraftingManager(this);
        this.craftingGUI = new CraftingGUI(this, craftingManager, getConfig(), customItemManager);
        this.recipeBookListener = new RecipeBookListener(this, recipeBookItem, craftingGUI, craftingManager);

        getServer().getPluginManager().registerEvents(recipeBookListener, this);

        getLogger().info("§a✓ Recipe Book System initialized");
        getLogger().info("§a✓ Crafting Manager initialized");
        getLogger().info("§a✓ Crafting GUI initialized");
    }

    private void registerCommands() {
        if (getCommand("withdrawhearts") != null) {
            getCommand("withdrawhearts").setExecutor(commandHandler);
            getCommand("withdrawhearts").setTabCompleter(commandHandler);
        }
        if (getCommand("setmaxhp") != null) {
            getCommand("setmaxhp").setExecutor(commandHandler);
            getCommand("setmaxhp").setTabCompleter(commandHandler);
        }
        if (getCommand("craft") != null) {
            getCommand("craft").setExecutor(commandHandler);
            getCommand("craft").setTabCompleter(commandHandler);
        }
        if (getCommand("recipebook") != null) {
            getCommand("recipebook").setExecutor(commandHandler);
            getCommand("recipebook").setTabCompleter(commandHandler);
        }
        if (getCommand("customitems") != null) {
            getCommand("customitems").setExecutor(commandHandler);
            getCommand("customitems").setTabCompleter(commandHandler);
        }
    }
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (craftingGUI != null) {
            craftingGUI.removePlayer(uuid);
            if (craftingGUI.getAdminGUI() != null) {
                craftingGUI.getAdminGUI().cleanupPlayer(uuid);
            }
        }
    }

    @Override
    public void onDisable() {
        // Save all active crafting processes before shutdown
        if (craftingManager != null) {
            craftingManager.forceSave();
        }

        getLogger().log(Level.INFO, "§c❤ §eLifesteal Plugin disabled. Goodbye! §c❤");
    }

    public static TheLifesteal getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public HeartManager getHeartManager() {
        return heartManager;
    }

    public RecipeBookItem getRecipeBookItem() {
        return recipeBookItem;
    }

    public CraftingManager getCraftingManager() {
        return craftingManager;
    }

    public CraftingGUI getCraftingGUI() {
        return craftingGUI;
    }

    public CustomItemManager getCustomItemManager() {
        return customItemManager;
    }
}