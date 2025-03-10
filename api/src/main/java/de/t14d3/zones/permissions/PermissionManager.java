package de.t14d3.zones.permissions;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import de.t14d3.zones.Region;
import de.t14d3.zones.Zones;
import de.t14d3.zones.objects.*;
import de.t14d3.zones.utils.DebugLoggerManager;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PermissionManager {
    private final CacheUtils cacheUtils;
    private final DebugLoggerManager debugLogger;
    private final Zones zones;
    public static final String UNIVERSAL = "+universal";
    private final List<Permission> permissionMap;

    public PermissionManager(Zones zones) {
        this.zones = zones;
        this.debugLogger = zones.getDebugLogger();
        this.cacheUtils = CacheUtils.getInstance();
        this.permissionMap = readPermissions();
    }

    public boolean checkAction(BlockLocation location, World world, UUID playerUUID, Flag action, String type, Object... extra) {
        return checkAction(location, world, playerUUID.toString(), action, type, extra);
    }

    /**
     * Checks if a player can interact with a region.
     *
     * @param location The location of the interaction.
     * @param who      The UUID of the player.
     * @param action   The action the player wants to perform.
     * @param type     The type of the block or entity the interaction happened with.
     * @param extra    Additional, optional information, for example a spawn reason.
     * @return True if the player can interact with the region, false otherwise.
     */
    public boolean checkAction(BlockLocation location, World world, String who, Flag action, String type, Object... extra) {
        debugLogger.log(DebugLoggerManager.CHECK, action.name(), who, location, type);
        boolean nonplayer = who.equalsIgnoreCase(UNIVERSAL) || extra.length != 0 && (boolean) extra[0];
        boolean base = extra.length == 0;
        if (nonplayer) {
            debugLogger.log(DebugLoggerManager.UNI_CHECK, action.name(), location, type);
            return checkAction(location, world, action, type, extra);
        }
        // Check interaction cache
        if (base && cacheUtils.interactionCache.containsKey(who)) {
            ConcurrentLinkedQueue<CacheEntry> entries = cacheUtils.interactionCache.get(who);
            for (CacheEntry entry : entries) {
                if (entry.isEqual(location, action.name(), type)) {
                    debugLogger.log(DebugLoggerManager.CACHE_HIT_ACTION, action.name(), who, location, type);
                    return entry.result.equals(Result.TRUE);
                }
            }
        }

        List<Region> regions = zones.getRegionManager().getRegionsAt(location, world);
        if (!regions.isEmpty()) {
            Result result = Result.UNDEFINED;
            int priority = Integer.MIN_VALUE;

            for (Region region : regions) {
                debugLogger.log("Checking region " + region.getKey()
                        .toString() + " for " + action.name() + " with type " + type, DebugLoggerManager.CHECK);

                // Only check regions with a higher priority than the current value
                if (region.getPriority() > priority) {
                    Result hasPermission = action.getCustomHandler().evaluate(region, who, action.name(), type);
                    if (!hasPermission.equals(Result.UNDEFINED)) {
                        result = hasPermission;
                        priority = region.getPriority();
                        continue;
                    }
                }
                // If same priority, both have to be true, otherwise will assume false
                else if (region.getPriority() == priority) {
                    Result hasPermission = action.getCustomHandler().evaluate(region, who, action.name(), type);
                    if (hasPermission.equals(Result.FALSE) || result.equals(Result.FALSE)) {
                        result = Result.FALSE;
                        priority = region.getPriority();
                        continue;
                    }
                }
            }

            if (result.equals(Result.UNDEFINED)) {
                result = Result.valueOf(action.getDefaultValue(who));
            }

            // Update cache if needed
            if (base) {
                cacheUtils.interactionCache.computeIfAbsent(who, k -> new ConcurrentLinkedQueue<>())
                        .add(new CacheEntry(location, action.name(), type, result));
            }
            debugLogger.log(DebugLoggerManager.CACHE_MISS_ACTION, action.name(), who, location, type, result);
            return result.equals(Result.TRUE);

        } else {
            // No region found, check player permissions
            boolean bypass = false;
            Player player = PlayerRepository.get(UUID.fromString(who));
            if (player != null && player.hasPermission("zones.bypass.unclaimed")) {
                bypass = true;
            }
            debugLogger.log(DebugLoggerManager.PERM, action.name(), who, location, type, bypass);
            if (base) {
                cacheUtils.interactionCache.computeIfAbsent(who, k -> new ConcurrentLinkedQueue<>())
                        .add(new CacheEntry(location, action.name(), type, bypass ? Result.TRUE : Result.UNDEFINED));
            }
            return bypass;
        }
    }

    /**
     * Checks if a universal/non-player action is allowed at a location.
     * This method bypasses player-specific checks for efficiency.
     *
     * @param location The location of the interaction
     * @param action   The action being performed
     * @param type     The type of block/entity involved
     * @param extra    Additional context (used for cache control)
     * @return true if the action is allowed, false otherwise
     */
    public boolean checkAction(BlockLocation location, World world, Flag action, String type, Object... extra) {
        boolean base = extra == null || extra.length == 0;

        if (base && cacheUtils.interactionCache.containsKey(UNIVERSAL)) {
            ConcurrentLinkedQueue<CacheEntry> entries = cacheUtils.interactionCache.get(UNIVERSAL);
            for (CacheEntry entry : entries) {
                if (entry.isEqual(location, action.name(), type)) {
                    debugLogger.log(DebugLoggerManager.CACHE_HIT_ACTION, DebugLoggerManager.UNI_CHECK, action.name(),
                            location, type, entry.result);
                    return entry.result.equals(Result.TRUE);
                }
            }
        }

        List<Region> regions = zones.getRegionManager().getRegionsAt(location, world);
        Result result = Result.UNDEFINED;
        if (!regions.isEmpty()) {
            int priority = Integer.MIN_VALUE;

            for (Region region : regions) {
                if (region.getPriority() > priority) {
                    Result regionResult = action.getCustomHandler().evaluate(region, action.name(), type);
                    if (regionResult != Result.UNDEFINED) {
                        result = regionResult;
                        priority = region.getPriority();
                    }
                } else if (region.getPriority() == priority) {
                    Result regionResult = action.getCustomHandler().evaluate(region, action.name(), type);
                    if (regionResult == Result.FALSE || result == Result.FALSE) {
                        result = Result.FALSE;
                        priority = region.getPriority();
                    }
                }
            }
        }
        // No regions at location - use default value

        if (result == Result.UNDEFINED) {
            result = Result.valueOf(action.getDefaultValue(UNIVERSAL));
        }

        cacheUtils.interactionCache.computeIfAbsent(UNIVERSAL, k -> new ConcurrentLinkedQueue<>())
                .add(new CacheEntry(location, action.name(), type, result));

        debugLogger.log(DebugLoggerManager.CACHE_MISS_ACTION, DebugLoggerManager.UNI_CHECK, action.name(), location,
                type, result, extra);

        return result == Result.TRUE;
    }

    public static Result isAllowed(String perm, String type, Result result) {
        perm = perm.toLowerCase();
        type = type.toLowerCase();
        if (perm.equals("true") || perm.equals("*")) {
            result = Result.TRUE;
        } else if (perm.equals("false") || perm.equals("!*")) {
            result = Result.FALSE;
        } else if (perm.equals(type)) {
            result = Result.TRUE;
        } else if (perm.equals("!" + type)) {
            result = Result.FALSE;
        }
        return result;
    }

    public List<Permission> readPermissions() {
        List<Permission> permissions = new ArrayList<>();
        Gson gson = new Gson();
        JsonObject obj = gson.fromJson(
                new JsonReader(new InputStreamReader(zones.getClass().getResourceAsStream("/permissions.json"))),
                JsonObject.class);
        for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("permissions").entrySet()) {
            String key = entry.getKey();
            JsonObject permission = entry.getValue().getAsJsonObject();
            String description = permission.get("description").getAsString();
            int level = permission.get("level").getAsInt();
            permissions.add(new Permission(key, description, level));
        }
        return permissions;
    }

    /**
     * Gets a list of all permissions recognized this platform.
     *
     * @return List of {@link Permission} objects.
     */
    public List<Permission> getPermissions() {
        return permissionMap;
    }

    /**
     * Simple permission object.
     * Contains a name, description, and level (Vanilla operator level equivalent).
     */
    public static class Permission {
        private final String value;
        private final String description;
        private final int level;

        /**
         * Creates a new permission object.
         * @param value The permission name.
         * @param description The permission description.
         * @param level The permission level.
         */
        public Permission(String value, String description, int level) {
            this.value = value;
            this.description = description;
            this.level = level;
        }

        public String getName() {
            return this.value;
        }

        public String getDescription() {
            return this.description;
        }

        public int getLevel() {
            return this.level;
        }
    }

}
