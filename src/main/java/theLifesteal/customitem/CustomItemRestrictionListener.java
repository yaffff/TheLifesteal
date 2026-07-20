package theLifesteal.customitem;

import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class CustomItemRestrictionListener implements Listener {

    private final NamespacedKey itemIdKey;

    public CustomItemRestrictionListener(JavaPlugin plugin) {
        this.itemIdKey = new NamespacedKey(plugin, "custom_item_id");
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isCustomItem(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        if (isCustomItem(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        // prevent custom items from being smelted (input slot)
        if (isCustomItem(event.getSource())) {
            event.setCancelled(true);
        }
    }

    private boolean isCustomItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(itemIdKey, PersistentDataType.STRING);
    }
}