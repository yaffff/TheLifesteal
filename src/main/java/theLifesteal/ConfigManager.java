package theLifesteal;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    // Settings
    private double heartValue;
    private double minimumMaxHealth;
    private double halfHeartValue;
    private boolean dropHeartsOnDeath;

    // Sound settings
    private boolean heartUseSound;
    private boolean heartWithdrawSound;

    // Item settings
    private Material heartMaterial;
    private String heartDisplayName;
    private List<String> heartLore;
    private boolean heartGlow;
    private int heartCustomModelData;

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
}