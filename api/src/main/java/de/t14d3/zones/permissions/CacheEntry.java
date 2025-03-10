package de.t14d3.zones.permissions;

import de.t14d3.zones.objects.BlockLocation;
import de.t14d3.zones.objects.Result;

public class CacheEntry {
    private final Object flag;
    private final String value;
    private final Object key;

    public Result result;
    public final long timestamp;

    public CacheEntry(Object flag, String value, Object key, Result result) {
        this.flag = flag;
        this.value = value;
        this.key = key;
        this.result = result;
        this.timestamp = System.currentTimeMillis() >> 10; // Convert to seconds, inaccuracy is negligible
    }

    // Getters
    public Object getFlag() {
        return flag;
    }

    public String getValue() {
        return value;
    }

    public Object getKey() {
        return key;
    }

    public boolean isEqual(Object flag, String value, Object key) {
        // Weird stupid special handling for BlockLocation caches
        if (flag instanceof BlockLocation loc) {
            return loc.equals(this.flag) && this.value.equals(value) && this.key.equals(key);
        }
        return this.flag.equals(flag) && this.value.equals(value) && this.key.equals(key);
    }
}
