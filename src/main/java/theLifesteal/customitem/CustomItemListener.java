package theLifesteal.customitem;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class CustomItemListener implements Listener {
    private final JavaPlugin plugin;
    private final CustomItemGUI gui;

    public CustomItemListener(JavaPlugin plugin, CustomItemGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!gui.isCustomItemGUI(event.getView().getTitle())) return;
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getBottomInventory()))
            return;
        event.setCancelled(true);
        gui.handleClick(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (title.contains("Edit Item") || title.contains("Attributes") ||
                title.contains("Name & Lore") || title.contains("Flags")) {
            gui.saveOnClose(player);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        if (gui.isAwaitingInput(p.getUniqueId())) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> gui.handleChatInput(p, event.getMessage()));
        }
    }
}