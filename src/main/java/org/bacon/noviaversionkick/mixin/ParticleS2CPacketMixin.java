package org.bacon.noviaversionkick.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.bacon.noviaversionkick.network.ViaBrandTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleS2CPacket.class)
public abstract class ParticleS2CPacketMixin {
    @Shadow private double x;
    @Shadow private double y;
    @Shadow private double z;
    @Shadow private float offsetX;
    @Shadow private float offsetY;
    @Shadow private float offsetZ;
    @Shadow private float speed;
    @Shadow private int count;
    @Shadow private boolean forceSpawn;
    @Shadow private ParticleEffect parameters;

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void noviaversionkick$writeLegacyWhenNeeded(RegistryByteBuf buf, CallbackInfo ci) {
        ClientConnection connection = ViaBrandTracker.getCurrentConnection();
        if (ViaBrandTracker.shouldUseLegacyParticles(connection)) {
            noviaversionkick$writeLegacy(buf);
            ci.cancel();
        }
    }

    private void noviaversionkick$writeLegacy(RegistryByteBuf buf) {
        buf.writeBoolean(this.forceSpawn);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.offsetX);
        buf.writeFloat(this.offsetY);
        buf.writeFloat(this.offsetZ);
        buf.writeFloat(this.speed);
        buf.writeInt(this.count);
        ParticleTypes.PACKET_CODEC.encode(buf, this.parameters);
    }
}
