package theLifesteal.abilities.abilities;

import org.bukkit.Bukkit;
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

import java.util.*;

public class BerserkAbility extends ItemAbility {

    private final Set<UUID> ignoreDamage;

    public BerserkAbility(JavaPlugin plugin) {
        super(plugin, "berserk", "Berserk", ItemAbilityType.ON_HIT);
        this.ignoreDamage = new HashSet<>();
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("damagePerMissingHP", 0.05);
        config.put("maxBonusDamage", 15.0);
        config.put("affectPlayers", true);
        config.put("affectMobs", true);
        config.put("trigger_on", "FULL_ATTACK");
        config.put("affectPassive", true);
        config.put("affectHostile", true);
        return config;
    }

    @Override
    public Map<String, ConfigField> getConfigFields() {
        Map<String, ConfigField> fields = new LinkedHashMap<>();
        fields.put("damagePerMissingHP", new ConfigField("Damage per Missing HP", "double", 0.01, 1.0));
        fields.put("maxBonusDamage", new ConfigField("Max Bonus Damage", "double", 1.0, 50.0));
        fields.put("affectPlayers", new ConfigField("Affect Players", "boolean"));
        fields.put("affectMobs", new ConfigField("Affect Mobs", "boolean"));
        fields.put("trigger_on", new ConfigField("Trigger On", "string"));
        fields.put("affectPassive", new ConfigField("Affect Passive Mobs", "boolean"));
        fields.put("affectHostile", new ConfigField("Affect Hostile Mobs", "boolean"));
        return fields;
    }

    @Override
    public String buildLore(ItemAbilityData data) {
        double perHP = data.getConfigDouble("damagePerMissingHP");
        double max = data.getConfigDouble("maxBonusDamage");
        return "&7Deal &c+" + String.format("%.2f", perHP) + " damage &7per missing HP\n&7Max bonus &c" + formatDamage(max) + " &7| More hurt = more damage";
    }

    @Override
    public boolean onHitExecute(Player attacker, LivingEntity victim, ItemAbilityData data,
                                AbilityCooldownManager cooldownManager, String itemId, double baseDamage) {
        // Prevent recursive damage
        if (ignoreDamage.contains(victim.getUniqueId())) return false;
        if (victim instanceof Player && !data.getConfigBoolean("affectPlayers")) return false;
        if (!(victim instanceof Player) && !data.getConfigBoolean("affectMobs")) return false;

        double damagePerMissingHP = data.getConfigDouble("damagePerMissingHP");
        double maxBonusDamage = data.getConfigDouble("maxBonusDamage");

        double maxHP = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
        double currentHP = attacker.getHealth();
        double missingHP = maxHP - currentHP;

        double bonusDamage = Math.min(missingHP * damagePerMissingHP, maxBonusDamage);

        if (bonusDamage > 0) {
            UUID victimId = victim.getUniqueId();
            ignoreDamage.add(victimId);
            recordAbilityDamage(attacker, victim);
            victim.damage(bonusDamage, attacker);
            Bukkit.getScheduler().runTaskLater(getPlugin(), () -> ignoreDamage.remove(victimId), 2L);

            double intensity = Math.min(1.0, missingHP / maxHP);
            victim.getWorld().spawnParticle(Particle.DUST,
                    victim.getLocation().add(0, 1.5, 0),
                    5 + (int)(intensity * 15), 0.3, 0.5, 0.3,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 0, 0), 1.5f));

            if (intensity > 0.5) {
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 0.8f);
            }
        }

        return true;
    }

    @Override
    public boolean execute(Player player, ItemAbilityData data, AbilityCooldownManager cooldownManager, String itemId) {
        return false; // ON_HIT abilities don't use execute()
    }

    private String formatDamage(double damage) {
        if (damage == Math.floor(damage)) return (int) damage + "❤";
        return damage + "❤";
    }
}