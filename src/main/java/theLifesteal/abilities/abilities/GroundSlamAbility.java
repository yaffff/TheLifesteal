package theLifesteal.abilities.abilities;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import theLifesteal.ColorUtils;
import theLifesteal.abilities.AbilityCooldownManager;
import theLifesteal.abilities.ItemAbility;
import theLifesteal.abilities.ItemAbilityData;
import theLifesteal.abilities.ItemAbilityType;

import java.util.LinkedHashMap;
import java.util.Map;

public class GroundSlamAbility extends ItemAbility {

    public GroundSlamAbility(JavaPlugin plugin) {
        super(plugin, "ground_slam", "Ground Slam", ItemAbilityType.RIGHT_CLICK);
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("launchHeight", 5);
        config.put("slamRadius", 8);
        config.put("lightningStrikes", 5);
        config.put("strikeDelay", 4);
        config.put("damage", 8.0);
        config.put("slownessDuration", 4);
        config.put("slownessAmplifier", 1);
        config.put("cooldown", 30);
        return config;
    }

    @Override
    public Map<String, ConfigField> getConfigFields() {
        Map<String, ConfigField> fields = new LinkedHashMap<>();
        fields.put("launchHeight", new ConfigField("Launch Height", "int", 3, 20));
        fields.put("slamRadius", new ConfigField("Slam Radius", "int", 3, 30));
        fields.put("lightningStrikes", new ConfigField("Lightning Strikes", "int", 1, 20));
        fields.put("strikeDelay", new ConfigField("Ticks Between Strikes", "int", 1, 40));
        fields.put("damage", new ConfigField("Damage per Strike", "double", 0.0, 100.0));
        fields.put("slownessDuration", new ConfigField("Slowness Duration (s)", "int", 1, 30));
        fields.put("slownessAmplifier", new ConfigField("Slowness Level (0=I)", "int", 0, 5));
        fields.put("cooldown", new ConfigField("Cooldown (seconds)", "int", 0, 3600));
        return fields;
    }

    @Override
    public String buildLore(ItemAbilityData data) {
        int radius = data.getConfigInt("slamRadius");
        double damage = data.getConfigDouble("damage");
        int strikes = data.getConfigInt("lightningStrikes");
        int cooldown = data.getConfigInt("cooldown");
        return "&7Leap up & slam down with &e" + strikes + " &7lightning strikes\n&7Deals &c" + formatDamage(damage) + " &7in &b" + radius + " block radius & slows\n&8(&e" + cooldown + "s cooldown&8)";    }

    @Override
    public boolean execute(Player player, ItemAbilityData data, AbilityCooldownManager cooldownManager, String itemId) {
        int cooldown = data.getConfigInt("cooldown");

        if (cooldownManager.isOnCooldown(player.getUniqueId(), getId(), itemId)) {
            long remaining = cooldownManager.getRemainingCooldown(player.getUniqueId(), getId(), itemId);
            player.sendMessage(ColorUtils.colorize("&cOn cooldown! &7(" + cooldownManager.formatCooldown(remaining) + ")"));
            return false;
        }

        int launchHeight = data.getConfigInt("launchHeight");
        final int slamRadius = data.getConfigInt("slamRadius");
        final int lightningStrikes = data.getConfigInt("lightningStrikes");
        final int strikeDelay = data.getConfigInt("strikeDelay");
        final double damage = data.getConfigDouble("damage");
        final int slownessDuration = data.getConfigInt("slownessDuration");
        final int slownessAmplifier = data.getConfigInt("slownessAmplifier");

        // Check for blocks overhead
        Location checkLoc = player.getLocation();
        for (int y = 1; y <= 6; y++) {
            if (checkLoc.clone().add(0, y, 0).getBlock().getType().isSolid()) {
                player.sendMessage(ColorUtils.colorize("&cNot enough space above you!"));
                return false;
            }
        }

        player.setVelocity(new Vector(0, launchHeight * 0.4, 0));
        Location launchLoc = player.getLocation().clone();
        player.getWorld().playSound(launchLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);

        for (int i = 0; i < 25; i++) {
            player.getWorld().spawnParticle(Particle.FLAME,
                    launchLoc.clone().add(Math.random() * 0.8 - 0.4, i * 0.25, Math.random() * 0.8 - 0.4),
                    1, 0, 0, 0, 0.01);
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;

                if (player.isOnGround() && ticks > 5) {
                    executeSlam(player, slamRadius, damage, lightningStrikes, strikeDelay,
                            slownessDuration, slownessAmplifier);
                    this.cancel();
                    return;
                }

                if (ticks > 200) {
                    this.cancel();
                    return;
                }

                if (player.getVelocity().getY() < 0 && !player.isOnGround()) {
                    Location airLoc = player.getLocation();
                    for (int i = 0; i < 4; i++) {
                        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                                airLoc.clone().add(Math.random() * 1.2 - 0.6, Math.random() * 1.2 - 0.6, Math.random() * 1.2 - 0.6),
                                1, 0, 0, 0, 0);
                    }
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);



        player.sendMessage(ColorUtils.colorize("&e⚡ Ground Slam!"));
        cooldownManager.setCooldown(player.getUniqueId(), getId(), itemId, cooldown);
        return true;
    }

    private void executeSlam(Player player, int slamRadius, double damage, int lightningStrikes,
                             int strikeDelay, int slownessDuration, int slownessAmplifier) {
        Location groundLoc = player.getLocation().clone();

        player.getWorld().playSound(groundLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
        player.getWorld().playSound(groundLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.3f);

        // Shockwave rings
        for (int ring = 0; ring < 5; ring++) {
            double radius = ring * 1.5;
            for (int i = 0; i < 36; i++) {
                double angle = i * Math.PI * 2 / 36;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                groundLoc.getWorld().spawnParticle(Particle.DUST,
                        groundLoc.clone().add(x, 0.1, z),
                        1, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.YELLOW, 1.5f));
            }
        }

        // Ground explosion particles
        for (int i = 0; i < 80; i++) {
            double angle = Math.random() * Math.PI * 2;
            double dist = Math.random() * slamRadius;
            double x = Math.cos(angle) * dist;
            double z = Math.sin(angle) * dist;
            groundLoc.getWorld().spawnParticle(Particle.CLOUD,
                    groundLoc.clone().add(x, 0.1, z), 1, 0, 0.1, 0, 0.03);
        }

        groundLoc.getWorld().spawnParticle(Particle.EXPLOSION,
                groundLoc.clone().add(0, 0.5, 0), 3, 0.5, 0.5, 0.5, 0.1);

        // Lightning strikes with configurable delay
        for (int strike = 0; strike < lightningStrikes; strike++) {
            final long delay = (long) strike * strikeDelay;
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Entity entity : player.getNearbyEntities(slamRadius, 5, slamRadius)) {
                        if (entity instanceof LivingEntity && entity != player) {
                            double dist = entity.getLocation().distance(groundLoc);
                            if (dist <= slamRadius) {
                                Location strikeLoc = entity.getLocation().clone();
                                strikeLoc.getWorld().strikeLightningEffect(strikeLoc);
                                ((LivingEntity) entity).damage(damage, player);

                                ((LivingEntity) entity).addPotionEffect(new PotionEffect(
                                        PotionEffectType.SLOWNESS, slownessDuration * 20,
                                        slownessAmplifier, false, true, true));

                                for (int j = 0; j < 20; j++) {
                                    strikeLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                                            strikeLoc.clone().add(Math.random() * 3 - 1.5, Math.random() * 2.5, Math.random() * 3 - 1.5),
                                            1, 0, 0, 0, 0);
                                }
                                strikeLoc.getWorld().spawnParticle(Particle.FLASH,
                                        strikeLoc.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
                            }
                        }
                    }
                }
            }.runTaskLater(getPlugin(), delay);
        }
    }

    private String formatDamage(double damage) {
        if (damage == Math.floor(damage)) return (int) damage + "❤";
        return damage + "❤";
    }
}