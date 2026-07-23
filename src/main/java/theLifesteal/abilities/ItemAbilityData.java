package theLifesteal.abilities;

import java.util.LinkedHashMap;
import java.util.Map;

public class ItemAbilityData {
    private final String abilityId;
    private final ItemAbilityType type;
    private final Map<String, Object> config;

    public ItemAbilityData(String abilityId, ItemAbilityType type) {
        this.abilityId = abilityId;
        this.type = type;
        this.config = new LinkedHashMap<>();
    }

    public String getAbilityId() { return abilityId; }
    public ItemAbilityType getType() { return type; }

    public Map<String, Object> getConfig() { return new LinkedHashMap<>(config); }
    public void setConfig(Map<String, Object> config) { this.config.clear(); this.config.putAll(config); }
    public void setConfigValue(String key, Object value) { config.put(key, value); }
    public Object getConfigValue(String key) { return config.get(key); }
    public String getConfigString(String key) { return String.valueOf(config.getOrDefault(key, "")); }
    public int getConfigInt(String key) { return ((Number) config.getOrDefault(key, 0)).intValue(); }
    public double getConfigDouble(String key) { return ((Number) config.getOrDefault(key, 0.0)).doubleValue(); }
    public boolean getConfigBoolean(String key) { return (boolean) config.getOrDefault(key, false); }
}