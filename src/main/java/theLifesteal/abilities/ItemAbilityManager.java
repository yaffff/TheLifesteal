package theLifesteal.abilities;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import theLifesteal.ColorUtils;

import java.util.*;

public class ItemAbilityManager {

    private final JavaPlugin plugin;
    private final AbilityCooldownManager cooldownManager;
    private final AbilityKillTracker killTracker;
    private final Map<String, ItemAbility> registeredAbilities;

    public ItemAbilityManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.cooldownManager = new AbilityCooldownManager();
        this.killTracker = new AbilityKillTracker(plugin);
        this.registeredAbilities = new LinkedHashMap<>();
    }

    public AbilityKillTracker getKillTracker() {
        return killTracker;
    }

    public void registerAbility(ItemAbility ability) {
        registeredAbilities.put(ability.getId(), ability);
    }

    public ItemAbility getAbility(String id) {
        return registeredAbilities.get(id);
    }

    public Collection<ItemAbility> getAllAbilities() {
        return registeredAbilities.values();
    }

    public List<ItemAbility> getAbilitiesByType(ItemAbilityType type) {
        List<ItemAbility> result = new ArrayList<>();
        for (ItemAbility ability : registeredAbilities.values()) {
            if (canGoInSlot(ability.getType(), type)) {
                result.add(ability);
            }
        }
        return result;
    }

    private boolean canGoInSlot(ItemAbilityType abilityType, ItemAbilityType slotType) {
        if (abilityType == slotType) return true;
        if (abilityType == ItemAbilityType.RIGHT_CLICK && slotType == ItemAbilityType.SHIFT_RIGHT_CLICK) return true;
        if (abilityType == ItemAbilityType.SHIFT_RIGHT_CLICK && slotType == ItemAbilityType.RIGHT_CLICK) return true;
        return false;
    }

    public AbilityCooldownManager getCooldownManager() {
        return cooldownManager;
    }

    /**
     * Build the lore section for all abilities on an item.
     */
    public List<String> buildAbilityLore(Map<ItemAbilityType, List<ItemAbilityData>> abilityMap) {
        List<String> lore = new ArrayList<>();
        boolean hasAny = false;

        for (Map.Entry<ItemAbilityType, List<ItemAbilityData>> entry : abilityMap.entrySet()) {
            ItemAbilityType type = entry.getKey();
            List<ItemAbilityData> dataList = entry.getValue();

            if (dataList.isEmpty()) continue;

            if (!hasAny) {
                lore.add(ColorUtils.colorize("&6&l✦ Abilities"));
                hasAny = true;
            }

            for (ItemAbilityData data : dataList) {
                ItemAbility ability = getAbility(data.getAbilityId());
                if (ability != null) {
                    lore.add(ColorUtils.colorize(type.getLorePrefix() + "&f" + ability.getDisplayName()));
                    String loreText = ability.buildLore(data);
                    String[] lines = loreText.split("\n");
                    for (String line : lines) {
                        lore.add(ColorUtils.colorize("&7  " + line));
                    }
                }
            }
        }

        return lore;
    }

    /**
     * Execute a right-click ability.
     * Returns true if the ability triggered successfully.
     */
    public boolean executeRightClick(Player player, String itemId, Map<ItemAbilityType, List<ItemAbilityData>> abilityMap) {
        List<ItemAbilityData> abilities = abilityMap.get(ItemAbilityType.RIGHT_CLICK);
        if (abilities == null || abilities.isEmpty()) return false;

        ItemAbilityData data = abilities.get(0);
        ItemAbility ability = getAbility(data.getAbilityId());
        if (ability == null) return false;

        double hpCost = ability.getRequiredHpCost(data, player);
        if (hpCost > 0 && !ability.checkStrictHealthRequirement(player, hpCost)) {
            return false;
        }

        return ability.execute(player, data, cooldownManager, itemId);
    }

    /**
     * Execute a shift-right-click ability.
     * Returns true if the ability triggered successfully.
     */
    public boolean executeShiftRightClick(Player player, String itemId, Map<ItemAbilityType, List<ItemAbilityData>> abilityMap) {
        List<ItemAbilityData> abilities = abilityMap.get(ItemAbilityType.SHIFT_RIGHT_CLICK);
        if (abilities == null || abilities.isEmpty()) return false;

        ItemAbilityData data = abilities.get(0);
        ItemAbility ability = getAbility(data.getAbilityId());
        if (ability == null) return false;

        double hpCost = ability.getRequiredHpCost(data, player);
        if (hpCost > 0 && !ability.checkStrictHealthRequirement(player, hpCost)) {
            return false;
        }

        return ability.execute(player, data, cooldownManager, itemId);
    }

    /**
     * Execute all on-hit abilities.
     * Each ability handles its own logic via onHitExecute().
     */
    public void executeOnHit(Player attacker, LivingEntity victim, String itemId,
                             Map<ItemAbilityType, List<ItemAbilityData>> abilityMap, double baseDamage) {
        List<ItemAbilityData> abilities = abilityMap.get(ItemAbilityType.ON_HIT);
        if (abilities == null) return;

        for (ItemAbilityData data : abilities) {
            ItemAbility ability = getAbility(data.getAbilityId());
            if (ability == null) continue;

            double hpCost = ability.getRequiredHpCost(data, attacker);
            if (hpCost > 0 && !ability.checkStrictHealthRequirement(attacker, hpCost)) {
                continue;
            }

            ability.onHitExecute(attacker, victim, data, cooldownManager, itemId, baseDamage);
        }
    }

    /**
     * Serialize ability map to YML-compatible structure.
     */
    public Map<String, Object> serialize(Map<ItemAbilityType, List<ItemAbilityData>> abilityMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<ItemAbilityType, List<ItemAbilityData>> entry : abilityMap.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (ItemAbilityData data : entry.getValue()) {
                Map<String, Object> dataMap = new LinkedHashMap<>();
                dataMap.put("ability", data.getAbilityId());
                dataMap.putAll(data.getConfig());
                list.add(dataMap);
            }
            if (!list.isEmpty()) {
                result.put(entry.getKey().name(), list);
            }
        }
        return result;
    }

    /**
     * Deserialize ability map from YML.
     */
    @SuppressWarnings("unchecked")
    public Map<ItemAbilityType, List<ItemAbilityData>> deserialize(Map<String, Object> raw) {
        Map<ItemAbilityType, List<ItemAbilityData>> result = new LinkedHashMap<>();

        for (ItemAbilityType type : ItemAbilityType.values()) {
            result.put(type, new ArrayList<>());
        }

        if (raw == null) return result;

        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            ItemAbilityType type;
            try {
                type = ItemAbilityType.valueOf(entry.getKey());
            } catch (IllegalArgumentException e) {
                continue;
            }

            List<Map<String, Object>> list = (List<Map<String, Object>>) entry.getValue();
            if (list == null) continue;

            for (Map<String, Object> dataMap : list) {
                String abilityId = String.valueOf(dataMap.get("ability"));
                ItemAbility ability = getAbility(abilityId);
                if (ability == null) continue;

                ItemAbilityData data = new ItemAbilityData(abilityId, type);
                Map<String, Object> config = new LinkedHashMap<>(ability.getDefaultConfig());
                for (String key : config.keySet()) {
                    if (dataMap.containsKey(key)) {
                        config.put(key, dataMap.get(key));
                    }
                }
                data.setConfig(config);
                result.get(type).add(data);
            }
        }

        return result;
    }

    public void clearPlayer(UUID playerId) {
        cooldownManager.clearPlayer(playerId);
    }
}