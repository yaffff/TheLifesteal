package theLifesteal.abilities.abilities;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import theLifesteal.ColorUtils;
import theLifesteal.abilities.AbilityCooldownManager;
import theLifesteal.abilities.ItemAbility;
import theLifesteal.abilities.ItemAbilityData;
import theLifesteal.abilities.ItemAbilityType;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class PlasmaBlastAbility extends ItemAbility {

    public PlasmaBlastAbility(JavaPlugin plugin) {
        super(plugin, "plasma_blast", "Plasma Blast", ItemAbilityType.RIGHT_CLICK);
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("damage", 12.0);
        config.put("range", 25.0);
        config.put("hpCostPct", 0.0);
        config.put("cooldown", 10);
        config.put("cooldownScope", "ITEM");
        return config;
    }

    @Override
    public Map<String, ConfigField> getConfigFields() {
        Map<String, ConfigField> fields = new LinkedHashMap<>();
        fields.put("damage", new ConfigField("Damage", "double", 1.0, 100.0));
        fields.put("range", new ConfigField("Range (blocks)", "double", 5.0, 100.0));
        fields.put("hpCostPct", new ConfigField("HP Cost (%)", "double", 0.0, 100.0));
        fields.put("cooldown", new ConfigField("Cooldown (seconds)", "int", 0, 3600));
        fields.put("cooldownScope", new ConfigField("Cooldown Scope", "string"));
        return fields;
    }

    @Override
    public String buildLore(ItemAbilityData data) {
        double damage = data.getConfigDouble("damage");
        double range = data.getConfigDouble("range");
        double hpCostPct = data.getConfigDouble("hpCostPct");
        int cooldown = data.getConfigInt("cooldown");

        StringBuilder sb = new StringBuilder();
        sb.append("&7Shoots a high-energy plasma ray forward.\n");
        sb.append("&7Deals &c").append(damage).append(" &7damage up to &e").append(range).append(" blocks&7.\n");
        if (hpCostPct > 0) {
            sb.append("&7Costs &c").append((int) hpCostPct).append("% HP&7. ");
        }
        sb.append("&7Cooldown: &e").append(cooldown).append("s");
        return sb.toString();
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

        double requiredHp = getRequiredHpCost(data, player);
        if (requiredHp > 0) {
            if (!checkStrictHealthRequirement(player, requiredHp)) {
                return false;
            }
            applySelfHealthCost(player, requiredHp);
        }

        if (cooldown > 0) {
            cooldownManager.setCooldown(player.getUniqueId(), getId(), itemId, scope, cooldown);
        }

        // Plasma Beam Raycast
        double damage = data.getConfigDouble("damage");
        double range = data.getConfigDouble("range");

        Location eyeLoc = player.getEyeLocation();
        Vector dir = eyeLoc.getDirection().normalize();

        player.getWorld().playSound(eyeLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.8f);
        player.getWorld().playSound(eyeLoc, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.5f);

        Set<LivingEntity> hitVictims = new HashSet<>();

        for (double d = 0.5; d <= range; d += 0.5) {
            Location point = eyeLoc.clone().add(dir.clone().multiply(d));

            // Check block collision
            if (point.getBlock().getType().isSolid()) {
                player.getWorld().spawnParticle(Particle.FLASH, point, 1);
                player.getWorld().spawnParticle(Particle.EXPLOSION, point, 1);
                break;
            }

            // Beam Particles - FIXED 8 PARAMETERS FOR DUST (extra=0.0 before DustOptions)
            player.getWorld().spawnParticle(Particle.DUST, point, 2, 0.05, 0.05, 0.05, 0.0,
                    new Particle.DustOptions(Color.fromRGB(0, 230, 255), 1.5f));
            player.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0.02, 0.02, 0.02, 0.01);

            // Entity Hit Detection
            for (Entity entity : point.getWorld().getNearbyEntities(point, 0.8, 0.8, 0.8)) {
                if (entity instanceof LivingEntity victim && !victim.getUniqueId().equals(player.getUniqueId())) {
                    if (hitVictims.add(victim)) {
                        dealAbilityDamage(player, victim, damage);
                        point.getWorld().spawnParticle(Particle.FLASH, victim.getLocation().add(0, 1, 0), 1);
                        point.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, victim.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.1);
                        point.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0f, 1.5f);
                    }
                }
            }
        }

        player.sendMessage(ColorUtils.colorize("&b⚡ &7You fired a &bPlasma Blast&7!"));
        return true;
    }
}
