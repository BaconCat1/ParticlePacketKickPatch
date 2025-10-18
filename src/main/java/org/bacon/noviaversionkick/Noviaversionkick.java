package org.bacon.noviaversionkick;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bacon.noviaversionkick.mixin.ServerLoginNetworkHandlerAccessor;
import org.bacon.noviaversionkick.network.ViaBrandTracker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Noviaversionkick implements ModInitializer {
    private static final Logger LOGGER = LogManager.getLogger("Noviaversionkick");
    private static final Identifier FABRIC_MOD_LIST_CHANNEL = Identifier.of("fabric", "mod_list");
    private static final Identifier FABRIC_MODLIST_LEGACY_CHANNEL = Identifier.of("fabric", "modlist");

    @Override
    public void onInitialize() {
        registerFabricModListReceiver(FABRIC_MOD_LIST_CHANNEL);
        registerFabricModListReceiver(FABRIC_MODLIST_LEGACY_CHANNEL);
    }

    private static void registerFabricModListReceiver(Identifier channel) {
        ServerLoginNetworking.registerGlobalReceiver(channel, (MinecraftServer server, ServerLoginNetworkHandler handler, boolean understood, PacketByteBuf buf, ServerLoginNetworking.LoginSynchronizer synchronizer, PacketSender responseSender) -> {
            ClientConnection connection = ((ServerLoginNetworkHandlerAccessor) handler).noviaversionkick$getConnection();
            if (!understood || buf == null) {
                LOGGER.info("Fabric mod list query on {} was not understood or payload missing (channel={})", describeConnection(connection), channel);
                ViaBrandTracker.setClientModList(connection, null);
                return;
            }

            PacketByteBuf duplicate = PacketByteBufs.copy(buf);
            try {
                List<String> modIds = parseModListPayload(duplicate);
                if (modIds.isEmpty()) {
                    LOGGER.info("Received empty or unparseable Fabric mod list from {} on channel {}", describeConnection(connection), channel);
                    ViaBrandTracker.setClientModList(connection, null);
                    return;
                }
                ViaBrandTracker.setClientModList(connection, modIds);
            } catch (Throwable throwable) {
                LOGGER.error("Failed to parse Fabric mod list payload from {} on channel {}", describeConnection(connection), channel, throwable);
                ViaBrandTracker.setClientModList(connection, null);
            } finally {
                duplicate.release();
            }
        });
    }

    private static List<String> parseModListPayload(PacketByteBuf buf) {
        List<String> modIds = new ArrayList<>();
        buf.markReaderIndex();
        try {
            int handshakeVersion = buf.readVarInt();
            if (handshakeVersion >= 0 && handshakeVersion <= 5) {
                int modCount = buf.readVarInt();
                for (int i = 0; i < modCount; i++) {
                    String modId = buf.readString(Short.MAX_VALUE).trim();
                    if (buf.isReadable()) {
                        buf.readString(Short.MAX_VALUE);
                    }
                    if (!modId.isEmpty()) {
                        modIds.add(modId.toLowerCase(Locale.ROOT));
                    }
                }
                if (!modIds.isEmpty()) {
                    return modIds;
                }
            }
        } catch (Throwable ignored) {
        }
        buf.resetReaderIndex();

        try {
            int modCount = buf.readVarInt();
            for (int i = 0; i < modCount; i++) {
                String modId = buf.readString(Short.MAX_VALUE).trim();
                if (!modId.isEmpty()) {
                    modIds.add(modId.toLowerCase(Locale.ROOT));
                }
            }
            if (!modIds.isEmpty()) {
                return modIds;
            }
        } catch (Throwable ignored) {
        }
        buf.resetReaderIndex();

        try {
            Set<String> deduplicated = new HashSet<>();
            while (buf.isReadable()) {
                String modId = buf.readString(Short.MAX_VALUE).trim();
                if (modId.isEmpty()) {
                    break;
                }
                deduplicated.add(modId.toLowerCase(Locale.ROOT));
            }
            if (!deduplicated.isEmpty()) {
                modIds.addAll(deduplicated);
            }
        } catch (Throwable ignored) {
        }
        return modIds;
    }

    private static String describeConnection(ClientConnection connection) {
        if (connection == null) {
            return "unknown";
        }
        return String.valueOf(connection.getAddress());
    }
}
