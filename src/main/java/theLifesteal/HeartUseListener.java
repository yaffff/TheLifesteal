package theLifesteal;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class HeartUseListener implements Listener {

    private final TheLifesteal plugin;
    private final HeartManager heartManager;

    public HeartUseListener(TheLifesteal plugin) {
        this.plugin = plugin;
        this.heartManager = plugin.getHeartManager();
    }

    @EventHandler
    public void onHeartUse(PlayerInteractEvent event) {
        // Only handle right clicks
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Only handle main hand to prevent double-use with offhand
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack item = event.getItem();
        if (!heartManager.isHeartItem(item)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        // Apply the heart effect
        heartManager.applyHeartEffect(player);

        // Remove one item from stack
        item.setAmount(item.getAmount() - 1);
        if (item.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }
    }
}