package theLifesteal.customitem;

import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class AdvancedCustomItem {
    private final String id;
    private ItemStack baseItem;
    private String displayName;
    private List<String> lore;
    private Map<Attribute, Double> attributes;
    private EnumSet<CustomItemFlag> flags;
    private int customModelData;
    private int damage;
    private Map<String, Object> futureExtensions;

    public AdvancedCustomItem(String id, ItemStack baseItem) {
        this.id = id;
        this.baseItem = baseItem.clone();
        this.displayName = null;
        this.lore = new ArrayList<>();
        this.attributes = new HashMap<>();
        this.flags = EnumSet.noneOf(CustomItemFlag.class);
        this.customModelData = 0;
        this.damage = 0;
        this.futureExtensions = new HashMap<>();
    }

    public String getId() { return id; }
    public ItemStack getBaseItem() { return baseItem.clone(); }
    public void setBaseItem(ItemStack baseItem) { this.baseItem = baseItem.clone(); }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public List<String> getLore() { return new ArrayList<>(lore); }
    public void setLore(List<String> lore) { this.lore = new ArrayList<>(lore); }
    public Map<Attribute, Double> getAttributes() { return new HashMap<>(attributes); }
    public void setAttributes(Map<Attribute, Double> attributes) { this.attributes = new HashMap<>(attributes); }
    public void addAttribute(Attribute attribute, double value) { this.attributes.put(attribute, value); }
    public void removeAttribute(Attribute attribute) { this.attributes.remove(attribute); }
    public EnumSet<CustomItemFlag> getFlags() { return EnumSet.copyOf(flags); }
    public void setFlags(EnumSet<CustomItemFlag> flags) { this.flags = EnumSet.copyOf(flags); }
    public boolean hasFlag(CustomItemFlag flag) { return flags.contains(flag); }
    public void toggleFlag(CustomItemFlag flag) {
        if (flags.contains(flag)) flags.remove(flag);
        else flags.add(flag);
    }
    public int getCustomModelData() { return customModelData; }
    public void setCustomModelData(int customModelData) { this.customModelData = customModelData; }
    public int getDamage() { return damage; }
    public void setDamage(int damage) { this.damage = Math.max(0, damage); }
    public Map<String, Object> getFutureExtensions() { return new HashMap<>(futureExtensions); }
    public void setFutureExtensions(Map<String, Object> futureExtensions) { this.futureExtensions = new HashMap<>(futureExtensions); }

    public AdvancedCustomItem clone() {
        AdvancedCustomItem clone = new AdvancedCustomItem(this.id, this.baseItem);
        clone.setDisplayName(this.displayName);
        clone.setLore(this.lore);
        clone.setAttributes(this.attributes);
        clone.setFlags(this.flags);
        clone.setCustomModelData(this.customModelData);
        clone.setDamage(this.damage);
        clone.setFutureExtensions(this.futureExtensions);
        return clone;
    }
}