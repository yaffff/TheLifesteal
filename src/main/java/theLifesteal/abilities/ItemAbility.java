package theLifesteal.abilities;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;
import java.util.Map;

public abstract class ItemAbility {

    private final String id;
    private final String displayName;
    private final ItemAbilityType type;
    private final JavaPlugin plugin;

    public ItemAbility(JavaPlugin plugin, String id, String displayName, ItemAbilityType type) {
        this.plugin = plugin;
        this.id = id;
        this.displayName = displayName;
        this.type = type;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public ItemAbilityType getType() { return type; }
    public JavaPlugin getPlugin() { return plugin; }

    /**
     * Get the default config values for this ability.
     * Used when creating a new ability instance on an item.
     */
    public abstract Map<String, Object> getDefaultConfig();

    /**
     * Get the config field definitions for the GUI editor.
     * Each entry: fieldKey -> [displayName, type]
     * Types: "int", "double", "boolean", "string"
     */
    public abstract Map<String, ConfigField> getConfigFields();

    /**
     * Build the lore line for this ability with current config values.
     */
    public abstract String buildLore(ItemAbilityData data);

    /**
     * Execute the ability. Return true if successful (cooldown should be applied).
     */
    public abstract boolean execute(Player player, ItemAbilityData data, AbilityCooldownManager cooldownManager, String itemId);

    public static class ConfigField {
        private final String displayName;
        private final String type;
        private final double min;
        private final double max;

        public ConfigField(String displayName, String type) {
            this(displayName, type, Double.MIN_VALUE, Double.MAX_VALUE);
        }

        public ConfigField(String displayName, String type, double min, double max) {
            this.displayName = displayName;
            this.type = type;
            this.min = min;
            this.max = max;
        }

        public String getDisplayName() { return displayName; }
        public String getType() { return type; }
        public double getMin() { return min; }
        public double getMax() { return max; }
    }
}