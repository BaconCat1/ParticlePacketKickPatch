package org.bacon.noviaversionkick.network;

import net.minecraft.network.ClientConnection;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.SocketAddress;
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
    private static final Logger LOGGER = LogManager.getLogger("Noviaversionkick");
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
                    LOGGER.info("Clearing client brand for {}", describeConnection(connection));
                    info.setBrand(null);
                    info.resetLegacyDecisionLog();
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
            info.resetLegacyDecisionLog();
            LOGGER.info("Recorded client brand '{}' for {}", brand, describeConnection(connection));
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
                LOGGER.info("Detected Via channel '{}' from {}", channel, describeConnection(connection));
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
        return info.shouldUseLegacyParticles(connection);
    }

    private static void markViaModDetected(ClientConnection connection) {
        synchronized (CLIENTS) {
            ClientInfo info = CLIENTS.get(connection);
            if (info == null) {
                info = new ClientInfo();
                CLIENTS.put(connection, info);
            }
            info.setViaModDetected();
            info.resetLegacyDecisionLog();
            LOGGER.info("Marking {} as having Via-based mod detected", describeConnection(connection));
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

    private static String describeConnection(ClientConnection connection) {
        if (connection == null) {
            return "unknown";
        }
        SocketAddress address = connection.getAddress();
        return address != null ? address.toString() : "unknown";
    }

    private static final class ClientInfo {
        private volatile String brand;
        private volatile boolean viaModDetected;
        private volatile Boolean lastLegacyDecision;

        void setBrand(String brand) {
            this.brand = brand;
        }

        void setViaModDetected() {
            this.viaModDetected = true;
        }

        synchronized boolean shouldUseLegacyParticles(ClientConnection connection) {
            boolean legacy = computeLegacyDecision();
            Boolean previousDecision = this.lastLegacyDecision;
            if (previousDecision == null || previousDecision != legacy) {
                this.lastLegacyDecision = legacy;
                if (legacy) {
                    LOGGER.info(
                        "Using legacy particle encoding for {} (brand='{}', viaModDetected={})",
                        describeConnection(connection),
                        this.brand,
                        this.viaModDetected
                    );
                } else {
                    LOGGER.info(
                        "Using modern particle encoding for {} (brand='{}', viaModDetected={})",
                        describeConnection(connection),
                        this.brand,
                        this.viaModDetected
                    );
                }
            }
            return legacy;
        }

        void resetLegacyDecisionLog() {
            this.lastLegacyDecision = null;
        }

        boolean isEmpty() {
            return this.brand == null && !this.viaModDetected;
        }

        private boolean computeLegacyDecision() {
            if (this.viaModDetected) {
                return true;
            }
            String brand = this.brand;
            if (brand == null) {
                return false;
            }
            String normalized = brand.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                return false;
            }
            return normalized.contains("via") || normalized.contains("fabric");
        }
    }
}
