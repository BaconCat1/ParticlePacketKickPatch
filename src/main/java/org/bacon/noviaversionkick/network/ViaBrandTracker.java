package org.bacon.noviaversionkick.network;

import net.minecraft.network.ClientConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bacon.noviaversionkick.mixin.ClientConnectionAccessor;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
            LOGGER.info(
                "Raw brand payload received from {}: '{}'",
                describeConnection(connection),
                brand
            );
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

    public static void setClientModList(ClientConnection connection, Collection<String> mods) {
        LOGGER.info(
            "setClientModList called for connection={} with {} mods",
            describeConnection(connection),
            mods == null ? 0 : mods.size()
        );
        if (connection == null) {
            LOGGER.info("Ignoring setClientModList call because connection was null");
            return;
        }
        synchronized (CLIENTS) {
            ClientInfo info = CLIENTS.get(connection);
            if (mods == null) {
                if (info != null) {
                    LOGGER.info("Clearing client mod list for {}", describeConnection(connection));
                    info.setClientMods(null);
                    info.resetLegacyDecisionLog();
                    if (info.isEmpty()) {
                        LOGGER.info("No remaining data for {}; removing client entry", describeConnection(connection));
                        CLIENTS.remove(connection);
                    }
                } else {
                    LOGGER.info("No mod list stored for {}; nothing to clear", describeConnection(connection));
                }
                return;
            }
            if (info == null) {
                LOGGER.info("Creating new tracking entry for {} to store mod list", describeConnection(connection));
                info = new ClientInfo();
                CLIENTS.put(connection, info);
            }
            info.setClientMods(mods);
            info.resetLegacyDecisionLog();
            LOGGER.info(
                "Recorded {} client mods for {}: {}",
                info.describeClientModCount(),
                describeConnection(connection),
                info.describeClientMods()
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
        private volatile Set<String> clientMods;
        private volatile Boolean lastLegacyDecision;

        void setBrand(String brand) {
            this.brand = brand;
        }

        void setClientMods(Collection<String> mods) {
            if (mods == null || mods.isEmpty()) {
                this.clientMods = null;
                return;
            }
            Set<String> normalized = new LinkedHashSet<>();
            for (String mod : mods) {
                if (mod == null) {
                    continue;
                }
                String trimmed = mod.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                normalized.add(trimmed.toLowerCase(Locale.ROOT));
            }
            this.clientMods = normalized.isEmpty() ? null : normalized;
        }

        synchronized boolean shouldUseLegacyParticles(ClientConnection connection) {
            boolean legacy = computeLegacyDecision(connection);
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
            return this.brand == null && (this.clientMods == null || this.clientMods.isEmpty());
        }

        String describeClientModCount() {
            Set<String> mods = this.clientMods;
            return mods == null ? "0" : Integer.toString(mods.size());
        }

        String describeClientMods() {
            Set<String> mods = this.clientMods;
            if (mods == null || mods.isEmpty()) {
                return "[]";
            }
            return mods.toString();
        }

        private boolean computeLegacyDecision(ClientConnection connection) {
            boolean fabricBrand = brandIndicatesFabric(this.brand);
            boolean fabricMods = modsIndicateFabric(this.clientMods);

            LOGGER.info(
                "Evaluating platform for {}: brandIndicatesFabric={}, modsIndicateFabric={}",
                describeConnection(connection),
                fabricBrand,
                fabricMods
            );

            if (fabricBrand || fabricMods) {
                LOGGER.info(
                    "Assuming Fabric client for {}; legacy particle encoding will be used",
                    describeConnection(connection)
                );
                return true;
            }

            LOGGER.info(
                "Fabric indicators not detected for {}; modern particles will be used",
                describeConnection(connection)
            );
            return false;
        }

        private static boolean brandIndicatesFabric(String brand) {
            if (brand == null) {
                return false;
            }
            String sanitized = brand.trim();
            if (sanitized.isEmpty()) {
                return false;
            }
            String[] fragments = sanitized.split("\\u0000");
            for (String fragment : fragments) {
                if (fragment == null) {
                    continue;
                }
                String normalized = fragment.trim().toLowerCase(Locale.ROOT);
                if (normalized.isEmpty()) {
                    continue;
                }
                if (normalized.contains("fabric")) {
                    return true;
                }
            }
            return false;
        }

        private static boolean modsIndicateFabric(Set<String> mods) {
            if (mods == null || mods.isEmpty()) {
                return false;
            }
            if (mods.contains("fabricloader")) {
                return true;
            }
            for (String mod : mods) {
                if (mod != null && mod.contains("fabric")) {
                    return true;
                }
            }
            return false;
        }
    }
}
