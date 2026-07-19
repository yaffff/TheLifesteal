package theLifesteal;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import theLifesteal.crafting.*;
import java.util.logging.Level;

public final class TheLifesteal extends JavaPlugin {

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
            // File doesn't exist in jar, that's fine
            getLogger().info("No default recipes.yml found, will create when needed.");
        }

        try {
            saveResource("custom_items.yml", false);
        } catch (IllegalArgumentException e) {
            // File doesn't exist in jar, that's fine
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

        getLogger().log(Level.INFO, "§c❤ §aLifesteal Plugin v2.0 enabled for 1.21.11! §c❤");
        getLogger().log(Level.INFO, "§6⚒ §eCustom Crafting System loaded! §6⚒");
        getLogger().log(Level.INFO, "§d✨ §eCustom Items Manager loaded! §d✨");
    }

    private void initializeCraftingSystem() {
        NamespacedKey bookKey = new NamespacedKey(this, "recipe_book");

        this.recipeBookItem = new RecipeBookItem(bookKey, getConfig());
        this.craftingManager = new CraftingManager(this);
        this.craftingGUI = new CraftingGUI(this, craftingManager, getConfig(), customItemManager); // Pass 'this'
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