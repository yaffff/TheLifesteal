package theLifesteal.crafting;

import java.util.UUID;

/**
 * Immutable snapshot of a CraftingProcess for async saving.
 * No references to live objects.
 */
public class CraftingProcessData {

    private final UUID playerUuid;
    private final String recipeId;
    private final long startTime;
    private final long endTime;
    private final boolean claimed;

    public CraftingProcessData(UUID playerUuid, String recipeId, long startTime, long endTime, boolean claimed) {
        this.playerUuid = playerUuid;
        this.recipeId = recipeId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.claimed = claimed;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getRecipeId() { return recipeId; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public boolean isClaimed() { return claimed; }

    public static CraftingProcessData fromProcess(CraftingProcess process) {
        return new CraftingProcessData(
                process.getPlayerUUID(),
                process.getRecipe().getId(),
                process.getStartTime(),
                process.getEndTime(),
                process.isClaimed()
        );
    }
}