package theLifesteal.abilities.abilities;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import theLifesteal.ColorUtils;
import theLifesteal.abilities.AbilityCooldownManager;
import theLifesteal.abilities.ItemAbility;
import theLifesteal.abilities.ItemAbilityData;
import theLifesteal.abilities.ItemAbilityType;

import java.util.LinkedHashMap;
import java.util.Map;

public class DrainLifeAbility extends ItemAbility {

    public DrainLifeAbility(JavaPlugin plugin) {
        super(plugin, "drain_life", "Lifesteal", ItemAbilityType.ON_HIT);
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("chance", 10);
        config.put("health", 1.0);
        config.put("affectPlayers", true);
        config.put("affectMobs", true);
        return config;
    }

    @Override
    public Map<String, ConfigField> getConfigFields() {
        Map<String, ConfigField> fields = new LinkedHashMap<>();
        fields.put("chance", new ConfigField("Chance (%)", "int", 1, 100));
        fields.put("health", new ConfigField("Health Stolen", "double", 0.5, 20.0));
        fields.put("affectPlayers", new ConfigField("Affect Players", "boolean"));
        fields.put("affectMobs", new ConfigField("Affect Mobs", "boolean"));
        return fields;
    }

    @Override
    public String buildLore(ItemAbilityData data) {
        int chance = data.getConfigInt("chance");
        double health = data.getConfigDouble("health");
        return "&7Steal &c" + formatHealth(health) + " &7on hit &8(&b" + chance + "% chance&8)";
    }

    public boolean executeOnHit(Player attacker, LivingEntity victim, ItemAbilityData data,
                                AbilityCooldownManager cooldownManager, String itemId) {
        if (victim instanceof Player && !data.getConfigBoolean("affectPlayers")) return false;
        if (!(victim instanceof Player) && !data.getConfigBoolean("affectMobs")) return false;

        int chance = data.getConfigInt("chance");
        if (Math.random() * 100 >= chance) return false;

        double health = data.getConfigDouble("health");
        double maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
        double newHealth = Math.min(attacker.getHealth() + health, maxHealth);
        attacker.setHealth(newHealth);

        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_WITCH_DRINK, 0.8f, 1.0f);
        attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, attacker.getLocation().add(0, 1.5, 0), 5, 0.3, 0.5, 0.3, 0.1);

        attacker.sendMessage(ColorUtils.colorize("&d🩸 Lifesteal! &c+" + formatHealth(health)));
        return true;
    }

    private String formatHealth(double health) {
        if (health == Math.floor(health)) return (int) health + "❤";
        return health + "❤";
    }

    @Override
    public boolean execute(Player player, ItemAbilityData data, AbilityCooldownManager cooldownManager, String itemId) {
        return false;
    }
}