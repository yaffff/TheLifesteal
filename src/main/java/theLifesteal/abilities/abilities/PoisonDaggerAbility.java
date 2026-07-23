package theLifesteal.abilities.abilities;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import theLifesteal.ColorUtils;
import theLifesteal.abilities.AbilityCooldownManager;
import theLifesteal.abilities.ItemAbility;
import theLifesteal.abilities.ItemAbilityData;
import theLifesteal.abilities.ItemAbilityType;

import java.util.*;

public class PoisonDaggerAbility extends ItemAbility {

    private final Map<UUID, PoisonData> activePoisons;
    private final Map<UUID, ChargeData> chargeDataMap;
    private final Set<UUID> ignoreDamage;

    public PoisonDaggerAbility(JavaPlugin plugin) {
        super(plugin, "poison_dagger", "Poison Dagger", ItemAbilityType.RIGHT_CLICK);
        this.activePoisons = new HashMap<>();
        this.chargeDataMap = new HashMap<>();
        this.ignoreDamage = new HashSet<>();
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("projectiles", 3);
        config.put("spread", 15);
        config.put("poisonDuration", 5);
        config.put("baseDamagePerTick", 1.0);
        config.put("damagePerStack", 0.5);
        config.put("maxStacks", 5);
        config.put("range", 12);
        config.put("maxCharges", 3);
        config.put("chargeCooldown", 8);
        return config;
    }

    @Override
    public Map<String, ConfigField> getConfigFields() {
        Map<String, ConfigField> fields = new LinkedHashMap<>();
        fields.put("projectiles", new ConfigField("Projectiles per Charge", "int", 1, 10));
        fields.put("spread", new ConfigField("Spread Angle", "int", 5, 45));
        fields.put("poisonDuration", new ConfigField("Poison Duration (s)", "int", 1, 30));
        fields.put("baseDamagePerTick", new ConfigField("Base Damage per Tick", "double", 0.25, 10.0));
        fields.put("damagePerStack", new ConfigField("Extra Damage per Stack", "double", 0.1, 5.0));
        fields.put("maxStacks", new ConfigField("Max Stacks", "int", 1, 20));
        fields.put("range", new ConfigField("Range", "int", 5, 30));
        fields.put("maxCharges", new ConfigField("Max Charges", "int", 1, 10));
        fields.put("chargeCooldown", new ConfigField("Cooldown per Charge (s)", "int", 1, 120));
        return fields;
    }

    @Override
    public String buildLore(ItemAbilityData data) {
        int projectiles = data.getConfigInt("projectiles");
        int maxCharges = data.getConfigInt("maxCharges");
        int chargeCooldown = data.getConfigInt("chargeCooldown");
        double baseDmg = data.getConfigDouble("baseDamagePerTick");
        double perStack = data.getConfigDouble("damagePerStack");
        int maxStacks = data.getConfigInt("maxStacks");
        int duration = data.getConfigInt("poisonDuration");
        return "&7Throw &e" + projectiles + " &astoxic daggers\n&7&o" + maxCharges + " charges &8| &e" + chargeCooldown + "s &8per charge\n&7&o" + String.format("%.1f", baseDmg) + " &a+" + String.format("%.1f", perStack) + "/stack &7per tick &8| &2" + maxStacks + "x &7max\n&7&oLasts &a" + duration + "s";
    }

    @Override
    public boolean execute(Player player, ItemAbilityData data, AbilityCooldownManager cooldownManager, String itemId) {
        int maxCharges = data.getConfigInt("maxCharges");
        int chargeCooldown = data.getConfigInt("chargeCooldown");

        UUID uuid = player.getUniqueId();
        ChargeData chargeData = chargeDataMap.computeIfAbsent(uuid, k -> new ChargeData());

        if (chargeData.cooldowns.length != maxCharges) {
            long[] newArr = new long[maxCharges];
            int copyCount = Math.min(chargeData.cooldowns.length, maxCharges);
            System.arraycopy(chargeData.cooldowns, 0, newArr, 0, copyCount);
            chargeData.cooldowns = newArr;
        }

        long now = System.currentTimeMillis();
        int chargeIndex = -1;

        for (int i = 0; i < chargeData.cooldowns.length; i++) {
            if (chargeData.cooldowns[i] <= now) {
                chargeIndex = i;
                break;
            }
        }

        if (chargeIndex == -1) {
            long soonest = Long.MAX_VALUE;
            for (long cd : chargeData.cooldowns) {
                if (cd < soonest) soonest = cd;
            }
            long remaining = Math.max(0, (soonest - now) / 1000);
            player.sendMessage(ColorUtils.colorize("&cNo charges! &7Next charge in &e" + remaining + "s"));
            return false;
        }

        chargeData.cooldowns[chargeIndex] = now + (chargeCooldown * 1000L);

        int projectiles = data.getConfigInt("projectiles");
        int spread = data.getConfigInt("spread");
        int poisonDuration = data.getConfigInt("poisonDuration");
        double baseDamagePerTick = data.getConfigDouble("baseDamagePerTick");
        double damagePerStack = data.getConfigDouble("damagePerStack");
        int maxStacks = data.getConfigInt("maxStacks");
        int range = data.getConfigInt("range");

        Location start = player.getEyeLocation();
        Vector baseDirection = start.getDirection().normalize();
        Vector perpendicular = new Vector(-baseDirection.getZ(), 0, baseDirection.getX()).normalize();

        player.getWorld().playSound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
        player.getWorld().playSound(start, Sound.ENTITY_LLAMA_SPIT, 0.8f, 1.0f);

        for (int i = 0; i < projectiles; i++) {
            double spreadAngle = (i - (projectiles - 1) / 2.0) * (spread / (double) projectiles);
            double radians = Math.toRadians(spreadAngle);

            Vector direction = baseDirection.clone()
                    .add(perpendicular.clone().multiply(Math.sin(radians)))
                    .normalize();

            new BukkitRunnable() {
                Location current = start.clone();
                double traveled = 0;

                @Override
                public void run() {
                    if (traveled >= range) {
                        this.cancel();
                        return;
                    }

                    current.add(direction.clone().multiply(0.8));
                    traveled += 0.8;

                    for (int j = 0; j < 4; j++) {
                        double angle = (traveled * 20 + j * Math.PI * 2 / 4);
                        double spinX = Math.cos(angle) * 0.35;
                        double spinZ = Math.sin(angle) * 0.35;
                        current.getWorld().spawnParticle(Particle.DUST,
                                current.clone().add(spinX, 0.1, spinZ),
                                1, 0, 0, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 200, 0), 2f));
                        current.getWorld().spawnParticle(Particle.DUST,
                                current.clone().add(spinX * 0.6, 0.2, spinZ * 0.6),
                                1, 0, 0, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(50, 255, 50), 1.5f));
                    }

                    current.getWorld().spawnParticle(Particle.DUST,
                            current, 2, 0.15, 0.15, 0.15,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 180, 0), 1.5f));
                    current.getWorld().spawnParticle(Particle.WITCH,
                            current, 1, 0.1, 0.1, 0.1, 0);

                    for (Entity entity : current.getWorld().getNearbyEntities(current, 1, 1, 1)) {
                        if (entity instanceof LivingEntity && entity != player) {
                            LivingEntity target = (LivingEntity) entity;
                            applyStackingPoison(player, target, poisonDuration, baseDamagePerTick, damagePerStack, maxStacks);
                            this.cancel();
                            return;
                        }
                    }

                    if (current.getBlock().getType().isSolid()) {
                        current.getWorld().spawnParticle(Particle.DUST,
                                current, 10, 0.3, 0.3, 0.3,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 150, 0), 1f));
                        this.cancel();
                    }
                }
            }.runTaskTimer(getPlugin(), 0L, 1L);
        }

        int available = countAvailableCharges(chargeData);
        player.sendMessage(ColorUtils.colorize("&a🗡 Dagger thrown! &7(&e" + available + " &7charges left)"));
        return true;
    }

    private int countAvailableCharges(ChargeData chargeData) {
        long now = System.currentTimeMillis();
        int count = 0;
        for (long cd : chargeData.cooldowns) {
            if (cd <= now) count++;
        }
        return count;
    }

    private void applyStackingPoison(Player attacker, LivingEntity victim, int duration, double baseDamagePerTick,
                                     double damagePerStack, int maxStacks) {
        UUID victimId = victim.getUniqueId();
        PoisonData poisonData = activePoisons.get(victimId);

        int newStacks;
        if (poisonData == null || poisonData.isExpired()) {
            newStacks = 1;
        } else {
            newStacks = Math.min(poisonData.stacks + 1, maxStacks);
        }

        double dmgPerTick = baseDamagePerTick + (damagePerStack * (newStacks - 1));

        if (poisonData != null && poisonData.task != null) {
            poisonData.task.cancel();
        }

        final int finalStacks = newStacks;
        final int finalDuration = duration;
        final double finalDmg = dmgPerTick;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(getPlugin(), new Runnable() {
            int ticks = 0;
            int maxTicks = finalDuration * 20;

            @Override
            public void run() {
                PoisonData pd = activePoisons.get(victimId);
                if (pd == null || pd.stacks != finalStacks) return;

                if (ticks >= maxTicks || victim.isDead() || !victim.isValid()) {
                    pd.task = null;
                    if (pd.stacks == finalStacks) {
                        activePoisons.remove(victimId);
                    }
                    return;
                }

                if (ticks % 10 == 0 && ticks > 0) {
                    ignoreDamage.add(victimId);
                    victim.damage(finalDmg);
                    Bukkit.getScheduler().runTaskLater(getPlugin(), () -> ignoreDamage.remove(victimId), 2L);

                    victim.getWorld().spawnParticle(Particle.DUST,
                            victim.getLocation().add(0, 1.5, 0),
                            5, 0.2, 0.3, 0.2,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 180, 0), 1.5f));
                }
                ticks++;
            }
        }, 0L, 1L);

        poisonData = new PoisonData(newStacks, task, System.currentTimeMillis() + (duration * 1000L));
        activePoisons.put(victimId, poisonData);

        victim.getWorld().spawnParticle(Particle.DUST,
                victim.getLocation().add(0, 1.5, 0),
                10, 0.3, 0.5, 0.3,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 200, 0), 2f));
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_SPIDER_HURT, 0.5f, 1.0f);
    }

    public void cleanupPlayer(UUID uuid) {
        chargeDataMap.remove(uuid);
    }

    private static class ChargeData {
        long[] cooldowns;

        ChargeData() {
            this.cooldowns = new long[3];
        }
    }

    private static class PoisonData {
        int stacks;
        BukkitTask task;
        long expireTime;

        PoisonData(int stacks, BukkitTask task, long expireTime) {
            this.stacks = stacks;
            this.task = task;
            this.expireTime = expireTime;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
}