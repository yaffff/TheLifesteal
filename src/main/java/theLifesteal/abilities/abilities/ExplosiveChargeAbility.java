package theLifesteal.abilities.abilities;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import theLifesteal.ColorUtils;
import theLifesteal.abilities.AbilityCooldownManager;
import theLifesteal.abilities.ItemAbility;
import theLifesteal.abilities.ItemAbilityData;
import theLifesteal.abilities.ItemAbilityType;

import java.util.*;

public class ExplosiveChargeAbility extends ItemAbility {

    private final Map<UUID, ChargeSession> chargeSessions;

    public ExplosiveChargeAbility(JavaPlugin plugin) {
        super(plugin, "explosive_charge", "Explosive Charge", ItemAbilityType.RIGHT_CLICK);
        this.chargeSessions = new HashMap<>();
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("chargeTime", 4);
        config.put("explosionRadius", 8);
        config.put("maxDamage", 20.0);
        config.put("knockbackPower", 3.0);
        config.put("blindnessDuration", 3);
        config.put("selfDamage", false);
        config.put("cooldown", 45);
        return config;
    }

    @Override
    public Map<String, ConfigField> getConfigFields() {
        Map<String, ConfigField> fields = new LinkedHashMap<>();
        fields.put("chargeTime", new ConfigField("Charge Time (seconds)", "int", 2, 15));
        fields.put("explosionRadius", new ConfigField("Explosion Radius", "int", 3, 20));
        fields.put("maxDamage", new ConfigField("Max Damage", "double", 5.0, 100.0));
        fields.put("knockbackPower", new ConfigField("Knockback Power", "double", 0.5, 8.0));
        fields.put("blindnessDuration", new ConfigField("Blindness Duration (s)", "int", 0, 10));
        fields.put("selfDamage", new ConfigField("Self Damage", "boolean"));
        fields.put("cooldown", new ConfigField("Cooldown (seconds)", "int", 0, 3600));
        return fields;
    }

    @Override
    public String buildLore(ItemAbilityData data) {
        int chargeTime = data.getConfigInt("chargeTime");
        double maxDmg = data.getConfigDouble("maxDamage");
        int radius = data.getConfigInt("explosionRadius");
        int cooldown = data.getConfigInt("cooldown");
        return "&7Hold to charge an explosion over &e" + chargeTime + "s\n&7Deals up to &c" + formatDamage(maxDmg) + " &7in &b" + radius + " block radius\n&8(&e" + cooldown + "s cooldown&8)";
    }

    @Override
    public boolean execute(Player player, ItemAbilityData data, AbilityCooldownManager cooldownManager, String itemId) {
        int cooldown = data.getConfigInt("cooldown");

        if (cooldownManager.isOnCooldown(player.getUniqueId(), getId(), itemId)) {
            long remaining = cooldownManager.getRemainingCooldown(player.getUniqueId(), getId(), itemId);
            player.sendMessage(ColorUtils.colorize("&cOn cooldown! &7(" + cooldownManager.formatCooldown(remaining) + ")"));
            return false;
        }

        UUID uuid = player.getUniqueId();

        if (chargeSessions.containsKey(uuid)) return true;

        int chargeTime = data.getConfigInt("chargeTime");
        int explosionRadius = data.getConfigInt("explosionRadius");
        double maxDamage = data.getConfigDouble("maxDamage");
        double knockbackPower = data.getConfigDouble("knockbackPower");
        int blindnessDuration = data.getConfigInt("blindnessDuration");
        boolean selfDamage = data.getConfigBoolean("selfDamage");

        int totalTicks = chargeTime * 20;

        final Location chargeLocation = player.getLocation().clone();
        player.setWalkSpeed(0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, totalTicks + 40, -10, false, false, false));

        BossBar bossBar = Bukkit.createBossBar(
                ColorUtils.colorize("&7💣 Charging... &f0%"),
                BarColor.RED, BarStyle.SOLID);
        bossBar.addPlayer(player);
        bossBar.setProgress(0.0);

        ChargeSession session = new ChargeSession();
        session.chargeLocation = chargeLocation;
        chargeSessions.put(uuid, session);

        player.sendMessage(ColorUtils.colorize("&7💣 Charging... Hold your item!"));

        NamespacedKey customItemKey = new NamespacedKey(getPlugin(), "custom_item_id");

        BukkitTask task = new BukkitRunnable() {
            int tick = 0;
            int offTick = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelCharge(player, bossBar);
                    this.cancel();
                    return;
                }

                // Lock player to charge position
                if (!player.getLocation().getBlock().equals(chargeLocation.getBlock())) {
                    Location currentLoc = player.getLocation().clone();
                    currentLoc.setX(chargeLocation.getX());
                    currentLoc.setZ(chargeLocation.getZ());
                    if (currentLoc.getY() < chargeLocation.getY()) {
                        currentLoc.setY(chargeLocation.getY());
                    }
                    currentLoc.setYaw(player.getLocation().getYaw());
                    currentLoc.setPitch(player.getLocation().getPitch());
                    player.teleport(currentLoc);
                }

                // Block any velocity
                if (player.getVelocity().length() > 0.01) {
                    player.setVelocity(new Vector(0, 0, 0));
                }

                // Check if holding the custom item
                boolean holdingItem = false;
                if (player.getInventory().getItemInMainHand() != null &&
                        player.getInventory().getItemInMainHand().hasItemMeta() &&
                        player.getInventory().getItemInMainHand().getItemMeta().getPersistentDataContainer()
                                .has(customItemKey, PersistentDataType.STRING)) {
                    holdingItem = true;
                }

                if (!holdingItem) {
                    offTick++;
                    if (offTick >= 30) {
                        cancelCharge(player, bossBar);
                        player.sendMessage(ColorUtils.colorize("&cCharge cancelled - you let go!"));
                        this.cancel();
                        return;
                    }
                    if (offTick == 10) {
                        player.sendMessage(ColorUtils.colorize("&eRe-equip the item to keep charging!"));
                    }
                    return;
                } else {
                    offTick = 0;
                }

                if (tick >= totalTicks) {
                    bossBar.removeAll();
                    chargeSessions.remove(uuid);
                    explode(player, maxDamage, explosionRadius, knockbackPower, blindnessDuration, selfDamage,
                            cooldownManager, itemId, cooldown);
                    resetPlayer(player);
                    this.cancel();
                    return;
                }

                double progress = (double) tick / totalTicks;
                int percent = (int) (progress * 100);

                bossBar.setProgress(Math.min(1.0, progress));
                if (progress < 0.5) {
                    bossBar.setTitle(ColorUtils.colorize("&7💣 Charging... &f" + percent + "%"));
                    bossBar.setColor(BarColor.YELLOW);
                } else if (progress < 0.85) {
                    bossBar.setTitle(ColorUtils.colorize("&6💣 Building... &f" + percent + "%"));
                    bossBar.setColor(BarColor.RED);
                } else {
                    bossBar.setTitle(ColorUtils.colorize("&c💥 ABOUT TO EXPLODE! &f" + percent + "%"));
                    bossBar.setColor(BarColor.RED);
                }

                Location loc = player.getLocation().add(0, 1, 0);

                org.bukkit.Color color;
                if (progress < 0.3) {
                    double p = progress / 0.3;
                    color = org.bukkit.Color.fromRGB((int)(40 + p * 215), (int)(40 + p * 125), (int)(40 + p * 0));
                } else if (progress < 0.6) {
                    double p = (progress - 0.3) / 0.3;
                    color = org.bukkit.Color.fromRGB(255, (int)(165 + p * 90), (int)(p * 255));
                } else {
                    double p = (progress - 0.6) / 0.4;
                    color = org.bukkit.Color.fromRGB(255, (int)(255 - p * 155), (int)(255 - p * 155));
                }

                int particleCount = 2 + (int)(progress * 15);
                double orbitRadius = 1.0 + progress * 1.2;
                double orbitSpeed = 0.3 + progress * 3.0;

                for (int i = 0; i < particleCount; i++) {
                    double angle = (tick * orbitSpeed * 0.3 + i * Math.PI * 2 / particleCount);
                    double x = Math.cos(angle) * orbitRadius;
                    double z = Math.sin(angle) * orbitRadius;
                    double y = Math.sin(tick * 0.3 + i) * 0.5;

                    loc.getWorld().spawnParticle(Particle.DUST,
                            loc.clone().add(x, y, z),
                            1, 0, 0, 0,
                            new Particle.DustOptions(color, 1.5f + (float)progress));
                }

                for (int i = 0; i < 3 + (int)(progress * 8); i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double radius = Math.random() * 0.6;
                    loc.getWorld().spawnParticle(Particle.DUST,
                            loc.clone().add(Math.cos(angle) * radius, Math.random() * 1.5, Math.sin(angle) * radius),
                            1, 0, 0, 0, new Particle.DustOptions(color, 1f));
                }

                if (tick % 20 == 0 && progress < 0.5) {
                    player.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BASS, 0.4f, 0.5f + (float)progress);
                }
                if (progress > 0.7 && tick % 8 == 0) {
                    player.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.5f, 0.3f);
                }

                tick++;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        session.task = task;
        session.bossBar = bossBar;

        return true;
    }

    private void cancelCharge(Player player, BossBar bossBar) {
        UUID uuid = player.getUniqueId();
        ChargeSession session = chargeSessions.remove(uuid);
        if (session != null && session.task != null) {
            session.task.cancel();
        }
        if (bossBar != null) {
            bossBar.removeAll();
        }
        resetPlayer(player);
    }

    private void explode(Player player, double damage, int radius, double knockbackPower,
                         int blindnessDuration, boolean selfDamage, AbilityCooldownManager cooldownManager,
                         String itemId, int cooldown) {
        Location center = player.getLocation();

        player.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        player.getWorld().playSound(center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5f, 0.3f);

        player.getWorld().spawnParticle(Particle.EXPLOSION, center.add(0, 1, 0), 5, 1, 1, 1, 0.2);

        for (int ring = 0; ring < 4; ring++) {
            double ringR = ring * 2.0;
            for (int i = 0; i < 48; i++) {
                double angle = i * Math.PI * 2 / 48;
                double x = Math.cos(angle) * ringR;
                double z = Math.sin(angle) * ringR;
                center.getWorld().spawnParticle(Particle.DUST,
                        center.clone().add(x, 0.2, z), 1, 0, 0, 0,
                        new Particle.DustOptions(
                                ring < 2 ? org.bukkit.Color.fromRGB(255, 200, 50) : org.bukkit.Color.fromRGB(255, 100, 30),
                                2f));
            }
        }

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity == player && !selfDamage) continue;

            LivingEntity target = (LivingEntity) entity;
            double dist = target.getLocation().distance(center);
            if (dist <= radius) {
                double damageMod = 1.0 - (dist / radius);
                target.damage(damage * damageMod, player);

                Vector dir = target.getLocation().toVector().subtract(center.toVector()).normalize();
                dir.setY(0.5);
                target.setVelocity(dir.multiply(knockbackPower * (1.0 - dist / radius + 0.3)));

                if (target instanceof Player && blindnessDuration > 0) {
                    ((Player) target).addPotionEffect(new PotionEffect(
                            PotionEffectType.BLINDNESS, blindnessDuration * 20, 0, false, true, true));
                }
            }
        }

        if (selfDamage) {
            player.damage(damage * 0.3);
        }

        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 40) { this.cancel(); return; }
                for (int i = 0; i < 10; i++) {
                    center.getWorld().spawnParticle(Particle.CLOUD,
                            center.clone().add(Math.random() * radius * 0.6 - radius * 0.3, Math.random() * 3, Math.random() * radius * 0.6 - radius * 0.3),
                            1, 0, 0, 0, 0.02);
                }
                t++;
            }
        }.runTaskTimer(getPlugin(), 0L, 2L);

        player.sendMessage(ColorUtils.colorize("&c💥 BOOM!"));
        cooldownManager.setCooldown(player.getUniqueId(), getId(), itemId, cooldown);
    }

    private void resetPlayer(Player player) {
        player.setWalkSpeed(0.2f);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    public void cleanupPlayer(UUID uuid) {
        ChargeSession session = chargeSessions.remove(uuid);
        if (session != null) {
            if (session.task != null) session.task.cancel();
            if (session.bossBar != null) session.bossBar.removeAll();
        }
    }

    private static class ChargeSession {
        BukkitTask task;
        BossBar bossBar;
        Location chargeLocation;
    }

    private String formatDamage(double damage) {
        if (damage == Math.floor(damage)) return (int) damage + "❤";
        return damage + "❤";
    }
}