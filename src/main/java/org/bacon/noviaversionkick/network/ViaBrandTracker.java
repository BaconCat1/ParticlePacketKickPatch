package org.bacon.noviaversionkick.network;

import net.minecraft.network.ClientConnection;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks information about connected clients so that we can tailor the packets
 * that are sent to them.
 */
public final class ViaBrandTracker {
    private static final Map<ClientConnection, ClientInfo> CLIENTS = Collections.synchronizedMap(new WeakHashMap<>());

    private ViaBrandTracker() {
    }

    public static void setBrand(ClientConnection connection, String brand) {
        if (connection == null) {
            return;
        }
        synchronized (CLIENTS) {
            ClientInfo info = CLIENTS.get(connection);
            if (brand == null) {
                if (info != null) {
                    info.setBrand(null);
                    if (info.isEmpty()) {
                        CLIENTS.remove(connection);
                    }
                }
                return;
            }
            if (info == null) {
                info = new ClientInfo();
                CLIENTS.put(connection, info);
            }
            info.setBrand(brand);
        }
    }

    public static void trackChannels(ClientConnection connection, Collection<Identifier> channels) {
        if (connection == null || channels == null || channels.isEmpty()) {
            return;
        }
        for (Identifier channel : channels) {
            if (channel == null) {
                continue;
            }
            if (isViaChannel(channel)) {
                markViaModDetected(connection);
                break;
            }
        }
    }

    public static boolean shouldUseLegacyParticles(ClientConnection connection) {
        if (connection == null) {
            return false;
        }
        ClientInfo info;
        synchronized (CLIENTS) {
            info = CLIENTS.get(connection);
        }
        if (info == null) {
            return false;
        }
        return info.shouldUseLegacyParticles();
    }

    private static void markViaModDetected(ClientConnection connection) {
        synchronized (CLIENTS) {
            ClientInfo info = CLIENTS.get(connection);
            if (info == null) {
                info = new ClientInfo();
                CLIENTS.put(connection, info);
            }
            info.setViaModDetected();
        }
    }

    private static boolean isViaChannel(Identifier identifier) {
        String namespace = identifier.getNamespace();
        if (namespace != null && namespace.toLowerCase(Locale.ROOT).startsWith("via")) {
            return true;
        }
        String path = identifier.getPath();
        return path != null && path.toLowerCase(Locale.ROOT).startsWith("via");
    }

    private static final class ClientInfo {
        private volatile String brand;
        private volatile boolean viaModDetected;

        void setBrand(String brand) {
            this.brand = brand;
        }

        void setViaModDetected() {
            this.viaModDetected = true;
        }

        boolean shouldUseLegacyParticles() {
            if (this.viaModDetected) {
                return true;
            }
            String brand = this.brand;
            if (brand == null) {
                return false;
            }
            String normalized = brand.trim().toLowerCase(Locale.ROOT);
            return !normalized.isEmpty() && normalized.contains("via");
        }

        boolean isEmpty() {
            return this.brand == null && !this.viaModDetected;
        }
    }
}
