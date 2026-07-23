package theLifesteal.abilities;

import org.bukkit.entity.LivingEntity;
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

    public AbilityKillTracker getKillTracker() {
        if (plugin instanceof theLifesteal.TheLifesteal lifesteal) {
            if (lifesteal.getAbilityManager() != null) {
                return lifesteal.getAbilityManager().getKillTracker();
            }
        }
        return null;
    }

    public void recordAbilityDamage(Player caster, LivingEntity victim) {
        recordAbilityDamage(caster, victim, 15000L);
    }

    public void recordAbilityDamage(Player caster, LivingEntity victim, long durationMs) {
        AbilityKillTracker tracker = getKillTracker();
        if (tracker != null && caster != null && victim != null) {
            tracker.recordAbilityDamage(caster, victim, getId(), durationMs);
        }
    }

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
     * Execute the ability for right-click or shift-right-click.
     * Return true if successful (cooldown should be applied, item can be consumed).
     */
    public abstract boolean execute(Player player, ItemAbilityData data, AbilityCooldownManager cooldownManager, String itemId);

    /**
     * Execute the ability when the player hits an entity (ON_HIT type).
     * Override this in your ON_HIT ability to add custom behavior.
     *
     * @param attacker The player who hit
     * @param victim The entity that was hit
     * @param data The ability's config data
     * @param cooldownManager The cooldown manager
     * @param itemId The custom item ID
     * @param baseDamage The original damage of the hit
     * @return true if the ability triggered (for consumption logic)
     */
    public boolean onHitExecute(Player attacker, LivingEntity victim, ItemAbilityData data,
                                AbilityCooldownManager cooldownManager, String itemId, double baseDamage) {
        // Default: do nothing. ON_HIT abilities override this.
        return false;
    }

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