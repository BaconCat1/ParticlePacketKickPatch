package org.bacon.noviaversionkick.mixin;

import net.minecraft.network.packet.BrandCustomPayload;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.bacon.noviaversionkick.network.ViaBrandTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Inject(method = "onCustomPayload", at = @At("HEAD"))
    private void noviaversionkick$trackBrand(CustomPayloadC2SPacket packet, CallbackInfo ci) {
        CustomPayload payload = packet.payload();
        if (payload instanceof BrandCustomPayload brandPayload) {
            ViaBrandTracker.setBrand(((ServerCommonNetworkHandlerAccessor) this).noviaversionkick$getConnection(), brandPayload.brand());
            return;
        }
    }
}
