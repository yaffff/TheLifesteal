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
import theLifesteal.abilities.ItemAbilityManager;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AdvancedCustomItemManager {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Map<String, AdvancedCustomItem> items;
    private final NamespacedKey itemIdKey;
    private ItemAbilityManager abilityManager;

    public AdvancedCustomItemManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "custom_items.yml");
        this.items = new LinkedHashMap<>();
        this.itemIdKey = new NamespacedKey(plugin, "custom_item_id");
    }

    public void setAbilityManager(ItemAbilityManager abilityManager) {
        this.abilityManager = abilityManager;
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
                if (itemSection.contains("category")) item.setCategory(itemSection.getString("category", "Misc"));
                if (itemSection.contains("rarity")) {
                    String rarityStr = itemSection.getString("rarity", "COMMON");
                    try {
                        item.setRarity(ItemLoreBuilder.Rarity.valueOf(rarityStr.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        item.setRarity(ItemLoreBuilder.Rarity.COMMON);
                    }
                }

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
                if (flagNames != null && !flagNames.isEmpty()) {
                    EnumSet<CustomItemFlag> flags = EnumSet.noneOf(CustomItemFlag.class);
                    for (String flagName : flagNames) {
                        try {
                            flags.add(CustomItemFlag.valueOf(flagName));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    item.setFlags(flags);
                }

                if (itemSection.contains("customModelData")) item.setCustomModelData(itemSection.getInt("customModelData"));
                if (itemSection.contains("damage")) item.setDamage(itemSection.getInt("damage"));

                // Load enchants
                List<String> enchantData = itemSection.getStringList("enchants");
                if (!enchantData.isEmpty()) {
                    Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
                    for (String data : enchantData) {
                        String[] parts = data.split(":");
                        if (parts.length == 2) {
                            Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(parts[0].toLowerCase()));
                            if (ench != null) {
                                try {
                                    enchants.put(ench, Integer.parseInt(parts[1]));
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                    item.setEnchants(enchants);
                }

                List<String> potionData = itemSection.getStringList("potionEffects");
                if (!potionData.isEmpty()) {
                    List<AdvancedCustomItem.PotionEffectData> effects = new ArrayList<>();
                    for (String data : potionData) {
                        AdvancedCustomItem.PotionEffectData effect = AdvancedCustomItem.PotionEffectData.deserialize(data);
                        if (effect != null) effects.add(effect);
                    }
                    item.setPotionEffects(effects);
                }

                if (itemSection.contains("abilities") && abilityManager != null) {
                    ConfigurationSection abilitiesSection = itemSection.getConfigurationSection("abilities");
                    if (abilitiesSection != null) {
                        Map<String, Object> rawMap = new LinkedHashMap<>();
                        for (String key : abilitiesSection.getKeys(false)) {
                            rawMap.put(key, abilitiesSection.get(key));
                        }
                        item.setAbilities(abilityManager.deserialize(rawMap));
                    }
                }

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
            itemSection.set("category", item.getCategory());
            itemSection.set("rarity", item.getRarity().name());
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

            // Save enchants
            if (!item.getEnchants().isEmpty()) {
                List<String> enchantData = new ArrayList<>();
                for (Map.Entry<Enchantment, Integer> entry : item.getEnchants().entrySet()) {
                    enchantData.add(entry.getKey().getKey().getKey() + ":" + entry.getValue());
                }
                itemSection.set("enchants", enchantData);
            }

            if (!item.getPotionEffects().isEmpty()) {
                List<String> potionData = new ArrayList<>();
                for (AdvancedCustomItem.PotionEffectData effect : item.getPotionEffects()) {
                    potionData.add(effect.serialize());
                }
                itemSection.set("potionEffects", potionData);
            }

            if (abilityManager != null) {
                Map<String, Object> abilityData = abilityManager.serialize(item.getAbilities());
                if (!abilityData.isEmpty()) {
                    itemSection.createSection("abilities", abilityData);
                }
            }

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
        return buildItem(item, item.getId());
    }

    @SuppressWarnings("deprecation")
    public ItemStack buildItem(AdvancedCustomItem item, String idToStore) {
        if (item == null) return null;

        ItemStack result = item.getBaseItem().clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        meta.setDisplayName(null);
        meta.setLore(null);
        meta.setCustomModelData(null);
        meta.setUnbreakable(false);
        for (Attribute attr : Attribute.values()) {
            meta.removeAttributeModifier(attr);
        }
        meta.removeItemFlags(ItemFlag.values());
        for (Enchantment ench : meta.getEnchants().keySet()) {
            meta.removeEnchant(ench);
        }

        String rarityColor = ItemLoreBuilder.getRarityColor(item.getRarity());
        if (item.getDisplayName() != null && !item.getDisplayName().isEmpty()) {
            meta.setDisplayName(ColorUtils.colorize(rarityColor + item.getDisplayName()));
        } else {
            meta.setDisplayName(ColorUtils.colorize(rarityColor + formatMaterialName(result.getType())));
        }

        List<String> builtLore = ItemLoreBuilder.buildLore(item, abilityManager);
        if (!builtLore.isEmpty()) {
            meta.setLore(builtLore);
        }

        // Apply custom enchants
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchants().entrySet()) {
            meta.addEnchant(entry.getKey(), entry.getValue(), true);
        }

        if (item.getCustomModelData() > 0) meta.setCustomModelData(item.getCustomModelData());

        if (item.getDamage() > 0 && result.getType().getMaxDurability() > 0) {
            result.setDurability((short) Math.min(result.getType().getMaxDurability(), item.getDamage()));
        }

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

        for (Map.Entry<Attribute, Double> entry : item.getAttributes().entrySet()) {
            AttributeModifier modifier = new AttributeModifier(
                    UUID.nameUUIDFromBytes(("custom_attr:" + entry.getKey().name()).getBytes()),
                    "custom_mod",
                    entry.getValue(),
                    AttributeModifier.Operation.ADD_NUMBER
            );
            meta.addAttributeModifier(entry.getKey(), modifier);
        }

        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, idToStore);

        result.setItemMeta(meta);
        return result;
    }

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

    public AdvancedCustomItem getItemByStack(ItemStack item) {
        String id = getItemId(item);
        if (id == null) return null;
        return items.get(id);
    }

    private String formatMaterialName(org.bukkit.Material mat) {
        String name = mat.name().replace("_", " ").toLowerCase();
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }
}