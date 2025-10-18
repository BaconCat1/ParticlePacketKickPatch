package org.bacon.noviaversionkick.network;

import net.minecraft.network.ClientConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.SocketAddress;
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
        LOGGER.info(
            "setBrand called for connection={} with brand='{}'",
            describeConnection(connection),
            brand
        );
        if (connection == null) {
            LOGGER.info("Ignoring setBrand call because connection was null");
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
                        LOGGER.info("No remaining data for {}; removing client entry", describeConnection(connection));
                        CLIENTS.remove(connection);
                    }
                } else {
                    LOGGER.info("No brand information stored for {}; nothing to clear", describeConnection(connection));
                }
                return;
            }
            String sanitized = brand.strip();
            if (info == null) {
                LOGGER.info("Creating new tracking entry for {}", describeConnection(connection));
                info = new ClientInfo();
                CLIENTS.put(connection, info);
            }
            info.setBrand(sanitized);
            info.resetLegacyDecisionLog();
            LOGGER.info(
                "Recorded sanitized client brand '{}' for {}",
                sanitized,
                describeConnection(connection)
            );
        }
    }

    public static boolean shouldUseLegacyParticles(ClientConnection connection) {
        if (connection == null) {
            return false;
        }
        ClientInfo info;
        LOGGER.info("Evaluating particle encoding strategy for {}", describeConnection(connection));
        synchronized (CLIENTS) {
            info = CLIENTS.get(connection);
        }
        if (info == null) {
            LOGGER.info("No client info stored for {}; defaulting to modern particles", describeConnection(connection));
            return false;
        }
        return info.shouldUseLegacyParticles(connection);
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
        private volatile Boolean lastLegacyDecision;

        void setBrand(String brand) {
            this.brand = brand;
        }

        synchronized boolean shouldUseLegacyParticles(ClientConnection connection) {
            boolean legacy = computeLegacyDecision();
            Boolean previousDecision = this.lastLegacyDecision;
            if (previousDecision == null || previousDecision != legacy) {
                this.lastLegacyDecision = legacy;
                if (legacy) {
                    LOGGER.info(
                        "Using legacy particle encoding for {} (brand='{}')",
                        describeConnection(connection),
                        this.brand
                    );
                } else {
                    LOGGER.info(
                        "Using modern particle encoding for {} (brand='{}')",
                        describeConnection(connection),
                        this.brand
                    );
                }
            }
            return legacy;
        }

        void resetLegacyDecisionLog() {
            this.lastLegacyDecision = null;
        }

        boolean isEmpty() {
            return this.brand == null;
        }

        private boolean computeLegacyDecision() {
            String brand = this.brand;
            if (brand == null) {
                LOGGER.info("No brand recorded; modern particles will be used");
                return false;
            }
            String normalized = brand.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                LOGGER.info("Brand string was empty after normalization; modern particles will be used");
                return false;
            }
            boolean fabricDetected = normalized.contains("fabric");
            LOGGER.info(
                "Normalized brand='{}'; fabricDetected={}",
                normalized,
                fabricDetected
            );
            return fabricDetected;
        }
    }
}
