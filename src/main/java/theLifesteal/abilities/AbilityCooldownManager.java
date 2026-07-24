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
     * Shared across ALL items with the same abilityId for that player.
     *
     * @param playerId The player's UUID
     * @param abilityId The ability ID (e.g., "healing", "meteor_strike")
     * @param itemId The custom item ID (e.g., "healing_staff_1")
     * @param scope Cooldown scope parameter (retained for API compatibility)
     */
    public boolean isOnCooldown(UUID playerId, String abilityId, String itemId, String scope) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return false;

        long now = System.currentTimeMillis();

        // 1. Check shared ability-wide cooldown key (shared across all items with same abilityId)
        Long abilityExpiry = playerCooldowns.get(abilityId);
        if (abilityExpiry != null && now < abilityExpiry) {
            return true;
        }

        // 2. Check item-specific cooldown key as fallback
        String itemKey = abilityId + ":" + itemId;
        Long itemExpiry = playerCooldowns.get(itemKey);
        return itemExpiry != null && now < itemExpiry;
    }

    /**
     * Get remaining cooldown time in milliseconds across shared ability key and item key.
     */
    public long getRemainingCooldown(UUID playerId, String abilityId, String itemId, String scope) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return 0;

        long now = System.currentTimeMillis();
        long remaining = 0;

        Long abilityExpiry = playerCooldowns.get(abilityId);
        if (abilityExpiry != null && now < abilityExpiry) {
            remaining = Math.max(remaining, abilityExpiry - now);
        }

        String itemKey = abilityId + ":" + itemId;
        Long itemExpiry = playerCooldowns.get(itemKey);
        if (itemExpiry != null && now < itemExpiry) {
            remaining = Math.max(remaining, itemExpiry - now);
        }

        return remaining;
    }

    /**
     * Set a cooldown for an ability.
     * Sets the shared ability key so ALL items with this ability ID share the cooldown.
     *
     * @param cooldownSeconds Duration in seconds of the item used first
     */
    public void setCooldown(UUID playerId, String abilityId, String itemId, String scope, long cooldownSeconds) {
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(playerId, k -> new HashMap<>());
        long expiry = System.currentTimeMillis() + (cooldownSeconds * 1000);

        // Always set the global abilityId key for the player
        playerCooldowns.put(abilityId, expiry);

        // Also set item key for reference
        if (itemId != null && !itemId.isEmpty()) {
            playerCooldowns.put(abilityId + ":" + itemId, expiry);
        }
    }

    /**
     * Clear expired cooldowns for a player while retaining active cooldowns.
     */
    public void cleanupExpired(UUID playerId) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns != null) {
            long now = System.currentTimeMillis();
            playerCooldowns.entrySet().removeIf(entry -> now >= entry.getValue());
            if (playerCooldowns.isEmpty()) {
                cooldowns.remove(playerId);
            }
        }
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