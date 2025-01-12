package de.t14d3.zones;

import de.t14d3.zones.utils.Actions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PermissionManager {

    private RegionManager regionManager;

    // Cache of player UUID -> (Region Name -> Action/Type Permission)
    private final Map<UUID, Map<String, Boolean>> permissionCache = new HashMap<>();

    public PermissionManager() {}

    // Setter for RegionManager to avoid circular dependency
    public void setRegionManager(RegionManager regionManager) {
        this.regionManager = regionManager;
    }

    /**
     * Checks if a player can interact with a region.
     * @param location The location of the interaction.
     * @param playerUUID The UUID of the player.
     * @param action The action the player wants to perform.
     * @param type The type of the block or entity the interaction happened with.
     * @return True if the player can interact with the region, false otherwise.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean canInteract(Location location, UUID playerUUID, Actions action, String type) {
        Map<String, Boolean> playerCache = permissionCache.getOrDefault(playerUUID, new HashMap<>());
        String cacheKey = getCacheKey(location, action, type);

        if (playerCache.containsKey(cacheKey)) {
            return playerCache.get(cacheKey); // Return cached result
        }

        for (Map.Entry<String, Region> entry : regionManager.regions().entrySet()) {
            Region region = entry.getValue();
            BoundingBox box = BoundingBox.of(region.getMin(), region.getMax());

            if (box.contains(location.toVector())) {
                boolean hasPermission = hasPermission(playerUUID, action.name(), type, region);

                // Cache the result for this region
                playerCache.put(cacheKey, hasPermission);
                permissionCache.put(playerUUID, playerCache);

                return hasPermission;
            }
        }

        Player player = Bukkit.getPlayer(playerUUID);
        return player != null && player.hasPermission("zones.bypass.unclaimed");
    }

    private String getCacheKey(Location location, Actions action, String type) {
        return location.toString() + "|" + action.name() + "|" + type;
    }

    public void invalidateCache(UUID playerUUID) {
        permissionCache.remove(playerUUID);
    }
    public void invalidateCacheForLocation(Location location) {
        List<Region> regions = regionManager.getRegionsAt(location);

        // Invalidate caches for all players that have permissions in the regions covering the location
        for (Region region : regions) {
            for (UUID playerUUID : permissionCache.keySet()) {
                // Remove the player's cached permissions related to this region
                permissionCache.get(playerUUID).keySet().removeIf(key -> key.startsWith(getCacheKey(location, Actions.valueOf(""), "")));
            }
        }
    }

    public void invalidateAllCaches() {
        permissionCache.clear();
    }


    /**
     * hasPermission(UUID, String, String, Region) overload
     *
     * @param uuid       The UUID of the player whose permission is being checked.
     * @param permission The permission being checked (e.g., "break", "place").
     * @param type       The type of object the permission applies to (e.g., "GRASS_BLOCK").
     * @param region     The region in which the permission is being checked.
     * @return true if the player has the specified permission for the type, false otherwise.
     * @see #hasPermission(String, String, String, Region)
     */
    public boolean hasPermission(UUID uuid, String permission, String type, Region region) {
        return hasPermission(uuid.toString(), permission, type, region);
    }
    /**
     * Checks if a player has a specific permission for a given type in the provided region.
     *
     * @param who    Who to check the permission for
     * @param permission The permission being checked (e.g., "break", "place").
     * @param type    The type of object the permission applies to (e.g., "GRASS_BLOCK").
     * @param region  The region in which the permission is being checked.
     * @return True if the player has the specified permission for the type, false otherwise.
     */
    public boolean hasPermission(String who, String permission, String type, Region region) {
        permission = permission.toLowerCase();
        Player player = null;
        try {
            player = Bukkit.getPlayer(UUID.fromString(who));
        } catch (IllegalArgumentException ignored) {
        }

        // Check if player has a global bypass permission
        if (player != null && player.hasPermission("zones.bypass.claimed")) {
            return true;
        }

        // Retrieve the permissions for the player in the specified region
        Map<String, Map<String, String>> members = region.getMembers();

        // Get the permissions for the player
        Map<String, String> permissions = members.get(who);
        String value = permissions.get(permission);

        // If no value found, check parent region and group permissions
        if (value == null) {
            if (region.getParent() != null) {
                return hasPermission(who, permission, type, region.getParentRegion(this.regionManager));
            }
            if (permissions.containsKey("group")) {
                if (who.startsWith(":group-") && !Zones.getInstance().getConfig().getBoolean("allow-group-recursion", false)) {
                    Zones.getInstance().getLogger().severe("Recursive group permissions detected!! Groups are not allowed to contain other groups!");
                    Zones.getInstance().getLogger().severe("Group '" + who.substring(7) + "' contains 'group' permission entry in region '" + region.getKey() + "'");
                    Zones.getInstance().getLogger().severe("If you are 100% sure this is fine, add 'allow-group-recursion: true' to your config.yml");
                    return false;
                }
                for (String group : permissions.get("group").split(",")) {
                    return hasPermission(":group-" + group, permission, type, region);
                }
            }
            // Nothing found, deny access
            else {
                return false;
            }
        }

        // Analyze permission values
        boolean explicitAllow = false;
        boolean explicitDeny = false;

        if (value != null) {
            for (String permittedValue : value.split(",")) {
                permittedValue = permittedValue.trim(); // Trim whitespace

                // Check for wildcard allow
                if ("*".equals(permittedValue) || "true".equalsIgnoreCase(permittedValue)) {
                    explicitAllow = true;
                }
                // Check for wildcard deny
                else if ("! *".equals(permittedValue) || "false".equalsIgnoreCase(permittedValue)) {
                    explicitDeny = true;
                }
                // Check for specific type allow
                else if (permittedValue.equalsIgnoreCase(type)) {
                    explicitAllow = true;
                }
                // Check for specific type deny
                else if (permittedValue.equalsIgnoreCase("!" + type)) {
                    explicitDeny = true;
                }
            }
        }

        // Determine final access based on explicit allow/deny flags
        if (explicitDeny) {
            return false;
        } else if (explicitAllow) {
            return true;
        }
        // Deny by default
        return false;
    }

    public boolean isAdmin(String who, Region region) {
        return hasPermission(who, "role", "owner", region) || hasPermission(who, "role", "admin", region);
    }
}
