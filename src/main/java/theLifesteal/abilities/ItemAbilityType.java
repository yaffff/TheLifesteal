package theLifesteal.abilities;

public enum ItemAbilityType {
    RIGHT_CLICK("Right-Click", "&e▶ Right-Click: ", 1),
    SHIFT_RIGHT_CLICK("Shift Right-Click", "&e▶ Shift-Right: ", 1),
    PASSIVE("Passive", "&e▶ Passive: ", 99),
    ON_HIT("On Hit", "&e▶ On Hit: ", 99);

    private final String displayName;
    private final String lorePrefix;
    private final int maxPerItem;

    ItemAbilityType(String displayName, String lorePrefix, int maxPerItem) {
        this.displayName = displayName;
        this.lorePrefix = lorePrefix;
        this.maxPerItem = maxPerItem;
    }

    public String getDisplayName() { return displayName; }
    public String getLorePrefix() { return lorePrefix; }
    public int getMaxPerItem() { return maxPerItem; }
}