package com.velocityorm.core.cache;

import java.util.HashMap;
import java.util.Map;

public class L1Cache {
    private final Map<Key, Object> cache = new HashMap<>();

    public void put(Class<?> clazz, Object id, Object entity) {
        if (id == null || entity == null) return;
        cache.put(new Key(clazz, id), entity);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz, Object id) {
        if (id == null) return null;
        return (T) cache.get(new Key(clazz, id));
    }

    public void remove(Class<?> clazz, Object id) {
        if (id == null) return;
        cache.remove(new Key(clazz, id));
    }

    public void clear() {
        cache.clear();
    }

    private static class Key {
        private final Class<?> clazz;
        private final Object id;

        public Key(Class<?> clazz, Object id) {
            this.clazz = clazz;
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return clazz.equals(key.clazz) && id.equals(key.id);
        }

        @Override
        public int hashCode() {
            return 31 * clazz.hashCode() + id.hashCode();
        }
    }
}
