package theLifesteal.abilities.abilities;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import theLifesteal.ColorUtils;
import theLifesteal.abilities.AbilityCooldownManager;
import theLifesteal.abilities.ItemAbility;
import theLifesteal.abilities.ItemAbilityData;
import theLifesteal.abilities.ItemAbilityType;

import java.util.*;

public class ShadowEscapeAbility extends ItemAbility {

    public ShadowEscapeAbility(JavaPlugin plugin) {
        super(plugin, "shadow_escape", "Shadow Escape", ItemAbilityType.ON_HIT);
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("speedAmplifier", 1);
        config.put("speedDuration", 4);
        config.put("invisibilityDuration", 3);
        config.put("affectPlayers", true);
        config.put("affectMobs", true);
        return config;
    }

    @Override
    public Map<String, ConfigField> getConfigFields() {
        Map<String, ConfigField> fields = new LinkedHashMap<>();
        fields.put("speedAmplifier", new ConfigField("Speed Level (0=I)", "int", 0, 5));
        fields.put("speedDuration", new ConfigField("Speed Duration (s)", "int", 1, 30));
        fields.put("invisibilityDuration", new ConfigField("Invisibility Duration (s)", "int", 1, 30));
        fields.put("affectPlayers", new ConfigField("Affect Players", "boolean"));
        fields.put("affectMobs", new ConfigField("Affect Mobs", "boolean"));
        return fields;
    }

    @Override
    public String buildLore(ItemAbilityData data) {
        int speedAmp = data.getConfigInt("speedAmplifier");
        int speedDur = data.getConfigInt("speedDuration");
        int invisDur = data.getConfigInt("invisibilityDuration");
        return "&7Gain &bSpeed " + toRoman(speedAmp + 1) + " &7for &e" + speedDur + "s\n&7& Invisibility for &e" + invisDur + "s &7on hit";
    }

    @Override
    public boolean execute(Player player, ItemAbilityData data, AbilityCooldownManager cooldownManager, String itemId) {
        // This is ON_HIT, handled via executeOnHit pattern if needed
        int speedAmplifier = data.getConfigInt("speedAmplifier");
        int speedDuration = data.getConfigInt("speedDuration");
        int invisibilityDuration = data.getConfigInt("invisibilityDuration");

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, speedDuration * 20, speedAmplifier, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, invisibilityDuration * 20, 0, false, false, true));

        // Shadow particles
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.05);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 15, 0.5, 1, 0.5,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(30, 30, 30), 1.5f));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);

        player.sendMessage(ColorUtils.colorize("&8🖤 Vanished into shadows!"));
        return true;
    }

    private String toRoman(int num) {
        String[] romans = {"", "I", "II", "III", "IV", "V", "VI"};
        if (num > 0 && num < romans.length) return romans[num];
        return String.valueOf(num);
    }
}