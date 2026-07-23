package theLifesteal.abilities.abilities;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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

import java.util.LinkedHashMap;
import java.util.Map;

public class GravityPullAbility extends ItemAbility {

    public GravityPullAbility(JavaPlugin plugin) {
        super(plugin, "gravity_pull", "Gravity Pull", ItemAbilityType.RIGHT_CLICK);
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("pullRange", 12);
        config.put("pullStrength", 2.0);
        config.put("coneAngle", 40);
        config.put("cooldown", 20);
        config.put("cooldownScope", "ITEM");
        return config;
    }

    @Override
    public Map<String, ConfigField> getConfigFields() {
        Map<String, ConfigField> fields = new LinkedHashMap<>();
        fields.put("pullRange", new ConfigField("Pull Range", "int", 3, 50));
        fields.put("pullStrength", new ConfigField("Pull Strength", "double", 0.5, 10.0));
        fields.put("coneAngle", new ConfigField("Cone Angle (degrees)", "int", 10, 90));
        fields.put("cooldown", new ConfigField("Cooldown (seconds)", "int", 0, 3600));
        fields.put("cooldownScope", new ConfigField("Cooldown Scope", "string"));
        return fields;
    }

    @Override
    public String buildLore(ItemAbilityData data) {
        int range = data.getConfigInt("pullRange");
        double strength = data.getConfigDouble("pullStrength");
        int cooldown = data.getConfigInt("cooldown");
        return "&7Pull targets within &b" + range + " blocks &7with &d" + String.format("%.1f", strength) + "x force &8(&e" + cooldown + "s cooldown&8)";
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

        int pullRange = data.getConfigInt("pullRange");
        double pullStrength = data.getConfigDouble("pullStrength");
        int coneAngle = data.getConfigInt("coneAngle");
        double coneDot = Math.cos(Math.toRadians(coneAngle));

        Location playerLoc = player.getEyeLocation();
        Vector lookDir = playerLoc.getDirection().normalize();

        int pulled = 0;

        for (Entity entity : player.getNearbyEntities(pullRange, pullRange, pullRange)) {
            if (!(entity instanceof LivingEntity) || entity == player) continue;

            Location entityLoc = entity.getLocation();
            Vector toEntity = entityLoc.toVector().subtract(playerLoc.toVector());
            double distance = toEntity.length();

            if (distance > pullRange) continue;

            toEntity.normalize();
            double dot = lookDir.dot(toEntity);

            if (dot < coneDot) continue;
            if (!hasLineOfSight(playerLoc, entityLoc, distance)) continue;

            Vector pullDir = playerLoc.toVector().subtract(entityLoc.toVector()).normalize();
            double pullForce = pullStrength * (1.0 - (distance / pullRange)) + 0.5;
            Vector velocity = pullDir.multiply(pullForce);
            velocity.setY(velocity.getY() + 0.4);

            entity.setVelocity(velocity);

            for (int i = 0; i < 15; i++) {
                double progress = (double) i / 15;
                Location particleLoc = entityLoc.clone().add(
                        playerLoc.toVector().subtract(entityLoc.toVector()).normalize().multiply(distance * progress));
                entity.getWorld().spawnParticle(Particle.DUST,
                        particleLoc, 1, 0.1, 0.1, 0.1,
                        new Particle.DustOptions(org.bukkit.Color.PURPLE, 1f));
                entity.getWorld().spawnParticle(Particle.WITCH,
                        particleLoc, 1, 0.1, 0.1, 0.1, 0);
            }

            for (int i = 0; i < 5; i++) {
                entity.getWorld().spawnParticle(Particle.DUST,
                        entityLoc.clone().add(0, 1, 0),
                        1, 0.3, 0.5, 0.3,
                        new Particle.DustOptions(org.bukkit.Color.PURPLE, 1.5f));
            }

            pulled++;
        }

        if (pulled > 0) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 0.5f);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 2.0f);

            for (int i = 0; i < 40; i++) {
                double angle = Math.random() * Math.PI * 2;
                double radius = Math.random() * 3;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                player.getWorld().spawnParticle(Particle.DUST,
                        playerLoc.clone().add(x, Math.random() * 2, z),
                        1, 0, 0, 0,
                        new Particle.DustOptions(org.bukkit.Color.PURPLE, 2f));
            }

            player.sendMessage(ColorUtils.colorize("&5🕳 Pulled &d" + pulled + " &5targets!"));

            if (cooldown > 0) {
                cooldownManager.setCooldown(player.getUniqueId(), getId(), itemId, scope, cooldown);
            }
            return true;
        } else {
            player.sendMessage(ColorUtils.colorize("&7No targets in range."));
            return false;
        }
    }

    private boolean hasLineOfSight(Location start, Location end, double distance) {
        Vector direction = end.toVector().subtract(start.toVector()).normalize();
        for (double d = 0; d <= distance; d += 0.5) {
            Location check = start.clone().add(direction.clone().multiply(d));
            if (check.getBlock().getType().isSolid() &&
                    !check.getBlock().getType().name().contains("GLASS") &&
                    !check.getBlock().getType().name().contains("LEAVES")) {
                return false;
            }
        }
        return true;
    }
}