package org.bacon.noviaversionkick.network;

import net.minecraft.network.ClientConnection;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks information about connected clients so that we can tailor the packets
 * that are sent to them.
 */
public final class ViaBrandTracker {
    private static final ThreadLocal<ClientConnection> CURRENT_CONNECTION = new ThreadLocal<>();
    private static final Map<ClientConnection, String> BRANDS = Collections.synchronizedMap(new WeakHashMap<>());

    private ViaBrandTracker() {
    }

    public static void pushConnection(ClientConnection connection) {
        CURRENT_CONNECTION.set(connection);
    }

    public static void popConnection(ClientConnection connection) {
        ClientConnection current = CURRENT_CONNECTION.get();
        if (current == connection) {
            CURRENT_CONNECTION.remove();
        }
    }

    public static ClientConnection getCurrentConnection() {
        return CURRENT_CONNECTION.get();
    }

    public static void setBrand(ClientConnection connection, String brand) {
        if (connection == null) {
            return;
        }
        if (brand == null) {
            BRANDS.remove(connection);
            return;
        }
        BRANDS.put(connection, brand);
    }

    public static boolean shouldUseLegacyParticles(ClientConnection connection) {
        if (connection == null) {
            return false;
        }
        String brand = BRANDS.get(connection);
        if (brand == null) {
            return false;
        }
        String normalized = brand.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        return normalized.contains("via");
    }
}
