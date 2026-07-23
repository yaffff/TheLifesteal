package theLifesteal.abilities;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AbilityCooldownManager {

    // player UUID -> (cooldownKey -> expiry timestamp)
    private final Map<UUID, Map<String, Long>> cooldowns;
    private final Map<UUID, Map<String, Integer>> customCounters;

    public AbilityCooldownManager() {
        this.cooldowns = new HashMap<>();
        this.customCounters = new HashMap<>();
    }

    /**
     * Check if a player is on cooldown for an ability.
     * @param playerId The player's UUID
     * @param abilityId The ability ID (e.g., "healing")
     * @param itemId The custom item ID (e.g., "life_shard")
     * @param scope "ABILITY" = shared across all items, "ITEM" = per-item
     */
    public boolean isOnCooldown(UUID playerId, String abilityId, String itemId, String scope) {
        String key = buildKey(abilityId, itemId, scope);
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return false;

        Long expiry = playerCooldowns.get(key);
        if (expiry == null) return false;

        return System.currentTimeMillis() < expiry;
    }

    /**
     * Get remaining cooldown time in milliseconds.
     */
    public long getRemainingCooldown(UUID playerId, String abilityId, String itemId, String scope) {
        String key = buildKey(abilityId, itemId, scope);
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return 0;

        Long expiry = playerCooldowns.get(key);
        if (expiry == null) return 0;

        long remaining = expiry - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * Set a cooldown for an ability.
     * @param cooldownSeconds Duration in seconds
     */
    public void setCooldown(UUID playerId, String abilityId, String itemId, String scope, long cooldownSeconds) {
        String key = buildKey(abilityId, itemId, scope);
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(playerId, k -> new HashMap<>());
        playerCooldowns.put(key, System.currentTimeMillis() + (cooldownSeconds * 1000));
    }

    /**
     * Build the cooldown key based on scope.
     * ABILITY scope: key = abilityId (shared across all items with this ability)
     * ITEM scope: key = abilityId:itemId (separate per item, default)
     */
    private String buildKey(String abilityId, String itemId, String scope) {
        if ("ABILITY".equalsIgnoreCase(scope)) {
            return abilityId;
        }
        return abilityId + ":" + itemId;
    }

    /**
     * Clear all cooldowns for a player.
     */
    public void clearPlayer(UUID playerId) {
        cooldowns.remove(playerId);
        customCounters.remove(playerId);
    }

    /**
     * Format remaining cooldown as readable string.
     */
    public String formatCooldown(long remainingMillis) {
        if (remainingMillis <= 0) return "Ready";
        long seconds = remainingMillis / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return minutes + "m " + seconds + "s";
    }

    // Custom counters for abilities that need to track state
    public int getCustomCounter(UUID playerId, String key) {
        Map<String, Integer> playerCounters = customCounters.get(playerId);
        if (playerCounters == null) return 0;
        return playerCounters.getOrDefault(key, 0);
    }

    public void setCustomCounter(UUID playerId, String key, int value) {
        customCounters.computeIfAbsent(playerId, k -> new HashMap<>()).put(key, value);
    }
}