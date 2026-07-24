package theLifesteal.abilities.abilities;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import theLifesteal.ColorUtils;
import theLifesteal.abilities.AbilityCooldownManager;
import theLifesteal.abilities.ItemAbility;
import theLifesteal.abilities.ItemAbilityData;
import theLifesteal.abilities.ItemAbilityType;

import java.util.LinkedHashMap;
import java.util.Map;

public class PierceArmorAbility extends ItemAbility {

    public PierceArmorAbility(JavaPlugin plugin) {
        super(plugin, "pierce_armor", "Pierce Armor", ItemAbilityType.PASSIVE);
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("piercePct", 50.0);
        config.put("hpCostPct", 0.0);
        config.put("cooldown", 0);
        config.put("cooldownScope", "ITEM");
        config.put("trigger_on", "ALL");
        config.put("affectPlayers", true);
        config.put("affectPassive", true);
        config.put("affectHostile", true);
        return config;
    }

    @Override
    public Map<String, ConfigField> getConfigFields() {
        Map<String, ConfigField> fields = new LinkedHashMap<>();
        fields.put("piercePct", new ConfigField("Armor Pierce (%)", "double", 1.0, 100.0));
        fields.put("hpCostPct", new ConfigField("HP Cost (%)", "double", 0.0, 100.0));
        fields.put("cooldown", new ConfigField("Cooldown (seconds)", "int", 0, 3600));
        fields.put("cooldownScope", new ConfigField("Cooldown Scope", "string"));
        return fields;
    }

    @Override
    public String buildLore(ItemAbilityData data) {
        double piercePct = data.getConfigDouble("piercePct");
        double hpCostPct = data.getConfigDouble("hpCostPct");
        int cooldown = data.getConfigInt("cooldown");

        StringBuilder sb = new StringBuilder();
        sb.append("&7Attacks ignore &c").append((int) piercePct).append("% &7of victim's armor stat.\n");
        if (hpCostPct > 0) {
            sb.append("&7Costs &c").append((int) hpCostPct).append("% HP &7per hit. ");
        }
        if (cooldown > 0) {
            sb.append("&7Cooldown: &e").append(cooldown).append("s");
        }
        return sb.toString();
    }

    @Override
    public boolean execute(Player player, ItemAbilityData data, AbilityCooldownManager cooldownManager, String itemId) {
        return false; // PASSIVE ability triggered on hit
    }

    @Override
    public boolean onHitExecute(Player attacker, LivingEntity victim, ItemAbilityData data,
                                AbilityCooldownManager cooldownManager, String itemId, double baseDamage,
                                EntityDamageByEntityEvent event) {
        if (attacker == null || victim == null || event == null) return false;

        // Check if victim has armor to pierce
        AttributeInstance armorAttr = victim.getAttribute(Attribute.ARMOR);
        double armor = armorAttr != null ? armorAttr.getValue() : 0.0;
        if (armor <= 0) {
            return false; // No armor to pierce on naked target
        }

        int cooldown = data.getConfigInt("cooldown");
        String scope = data.getConfigString("cooldownScope");
        if (scope == null || scope.isEmpty()) scope = "ITEM";

        if (cooldown > 0 && cooldownManager.isOnCooldown(attacker.getUniqueId(), getId(), itemId, scope)) {
            return false;
        }

        double requiredHp = getRequiredHpCost(data, attacker);
        if (requiredHp > 0) {
            if (!checkStrictHealthRequirement(attacker, requiredHp)) {
                return false;
            }
            applySelfHealthCost(attacker, requiredHp);
        }

        if (cooldown > 0) {
            cooldownManager.setCooldown(attacker.getUniqueId(), getId(), itemId, scope, cooldown);
        }

        double piercePct = data.getConfigDouble("piercePct");
        AttributeInstance toughnessAttr = victim.getAttribute(Attribute.ARMOR_TOUGHNESS);
        double toughness = toughnessAttr != null ? toughnessAttr.getValue() : 0.0;

        double rawDamage = event.getDamage();

        // Calculate vanilla reduction under pierced armor
        double piercedArmor = Math.max(0.0, armor * (1.0 - (piercePct / 100.0)));
        double defensePierced = Math.min(20.0, Math.max(piercedArmor / 5.0, piercedArmor - rawDamage / (2.0 + toughness / 4.0)));
        double fPierced = Math.max(0.0, 1.0 - (defensePierced / 25.0));

        // Desired final damage victim should receive (strictly capped by unarmored raw damage)
        double targetDamage = Math.min(rawDamage, rawDamage * fPierced);

        // Override armor modifier so final damage equals targetDamage exactly
        if (event.isApplicable(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.ARMOR)) {
            event.setDamage(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.ARMOR, -(rawDamage - targetDamage));
        } else {
            event.setDamage(Math.min(rawDamage, targetDamage));
        }

        // Record ability damage attribution for kill credit
        recordAbilityDamage(attacker, victim);

        // Visual & Sound Feedback
        victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.2, 0), 12, 0.3, 0.3, 0.3, 0.2);
        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.8f, 1.4f);

        return true;
    }
}
