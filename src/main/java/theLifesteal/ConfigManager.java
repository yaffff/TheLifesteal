package theLifesteal;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    // Settings
    private double heartValue;
    private double minimumMaxHealth;
    private double halfHeartValue;
    private boolean dropHeartsOnDeath;
    private double maxHealthCap;
    private double dropChance; // NEW

    // Sound settings
    private boolean heartUseSound;
    private boolean heartWithdrawSound;

    // Item settings
    private Material heartMaterial;
    private String heartDisplayName;
    private List<String> heartLore;
    private boolean heartGlow;
    private int heartCustomModelData;

    // Rank-based max crafts
    private Map<String, Integer> maxCraftsMap;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plugin.saveDefaultConfig();
        loadConfig();
    }

    public void loadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();

        // Load settings
        heartValue = config.getDouble("settings.heart-value", 2.0);
        minimumMaxHealth = config.getDouble("settings.minimum-max-health", 6.0);
        halfHeartValue = config.getDouble("settings.half-heart-value", 1.0);
        dropHeartsOnDeath = config.getBoolean("settings.drop-hearts-on-death", true);
        maxHealthCap = config.getDouble("settings.max-health-cap", 40.0);
        dropChance = config.getDouble("settings.drop-chance", 1.0); // NEW

        // Load sound settings
        heartUseSound = config.getBoolean("settings.sounds.heart-use", true);
        heartWithdrawSound = config.getBoolean("settings.sounds.heart-withdraw", true);

        // Load item settings
        String materialName = config.getString("items.half-heart.material", "RED_DYE");
        try {
            heartMaterial = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            heartMaterial = Material.RED_DYE;
            plugin.getLogger().warning("Invalid material in config: " + materialName + ". Using RED_DYE instead.");
        }

        heartDisplayName = config.getString("items.half-heart.name", "&c❤ &4Half a Heart &c❤");
        heartLore = config.getStringList("items.half-heart.lore");
        heartGlow = config.getBoolean("items.half-heart.glow", true);
        heartCustomModelData = config.getInt("items.half-heart.custom-model-data", 0);

        // Load rank-based max crafts
        maxCraftsMap = new HashMap<>();
        ConfigurationSection craftsSection = config.getConfigurationSection("max-crafts");
        if (craftsSection != null) {
            for (String key : craftsSection.getKeys(false)) {
                maxCraftsMap.put(key, craftsSection.getInt(key));
            }
        }
        if (maxCraftsMap.isEmpty()) {
            maxCraftsMap.put("default", 5);
            maxCraftsMap.put("op", 100);
        }
    }

    public String getMessage(String path) {
        String prefix = config.getString("messages.prefix", "&8[&c❤&8] &r");
        String message = config.getString("messages." + path, "");
        return ColorUtils.colorize(prefix + message);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    // Getters
    public double getHeartValue() { return heartValue; }
    public double getMinimumMaxHealth() { return minimumMaxHealth; }
    public double getHalfHeartValue() { return halfHeartValue; }
    public boolean shouldDropHeartsOnDeath() { return dropHeartsOnDeath; }
    public boolean isHeartUseSoundEnabled() { return heartUseSound; }
    public boolean isHeartWithdrawSoundEnabled() { return heartWithdrawSound; }
    public Material getHeartMaterial() { return heartMaterial; }
    public String getHeartDisplayName() { return heartDisplayName; }
    public List<String> getHeartLore() { return heartLore; }
    public boolean isHeartGlow() { return heartGlow; }
    public int getHeartCustomModelData() { return heartCustomModelData; }
    public double getMaxHealthCap() { return maxHealthCap; }
    public double getDropChance() { return dropChance; } // NEW

    public int getMaxCraftsForPlayer(Player player) {
        for (int i = 100; i > 0; i--) {
            if (player.hasPermission("thelifesteal.maxcrafts." + i)) {
                return i;
            }
        }
        if (player.isOp()) {
            return maxCraftsMap.getOrDefault("op", 100);
        }
        return maxCraftsMap.getOrDefault("default", 5);
    }
}