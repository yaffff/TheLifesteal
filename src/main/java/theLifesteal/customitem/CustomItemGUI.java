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
    private final Map<UUID, Runnable> returnAction;
    private final Map<UUID, Boolean> deleteMode;
    private final Map<UUID, Integer> pageCache;
    private final Set<UUID> inTransition;

    private static final String MAIN_TITLE = "&5✦ &dItem Creator &5✦";
    private static final int ITEMS_PER_PAGE = 28;
    private static final int[] ITEM_SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};

    private static final List<Attribute> ATTRIBUTES = Arrays.asList(
            Attribute.ARMOR, Attribute.ARMOR_TOUGHNESS, Attribute.ATTACK_DAMAGE,
            Attribute.ATTACK_KNOCKBACK, Attribute.ATTACK_SPEED, Attribute.BLOCK_BREAK_SPEED,
            Attribute.BLOCK_INTERACTION_RANGE, Attribute.ENTITY_INTERACTION_RANGE,
            Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, Attribute.FALL_DAMAGE_MULTIPLIER,
            Attribute.GRAVITY, Attribute.JUMP_STRENGTH, Attribute.KNOCKBACK_RESISTANCE,
            Attribute.LUCK, Attribute.MAX_HEALTH, Attribute.MOVEMENT_SPEED,
            Attribute.OXYGEN_BONUS, Attribute.SAFE_FALL_DISTANCE, Attribute.SCALE,
            Attribute.STEP_HEIGHT, Attribute.WATER_MOVEMENT_EFFICIENCY,
            Attribute.SNEAKING_SPEED, Attribute.SUBMERGED_MINING_SPEED,
            Attribute.SWEEPING_DAMAGE_RATIO
    );

    public CustomItemGUI(JavaPlugin plugin, AdvancedCustomItemManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.sessions = new HashMap<>();
        this.chatInput = new HashMap<>();
        this.returnAction = new HashMap<>();
        this.deleteMode = new HashMap<>();
        this.pageCache = new HashMap<>();
        this.inTransition = new HashSet<>();
    }

    // ---------- Public API ----------
    public void openMainMenu(Player player) {
        pageCache.put(player.getUniqueId(), 0);
        player.openInventory(buildMainGUI(player));
    }

    public void openEditGUI(Player player, String itemId) {
        AdvancedCustomItem item = manager.getItem(itemId);
        if (item == null) {
            player.sendMessage(ColorUtils.colorize("&cItem not found!"));
            return;
        }
        sessions.put(player.getUniqueId(), new EditingSession(item.clone()));
        player.openInventory(buildEditMain(player));
    }

    public boolean isAwaitingInput(UUID uuid) { return chatInput.containsKey(uuid); }

    public boolean isCustomItemGUI(String title) {
        return title.contains("Item Creator") || title.contains("Choose Base") ||
                title.contains("Edit Item") || title.contains("Attributes") ||
                title.contains("Name & Lore") || title.contains("Flags") ||
                title.contains("Coming Soon");
    }

    public void saveOnClose(Player player) {
        UUID uuid = player.getUniqueId();
        if (inTransition.remove(uuid)) return;
        commitSession(player);
    }

    public void handleChatInput(Player player, String message) {
        UUID uuid = player.getUniqueId();
        String key = chatInput.remove(uuid);
        Runnable back = returnAction.remove(uuid);
        if (key == null || message.equalsIgnoreCase("cancel")) {
            player.sendMessage(ColorUtils.colorize("&cCancelled."));
            if (back != null) back.run();
            else openMainMenu(player);
            return;
        }

        EditingSession ses = sessions.get(uuid);
        if (ses == null) { openMainMenu(player); return; }

        switch (key) {
            case "displayName":
                ses.item.setDisplayName(message.trim().isEmpty() ? null : message);
                player.sendMessage(ColorUtils.colorize("&aName updated."));
                break;
            case "lore_add":
                List<String> lore = ses.item.getLore();
                if (lore.size() >= 28) {
                    player.sendMessage(ColorUtils.colorize("&cMax 28 lines."));
                    break;
                }
                lore.add(message);
                ses.item.setLore(lore);  // ← FIX: save the modified list
                player.sendMessage(ColorUtils.colorize("&aLine added."));
                break;
            default:
                if (key.startsWith("lore_edit:")) {
                    int idx = Integer.parseInt(key.split(":")[1]);
                    List<String> l = ses.item.getLore();
                    if (idx >= 0 && idx < l.size()) {
                        l.set(idx, message);
                        ses.item.setLore(l);  // ← FIX: save the modified list
                        player.sendMessage(ColorUtils.colorize("&aLine updated."));
                    }
                } else if (key.startsWith("attr:")) {
                    try {
                        double val = Double.parseDouble(message);
                        String attrName = key.substring(5);
                        Attribute attr = Attribute.valueOf(attrName);
                        if (val == 0) ses.item.removeAttribute(attr);
                        else ses.item.addAttribute(attr, val);
                        player.sendMessage(ColorUtils.colorize("&aAttribute updated."));
                    } catch (Exception e) { player.sendMessage(ColorUtils.colorize("&cInvalid.")); }
                } else if (key.equals("modeldata")) {
                    try {
                        ses.item.setCustomModelData(Integer.parseInt(message));
                        player.sendMessage(ColorUtils.colorize("&aModel data set."));
                    } catch (NumberFormatException e) { player.sendMessage(ColorUtils.colorize("&cInvalid number.")); }
                } else if (key.equals("damage")) {
                    try {
                        ses.item.setDamage(Math.max(0, Integer.parseInt(message)));
                        player.sendMessage(ColorUtils.colorize("&aDurability set."));
                    } catch (NumberFormatException e) { player.sendMessage(ColorUtils.colorize("&cInvalid number.")); }
                }
        }
        if (back != null) back.run();
        else openMainMenu(player);
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

        if (title.contains("Item Creator"))            mainClick(p, slot, click, current);
        else if (title.contains("Choose Base"))        templateClick(p, slot, event.getCursor());
        else if (title.contains("Edit Item"))          editMainClick(p, slot);
        else if (title.contains("Attributes"))         attrClick(p, slot);
        else if (title.contains("Name & Lore"))        nameLoreClick(p, slot, click);
        else if (title.contains("Flags"))              flagClick(p, slot);
        else if (title.contains("Coming Soon") && slot == 26) openMainMenu(p);
    }

    public void cleanupPlayer(UUID uuid) {
        sessions.remove(uuid);
        chatInput.remove(uuid);
        returnAction.remove(uuid);
        deleteMode.remove(uuid);
        pageCache.remove(uuid);
        inTransition.remove(uuid);
    }

    // ==================== GUI BUILDERS ====================

    private Inventory buildMainGUI(Player player) {
        int page = pageCache.getOrDefault(player.getUniqueId(), 0);
        Inventory gui = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text(ColorUtils.colorize(MAIN_TITLE)));
        List<AdvancedCustomItem> all = new ArrayList<>(manager.getAllItems());
        int totalPages = (int) Math.ceil((double) all.size() / ITEMS_PER_PAGE);
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, all.size());

        for (int i = start; i < end; i++) {
            AdvancedCustomItem item = all.get(i);
            ItemStack display = manager.buildItem(item);
            manager.storeItemId(display, item.getId());
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(ColorUtils.colorize("&7&m-------------------"));
                lore.add(ColorUtils.colorize("&a▶ Left → get &8| &e▶ Right → edit"));
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            gui.setItem(ITEM_SLOTS[i - start], display);
        }

        if (page > 0) gui.setItem(45, item(Material.ARROW, "&a← Previous"));
        if (page < totalPages - 1) gui.setItem(53, item(Material.ARROW, "&aNext →"));

        gui.setItem(48, item(Material.EMERALD_BLOCK, "&a✦ &6Create New", "&7Pick a base material"));
        boolean del = deleteMode.getOrDefault(player.getUniqueId(), false);
        gui.setItem(49, deleteToggleItem(del));
        gui.setItem(53, item(Material.BARRIER, "&c✖ Close"));
        filler(gui);
        return gui;
    }

    private Inventory buildEditMain(Player player) {
        EditingSession ses = sessions.get(player.getUniqueId());
        if (ses == null) return buildMainGUI(player);
        Inventory gui = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text(ColorUtils.colorize("&6✦ &eEdit Item &6✦")));
        for (int i : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,50,51,52,53})
            gui.setItem(i, glass(Material.CYAN_STAINED_GLASS_PANE));

        gui.setItem(22, manager.buildItem(ses.item));
        gui.setItem(28, item(Material.NAME_TAG, "&6✏ Name & Lore"));
        gui.setItem(29, item(Material.DIAMOND_SWORD, "&6⚔ Attributes"));
        gui.setItem(30, item(Material.LEATHER_CHESTPLATE, "&6🏁 Flags"));
        gui.setItem(31, item(Material.BEACON, "&d✦ Coming Soon"));

        gui.setItem(49, item(Material.LIME_DYE, "&a✔ Save & Close"));
        gui.setItem(50, item(Material.BARRIER, "&c✖ Cancel"));
        filler(gui);
        return gui;
    }

    private Inventory buildNameLore(Player player) {
        EditingSession ses = sessions.get(player.getUniqueId());
        if (ses == null) return buildMainGUI(player);
        Inventory gui = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text(ColorUtils.colorize("&6✦ &eName & Lore &6✦")));
        for (int i : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,50,51,52,53})
            gui.setItem(i, glass(Material.LIGHT_BLUE_STAINED_GLASS_PANE));

        String name = ses.item.getDisplayName() != null ? ses.item.getDisplayName() : "&7(no custom name)";
        gui.setItem(13, item(Material.NAME_TAG, "&eCurrent Name", "&f" + ColorUtils.colorize(name), "", "&aClick to change name"));

        List<String> lore = ses.item.getLore();
        int[] lineSlots = {19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43,10,11,12,14,15,16};
        for (int i = 0; i < Math.min(lore.size(), lineSlots.length); i++) {
            ItemStack lineItem = new ItemStack(Material.PAPER);
            ItemMeta meta = lineItem.getItemMeta();
            meta.setDisplayName(ColorUtils.colorize("&eLine " + (i+1)));
            meta.setLore(Arrays.asList(
                    ColorUtils.colorize("&7" + ColorUtils.colorize(lore.get(i))),
                    "",
                    ColorUtils.colorize("&a▶ Left → edit &8| &c▶ Right → remove")
            ));
            lineItem.setItemMeta(meta);
            gui.setItem(lineSlots[i], lineItem);
        }

        gui.setItem(49, item(Material.WRITABLE_BOOK, "&a➕ Add Line"));
        gui.setItem(53, item(Material.ARROW, "&a← Back to Editor"));
        filler(gui);
        return gui;
    }

    private Inventory buildAttr(Player player) {
        EditingSession ses = sessions.get(player.getUniqueId());
        if (ses == null) return buildMainGUI(player);
        Inventory gui = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text(ColorUtils.colorize("&6✦ &eAttributes &6✦")));
        for (int i : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,50,51,52,53})
            gui.setItem(i, glass(Material.BLUE_STAINED_GLASS_PANE));

        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        for (int i = 0; i < Math.min(ATTRIBUTES.size(), slots.length); i++) {
            Attribute a = ATTRIBUTES.get(i);
            double val = ses.item.getAttributes().getOrDefault(a, 0.0);
            gui.setItem(slots[i], item(Material.PAPER, "&e" + a.name(),
                    "&7Current: &f" + (val != 0 ? val : "—"),
                    "&aClick to set value"));
        }

        gui.setItem(49, item(Material.ANVIL, "&6🔧 Durability: &f" + ses.item.getDamage(), "&7Set durability loss"));
        gui.setItem(50, item(Material.COMMAND_BLOCK, "&6🔢 Model Data: &f" + ses.item.getCustomModelData(), "&7Set model data"));
        gui.setItem(53, item(Material.ARROW, "&a← Back"));
        filler(gui);
        return gui;
    }

    private Inventory buildFlags(Player player) {
        EditingSession ses = sessions.get(player.getUniqueId());
        if (ses == null) return buildMainGUI(player);
        Inventory gui = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text(ColorUtils.colorize("&6✦ &eFlags &6✦")));
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        CustomItemFlag[] flags = CustomItemFlag.values();
        for (int i = 0; i < Math.min(flags.length, slots.length); i++) {
            boolean on = ses.item.hasFlag(flags[i]);
            gui.setItem(slots[i], toggleItem(on ? Material.LIME_DYE : Material.GRAY_DYE,
                    "&e" + flags[i].name().toLowerCase().replace("_"," "), on));
        }
        gui.setItem(53, item(Material.ARROW, "&a← Back"));
        filler(gui);
        return gui;
    }

    private Inventory buildTemplate() {
        Inventory gui = Bukkit.createInventory(null, 27,
                net.kyori.adventure.text.Component.text(ColorUtils.colorize("&6✦ &eChoose Base Item &6✦")));
        gui.setItem(13, item(Material.GRAY_STAINED_GLASS_PANE, "&7Click with an item", "&7to set it as the template"));
        gui.setItem(22, item(Material.LIME_DYE, "&a✔ Confirm"));
        gui.setItem(26, item(Material.RED_DYE, "&c✖ Cancel"));
        filler(gui);
        return gui;
    }

    // ==================== CLICK HANDLERS ====================

    // *** FIX: Add inTransition before closing inventory for chat input ***
    private void startChatInput(Player p, String key, Runnable returnGui) {
        inTransition.add(p.getUniqueId()); // prevents session commit on close
        p.closeInventory();
        chatInput.put(p.getUniqueId(), key);
        returnAction.put(p.getUniqueId(), returnGui);
        p.sendMessage(ColorUtils.colorize("&eType your input. Type &ccancel &eto go back."));
    }

    private void mainClick(Player p, int slot, ClickType click, ItemStack current) {
        int page = pageCache.getOrDefault(p.getUniqueId(), 0);
        if (slot == 45 && page > 0) { pageCache.put(p.getUniqueId(), page-1); p.openInventory(buildMainGUI(p)); return; }
        if (slot == 53) {
            List<AdvancedCustomItem> all = new ArrayList<>(manager.getAllItems());
            int totalPages = (int) Math.ceil((double) all.size() / ITEMS_PER_PAGE);
            if (page < totalPages-1) { pageCache.put(p.getUniqueId(), page+1); p.openInventory(buildMainGUI(p)); }
            return;
        }
        if (slot == 48) { p.openInventory(buildTemplate()); return; }
        if (slot == 49) { deleteMode.put(p.getUniqueId(), !deleteMode.getOrDefault(p.getUniqueId(), false)); p.openInventory(buildMainGUI(p)); return; }

        String id = manager.getItemId(current);
        if (id == null) return;
        if (deleteMode.getOrDefault(p.getUniqueId(), false)) {
            manager.deleteItem(id);
            p.sendMessage(ColorUtils.colorize("&cDeleted."));
            p.openInventory(buildMainGUI(p));
            return;
        }
        if (click.isLeftClick()) {
            p.getInventory().addItem(manager.buildItem(manager.getItem(id)));
            p.sendMessage(ColorUtils.colorize("&aItem given."));
        } else if (click.isRightClick()) {
            openEditGUI(p, id);
        }
    }

    private void templateClick(Player p, int slot, ItemStack cursor) {
        if (slot == 13) {
            if (cursor != null && cursor.getType() != Material.AIR) p.getOpenInventory().setItem(13, cursor.clone());
        } else if (slot == 22) {
            ItemStack template = p.getOpenInventory().getItem(13);
            if (template == null || template.getType() == Material.AIR || template.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                p.sendMessage(ColorUtils.colorize("&cSet a template first!")); return;
            }
            String id = "custom_" + System.currentTimeMillis();
            manager.createItem(id, template);
            p.closeInventory();
            openEditGUI(p, id);
        } else if (slot == 26) openMainMenu(p);
    }

    private void editMainClick(Player p, int slot) {
        switch (slot) {
            case 28: inTransition.add(p.getUniqueId()); p.openInventory(buildNameLore(p)); break;
            case 29: inTransition.add(p.getUniqueId()); p.openInventory(buildAttr(p)); break;
            case 30: inTransition.add(p.getUniqueId()); p.openInventory(buildFlags(p)); break;
            case 31: inTransition.add(p.getUniqueId()); p.openInventory(comingSoon(p)); break;
            case 49: commitAndExit(p, true); break;
            case 50: sessions.remove(p.getUniqueId()); p.closeInventory(); openMainMenu(p); break;
        }
    }

    private void nameLoreClick(Player p, int slot, ClickType click) {
        EditingSession ses = sessions.get(p.getUniqueId());
        if (ses == null) return;
        if (slot == 13) { // set name
            startChatInput(p, "displayName", () -> p.openInventory(buildNameLore(p)));
            return;
        }
        if (slot == 49) { // add lore line
            if (ses.item.getLore().size() >= 28) { p.sendMessage(ColorUtils.colorize("&cMax 28 lines!")); return; }
            startChatInput(p, "lore_add", () -> p.openInventory(buildNameLore(p)));
            return;
        }
        if (slot == 53) { // back to editor
            inTransition.add(p.getUniqueId()); p.openInventory(buildEditMain(p)); return;
        }

        List<String> lore = ses.item.getLore();
        int[] lineSlots = {19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43,10,11,12,14,15,16};
        for (int i = 0; i < lineSlots.length; i++) {
            if (slot == lineSlots[i] && i < lore.size()) {
                if (click.isLeftClick()) {
                    startChatInput(p, "lore_edit:" + i, () -> p.openInventory(buildNameLore(p)));
                } else if (click.isRightClick()) {
                    lore.remove(i); ses.item.setLore(lore);
                    p.openInventory(buildNameLore(p));
                }
                return;
            }
        }
    }

    private void attrClick(Player p, int slot) {
        EditingSession ses = sessions.get(p.getUniqueId());
        if (ses == null) return;
        if (slot == 53) { inTransition.add(p.getUniqueId()); p.openInventory(buildEditMain(p)); return; }
        if (slot == 49) { // durability
            startChatInput(p, "damage", () -> p.openInventory(buildAttr(p)));
            return;
        }
        if (slot == 50) { // model data
            startChatInput(p, "modeldata", () -> p.openInventory(buildAttr(p)));
            return;
        }
        int[] attrSlots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        for (int i = 0; i < attrSlots.length && i < ATTRIBUTES.size(); i++) {
            if (slot == attrSlots[i]) {
                Attribute a = ATTRIBUTES.get(i);
                startChatInput(p, "attr:" + a.name(), () -> p.openInventory(buildAttr(p)));
                return;
            }
        }
    }

    private void flagClick(Player p, int slot) {
        if (slot == 53) { inTransition.add(p.getUniqueId()); p.openInventory(buildEditMain(p)); return; }
        EditingSession ses = sessions.get(p.getUniqueId());
        if (ses == null) return;
        int[] flagSlots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        CustomItemFlag[] all = CustomItemFlag.values();
        for (int i = 0; i < flagSlots.length && i < all.length; i++) {
            if (slot == flagSlots[i]) {
                ses.item.toggleFlag(all[i]);
                // Mark transition so session isn't removed when GUI closes
                inTransition.add(p.getUniqueId());
                p.openInventory(buildFlags(p));
                return;
            }
        }
    }

    // ==================== SESSION ====================

    private void commitSession(Player player) {
        EditingSession ses = sessions.get(player.getUniqueId());
        if (ses == null) return;
        AdvancedCustomItem original = manager.getItem(ses.item.getId());
        if (original != null) {
            original.setBaseItem(ses.item.getBaseItem());
            original.setDisplayName(ses.item.getDisplayName());
            original.setLore(ses.item.getLore());
            original.setAttributes(ses.item.getAttributes());
            original.setFlags(ses.item.getFlags());
            original.setCustomModelData(ses.item.getCustomModelData());
            original.setDamage(ses.item.getDamage());
            original.setFutureExtensions(ses.item.getFutureExtensions());
            manager.saveItems();
        }
        sessions.remove(player.getUniqueId());
        chatInput.remove(player.getUniqueId());
        returnAction.remove(player.getUniqueId());
        inTransition.remove(player.getUniqueId());
    }

    private void commitAndExit(Player player, boolean close) {
        commitSession(player);
        if (close) player.closeInventory();
        openMainMenu(player);
    }

    // ==================== ITEMS ====================

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(ColorUtils.colorize(name));
        if (lore.length > 0) {
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(ColorUtils.colorize(s));
            m.setLore(l);
        }
        i.setItemMeta(m);
        return i;
    }

    private ItemStack toggleItem(Material mat, String name, boolean state) {
        String status = state ? "&a✔ ON" : "&c✖ OFF";
        return item(mat, name + " &7(" + status + ")", "&7Click to toggle");
    }

    private ItemStack deleteToggleItem(boolean on) {
        Material mat = on ? Material.LIME_DYE : Material.RED_DYE;
        String color = on ? "&a" : "&c";
        return item(mat, color + "🗑 Delete Mode &7(" + (on ? "&a✔ ON" : "&c✖ OFF") + ")");
    }

    private ItemStack glass(Material mat) {
        ItemStack g = new ItemStack(mat);
        ItemMeta m = g.getItemMeta();
        m.setDisplayName(" ");
        g.setItemMeta(m);
        return g;
    }

    private void filler(Inventory inv) {
        ItemStack f = glass(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, f);
    }

    private Inventory comingSoon(Player p) {
        Inventory gui = Bukkit.createInventory(null, 27,
                net.kyori.adventure.text.Component.text(ColorUtils.colorize("&6✦ &eComing Soon &6✦")));
        gui.setItem(13, item(Material.CLOCK, "&d✦ Potions, Armor Color, etc."));
        gui.setItem(26, item(Material.ARROW, "&a← Back"));
        filler(gui);
        return gui;
    }

    // ==================== INNER CLASS ====================

    private static class EditingSession {
        final AdvancedCustomItem item;
        EditingSession(AdvancedCustomItem item) { this.item = item; }
    }
}