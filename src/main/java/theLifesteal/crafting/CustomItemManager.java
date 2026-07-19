package theLifesteal.crafting;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class CustomItemManager {

    private final JavaPlugin plugin;
    private final Map<String, CustomItem> customItems;
    private final File itemsFile;

    public CustomItemManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.customItems = new LinkedHashMap<>();
        this.itemsFile = new File(plugin.getDataFolder(), "custom_items.yml");

        loadItems();
    }

    public void loadItems() {
        if (!itemsFile.exists()) {
            // Don't try to create it from jar, just return
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(itemsFile);

        for (String key : config.getKeys(false)) {
            ItemStack item = config.getItemStack(key + ".item");
            if (item != null) {
                String name = config.getString(key + ".name", "Unknown Item");
                String category = config.getString(key + ".category", "Misc");
                List<String> tags = config.getStringList(key + ".tags");

                CustomItem customItem = new CustomItem(key, item, name, category, tags);
                customItems.put(key, customItem);
            }
        }
    }

    public void saveItem(String id, CustomItem item) {
        customItems.put(id, item);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(itemsFile);
        config.set(id + ".item", item.getItem());
        config.set(id + ".name", item.getName());
        config.set(id + ".category", item.getCategory());
        config.set(id + ".tags", item.getTags());

        try {
            config.save(itemsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save custom item: " + e.getMessage());
        }
    }

    public void removeItem(String id) {
        customItems.remove(id);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(itemsFile);
        config.set(id, null);

        try {
            config.save(itemsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not remove custom item: " + e.getMessage());
        }
    }

    public CustomItem getItem(String id) {
        return customItems.get(id);
    }

    public Collection<CustomItem> getAllItems() {
        return customItems.values();
    }

    public List<CustomItem> getItemsByCategory(String category) {
        return customItems.values().stream()
                .filter(item -> item.getCategory().equalsIgnoreCase(category))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<String> getCategories() {
        return customItems.values().stream()
                .map(CustomItem::getCategory)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }
}