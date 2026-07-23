package theLifesteal.customitem;

import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import theLifesteal.abilities.ItemAbilityData;
import theLifesteal.abilities.ItemAbilityType;

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
    private List<PotionEffectData> potionEffects;
    private String category;
    private ItemLoreBuilder.Rarity rarity;
    private Map<ItemAbilityType, List<ItemAbilityData>> abilities;
    private Map<Enchantment, Integer> enchants;

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
        this.potionEffects = new ArrayList<>();
        this.category = "Misc";
        this.rarity = ItemLoreBuilder.Rarity.COMMON;
        this.abilities = new LinkedHashMap<>();
        for (ItemAbilityType type : ItemAbilityType.values()) {
            this.abilities.put(type, new ArrayList<>());
        }
        this.enchants = new LinkedHashMap<>();
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

    public List<PotionEffectData> getPotionEffects() { return new ArrayList<>(potionEffects); }
    public void setPotionEffects(List<PotionEffectData> effects) { this.potionEffects = new ArrayList<>(effects); }
    public void addPotionEffect(PotionEffectData effect) { this.potionEffects.add(effect); }
    public void removePotionEffect(int index) {
        if (index >= 0 && index < potionEffects.size()) potionEffects.remove(index);
    }
    public void clearPotionEffects() { this.potionEffects.clear(); }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public ItemLoreBuilder.Rarity getRarity() { return rarity; }
    public void setRarity(ItemLoreBuilder.Rarity rarity) { this.rarity = rarity; }

    public Map<ItemAbilityType, List<ItemAbilityData>> getAbilities() { return abilities; }
    public void setAbilities(Map<ItemAbilityType, List<ItemAbilityData>> abilities) { this.abilities = abilities; }

    public Map<Enchantment, Integer> getEnchants() { return enchants; }
    public void setEnchants(Map<Enchantment, Integer> enchants) { this.enchants = new LinkedHashMap<>(enchants); }

    public AdvancedCustomItem clone() {
        AdvancedCustomItem clone = new AdvancedCustomItem(this.id, this.baseItem);
        clone.setDisplayName(this.displayName);
        clone.setLore(this.lore);
        clone.setAttributes(this.attributes);
        clone.setFlags(this.flags);
        clone.setCustomModelData(this.customModelData);
        clone.setDamage(this.damage);
        clone.setFutureExtensions(this.futureExtensions);
        clone.setPotionEffects(this.potionEffects);
        clone.setCategory(this.category);
        clone.setRarity(this.rarity);

        Map<ItemAbilityType, List<ItemAbilityData>> abilitiesCopy = new LinkedHashMap<>();
        for (Map.Entry<ItemAbilityType, List<ItemAbilityData>> entry : this.abilities.entrySet()) {
            List<ItemAbilityData> listCopy = new ArrayList<>();
            for (ItemAbilityData data : entry.getValue()) {
                ItemAbilityData dataCopy = new ItemAbilityData(data.getAbilityId(), data.getType());
                dataCopy.setConfig(new LinkedHashMap<>(data.getConfig()));
                listCopy.add(dataCopy);
            }
            abilitiesCopy.put(entry.getKey(), listCopy);
        }
        clone.setAbilities(abilitiesCopy);

        clone.setEnchants(new LinkedHashMap<>(this.enchants));

        return clone;
    }

    public static class PotionEffectData {
        private final PotionEffectType type;
        private final int amplifier;
        private final boolean showParticles;

        public PotionEffectData(PotionEffectType type, int amplifier, boolean showParticles) {
            this.type = type;
            this.amplifier = amplifier;
            this.showParticles = showParticles;
        }

        public PotionEffectType getType() { return type; }
        public int getAmplifier() { return amplifier; }
        public boolean showParticles() { return showParticles; }

        public PotionEffect toEffect(int duration) {
            return new PotionEffect(type, duration, amplifier, false, showParticles, true);
        }

        public String serialize() {
            return type.getKey().getKey() + ":" + amplifier + ":" + showParticles;
        }

        public static PotionEffectData deserialize(String data) {
            String[] parts = data.split(":");
            if (parts.length < 3) return null;
            PotionEffectType type = PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(parts[0]));
            if (type == null) return null;
            try {
                int amplifier = Integer.parseInt(parts[1]);
                boolean particles = Boolean.parseBoolean(parts[2]);
                return new PotionEffectData(type, amplifier, particles);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}