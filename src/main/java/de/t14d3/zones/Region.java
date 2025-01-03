package de.t14d3.zones;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.BlockVector;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


/**
 * Represents a region in the plugin.
 * Constructive or destructive methods are implemented in the {@link de.t14d3.zones.RegionManager}.
 */
public class Region {
    private String name;
    private Location min;
    private Location max;
    private Map<UUID, Map<String, String>> members;
    private String key;
    private String parent;

    // Constructor
    /**
     * Constructs a new region with the given name, minimum and maximum locations, members and parent.
     * @param name The name of the region (not unique).
     * @param min The minimum location of the region.
     * @param max The maximum location of the region.
     * @param members The members of the region.
     * @param key The key of the region.
     * @param parent The parent (if any) of the region.
     *
     * @see #Region(String, Location, Location, Map, String)
     */
    Region(@NotNull String name, @NotNull Location min, @NotNull Location max, Map<UUID, Map<String, String>> members, @NotNull String key, @Nullable String parent) {
        this.name = name;
        this.min = min;
        this.max = max;
        this.members = (members != null) ? members : new HashMap<>();
        this.key = key;
        this.parent = parent;
    }

    // Constructor overload for regions without parent
    /**
     * Constructs a new region with the given name, minimum and maximum locations, and members.
     * @param name The name of the region (not unique).
     * @param min The minimum location of the region.
     * @param max The maximum location of the region.
     * @param members The members of the region.
     * @param key The key of the region.
     *
     * @see #Region(String, Location, Location, Map, String)
     */
    Region(String name, Location min, Location max, Map<UUID, Map<String, String>> members, String key) {
        this(name, min, max, members, key, null);
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    void setName(String name, RegionManager regionManager, String key) {
        this.name = name;
        regionManager.saveRegion(key, this); // Ensure changes are saved
    }

    Location getMin() {
        return min;
    }

    public String getMinString() {
        return min.getBlockX() + "," + min.getBlockY() + "," + min.getBlockZ();
    }

    void setMin(Location min, RegionManager regionManager) {
        this.min = min;
        regionManager.saveRegion(key, this); // Ensure changes are saved
    }

    void setMin(Location min) {
        this.min = min;
    }

    void setMin(BlockVector min) {
        this.min.set(min.getBlockX(), min.getBlockY(), min.getBlockZ());
    }

    Location getMax() {
        return max;
    }

    public String getMaxString() {
        return max.getBlockX() + "," + max.getBlockY() + "," + max.getBlockZ();
    }

    void setMax(Location max, RegionManager regionManager) {
        this.max = max;
        regionManager.saveRegion(key, this); // Ensure changes are saved
    }

    void setMax(Location max) {
        this.max = max;
    }

    void setMax(BlockVector max) {
        this.max.set(max.getBlockX(), max.getBlockY(), max.getBlockZ());
    }

    public Map<UUID, Map<String, String>> getMembers() {
        return members;
    }

    void setMembers(Map<UUID, Map<String, String>> members, RegionManager regionManager, String key) {
        this.members = members;
        regionManager.saveRegion(key, this); // Ensure changes are saved
    }

    void addMember(UUID uuid, Map<String, String> permissions, RegionManager regionManager, String key) {
        this.members.put(uuid, permissions);
        regionManager.saveRegion(key, this); // Ensure changes are saved
    }

    void removeMember(UUID uuid, RegionManager regionManager, String key) {
        this.members.remove(uuid);
        regionManager.saveRegion(key, this); // Ensure changes are saved
    }

    public boolean isMember(UUID uuid) {
        return this.members.containsKey(uuid);
    }

    public boolean isAdmin(UUID uuid) {
        if (this.members.containsKey(uuid) && this.members.get(uuid).containsKey("role")) {
            return this.members.get(uuid).get("role").equals("admin") || this.members.get(uuid).get("role").equals("owner");
        }
        return false; // Default to false
    }

    Map<String, String> getMemberPermissions(UUID uuid) {
        return this.members.get(uuid);
    }

    public String getParent() {
        return this.parent;
    }

    void setParent(String parent, RegionManager regionManager, String key) {
        this.parent = parent;
        regionManager.saveRegion(key, this); // Ensure changes are saved
    }

    Region getParentRegion(RegionManager regionManager) {
        return regionManager.regions().get(parent);
    }

    public List<Region> getChildren(RegionManager regionManager) {
        List<Region> children = new ArrayList<>();
        for (Region region : regionManager.regions().values()) {
            if (region.getParent().equals(key)) {
                children.add(region);
            }
        }
        return children;
    }

    public String getKey() {
        return key;
    }

    /**
     * Careful, can easily break things.
     */
    void setKey(String key, RegionManager regionManager) {
        this.key = key;
        regionManager.saveRegion(key, this); // Ensure changes are saved
    }

    public boolean contains(Location location) {
        BoundingBox box = BoundingBox.of(min, max);
        return box.contains(location.toVector());
    }

    void addMemberPermission(UUID uuid, String permission, String value, RegionManager regionManager) {
        this.members.computeIfAbsent(uuid, k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)).put(permission, value);
        regionManager.saveRegion(key, this); // Ensure changes are saved
    }

    JsonObject getAsJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", getName());
        JsonObject membersJson = new JsonObject();
        for (Map.Entry<UUID, Map<String, String>> member : getMembers().entrySet()) {
            JsonObject memberJson = new JsonObject();
            memberJson.addProperty("player", Bukkit.getPlayer(member.getKey()) != null ? Bukkit.getPlayer(member.getKey()).getName() : member.getKey().toString());
            JsonObject permissions = new JsonObject();
            for (Map.Entry<String, String> perm : member.getValue().entrySet()) {
                permissions.addProperty(perm.getKey(), perm.getValue());
            }
            memberJson.add("permissions", permissions);
            membersJson.add(member.getKey().toString(), memberJson);
        }
        json.add("members", membersJson);
        return json;
    }
}
