package theLifesteal.crafting;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import theLifesteal.ColorUtils;

import java.util.HashMap;

public class RecipeBookListener implements Listener {

    private final JavaPlugin plugin;
    private final RecipeBookItem recipeBookItem;
    private final CraftingGUI craftingGUI;
    private final CraftingManager craftingManager;
    private final HashMap<Player, ItemStack> deathBooks;

    public RecipeBookListener(JavaPlugin plugin, RecipeBookItem recipeBookItem,
                              CraftingGUI craftingGUI, CraftingManager craftingManager) {
        this.plugin = plugin;
        this.recipeBookItem = recipeBookItem;
        this.craftingGUI = craftingGUI;
        this.craftingManager = craftingManager;
        this.deathBooks = new HashMap<>();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (recipeBookItem.giveOnJoin()) {
            boolean hasBook = false;
            for (ItemStack item : player.getInventory().getContents()) {
                if (recipeBookItem.isRecipeBook(item)) {
                    hasBook = true;
                    if (player.getInventory().first(item) != recipeBookItem.getSlot()) {
                        moveToCorrectSlot(player);
                    }
                    break;
                }
            }

            if (!hasBook) {
                giveRecipeBook(player);
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (recipeBookItem.giveOnRespawn()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ItemStack savedBook = deathBooks.remove(player);

                boolean hasBook = false;
                for (ItemStack item : player.getInventory().getContents()) {
                    if (recipeBookItem.isRecipeBook(item)) {
                        hasBook = true;
                        break;
                    }
                }

                if (!hasBook) {
                    if (savedBook != null) {
                        player.getInventory().setItem(recipeBookItem.getSlot(), savedBook);
                    } else {
                        giveRecipeBook(player);
                    }
                }
            }, 5L);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Find and remove recipe book from drops
        event.getDrops().removeIf(item -> {
            if (recipeBookItem.isRecipeBook(item)) {
                deathBooks.put(player, item.clone());
                return true;
            }
            return false;
        });

        // Also remove from player's inventory to prevent duplication
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (recipeBookItem.isRecipeBook(contents[i])) {
                if (!deathBooks.containsKey(player)) {
                    deathBooks.put(player, contents[i].clone());
                }
                contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        if (recipeBookItem.isRecipeBook(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtils.colorize("&cYou cannot drop your Recipe Book!"));
        }
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item != null && recipeBookItem.isRecipeBook(item)) {
            event.setCancelled(true);
            craftingGUI.openMainMenu(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // ---- FIX 1: Recipe Editor handling ----
        if (title.contains("Recipe Editor")) {
            // Allow clicks in the player's own inventory (bottom)
            if (event.getClickedInventory() != null &&
                    event.getClickedInventory().equals(event.getView().getBottomInventory())) {
                // Let normal inventory actions happen (pick up, place, etc.)
                event.setCancelled(false);
                return;
            }

            // Top inventory (the editor) – we handle clicks ourselves
            event.setCancelled(true);
            craftingGUI.getAdminGUI().handleEditorClick(
                    player,
                    event.getSlot(),
                    event.getClick(),
                    event.getCursor(),
                    event.getCurrentItem()
            );
            return;
        }

        // ---- Other crafting GUIs (main menu, details, active, admin) ----
        if (isCraftingGUI(title)) {
            // We still want to block bottom clicks in these other GUIs to prevent item stealing
            // But we allow the top inventory clicks to be handled by CraftingGUI
            event.setCancelled(true);
            craftingGUI.handleClick(player, title, event.getSlot(), event.getClick());
            return;
        }

        // ---- Recipe book protection (unchanged) ----
        int bookSlot = recipeBookItem.getSlot();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (recipeBookItem.isRecipeBook(current) || recipeBookItem.isRecipeBook(cursor)) {
            event.setCancelled(true);
            if (event.getSlot() == bookSlot && event.getClick() == ClickType.RIGHT) {
                craftingGUI.openMainMenu(player);
            }
            return;
        }

        if (event.isShiftClick() && recipeBookItem.isRecipeBook(current)) {
            event.setCancelled(true);
            return;
        }

        if (event.getHotbarButton() != -1) {
            ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            if (recipeBookItem.isRecipeBook(hotbarItem) ||
                    (event.getSlot() == bookSlot && recipeBookItem.isRecipeBook(current))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // Cancel all drags in crafting GUIs
        if (isCraftingGUI(title)) {
            event.setCancelled(true);
            return;
        }

        // Prevent dragging recipe book
        int bookSlot = recipeBookItem.getSlot();
        if (event.getInventorySlots().contains(bookSlot)) {
            ItemStack bookSlotItem = player.getInventory().getItem(bookSlot);
            if (recipeBookItem.isRecipeBook(bookSlotItem)) {
                event.setCancelled(true);
            }
        }
    }

    private boolean isCraftingGUI(String title) {
        return title.contains("Custom Crafting") ||
                title.contains("Recipe Details") ||
                title.contains("Active Crafts") ||
                title.contains("Admin Crafting") ||
                title.contains("Recipe Editor") ||
                title.contains("Custom Items") ||
                title.contains("Set Crafting Time") ||  // sub-editors
                title.contains("Set XP Reward") ||
                title.contains("Set Amount for");
    }

    private void giveRecipeBook(Player player) {
        int bookSlot = recipeBookItem.getSlot();

        ItemStack slotItem = player.getInventory().getItem(bookSlot);
        if (slotItem != null && slotItem.getType() != Material.AIR) {
            if (recipeBookItem.isRecipeBook(slotItem)) {
                return;
            }
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(slotItem.clone());
            if (!leftover.isEmpty()) {
                player.sendMessage(ColorUtils.colorize("&cCould not give Recipe Book - inventory full!"));
                return;
            }
            player.getInventory().setItem(bookSlot, null);
        }

        player.getInventory().setItem(bookSlot, recipeBookItem.createRecipeBook());
    }

    private void moveToCorrectSlot(Player player) {
        int bookSlot = recipeBookItem.getSlot();

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (recipeBookItem.isRecipeBook(item)) {
                if (i != bookSlot) {
                    ItemStack currentSlotItem = player.getInventory().getItem(bookSlot);
                    player.getInventory().setItem(bookSlot, item.clone());
                    player.getInventory().setItem(i, currentSlotItem);
                }
                break;
            }
        }
    }
}