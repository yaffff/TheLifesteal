package theLifesteal.abilities;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import theLifesteal.abilities.abilities.CriticalStrikeAbility;
import theLifesteal.customitem.AdvancedCustomItem;
import theLifesteal.customitem.AdvancedCustomItemManager;

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
        // Only handle right clicks
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Only main hand to prevent double-trigger
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        AdvancedCustomItem customItem = customItemManager.getItemByStack(item);
        if (customItem == null) return;

        Map<ItemAbilityType, List<ItemAbilityData>> abilities = customItem.getAbilities();
        boolean success;

        if (player.isSneaking()) {
            // Shift right-click
            success = abilityManager.executeShiftRightClick(player, customItem.getId(), abilities);
        } else {
            // Normal right-click
            success = abilityManager.executeRightClick(player, customItem.getId(), abilities);
        }

        if (success) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // Small delay to check the new item
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
            AdvancedCustomItem customItem = customItemManager.getItemByStack(newItem);
            if (customItem == null || !hasCriticalStrikeAbility(customItem)) {
                // Remove bossbar if switching away from crit item
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
        abilityManager.executeOnHit(attacker, victim, customItem.getId(), abilities, event.getDamage());
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