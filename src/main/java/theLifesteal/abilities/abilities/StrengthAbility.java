package theLifesteal.abilities.abilities;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import theLifesteal.ColorUtils;
import theLifesteal.abilities.AbilityCooldownManager;
import theLifesteal.abilities.ItemAbility;
import theLifesteal.abilities.ItemAbilityData;
import theLifesteal.abilities.ItemAbilityType;

import java.util.LinkedHashMap;
import java.util.Map;

public class StrengthAbility extends ItemAbility {

    public StrengthAbility(JavaPlugin plugin) {
        super(plugin, "strength", "Rage", ItemAbilityType.RIGHT_CLICK);
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("amplifier", 0);
        config.put("duration", 10);
        config.put("cooldown", 60);
        config.put("cooldownScope", "ITEM");
        return config;
    }

    @Override
    public Map<String, ConfigField> getConfigFields() {
        Map<String, ConfigField> fields = new LinkedHashMap<>();
        fields.put("amplifier", new ConfigField("Strength Level (0=I, 1=II...)", "int", 0, 10));
        fields.put("duration", new ConfigField("Duration (seconds)", "int", 1, 300));
        fields.put("cooldown", new ConfigField("Cooldown (seconds)", "int", 0, 3600));
        fields.put("cooldownScope", new ConfigField("Cooldown Scope", "string"));
        return fields;
    }

    @Override
    public String buildLore(ItemAbilityData data) {
        int amplifier = data.getConfigInt("amplifier");
        int duration = data.getConfigInt("duration");
        int cooldown = data.getConfigInt("cooldown");
        String level = toRoman(amplifier + 1);
        return "&7Gain &cStrength " + level + " &7for &e" + duration + "s\n&8(&e" + cooldown + "s cooldown&8)";
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

        int amplifier = data.getConfigInt("amplifier");
        int duration = data.getConfigInt("duration");

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.STRENGTH, duration * 20, amplifier, false, true, true));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.8f);
        player.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, player.getLocation().add(0, 1.5, 0), 10, 0.5, 0.5, 0.5, 0.1);

        player.sendMessage(ColorUtils.colorize("&c💢 Rage activated! &7(Strength " + toRoman(amplifier + 1) + " for " + duration + "s)"));

        if (cooldown > 0) {
            cooldownManager.setCooldown(player.getUniqueId(), getId(), itemId, scope, cooldown);
        }
        return true;
    }

    private String toRoman(int num) {
        String[] romans = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI"};
        if (num > 0 && num < romans.length) return romans[num];
        return String.valueOf(num);
    }
}