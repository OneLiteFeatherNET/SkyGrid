package net.onelitefeather.skygridng.core.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A small map-backed registry of values keyed by id, in insertion order.
 *
 * The key set is the single source of truth for "what is known", which is why a registry replaces
 * parallel {@code switch} statements: lookup and existence checks read the same data and cannot
 * drift. Each value is typically a factory (a Factory Method) keyed by a namespaced id, mirroring
 * how Minecraft and WorldEdit registries work. Framework free.
 */
public final class Registry<K, V> {

    private final Map<K, V> entries = new LinkedHashMap<>();

    /** Registers a value under a key. Rejects null and duplicate keys. */
    public Registry<K, V> register(K key, V value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("registry key and value must not be null");
        }
        if (entries.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("duplicate registry key: " + key);
        }
        return this;
    }

    /** The value registered under {@code key}, if any. */
    public Optional<V> find(K key) {
        return Optional.ofNullable(entries.get(key));
    }

    /** True if a value is registered under {@code key}. */
    public boolean contains(K key) {
        return entries.containsKey(key);
    }

    /** All keys, in registration order. */
    public Set<K> keys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(entries.keySet()));
    }
}
