package theLifesteal.customitem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import theLifesteal.ColorUtils;

import java.util.*;

public class CustomItemGUI {

    private final JavaPlugin plugin;
    private final AdvancedCustomItemManager manager;
    private final Map<UUID, EditingSession> sessions;
    private final Map<UUID, String> chatInput;
    private final Map<UUID, Boolean> deleteMode;
    private final Map<UUID, Long> lastDeleteClick;

    // Exact attribute list for 1.21 (without GENERIC_ prefix)
    private static final List<Attribute> ATTRIBUTES = Arrays.asList(
            Attribute.ARMOR,
            Attribute.ARMOR_TOUGHNESS,
            Attribute.ATTACK_DAMAGE,
            Attribute.ATTACK_KNOCKBACK,
            Attribute.ATTACK_SPEED,
            Attribute.BLOCK_BREAK_SPEED,
            Attribute.BLOCK_INTERACTION_RANGE,
            Attribute.ENTITY_INTERACTION_RANGE,
            Attribute.EXPLOSION_KNOCKBACK_RESISTANCE,
            Attribute.FALL_DAMAGE_MULTIPLIER,
            Attribute.GRAVITY,
            Attribute.JUMP_STRENGTH,
            Attribute.KNOCKBACK_RESISTANCE,
            Attribute.LUCK,
            Attribute.MAX_HEALTH,
            Attribute.MOVEMENT_SPEED,
            Attribute.OXYGEN_BONUS,
            Attribute.SAFE_FALL_DISTANCE,
            Attribute.SCALE,
            Attribute.STEP_HEIGHT,
            Attribute.WATER_MOVEMENT_EFFICIENCY,
            Attribute.SNEAKING_SPEED,
            Attribute.SUBMERGED_MINING_SPEED,
            Attribute.SWEEPING_DAMAGE_RATIO
    );

    public CustomItemGUI(JavaPlugin plugin, AdvancedCustomItemManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.sessions = new HashMap<>();
        this.chatInput = new HashMap<>();
        this.deleteMode = new HashMap<>();
        this.lastDeleteClick = new HashMap<>();
    }

    // ---------- Public API ----------
    public void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text(ColorUtils.colorize("&6✦ &eCustom Items &6✦")));
        fillMainMenu(player, gui, 0);
        player.openInventory(gui);
    }

    public void openEditGUI(Player player, String itemId) {
        AdvancedCustomItem item = manager.getItem(itemId);
        if (item == null) {
            player.sendMessage(ColorUtils.colorize("&cItem not found!"));
            return;
        }
        sessions.put(player.getUniqueId(), new EditingSession(item.clone()));
        refreshEditGUI(player);
    }

    public boolean isAwaitingInput(UUID uuid) {
        return chatInput.containsKey(uuid);
    }

    public boolean isCustomItemGUI(String title) {
        return title.contains("Custom Items") || title.contains("Choose Base Item") ||
                title.contains("Edit Item") || title.contains("Attributes") ||
                title.contains("Edit Lore") || title.contains("Flags") ||
                title.contains("Coming Soon");
    }

    public void handleChatInput(Player player, String message) {
        String key = chatInput.remove(player.getUniqueId());
        if (key == null) return;
        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(ColorUtils.colorize("&cCancelled."));
            openMainMenu(player);
            return;
        }

        EditingSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            openMainMenu(player);
            return;
        }

        if (key.startsWith("displayName")) {
            session.getItem().setDisplayName(message.trim().isEmpty() ? null : message);
            player.sendMessage(ColorUtils.colorize("&aDisplay name updated."));
            refreshEditGUI(player);
        } else if (key.startsWith("lore_add")) {
            List<String> lore = session.getItem().getLore();
            lore.add(message);
            session.getItem().setLore(lore);
            player.sendMessage(ColorUtils.colorize("&aLore line added."));
            refreshEditGUI(player);
            openLoreEditor(player);
        } else if (key.startsWith("lore_edit:")) {
            int idx = Integer.parseInt(key.split(":")[1]);
            List<String> lore = session.getItem().getLore();
            if (idx >= 0 && idx < lore.size()) {
                lore.set(idx, message);
                session.getItem().setLore(lore);
                player.sendMessage(ColorUtils.colorize("&aLore line updated."));
                refreshEditGUI(player);
                openLoreEditor(player);
            }
        } else if (key.startsWith("numeric:")) {
            try {
                double val = Double.parseDouble(message);
                String context = key.substring(8);
                if (context.equals("modeldata")) {
                    session.getItem().setCustomModelData((int) val);
                    player.sendMessage(ColorUtils.colorize("&aModel data set to " + (int) val));
                } else if (context.equals("damage")) {
                    session.getItem().setDamage((int) Math.max(0, val));
                    player.sendMessage(ColorUtils.colorize("&aDurability set to " + (int) val));
                } else if (context.startsWith("attr:")) {
                    String attrName = context.substring(5);
                    try {
                        Attribute attr = Attribute.valueOf(attrName);
                        if (val == 0) session.getItem().removeAttribute(attr);
                        else session.getItem().addAttribute(attr, val);
                        player.sendMessage(ColorUtils.colorize("&aAttribute " + attrName + " updated."));
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(ColorUtils.colorize("&cInvalid attribute."));
                    }
                }
                refreshEditGUI(player);
            } catch (NumberFormatException e) {
                player.sendMessage(ColorUtils.colorize("&cInvalid number."));
                refreshEditGUI(player);
            }
        }
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        String title = event.getView().getTitle();
        if (!isCustomItemGUI(title)) return;
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getBottomInventory()))
            return;
        event.setCancelled(true);
        int slot = event.getSlot();
        ClickType click = event.getClick();
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;

        if (title.contains("Custom Items")) handleMainMenu(p, slot, click, current);
        else if (title.contains("Choose Base Item")) handleTemplate(p, slot, click, event.getCursor());
        else if (title.contains("Edit Item")) handleEdit(p, slot, click);
        else if (title.contains("Attributes")) handleAttributes(p, slot, click);
        else if (title.contains("Edit Lore")) handleLore(p, slot, click);
        else if (title.contains("Flags")) handleFlags(p, slot, click);
        else if (title.contains("Coming Soon") && slot == 26) openMainMenu(p);
    }

    public void cleanupPlayer(UUID uuid) {
        sessions.remove(uuid);
        chatInput.remove(uuid);
        deleteMode.remove(uuid);
        lastDeleteClick.remove(uuid);
    }

    // ---------- GUI Builders ----------
    private void fillMainMenu(Player player, Inventory gui, int page) {
        gui.clear();
        List<AdvancedCustomItem> list = new ArrayList<>(manager.getAllItems());
        int perPage = 28;
        int start = page * perPage;
        int end = Math.min(start + perPage, list.size());
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = start; i < end; i++) {
            AdvancedCustomItem item = list.get(i);
            ItemStack display = manager.buildItem(item);
            manager.storeItemId(display, item.getId());
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
                lore.add("");
                lore.add(ColorUtils.colorize("&7&m-------------------"));
                lore.add(ColorUtils.colorize("&a▶ Left-click to get"));
                lore.add(ColorUtils.colorize("&e▶ Right-click to edit"));
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            gui.setItem(slots[i - start], display);
        }

        if (page > 0) gui.setItem(45, createButton(Material.ARROW, "&a← Prev"));
        if (page < (list.size() + perPage - 1) / perPage - 1)
            gui.setItem(53, createButton(Material.ARROW, "&aNext →"));

        gui.setItem(48, createButton(Material.EMERALD_BLOCK, "&a✦ &6Create New", "&7Choose base item"));
        boolean del = deleteMode.getOrDefault(player.getUniqueId(), false);
        gui.setItem(49, createToggleButton(Material.REDSTONE_BLOCK, "&c🗑 Delete Mode", del));
        gui.setItem(53, createButton(Material.BARRIER, "&c✖ Close"));
        fillRest(gui);
    }

    private void refreshEditGUI(Player player) {
        EditingSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            openMainMenu(player);
            return;
        }
        Inventory gui = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text(ColorUtils.colorize("&6✦ &eEdit Item &6✦")));

        // Decorative border (cyan glass)
        int[] borderSlots = {0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,50,51,52,53};
        ItemStack border = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(" ");
        border.setItemMeta(borderMeta);
        for (int slot : borderSlots) gui.setItem(slot, border);

        // Center preview (slot 22)
        ItemStack preview = manager.buildItem(session.getItem());
        gui.setItem(22, preview);

        // Action buttons (centered around preview)
        gui.setItem(28, createButton(Material.DIAMOND_SWORD, "&6⚔ Attributes", "&7Edit attributes & durability"));
        gui.setItem(29, createButton(Material.NAME_TAG, "&6✏ Display Name", "&7Set custom name"));
        gui.setItem(30, createButton(Material.BOOK, "&6📖 Lore", "&7Edit lore lines"));
        gui.setItem(31, createButton(Material.LEATHER_CHESTPLATE, "&6🏁 Flags", "&7Toggle flags"));

        // Bottom row: Save, Coming Soon, Cancel
        gui.setItem(48, createButton(Material.LIME_DYE, "&a✔ Save & Close"));
        gui.setItem(49, createButton(Material.BEACON, "&d✦ Coming Soon", "&7Future features"));
        gui.setItem(50, createButton(Material.BARRIER, "&c✖ Cancel"));

        fillRest(gui);
        player.openInventory(gui);
    }

    private void openTemplate(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27,
                net.kyori.adventure.text.Component.text(ColorUtils.colorize("&6✦ &eChoose Base Item &6✦")));
        gui.setItem(13, createButton(Material.GRAY_STAINED_GLASS_PANE, "&7Click with item", "&7to set as template"));
        gui.setItem(22, createButton(Material.LIME_DYE, "&a✔ Confirm"));
        gui.setItem(26, createButton(Material.RED_DYE, "&c✖ Cancel"));
        fillRest(gui);
        player.openInventory(gui);
    }

    private void openAttributes(Player player) {
        EditingSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        Inventory gui = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text(ColorUtils.colorize("&6✦ &eAttributes &6✦")));

        // Decorative border (blue glass)
        int[] borderSlots = {0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,51,52,53};
        ItemStack border = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(" ");
        border.setItemMeta(borderMeta);
        for (int slot : borderSlots) gui.setItem(slot, border);

        // Attribute slots
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < Math.min(ATTRIBUTES.size(), slots.length); i++) {
            Attribute a = ATTRIBUTES.get(i);
            double val = session.getItem().getAttributes().getOrDefault(a, 0.0);
            ItemStack btn = new ItemStack(Material.PAPER);
            ItemMeta meta = btn.getItemMeta();
            meta.setDisplayName(ColorUtils.colorize("&e" + a.name()));
            meta.setLore(Arrays.asList(
                    ColorUtils.colorize("&7Current: &f" + (val != 0 ? val : "—")),
                    "",
                    ColorUtils.colorize("&a▶ Click to set value")
            ));
            btn.setItemMeta(meta);
            gui.setItem(slots[i], btn);
        }

        // Durability button (slot 49)
        int currentDurability = session.getItem().getDamage();
        gui.setItem(49, createButton(Material.ANVIL, "&6🔧 Durability: &f" + currentDurability,
                "&7Click to set durability loss"));

        // Model Data button (slot 50)
        int currentModelData = session.getItem().getCustomModelData();
        gui.setItem(50, createButton(Material.COMMAND_BLOCK, "&6🔢 Model Data: &f" + currentModelData,
                "&7Click to set custom model data"));

        // Back button (slot 53)
        gui.setItem(53, createButton(Material.ARROW, "&a← Back"));
        fillRest(gui);
        player.openInventory(gui);
    }

    private void openLoreEditor(Player player) {
        EditingSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        Inventory gui = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text(ColorUtils.colorize("&6✦ &eEdit Lore &6✦")));
        List<String> lore = session.getItem().getLore();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < Math.min(lore.size(), slots.length); i++) {
            ItemStack line = new ItemStack(Material.PAPER);
            ItemMeta meta = line.getItemMeta();
            meta.setDisplayName(ColorUtils.colorize(lore.get(i)));
            meta.setLore(Arrays.asList(
                    ColorUtils.colorize("&a▶ Left-click to edit"),
                    ColorUtils.colorize("&c▶ Right-click to remove")
            ));
            line.setItemMeta(meta);
            gui.setItem(slots[i], line);
        }
        gui.setItem(49, createButton(Material.WRITABLE_BOOK, "&a➕ Add Line"));
        gui.setItem(50, createButton(Material.BOOKSHELF, "&d✦ Format Helper"));
        gui.setItem(53, createButton(Material.ARROW, "&a← Back"));
        fillRest(gui);
        player.openInventory(gui);
    }

    private void openFlags(Player player) {
        EditingSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        Inventory gui = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text(ColorUtils.colorize("&6✦ &eFlags &6✦")));
        CustomItemFlag[] flags = CustomItemFlag.values();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < Math.min(flags.length, slots.length); i++) {
            CustomItemFlag f = flags[i];
            boolean enabled = session.getItem().hasFlag(f);
            String name = f.name().toLowerCase().replace("_", " ");
            ItemStack btn = createToggleButton(Material.PAPER, "&e" + name, enabled);
            gui.setItem(slots[i], btn);
        }
        gui.setItem(49, createButton(Material.ARROW, "&a← Back"));
        fillRest(gui);
        player.openInventory(gui);
    }

    private void openWIP(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27,
                net.kyori.adventure.text.Component.text(ColorUtils.colorize("&6✦ &eComing Soon &6✦")));
        gui.setItem(13, createButton(Material.CLOCK, "&d✦ Future Features", "&7Potion effects, armor color, etc."));
        gui.setItem(26, createButton(Material.ARROW, "&a← Back"));
        fillRest(gui);
        player.openInventory(gui);
    }

    // ---------- Click Handlers ----------
    private void handleMainMenu(Player p, int slot, ClickType click, ItemStack current) {
        UUID uuid = p.getUniqueId();
        if (slot == 48) {
            openTemplate(p);
            return;
        }
        if (slot == 49) {
            deleteMode.put(uuid, !deleteMode.getOrDefault(uuid, false));
            p.sendMessage(ColorUtils.colorize(deleteMode.get(uuid) ? "&cDelete mode ON" : "&aDelete mode OFF"));
            openMainMenu(p);
            return;
        }
        if (slot == 53) {
            p.closeInventory();
            return;
        }
        if (slot == 45 || slot == 53) {
            p.sendMessage(ColorUtils.colorize("&cPagination not implemented"));
            return;
        }

        String id = manager.getItemId(current);
        if (id == null) return;
        if (deleteMode.getOrDefault(uuid, false)) {
            manager.deleteItem(id);
            p.sendMessage(ColorUtils.colorize("&cDeleted item."));
            openMainMenu(p);
            return;
        }
        if (click.isLeftClick()) {
            ItemStack give = manager.buildItem(manager.getItem(id));
            p.getInventory().addItem(give);
            p.sendMessage(ColorUtils.colorize("&aItem given."));
        } else if (click.isRightClick()) {
            openEditGUI(p, id);
        }
    }

    private void handleTemplate(Player p, int slot, ClickType click, ItemStack cursor) {
        if (slot == 13) {
            if (cursor != null && cursor.getType() != Material.AIR) {
                p.getOpenInventory().setItem(13, cursor.clone());
                p.sendMessage(ColorUtils.colorize("&aTemplate set."));
            } else p.sendMessage(ColorUtils.colorize("&cHold an item!"));
        } else if (slot == 22) {
            ItemStack template = p.getOpenInventory().getItem(13);
            if (template == null || template.getType() == Material.AIR || template.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                p.sendMessage(ColorUtils.colorize("&cSet a template first!"));
                return;
            }
            String id = "custom_" + System.currentTimeMillis();
            manager.createItem(id, template);
            p.closeInventory();
            openEditGUI(p, id);
        } else if (slot == 26) {
            openMainMenu(p);
        }
    }

    private void handleEdit(Player p, int slot, ClickType click) {
        EditingSession session = sessions.get(p.getUniqueId());
        if (session == null) return;
        switch (slot) {
            case 28:
                openAttributes(p);
                break;
            case 29:
                p.closeInventory();
                p.sendMessage(ColorUtils.colorize("&eType new name (use &). Type cancel to abort."));
                chatInput.put(p.getUniqueId(), "displayName");
                break;
            case 30:
                openLoreEditor(p);
                break;
            case 31:
                openFlags(p);
                break;
            case 48: // Save
                // Copy session data back to manager item
                AdvancedCustomItem original = manager.getItem(session.getItem().getId());
                if (original != null) {
                    original.setBaseItem(session.getItem().getBaseItem());
                    original.setDisplayName(session.getItem().getDisplayName());
                    original.setLore(session.getItem().getLore());
                    original.setAttributes(session.getItem().getAttributes());
                    original.setFlags(session.getItem().getFlags());
                    original.setCustomModelData(session.getItem().getCustomModelData());
                    original.setDamage(session.getItem().getDamage());
                    original.setFutureExtensions(session.getItem().getFutureExtensions());
                }
                manager.saveItems();
                sessions.remove(p.getUniqueId());
                p.closeInventory();
                p.sendMessage(ColorUtils.colorize("&aSaved."));
                openMainMenu(p);
                break;
            case 49: // Coming Soon (changed)
                openWIP(p);
                break;
            case 50: // Cancel
                sessions.remove(p.getUniqueId());
                p.closeInventory();
                openMainMenu(p);
                break;
        }
    }

    private void handleAttributes(Player p, int slot, ClickType click) {
        if (slot == 53) {
            refreshEditGUI(p);
            return;
        }
        if (slot == 49) { // Durability
            p.closeInventory();
            p.sendMessage(ColorUtils.colorize("&eEnter durability loss (0 = full):"));
            chatInput.put(p.getUniqueId(), "numeric:damage");
            return;
        }
        if (slot == 50) { // Model Data
            p.closeInventory();
            p.sendMessage(ColorUtils.colorize("&eEnter custom model data number:"));
            chatInput.put(p.getUniqueId(), "numeric:modeldata");
            return;
        }
        // Attribute slots
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i] && i < ATTRIBUTES.size()) {
                Attribute a = ATTRIBUTES.get(i);
                p.closeInventory();
                p.sendMessage(ColorUtils.colorize("&eEnter value for " + a.name() + " (0 to remove):"));
                chatInput.put(p.getUniqueId(), "numeric:attr:" + a.name());
                return;
            }
        }
    }

    private void handleLore(Player p, int slot, ClickType click) {
        EditingSession session = sessions.get(p.getUniqueId());
        if (session == null) return;
        if (slot == 49) {
            p.closeInventory();
            p.sendMessage(ColorUtils.colorize("&eType new lore line (use &). Type cancel to abort."));
            chatInput.put(p.getUniqueId(), "lore_add");
            return;
        }
        if (slot == 50) {
            // Send raw formatting codes without colorization
            p.sendMessage("&6&l=== Formatting Codes ===");
            p.sendMessage("&cRed &6Gold &eYellow &aGreen &bAqua &9Blue &dPink &fWhite &7Gray &8Dark Gray");
            p.sendMessage("&lBold &oItalic &nUnderline &mStrikethrough &kObfuscated");
            p.sendMessage("&7Use & before codes in your lore lines.");
            return;
        }
        if (slot == 53) {
            refreshEditGUI(p);
            openLoreEditor(p);
            return;
        }
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        List<String> lore = session.getItem().getLore();
        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i] && i < lore.size()) {
                if (click.isLeftClick()) {
                    p.closeInventory();
                    p.sendMessage(ColorUtils.colorize("&eEditing line " + (i + 1) + ":"));
                    p.sendMessage(ColorUtils.colorize("&7Current: " + ColorUtils.colorize(lore.get(i))));
                    p.sendMessage(ColorUtils.colorize("&eType new line. Type cancel to abort."));
                    chatInput.put(p.getUniqueId(), "lore_edit:" + i);
                } else if (click.isRightClick()) {
                    lore.remove(i);
                    session.getItem().setLore(lore);
                    p.sendMessage(ColorUtils.colorize("&aLine removed."));
                    refreshEditGUI(p);
                    openLoreEditor(p);
                }
                return;
            }
        }
    }

    private void handleFlags(Player p, int slot, ClickType click) {
        if (slot == 49) {
            refreshEditGUI(p);
            return;
        }
        EditingSession session = sessions.get(p.getUniqueId());
        if (session == null) return;
        CustomItemFlag[] flags = CustomItemFlag.values();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i] && i < flags.length) {
                session.getItem().toggleFlag(flags[i]);
                openFlags(p);
                return;
            }
        }
    }

    // ---------- Utilities ----------
    private void fillRest(Inventory inv) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
    }

    private ItemStack createButton(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorUtils.colorize(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(ColorUtils.colorize(s));
        meta.setLore(l);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createToggleButton(Material mat, String name, boolean state) {
        String status = state ? "&a✔ ON" : "&c✖ OFF";
        return createButton(mat, name + " &7(" + status + ")", "&7Click to toggle");
    }

    // ---------- Inner EditingSession ----------
    private static class EditingSession {
        private AdvancedCustomItem item;

        public EditingSession(AdvancedCustomItem item) {
            this.item = item;
        }

        public AdvancedCustomItem getItem() {
            return item;
        }
    }
}