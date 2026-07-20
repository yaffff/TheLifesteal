package theLifesteal.customitem;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import theLifesteal.ColorUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AdvancedCustomItemManager {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Map<String, AdvancedCustomItem> items;
    private final NamespacedKey itemIdKey;

    public AdvancedCustomItemManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "custom_items.yml");
        this.items = new LinkedHashMap<>();
        this.itemIdKey = new NamespacedKey(plugin, "custom_item_id");
        loadItems();
    }

    public NamespacedKey getItemIdKey() { return itemIdKey; }

    @SuppressWarnings("deprecation")
    public void loadItems() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = config.getConfigurationSection("advanced-items");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            try {
                ConfigurationSection itemSection = section.getConfigurationSection(id);
                if (itemSection == null) continue;
                ItemStack baseItem = itemSection.getItemStack("baseItem");
                if (baseItem == null) continue;

                AdvancedCustomItem item = new AdvancedCustomItem(id, baseItem);
                if (itemSection.contains("displayName")) item.setDisplayName(itemSection.getString("displayName"));
                if (itemSection.contains("lore")) item.setLore(itemSection.getStringList("lore"));

                ConfigurationSection attrSection = itemSection.getConfigurationSection("attributes");
                if (attrSection != null) {
                    for (String attrName : attrSection.getKeys(false)) {
                        try {
                            Attribute attr = Attribute.valueOf(attrName);
                            double value = attrSection.getDouble(attrName);
                            item.addAttribute(attr, value);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }

                List<String> flagNames = itemSection.getStringList("flags");
                for (String flagName : flagNames) {
                    try { item.getFlags().add(CustomItemFlag.valueOf(flagName)); } catch (IllegalArgumentException ignored) {}
                }

                if (itemSection.contains("customModelData")) item.setCustomModelData(itemSection.getInt("customModelData"));
                if (itemSection.contains("damage")) item.setDamage(itemSection.getInt("damage"));

                if (itemSection.contains("futureExtensions")) {
                    ConfigurationSection extSection = itemSection.getConfigurationSection("futureExtensions");
                    if (extSection != null) {
                        Map<String, Object> extMap = new HashMap<>();
                        for (String key : extSection.getKeys(false)) extMap.put(key, extSection.get(key));
                        item.setFutureExtensions(extMap);
                    }
                }
                items.put(id, item);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load item: " + id + " - " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + items.size() + " advanced items.");
    }

    @SuppressWarnings("deprecation")
    public void saveItems() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection section = config.createSection("advanced-items");
        for (AdvancedCustomItem item : items.values()) {
            ConfigurationSection itemSection = section.createSection(item.getId());
            itemSection.set("baseItem", item.getBaseItem());
            if (item.getDisplayName() != null) itemSection.set("displayName", item.getDisplayName());
            if (!item.getLore().isEmpty()) itemSection.set("lore", item.getLore());
            if (!item.getAttributes().isEmpty()) {
                ConfigurationSection attrSection = itemSection.createSection("attributes");
                for (Map.Entry<Attribute, Double> entry : item.getAttributes().entrySet()) {
                    attrSection.set(entry.getKey().name(), entry.getValue());
                }
            }
            if (!item.getFlags().isEmpty()) {
                List<String> flagNames = new ArrayList<>();
                for (CustomItemFlag flag : item.getFlags()) flagNames.add(flag.name());
                itemSection.set("flags", flagNames);
            }
            if (item.getCustomModelData() != 0) itemSection.set("customModelData", item.getCustomModelData());
            if (item.getDamage() != 0) itemSection.set("damage", item.getDamage());
            if (!item.getFutureExtensions().isEmpty()) {
                ConfigurationSection extSection = itemSection.createSection("futureExtensions");
                for (Map.Entry<String, Object> entry : item.getFutureExtensions().entrySet()) {
                    extSection.set(entry.getKey(), entry.getValue());
                }
            }
        }
        try { config.save(dataFile); } catch (IOException e) { plugin.getLogger().warning("Failed to save: " + e.getMessage()); }
    }

    public AdvancedCustomItem createItem(String id, ItemStack baseItem) {
        AdvancedCustomItem item = new AdvancedCustomItem(id, baseItem);
        items.put(id, item);
        saveItems();
        return item;
    }

    public void deleteItem(String id) {
        items.remove(id);
        saveItems();
    }

    public AdvancedCustomItem getItem(String id) { return items.get(id); }
    public Collection<AdvancedCustomItem> getAllItems() { return new ArrayList<>(items.values()); }

    @SuppressWarnings("deprecation")
    public ItemStack buildItem(AdvancedCustomItem item) {
        if (item == null) return null;

        // Start with a clone of the base item
        ItemStack result = item.getBaseItem().clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        // ---- WIPE ALL EXISTING METADATA ----
        meta.setDisplayName(null);
        meta.setLore(null);
        meta.setCustomModelData(null);
        meta.setUnbreakable(false);
        // Remove all attribute modifiers
        for (Attribute attr : Attribute.values()) {
            meta.removeAttributeModifier(attr);
        }
        // Remove all item flags
        meta.removeItemFlags(ItemFlag.values());
        // Remove all enchantments
        for (Enchantment ench : meta.getEnchants().keySet()) {
            meta.removeEnchant(ench);
        }
        // Note: Persistent data container is not cleared here – we'll overwrite our key later.

        // ---- APPLY OUR CUSTOM DATA ----
        if (item.getDisplayName() != null && !item.getDisplayName().isEmpty()) {
            meta.setDisplayName(ColorUtils.colorize(item.getDisplayName()));
        }
        if (!item.getLore().isEmpty()) {
            List<String> colored = new ArrayList<>();
            for (String line : item.getLore()) colored.add(ColorUtils.colorize(line));
            meta.setLore(colored);
        }
        if (item.getCustomModelData() > 0) meta.setCustomModelData(item.getCustomModelData());

        // Durability
        if (item.getDamage() > 0 && result.getType().getMaxDurability() > 0) {
            result.setDurability((short) Math.min(result.getType().getMaxDurability(), item.getDamage()));
        }

        // Flags
        if (item.hasFlag(CustomItemFlag.GLOW)) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (item.hasFlag(CustomItemFlag.UNBREAKABLE)) {
            meta.setUnbreakable(true);
            if (item.hasFlag(CustomItemFlag.HIDE_UNBREAKABLE)) meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        if (item.hasFlag(CustomItemFlag.HIDE_ENCHANTS)) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        if (item.hasFlag(CustomItemFlag.HIDE_ATTRIBUTES)) meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (item.hasFlag(CustomItemFlag.HIDE_DESTROYS)) meta.addItemFlags(ItemFlag.HIDE_DESTROYS);
        if (item.hasFlag(CustomItemFlag.HIDE_PLACED_ON)) meta.addItemFlags(ItemFlag.HIDE_PLACED_ON);
        if (item.hasFlag(CustomItemFlag.HIDE_ADDITIONAL_TOOLTIP)) meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (item.hasFlag(CustomItemFlag.HIDE_DYE)) meta.addItemFlags(ItemFlag.HIDE_DYE);
        if (item.hasFlag(CustomItemFlag.HIDE_ARMOR_TRIM)) meta.addItemFlags(ItemFlag.HIDE_ARMOR_TRIM);

        // Attributes
        for (Map.Entry<Attribute, Double> entry : item.getAttributes().entrySet()) {
            AttributeModifier modifier = new AttributeModifier(
                    UUID.randomUUID(),
                    "custom_mod",
                    entry.getValue(),
                    AttributeModifier.Operation.ADD_NUMBER
            );
            meta.addAttributeModifier(entry.getKey(), modifier);
        }

        result.setItemMeta(meta);
        return result;
    }

    // Store item ID in PDC
    public void storeItemId(ItemStack item, String id) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
    }

    public String getItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }
}