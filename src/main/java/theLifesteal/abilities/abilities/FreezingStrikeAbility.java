package theLifesteal.abilities.abilities;

import org.bukkit.Location;
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

import java.util.LinkedHashMap;
import java.util.Map;

public class FreezingStrikeAbility extends ItemAbility {

    public FreezingStrikeAbility(JavaPlugin plugin) {
        super(plugin, "freezing_strike", "Freezing Strike", ItemAbilityType.ON_HIT);
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("chance", 15);
        config.put("slownessAmplifier", 4);
        config.put("duration", 3);
        config.put("affectPlayers", true);
        config.put("affectMobs", true);
        return config;
    }

    @Override
    public Map<String, ConfigField> getConfigFields() {
        Map<String, ConfigField> fields = new LinkedHashMap<>();
        fields.put("chance", new ConfigField("Chance (%)", "int", 1, 100));
        fields.put("slownessAmplifier", new ConfigField("Freeze Intensity (0-10)", "int", 0, 10));
        fields.put("duration", new ConfigField("Freeze Duration (seconds)", "int", 1, 30));
        fields.put("affectPlayers", new ConfigField("Affect Players", "boolean"));
        fields.put("affectMobs", new ConfigField("Affect Mobs", "boolean"));
        return fields;
    }

    @Override
    public String buildLore(ItemAbilityData data) {
        int chance = data.getConfigInt("chance");
        int duration = data.getConfigInt("duration");
        return "&7Freeze target on hit &8(&b" + chance + "% chance&7, &3" + duration + "s&8)";
    }

    public boolean executeOnHit(Player attacker, LivingEntity victim, ItemAbilityData data,
                                AbilityCooldownManager cooldownManager, String itemId) {
        if (victim instanceof Player && !data.getConfigBoolean("affectPlayers")) return false;
        if (!(victim instanceof Player) && !data.getConfigBoolean("affectMobs")) return false;

        int chance = data.getConfigInt("chance");
        if (Math.random() * 100 >= chance) return false;

        int slownessAmplifier = data.getConfigInt("slownessAmplifier");
        int duration = data.getConfigInt("duration");

        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration * 20, slownessAmplifier, false, true, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration * 20, 1, false, true, true));

        Location victimLoc = victim.getLocation();
        victim.getWorld().playSound(victimLoc, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.5f);
        victim.getWorld().playSound(victimLoc, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 1.0f);

        for (int i = 0; i < 30; i++) {
            victim.getWorld().spawnParticle(Particle.SNOWFLAKE,
                    victimLoc.clone().add(Math.random() * 1.5 - 0.75, Math.random() * 2.5, Math.random() * 1.5 - 0.75),
                    1, 0, 0, 0, 0.02);
        }
        for (int i = 0; i < 15; i++) {
            victim.getWorld().spawnParticle(Particle.DUST,
                    victimLoc.clone().add(Math.random() * 1.5 - 0.75, Math.random() * 2.5, Math.random() * 1.5 - 0.75),
                    1, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.AQUA, 1.5f));
        }
        for (int i = 0; i < 10; i++) {
            double angle = Math.random() * Math.PI * 2;
            double x = Math.cos(angle) * 1.2;
            double z = Math.sin(angle) * 1.2;
            victim.getWorld().spawnParticle(Particle.BLOCK,
                    victimLoc.clone().add(x, 0.5 + Math.random() * 1.5, z),
                    1, 0, 0, 0, org.bukkit.Material.ICE.createBlockData());
        }

        attacker.sendMessage(ColorUtils.colorize("&b❄ Froze target for " + duration + "s!"));
        return true;
    }

    @Override
    public boolean execute(Player player, ItemAbilityData data, AbilityCooldownManager cooldownManager, String itemId) {
        return false;
    }
}