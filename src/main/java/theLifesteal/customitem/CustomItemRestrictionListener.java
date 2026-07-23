package theLifesteal.customitem;

import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class CustomItemRestrictionListener implements Listener {

    private final NamespacedKey itemIdKey;

    public CustomItemRestrictionListener(JavaPlugin plugin) {
        this.itemIdKey = new NamespacedKey(plugin, "custom_item_id");
    }

    // ===== CRAFTING =====
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isCustomItem(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    // ===== FURNACE =====
    @EventHandler
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        if (isCustomItem(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (isCustomItem(event.getSource())) {
            event.setCancelled(true);
        }
    }

    // ===== BLOCK PLACING =====
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isCustomItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    // ===== CONSUMING (eating/drinking) =====
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isCustomItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    // ===== BREWING =====
    @EventHandler
    public void onBrew(BrewEvent event) {
        for (ItemStack item : event.getContents().getContents()) {
            if (isCustomItem(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ===== SMITHING TABLE =====
    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (isCustomItem(event.getInventory().getInputEquipment()) ||
                isCustomItem(event.getInventory().getInputMineral())) {
            event.setResult(null);
        }
    }

    // ===== GRINDSTONE =====
    @EventHandler
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (isCustomItem(event.getInventory().getUpperItem()) ||
                isCustomItem(event.getInventory().getLowerItem())) {
            event.setResult(null);
        }
    }

    // ===== ANVIL =====
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (isCustomItem(event.getInventory().getFirstItem()) ||
                isCustomItem(event.getInventory().getSecondItem())) {
            event.setResult(null);
        }
    }

    // ===== HELPER =====
    private boolean isCustomItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(itemIdKey, PersistentDataType.STRING);
    }
}