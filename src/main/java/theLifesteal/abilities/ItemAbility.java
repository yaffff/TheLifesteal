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

    public TotemProtectionManager getTotemProtectionManager() {
        if (plugin instanceof theLifesteal.TheLifesteal lifesteal) {
            return lifesteal.getTotemProtectionManager();
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
     * Safely deal ability damage to a victim, respecting Totem of Undying protection.
     * Prevents multi-hit or ability double-damage from popping totem AND killing player in the same damage tick.
     */
    public boolean dealAbilityDamage(Player caster, LivingEntity victim, double damage) {
        return dealAbilityDamage(caster, victim, damage, 15000L);
    }

    /**
     * Safely deal ability damage to a victim with custom attribution duration, respecting Totem of Undying.
     */
    public boolean dealAbilityDamage(Player caster, LivingEntity victim, double damage, long durationMs) {
        if (victim == null || victim.isDead() || !victim.isValid() || damage <= 0) return false;

        TotemProtectionManager totemMgr = getTotemProtectionManager();
        if (victim instanceof Player victimPlayer) {
            if (totemMgr != null && totemMgr.isTotemProtected(victimPlayer)) {
                // Target popped a totem on this tick/window — block further ability damage
                return false;
            }

            boolean hasTotem = totemMgr != null && totemMgr.hasTotem(victimPlayer);
            if (hasTotem && (victimPlayer.getHealth() - damage <= 0)) {
                // Incoming damage will pop totem! Mark totem protection so subsequent calls on this tick skip damage
                if (totemMgr != null) {
                    totemMgr.markTotemPop(victimPlayer);
                }
            }
        }

        recordAbilityDamage(caster, victim, durationMs);
        victim.damage(damage, caster);
        return true;
    }

    /**
     * Safely deduct health for self-damage or HP costs.
     * If the cost would kill a player holding a Totem of Undying, uses damage() to pop the totem safely rather than bypass it.
     */
    public boolean applySelfHealthCost(Player player, double cost) {
        if (player == null || player.isDead() || cost <= 0) return false;

        TotemProtectionManager totemMgr = getTotemProtectionManager();
        if (totemMgr != null && totemMgr.isTotemProtected(player)) {
            return false;
        }

        double currentHP = player.getHealth();
        if (currentHP - cost <= 0) {
            if (totemMgr != null && totemMgr.hasTotem(player)) {
                totemMgr.markTotemPop(player);
                player.damage(cost);
                return true;
            } else {
                player.setHealth(1.0);
                return true;
            }
        } else {
            player.setHealth(currentHP - cost);
            return true;
        }
    }

    /**
     * Calculate the required HP cost for this ability based on its configuration and caster state.
     * Base implementation checks standard HP cost keys ("hpCostPct", "hpCostPercent", "hpCost", "selfDamage").
     * Subclasses can override this method if they have custom HP cost calculation.
     */
    public double getRequiredHpCost(ItemAbilityData data, Player player) {
        if (data == null || player == null) return 0.0;

        Object hpCostPctObj = data.getConfigValue("hpCostPct");
        if (hpCostPctObj instanceof Number num) {
            double pct = num.doubleValue();
            if (pct > 0) {
                double maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null 
                        ? player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() 
                        : 20.0;
                return maxHp * (pct / 100.0);
            }
        }

        Object hpCostPercentObj = data.getConfigValue("hpCostPercent");
        if (hpCostPercentObj instanceof Number num) {
            double pct = num.doubleValue();
            if (pct > 0) {
                double maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null 
                        ? player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() 
                        : 20.0;
                return maxHp * (pct / 100.0);
            }
        }

        Object hpCostObj = data.getConfigValue("hpCost");
        if (hpCostObj instanceof Number num) {
            double cost = num.doubleValue();
            if (cost > 0) return cost;
        }

        Object selfDmgObj = data.getConfigValue("selfDamage");
        if (selfDmgObj instanceof Number num) {
            double selfDmg = num.doubleValue();
            if (selfDmg > 0) return selfDmg;
        }

        return 0.0;
    }

    /**
     * Checks if the player has strictly more HP than the specified cost amount.
     * If not, sends a feedback message telling the player they don't have enough HP.
     *
     * @param player The player attempting to use the ability
     * @param cost The required HP cost amount
     * @return true if player.getHealth() > cost, false otherwise
     */
    public boolean checkStrictHealthRequirement(Player player, double cost) {
        if (player == null || cost <= 0) return true;
        double currentHp = player.getHealth();
        if (currentHp <= cost) {
            String formattedCost = (cost == Math.floor(cost)) ? String.valueOf((int) cost) : String.format("%.1f", cost);
            player.sendMessage(theLifesteal.ColorUtils.colorize("&cYou don't have enough HP to use " + getDisplayName() + "! &7(Requires strictly more than &c" + formattedCost + "❤&7 HP)"));
            return false;
        }
        return true;
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

    public boolean onHitExecute(Player attacker, LivingEntity victim, ItemAbilityData data,
                                AbilityCooldownManager cooldownManager, String itemId, double baseDamage,
                                org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        return onHitExecute(attacker, victim, data, cooldownManager, itemId, baseDamage);
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