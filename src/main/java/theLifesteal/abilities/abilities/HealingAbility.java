package theLifesteal.abilities.abilities;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import theLifesteal.ColorUtils;
import theLifesteal.abilities.AbilityCooldownManager;
import theLifesteal.abilities.ItemAbility;
import theLifesteal.abilities.ItemAbilityData;
import theLifesteal.abilities.ItemAbilityType;

import java.util.LinkedHashMap;
import java.util.Map;

public class HealingAbility extends ItemAbility {

    public HealingAbility(JavaPlugin plugin) {
        super(plugin, "healing", "Heal", ItemAbilityType.RIGHT_CLICK);
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("health", 4.0);
        config.put("cooldown", 30);
        return config;
    }

    @Override
    public Map<String, ConfigField> getConfigFields() {
        Map<String, ConfigField> fields = new LinkedHashMap<>();
        fields.put("health", new ConfigField("Health Restored", "double", 0.5, 100.0));
        fields.put("cooldown", new ConfigField("Cooldown (seconds)", "int", 0, 3600));
        return fields;
    }

    @Override
    public String buildLore(ItemAbilityData data) {
        double health = data.getConfigDouble("health");
        int cooldown = data.getConfigInt("cooldown");
        return "&7Restore &c" + formatHealth(health) + " &7on use &8(&e" + cooldown + "s cooldown&8)";
    }

    @Override
    public boolean execute(Player player, ItemAbilityData data, AbilityCooldownManager cooldownManager, String itemId) {
        int cooldown = data.getConfigInt("cooldown");

        if (cooldownManager.isOnCooldown(player.getUniqueId(), getId(), itemId)) {
            long remaining = cooldownManager.getRemainingCooldown(player.getUniqueId(), getId(), itemId);
            player.sendMessage(ColorUtils.colorize("&cOn cooldown! &7(" + cooldownManager.formatCooldown(remaining) + ")"));
            return false;
        }

        double health = data.getConfigDouble("health");
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double newHealth = Math.min(player.getHealth() + health, maxHealth);
        player.setHealth(newHealth);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.5, 0), 7, 0.5, 0.5, 0.5, 0.1);

        player.sendMessage(ColorUtils.colorize("&a❤ Healed for &c" + formatHealth(health) + "&a!"));
        cooldownManager.setCooldown(player.getUniqueId(), getId(), itemId, cooldown);
        return true;
    }

    private String formatHealth(double health) {
        if (health == Math.floor(health)) return (int) health + "❤";
        return health + "❤";
    }
}