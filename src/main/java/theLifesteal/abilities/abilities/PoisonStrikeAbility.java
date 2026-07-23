package theLifesteal.abilities.abilities;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import theLifesteal.ColorUtils;
import theLifesteal.abilities.AbilityCooldownManager;
import theLifesteal.abilities.ItemAbility;
import theLifesteal.abilities.ItemAbilityData;
import theLifesteal.abilities.ItemAbilityType;

import java.util.*;

public class PoisonStrikeAbility extends ItemAbility {

    private final Map<UUID, PoisonData> activePoisons;
    private final Set<UUID> ignoreDamage;

    public PoisonStrikeAbility(JavaPlugin plugin) {
        super(plugin, "poison_strike", "Poison Strike", ItemAbilityType.ON_HIT);
        this.activePoisons = new HashMap<>();
        this.ignoreDamage = new HashSet<>();
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("chance", 20);
        config.put("poisonDuration", 5);
        config.put("baseDamagePerTick", 1.0);
        config.put("damagePerStack", 0.5);
        config.put("maxStacks", 5);
        config.put("affectPlayers", true);
        config.put("affectMobs", true);
        return config;
    }

    @Override
    public Map<String, ConfigField> getConfigFields() {
        Map<String, ConfigField> fields = new LinkedHashMap<>();
        fields.put("chance", new ConfigField("Chance (%)", "int", 1, 100));
        fields.put("poisonDuration", new ConfigField("Poison Duration (s)", "int", 1, 30));
        fields.put("baseDamagePerTick", new ConfigField("Base Damage per Tick", "double", 0.25, 10.0));
        fields.put("damagePerStack", new ConfigField("Extra Damage per Stack", "double", 0.1, 5.0));
        fields.put("maxStacks", new ConfigField("Max Stacks", "int", 1, 20));
        fields.put("affectPlayers", new ConfigField("Affect Players", "boolean"));
        fields.put("affectMobs", new ConfigField("Affect Mobs", "boolean"));
        return fields;
    }

    @Override
    public String buildLore(ItemAbilityData data) {
        int chance = data.getConfigInt("chance");
        int duration = data.getConfigInt("poisonDuration");
        double baseDmg = data.getConfigDouble("baseDamagePerTick");
        double perStack = data.getConfigDouble("damagePerStack");
        int maxStacks = data.getConfigInt("maxStacks");
        return "&7Apply &astacking poison &7on hit\n&8(&b" + chance + "%&7, &a" + duration + "s&7, &2" + String.format("%.1f", baseDmg) + " +" + String.format("%.1f", perStack) + "/stack&8)";
    }

    public boolean executeOnHit(Player attacker, LivingEntity victim, ItemAbilityData data,
                                AbilityCooldownManager cooldownManager, String itemId) {
        // Skip if this damage came from our poison tick
        if (ignoreDamage.contains(victim.getUniqueId())) return false;
        if (victim instanceof Player && !data.getConfigBoolean("affectPlayers")) return false;
        if (!(victim instanceof Player) && !data.getConfigBoolean("affectMobs")) return false;

        int chance = data.getConfigInt("chance");
        if (Math.random() * 100 >= chance) return false;

        int duration = data.getConfigInt("poisonDuration");
        double baseDamagePerTick = data.getConfigDouble("baseDamagePerTick");
        double damagePerStack = data.getConfigDouble("damagePerStack");
        int maxStacks = data.getConfigInt("maxStacks");

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
                15, 0.3, 0.5, 0.3,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 200, 0), 2f));
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_SPIDER_HURT, 0.6f, 1.0f);

        attacker.sendMessage(ColorUtils.colorize("&a☠ Poison stacked! &7(Stack &2" + newStacks + "&7/&2" + maxStacks + "&7, &c" + String.format("%.1f", finalDmg) + " &7per tick)"));
        return true;
    }

    @Override
    public boolean execute(Player player, ItemAbilityData data, AbilityCooldownManager cooldownManager, String itemId) {
        return false;
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