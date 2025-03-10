package de.t14d3.zones;

import de.t14d3.zones.objects.*;
import de.t14d3.zones.permissions.PermissionManager;
import de.t14d3.zones.permissions.flags.Flags;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents a region in the plugin.
 * Constructive or destructive methods are implemented in the
 * {@link de.t14d3.zones.RegionManager}.
 */
public class Region {
    private String name;
    private BlockLocation min;
    private BlockLocation max;
    private World world;
    private Map<String, List<RegionFlagEntry>> members;
    private RegionKey key;
    private RegionKey parent;
    private int priority;

    /**
     * Constructs a new region with the given name, minimum and maximum locations,
     * members and parent.
     *
     * @param name     The name of the region (not unique).
     * @param min      The minimum BlockLocation of the region.
     * @param max      The maximum BlockLocation of the region.
     * @param world    The world of the region.
     * @param members  The members of the region.
     * @param key      The key of the region.
     * @param parent   The parent (if any) of the region.
     * @param priority The priority of the region.
     * @see #Region(String, BlockLocation, BlockLocation, World, Map, RegionKey, int)
     */
    public Region(@NotNull String name, @NotNull BlockLocation min, @NotNull BlockLocation max, @NotNull World world,
                  Map<String, List<RegionFlagEntry>> members, @NotNull RegionKey key, @Nullable RegionKey parent,
                  int priority) {
        // noinspection ConstantConditions
        this.name = name == null ? key.toString() : name;
        this.min = min;
        this.max = max;
        this.world = world;
        this.members = (members != null) ? members : new HashMap<>();
        this.key = key;
        this.parent = parent;
        this.priority = priority;
    }

    // Constructor overload for regions without parent

    /**
     * Constructs a new region with the given name, minimum and maximum locations,
     * and members.
     *
     * @param name     The name of the region (not unique).
     * @param min      The minimum location of the region.
     * @param max      The maximum location of the region.
     * @param members  The members of the region.
     * @param key      The key of the region.
     * @param priority The priority of the region.
     * @see #Region(String, BlockLocation, BlockLocation, World, Map, RegionKey, int)
     */
    public Region(String name, BlockLocation min, BlockLocation max, World world, Map<String, List<RegionFlagEntry>> members,
           RegionKey key, int priority) {
        this(name, min, max, world, members, key, null, priority);
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name, RegionManager regionManager) {
        this.name = name;
        regionManager.saveRegion(key, this); // Ensure changes are saved
    }

    public BlockLocation getMin() {
        return min;
    }

    void setMin(BlockLocation min) {
        this.min = min;
    }

    public String getMinString() {
        return min.getX() + "," + min.getY() + "," + min.getZ();
    }

    public BlockLocation getMax() {
        return max;
    }

    void setMax(BlockLocation max) {
        this.max = max;
    }

    public String getMaxString() {
        return max.getX() + "," + max.getY() + "," + max.getZ();
    }

    /**
     * Get the members of this region and their permissions. <br>
     * Use {@link PermissionManager} to check player permissions.
     *
     * @return {@code Map<UUID player, Map<String permission, String value> permissions>}
     */
    public Map<String, List<RegionFlagEntry>> getMembers() {
        return members;
    }

    /**
     * Get the names of all groups in this region
     *
     * @return List of group names
     * @since 0.1.5
     */
    public List<String> getGroupNames() {
        List<String> groupNames = new ArrayList<>();
        members.keySet().forEach(key -> {
            if (key.startsWith("+group-")) {
                groupNames.add(key);
            }
        });
        return groupNames;
    }

    /**
     * Get the members of a group in this region
     *
     * @param group Group name
     * @return List of members
     * @since 0.1.6
     */
    public List<String> getGroupMembers(String group) {
        List<String> groupMembers = new ArrayList<>();
        members.forEach((subject, flags) -> {
            flags.forEach(flag -> {
                if (flag.getFlag() == Flags.GROUP && flag.getValue(group.substring(7)).equals(Result.TRUE)) {
                    groupMembers.add(subject);
                }
            });
        });
        return groupMembers;
    }

    public boolean isMember(UUID uuid) {
        return this.members.containsKey(uuid.toString());
    }

    public boolean isAdmin(UUID uuid) {
        if (members.containsKey(uuid.toString())) {
            for (RegionFlagEntry entry : members.get(uuid.toString())) {
                if (entry.getFlag().name().equalsIgnoreCase("role")) {
                    return entry.getValue("admin").equals(Result.TRUE) || entry.getValue("owner").equals(Result.TRUE);
                }
            }
        }
        return false; // Default to false
    }

    public List<RegionFlagEntry> getMemberPermissions(String who) {
        return this.members.get(who);
    }

    public RegionKey getParent() {
        return this.parent;
    }

    void setParent(RegionKey parent, RegionManager regionManager) {
        this.parent = parent;
        regionManager.saveRegion(key, this); // Ensure changes are saved
    }

    public Region getParentRegion(RegionManager regionManager) {
        return regionManager.regions().get(parent.getValue());
    }

    @ApiStatus.Experimental
    public Region getParentRegion() {
        return RegionManager.getRegion(parent);
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

    public RegionKey getKey() {
        return key;
    }

    /**
     * Careful, can easily break things.
     */
    void setKey(RegionKey key, RegionManager regionManager) {
        this.key = key;
        regionManager.saveRegion(key, this); // Ensure changes are saved
    }


    public boolean contains(BlockLocation vec) {
        return getBounds().contains(vec);
    }

    public boolean intersects(@NotNull BlockLocation min, @NotNull BlockLocation max, World world) {
        return getBounds().intersects(min, max, world);
    }

    public Box getBounds() {
        return new Box(min, max, world);
    }

    public void addMemberPermission(UUID uuid, String permission, String value, RegionManager regionManager) {
        addMemberPermission(uuid.toString(), permission, value, regionManager);
    }

    public void addMemberPermission(String who, String permission, String value, RegionManager regionManager) {
        List<RegionFlagEntry> entries = this.members.computeIfAbsent(who, k -> new ArrayList<>());
        for (RegionFlagEntry entry : entries) {
            if (entry.getFlagValue().equalsIgnoreCase(permission)) {
                boolean inverted = value.startsWith("!");
                entry.setValue(value.replaceFirst("!", ""), inverted);
                regionManager.saveRegion(key, this);
                return;
            }
        }
        Zones.getInstance().getDebugLogger().log("Entry does not exist, adding", permission);
        entries.add(new RegionFlagEntry(permission));
        regionManager.saveRegion(key, this);
    }

    public void addMemberPermissions(String who, List<RegionFlagEntry> entries, RegionManager regionManager) {
        this.members.put(who, entries);
        regionManager.saveRegion(key, this);
    }

    public void removeMemberPermission(String who, String permission, String value, RegionManager regionManager) {
        List<RegionFlagEntry> entries = this.members.get(who);
        if (entries != null) {
            for (RegionFlagEntry entry : entries) {
                if (entry.getFlagValue().equalsIgnoreCase(permission)) {
                    entry.removeValue(value);
                    regionManager.saveRegion(key, this);
                    return;
                }
            }
        }
    }

    public @Nullable UUID getOwner() {
        for (Map.Entry<String, List<RegionFlagEntry>> e : members.entrySet()) {
            for (RegionFlagEntry entry : e.getValue()) {
                if (entry.getFlagValue().equalsIgnoreCase("role")) {
                    if (entry.getValue("owner").equals(Result.TRUE)) {
                        return UUID.fromString(e.getKey());
                    }
                }
            }
        }
        return null;
    }

    public boolean isOwner(UUID uuid) {
        return getOwner() != null && getOwner().equals(uuid);
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public World getWorld() {
        return world;
    }

    /**
     * Set the world of the region.
     * Should very likely never be used.
     */
    @ApiStatus.Internal
    public void setWorld(World world) {
        this.world = world;
    }
}
