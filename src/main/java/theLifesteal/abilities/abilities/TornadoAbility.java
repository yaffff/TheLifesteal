package theLifesteal.abilities.abilities;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import theLifesteal.ColorUtils;
import theLifesteal.abilities.AbilityCooldownManager;
import theLifesteal.abilities.ItemAbility;
import theLifesteal.abilities.ItemAbilityData;
import theLifesteal.abilities.ItemAbilityType;

import java.util.*;

public class TornadoAbility extends ItemAbility {

    public TornadoAbility(JavaPlugin plugin) {
        super(plugin, "tornado", "Tornado", ItemAbilityType.RIGHT_CLICK);
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("range", 8);
        config.put("tornadoHeight", 10);
        config.put("pullRadius", 5);
        config.put("launchPower", 3.0);
        config.put("duration", 4);
        config.put("damage", 6.0);
        config.put("cooldown", 25);
        return config;
    }

    @Override
    public Map<String, ConfigField> getConfigFields() {
        Map<String, ConfigField> fields = new LinkedHashMap<>();
        fields.put("range", new ConfigField("Cast Range", "int", 3, 20));
        fields.put("tornadoHeight", new ConfigField("Tornado Height", "int", 5, 25));
        fields.put("pullRadius", new ConfigField("Pull Radius", "int", 2, 12));
        fields.put("launchPower", new ConfigField("Launch Power", "double", 1.0, 8.0));
        fields.put("duration", new ConfigField("Duration (seconds)", "int", 2, 15));
        fields.put("damage", new ConfigField("Damage", "double", 1.0, 30.0));
        fields.put("cooldown", new ConfigField("Cooldown (seconds)", "int", 0, 3600));
        return fields;
    }

    @Override
    public String buildLore(ItemAbilityData data) {
        int range = data.getConfigInt("range");
        int pullRadius = data.getConfigInt("pullRadius");
        double launch = data.getConfigDouble("launchPower");
        int duration = data.getConfigInt("duration");
        int cooldown = data.getConfigInt("cooldown");
        return "&7Summon a tornado at target location\n&7Pulls enemies in &b" + pullRadius + " blocks &7& launches up\n&8(&e" + duration + "s&7, &e" + cooldown + "s cooldown&8)";
    }

    @Override
    public boolean execute(Player player, ItemAbilityData data, AbilityCooldownManager cooldownManager, String itemId) {
        int cooldown = data.getConfigInt("cooldown");

        if (cooldownManager.isOnCooldown(player.getUniqueId(), getId(), itemId)) {
            long remaining = cooldownManager.getRemainingCooldown(player.getUniqueId(), getId(), itemId);
            player.sendMessage(ColorUtils.colorize("&cOn cooldown! &7(" + cooldownManager.formatCooldown(remaining) + ")"));
            return false;
        }

        int range = data.getConfigInt("range");
        int tornadoHeight = data.getConfigInt("tornadoHeight");
        int pullRadius = data.getConfigInt("pullRadius");
        double launchPower = data.getConfigDouble("launchPower");
        int duration = data.getConfigInt("duration");
        double damage = data.getConfigDouble("damage");

        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();
        Location tornadoLoc = eyeLoc.clone();

        for (double d = 0; d <= range; d += 0.5) {
            Location check = eyeLoc.clone().add(direction.clone().multiply(d));
            if (check.getBlock().getType().isSolid()) {
                tornadoLoc = check.clone().add(0, 1, 0);
                break;
            }
            if (d >= range - 0.4) {
                tornadoLoc = check;
            }
        }

        final Location center = tornadoLoc.clone();
        final int totalTicks = duration * 20;

        player.getWorld().playSound(center, Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 1.0f);
        player.getWorld().playSound(center, Sound.ENTITY_BREEZE_IDLE_AIR, 1.0f, 0.5f);

        Set<UUID> hitEntities = new HashSet<>();

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= totalTicks) {
                    center.getWorld().spawnParticle(Particle.CLOUD, center, 20, pullRadius * 0.8, 2, pullRadius * 0.8, 0.1);
                    center.getWorld().playSound(center, Sound.ENTITY_BREEZE_DEATH, 1.0f, 1.5f);
                    this.cancel();
                    return;
                }

                double progress = (double) tick / totalTicks;
                double currentRadius = pullRadius * (0.4 + progress * 0.6);

                // Spiral particles - 3 spiraling strands, much fewer particles
                for (int strand = 0; strand < 3; strand++) {
                    double strandOffset = strand * Math.PI * 2 / 3;
                    for (double y = 0; y < tornadoHeight; y += 1.2) {
                        double heightProgress = y / tornadoHeight;
                        double radius = currentRadius * (1.0 - heightProgress * 0.5);
                        double angle = (tick * 0.4) + (heightProgress * 6) + strandOffset;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;

                        center.getWorld().spawnParticle(Particle.CLOUD,
                                center.clone().add(x, y, z),
                                1, 0, 0, 0, 0.03);
                    }
                }

                // Minimal ground ring
                for (int i = 0; i < 12; i++) {
                    double angle = i * Math.PI * 2 / 12;
                    double x = Math.cos(angle) * currentRadius;
                    double z = Math.sin(angle) * currentRadius;
                    center.getWorld().spawnParticle(Particle.CLOUD,
                            center.clone().add(x, 0.1, z),
                            1, 0, 0, 0, 0.02);
                }

                // Pull and launch entities
                for (Entity entity : center.getWorld().getNearbyEntities(center, currentRadius + 3, tornadoHeight + 5, currentRadius + 3)) {
                    if (!(entity instanceof LivingEntity) || entity == player) continue;

                    LivingEntity target = (LivingEntity) entity;
                    Location entityLoc = target.getLocation();
                    double dist = entityLoc.distance(center.clone());

                    if (dist <= currentRadius) {
                        Vector toCenter = center.toVector().subtract(entityLoc.toVector());
                        toCenter.setY(0);
                        if (toCenter.length() > 0.3) {
                            toCenter.normalize().multiply(0.2);
                        }

                        double upForce = 0;
                        if (entityLoc.getY() < center.getY() + tornadoHeight) {
                            upForce = Math.min(launchPower * 0.3, 1.5);
                        }

                        Vector force = new Vector(toCenter.getX(), upForce, toCenter.getZ());
                        target.setVelocity(force);

                        Vector spinDir = entityLoc.toVector().subtract(center.toVector());
                        spinDir.setY(0);
                        if (spinDir.length() > 0.01) {
                            spinDir.normalize();
                            Vector tangent = new Vector(-spinDir.getZ(), 0, spinDir.getX());
                            target.setVelocity(target.getVelocity().add(tangent.multiply(0.2)));
                        }

                        UUID entityId = entity.getUniqueId();
                        if (!hitEntities.contains(entityId)) {
                            hitEntities.add(entityId);
                            target.damage(damage, player);
                        }
                    }
                }

                if (tick % 15 == 0) {
                    center.getWorld().playSound(center, Sound.ENTITY_BREEZE_IDLE_AIR, 0.2f, 0.5f + (float)progress);
                }

                tick++;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        player.sendMessage(ColorUtils.colorize("&f🌪 Tornado unleashed!"));
        cooldownManager.setCooldown(player.getUniqueId(), getId(), itemId, cooldown);
        return true;
    }
}