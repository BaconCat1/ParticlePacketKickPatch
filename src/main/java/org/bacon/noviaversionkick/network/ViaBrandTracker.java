package org.bacon.noviaversionkick.network;

import net.minecraft.network.ClientConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bacon.noviaversionkick.mixin.ClientConnectionAccessor;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
            return this.brand == null;
        }

        private boolean computeLegacyDecision(ClientConnection connection) {
            String brand = this.brand;
            if (brand == null) {
                LOGGER.info("No brand recorded; modern particles will be used");
                return false;
            }
            BrandMetadata metadata = BrandMetadata.fromRawBrand(brand);
            if (!metadata.hasComponents()) {
                LOGGER.info("Brand string was empty after normalization; modern particles will be used");
                return false;
            }
            LOGGER.info(
                "Brand analysis for {}: components={}, primaryPlatform='{}', viaWrappers={}",
                describeConnection(connection),
                metadata.describeComponents(),
                metadata.primaryPlatform(),
                Arrays.toString(metadata.viaWrappers())
            );
            if (metadata.requiresLegacyParticles()) {
                LOGGER.info(
                    "Detected Via wrapper(s) {} with base platform '{}'; legacy particle encoding will be used to avoid the 9-byte overflow",
                    Arrays.toString(metadata.viaWrappers()),
                    metadata.primaryPlatform()
                );
                return true;
            }
            if (metadata.primaryPlatform() == null) {
                LOGGER.info("Unable to determine a primary platform; defaulting to modern particles");
                return false;
            }
            LOGGER.info(
                "Primary platform '{}' does not require legacy particle encoding; modern particles will be used",
                metadata.primaryPlatform()
            );
            return false;
        }

        private static final class BrandMetadata {
            private final String[] components;
            private final String primaryPlatform;
            private final String[] viaWrappers;
            private final boolean requiresLegacyParticles;

            private BrandMetadata(String[] components, String primaryPlatform, String[] viaWrappers, boolean requiresLegacyParticles) {
                this.components = components;
                this.primaryPlatform = primaryPlatform;
                this.viaWrappers = viaWrappers;
                this.requiresLegacyParticles = requiresLegacyParticles;
            }

            static BrandMetadata fromRawBrand(String rawBrand) {
                if (rawBrand == null) {
                    return new BrandMetadata(new String[0], null, new String[0], false);
                }
                String sanitized = rawBrand.trim();
                if (sanitized.isEmpty()) {
                    return new BrandMetadata(new String[0], null, new String[0], false);
                }
                String[] fragments = sanitized.split("\\u0000");
                Set<String> orderedComponents = new LinkedHashSet<>();
                for (String fragment : fragments) {
                    if (fragment == null) {
                        continue;
                    }
                    String trimmed = fragment.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    orderedComponents.add(trimmed.toLowerCase(Locale.ROOT));
                }
                if (orderedComponents.isEmpty()) {
                    orderedComponents.add(sanitized.toLowerCase(Locale.ROOT));
                }
                List<String> viaWrappers = new ArrayList<>();
                String primaryPlatform = null;
                boolean requiresLegacy = false;
                for (String component : orderedComponents) {
                    if (component.startsWith("via")) {
                        viaWrappers.add(component);
                        requiresLegacy = true;
                        if (primaryPlatform == null) {
                            String inferred = inferPlatformFromVia(component);
                            if (inferred != null) {
                                primaryPlatform = inferred;
                            }
                        }
                        continue;
                    }
                    String normalizedPlatform = normalizePlatform(component);
                    if (normalizedPlatform != null && primaryPlatform == null) {
                        primaryPlatform = normalizedPlatform;
                        continue;
                    }
                    if (primaryPlatform == null) {
                        primaryPlatform = component;
                    }
                }
                if (primaryPlatform == null && !orderedComponents.isEmpty()) {
                    primaryPlatform = orderedComponents.iterator().next();
                }
                return new BrandMetadata(
                    orderedComponents.toArray(new String[0]),
                    primaryPlatform,
                    viaWrappers.toArray(new String[0]),
                    requiresLegacy
                );
            }

            boolean hasComponents() {
                return this.components.length > 0;
            }

            String describeComponents() {
                return Arrays.toString(this.components);
            }

            String primaryPlatform() {
                return this.primaryPlatform;
            }

            String[] viaWrappers() {
                return this.viaWrappers;
            }

            boolean requiresLegacyParticles() {
                return this.requiresLegacyParticles;
            }

            private static String normalizePlatform(String component) {
                if (component == null || component.isEmpty()) {
                    return null;
                }
                if (component.equals("fabric") || component.equals("fabricmc")) {
                    return "fabric";
                }
                if (component.equals("quilt")) {
                    return "quilt";
                }
                if (component.equals("forge")) {
                    return "forge";
                }
                if (component.equals("neoforge")) {
                    return "neoforge";
                }
                if (component.equals("vanilla")) {
                    return "vanilla";
                }
                return null;
            }

            private static String inferPlatformFromVia(String viaComponent) {
                if (viaComponent == null || viaComponent.length() <= 3) {
                    return null;
                }
                String suffix = viaComponent.substring(3);
                if (suffix.isEmpty()) {
                    return null;
                }
                String simplified = suffix.replace("-", "").replace("_", "");
                if (simplified.startsWith("fabric")) {
                    return "fabric";
                }
                if (simplified.startsWith("forge")) {
                    return "forge";
                }
                if (simplified.startsWith("neoforge")) {
                    return "neoforge";
                }
                if (simplified.startsWith("quilt")) {
                    return "quilt";
                }
                if (simplified.startsWith("vanilla")) {
                    return "vanilla";
                }
                return null;
            }
        }
    }
}
