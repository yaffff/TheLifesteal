package theLifesteal.abilities;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import theLifesteal.ColorUtils;
import theLifesteal.abilities.abilities.CriticalStrikeAbility;
import theLifesteal.customitem.AdvancedCustomItem;
import theLifesteal.customitem.AdvancedCustomItemManager;
import theLifesteal.customitem.CustomItemFlag;

import java.util.List;
import java.util.Map;

public class ItemAbilityListener implements Listener {

    private final JavaPlugin plugin;
    private final ItemAbilityManager abilityManager;
    private final AdvancedCustomItemManager customItemManager;

    public ItemAbilityListener(JavaPlugin plugin, ItemAbilityManager abilityManager, AdvancedCustomItemManager customItemManager) {
        this.plugin = plugin;
        this.abilityManager = abilityManager;
        this.customItemManager = customItemManager;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        AdvancedCustomItem customItem = customItemManager.getItemByStack(item);
        if (customItem == null) return;

        // CANCEL IMMEDIATELY — prevent vanilla use (snowball throw, shield, etc.)
        event.setCancelled(true);

        Map<ItemAbilityType, List<ItemAbilityData>> abilities = customItem.getAbilities();
        boolean success;

        if (player.isSneaking()) {
            success = abilityManager.executeShiftRightClick(player, customItem.getId(), abilities);
        } else {
            success = abilityManager.executeRightClick(player, customItem.getId(), abilities);
        }

        if (success && customItem.hasFlag(CustomItemFlag.CONSUMABLE)) {
            consumeItem(player);
        }
    }
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
            AdvancedCustomItem customItem = customItemManager.getItemByStack(newItem);
            if (customItem == null || !hasCriticalStrikeAbility(customItem)) {
                CriticalStrikeAbility critAbility = getCriticalStrikeAbility();
                if (critAbility != null) {
                    critAbility.removeBossBar(player);
                }
            }
        }, 2L);
    }

    private boolean hasCriticalStrikeAbility(AdvancedCustomItem item) {
        if (item == null) return false;
        List<ItemAbilityData> onHit = item.getAbilities().get(ItemAbilityType.ON_HIT);
        if (onHit == null) return false;
        for (ItemAbilityData data : onHit) {
            if (data.getAbilityId().equals("critical_strike")) return true;
        }
        return false;
    }

    private CriticalStrikeAbility getCriticalStrikeAbility() {
        ItemAbility ability = abilityManager.getAbility("critical_strike");
        if (ability instanceof CriticalStrikeAbility) {
            return (CriticalStrikeAbility) ability;
        }
        return null;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        CriticalStrikeAbility critAbility = getCriticalStrikeAbility();
        if (critAbility != null) {
            critAbility.removeBossBar(event.getEntity());
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        AdvancedCustomItem customItem = customItemManager.getItemByStack(item);
        if (customItem == null) return;

        Map<ItemAbilityType, List<ItemAbilityData>> abilities = customItem.getAbilities();
        List<ItemAbilityData> onHitAbilities = abilities.get(ItemAbilityType.ON_HIT);
        if (onHitAbilities == null || onHitAbilities.isEmpty()) return;

        boolean isFullAttack = attacker.getAttackCooldown() >= 0.99f;
        boolean isCritical = isFullAttack && attacker.getFallDistance() > 0.0f && !attacker.isOnGround();
        boolean isPlayer = victim instanceof Player;
        boolean isPassive = victim instanceof Animals || victim instanceof WaterMob || victim instanceof Ambient;
        boolean isHostile = victim instanceof Monster;

        boolean anyTriggered = false;

        for (ItemAbilityData data : onHitAbilities) {
            String triggerOn = data.getConfigString("trigger_on");
            if (triggerOn == null || triggerOn.isEmpty()) triggerOn = "ALL";

            boolean shouldTrigger = switch (triggerOn.toUpperCase()) {
                case "ALL" -> true;
                case "FULL_ATTACK" -> isFullAttack;
                case "CRITICAL" -> isCritical;
                case "FULL_OR_CRIT" -> isFullAttack || isCritical;
                default -> true;
            };

            if (!shouldTrigger) continue;

            if (isPlayer && !data.getConfigBoolean("affectPlayers")) continue;
            if (isPassive && !data.getConfigBoolean("affectPassive")) continue;
            if (isHostile && !data.getConfigBoolean("affectHostile")) continue;

            ItemAbility ability = abilityManager.getAbility(data.getAbilityId());
            if (ability != null) {
                boolean triggered = ability.onHitExecute(attacker, victim, data,
                        abilityManager.getCooldownManager(), customItem.getId(), event.getDamage());
                if (triggered) {
                    anyTriggered = true;
                }
            }
        }

        if (anyTriggered && customItem.hasFlag(CustomItemFlag.CONSUMABLE)) {
            consumeItem(attacker);
        }
    }

    /**
     * Consume one item from the player's main hand.
     * Removes instance UUID from tracking, plays break sound, deletes item.
     */
    private void consumeItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null) return;

        String instanceUuid = customItemManager.getInstanceUuid(item);
        if (instanceUuid != null) {
            customItemManager.removeInstance(instanceUuid);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);

        item.setAmount(item.getAmount() - 1);

        if (item.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }

        player.sendMessage(ColorUtils.colorize("&7✦ Item consumed!"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        abilityManager.clearPlayer(event.getPlayer().getUniqueId());
        CriticalStrikeAbility critAbility = getCriticalStrikeAbility();
        if (critAbility != null) {
            critAbility.removeBossBar(event.getPlayer());
        }
    }
}