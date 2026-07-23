package theLifesteal.abilities.abilities;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import theLifesteal.ColorUtils;
import theLifesteal.abilities.AbilityCooldownManager;
import theLifesteal.abilities.ItemAbility;
import theLifesteal.abilities.ItemAbilityData;
import theLifesteal.abilities.ItemAbilityType;

import java.util.LinkedHashMap;
import java.util.Map;

public class LifeConsumeAbility extends ItemAbility {

    public LifeConsumeAbility(JavaPlugin plugin) {
        super(plugin, "life_consume", "Life Consume", ItemAbilityType.RIGHT_CLICK);
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("hpAmount", 1.0);
        config.put("maxCap", 40.0);
        config.put("cooldown", 0);
        config.put("cooldownScope", "ITEM");
        return config;
    }

    @Override
    public Map<String, ConfigField> getConfigFields() {
        Map<String, ConfigField> fields = new LinkedHashMap<>();
        fields.put("hpAmount", new ConfigField("HP Amount", "double", 0.5, 100.0));
        fields.put("maxCap", new ConfigField("Max HP Cap", "double", 1.0, 200.0));
        fields.put("cooldown", new ConfigField("Cooldown (seconds)", "int", 0, 3600));
        fields.put("cooldownScope", new ConfigField("Cooldown Scope", "string"));
        return fields;
    }

    @Override
    public String buildLore(ItemAbilityData data) {
        double amount = data.getConfigDouble("hpAmount");
        double cap = data.getConfigDouble("maxCap");
        int cooldown = data.getConfigInt("cooldown");
        String scope = data.getConfigString("cooldownScope");
        String cooldownText = cooldown > 0 ? " &8(&e" + cooldown + "s&8)" : "";
        return "&7Increase max HP by &c" + formatHealth(amount) +
                "\n&7Up to &c" + formatHealth(cap) + " max" + cooldownText;
    }

    @Override
    public boolean execute(Player player, ItemAbilityData data, AbilityCooldownManager cooldownManager, String itemId) {
        int cooldown = data.getConfigInt("cooldown");
        String scope = data.getConfigString("cooldownScope");
        if (scope == null || scope.isEmpty()) scope = "ITEM";

        if (cooldown > 0 && cooldownManager.isOnCooldown(player.getUniqueId(), getId(), itemId, scope)) {
            long remaining = cooldownManager.getRemainingCooldown(player.getUniqueId(), getId(), itemId, scope);
            player.sendMessage(ColorUtils.colorize("&cOn cooldown! &7(" + cooldownManager.formatCooldown(remaining) + ")"));
            return false;
        }

        double amount = data.getConfigDouble("hpAmount");
        double cap = data.getConfigDouble("maxCap");

        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr == null) {
            player.sendMessage(ColorUtils.colorize("&cSomething went wrong!"));
            return false;
        }

        double currentMax = maxHealthAttr.getBaseValue();

        if (currentMax >= cap) {
            player.sendMessage(ColorUtils.colorize("&cYou are already at maximum health! (&c" + formatHealth(cap) + "&c)"));
            return false;
        }

        double newMax = Math.min(currentMax + amount, cap);
        maxHealthAttr.setBaseValue(newMax);

        double newHealth = Math.min(player.getHealth() + amount, newMax);
        player.setHealth(newHealth);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.0f);

        for (int i = 0; i < 10; i++) {
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(
                            Math.random() * 1.0 - 0.5, 1.5 + Math.random() * 1.0, Math.random() * 1.0 - 0.5),
                    1, 0, 0.1, 0, 0.05);
        }

        for (int i = 0; i < 15; i++) {
            player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(
                            Math.random() * 1.5 - 0.75, 0.5 + Math.random() * 2.0, Math.random() * 1.5 - 0.75),
                    1, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 50, 50), 2f));
        }

        double gained = newMax - currentMax;
        player.sendMessage(ColorUtils.colorize(
                "&c❤ &aYour max HP increased by &c" + formatHealth(gained) +
                        "&a! &7(Now: &c" + formatHealth(newMax) + "&7)"));

        if (cooldown > 0) {
            cooldownManager.setCooldown(player.getUniqueId(), getId(), itemId, scope, cooldown);
        }
        return true;
    }

    private String formatHealth(double health) {
        if (health == Math.floor(health)) return (int) health + "❤";
        return String.format("%.1f❤", health);
    }
}