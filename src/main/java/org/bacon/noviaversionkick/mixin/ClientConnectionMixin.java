package org.bacon.noviaversionkick.mixin;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.bacon.noviaversionkick.network.ViaBrandTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
    @Inject(method = "sendInternal(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"))
    private void noviaversionkick$pushConnection(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        ViaBrandTracker.pushConnection((ClientConnection) (Object) this);
    }

    @Inject(method = "sendInternal(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("TAIL"))
    private void noviaversionkick$popConnection(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        ViaBrandTracker.popConnection((ClientConnection) (Object) this);
    }
}
