package org.javarosa.core.services.storage;

import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;

/**
 * Java-compatible helper for StorageManager methods that take KClass.
 */
public class StorageManagerCompat {
    /**
     * Register storage using a Java Class.
     */
    public static void registerStorage(StorageManager manager, String key, Class<?> type) {
        manager.registerStorage(key, JvmClassMappingKt.getKotlinClass(type));
    }
}
