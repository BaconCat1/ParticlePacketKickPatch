package org.bacon.noviaversionkick.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.ParticleEffect;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.particle.ParticleTypes;
import org.bacon.noviaversionkick.network.PacketConnectionAttachment;
import org.bacon.noviaversionkick.network.ViaBrandTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleS2CPacket.class)
public abstract class ParticleS2CPacketMixin implements PacketConnectionAttachment {
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
    @Unique private ClientConnection noviaversionkick$connection;

    @Override
    public void noviaversionkick$setConnection(ClientConnection connection) {
        this.noviaversionkick$connection = connection;
    }

    @Override
    public ClientConnection noviaversionkick$getConnection() {
        return this.noviaversionkick$connection;
    }

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void noviaversionkick$writeLegacyWhenNeeded(RegistryByteBuf buf, CallbackInfo ci) {
        ClientConnection connection = this.noviaversionkick$getConnection();
        if (ViaBrandTracker.shouldUseLegacyParticles(connection)) {
            noviaversionkick$writeLegacy(buf);
            ci.cancel();
        }
    }

    private void noviaversionkick$writeLegacy(RegistryByteBuf buf) {
        ParticleEffect effect = this.parameters;
        if (effect == null) {
            return;
        }
        ByteBuf backing = Unpooled.buffer();
        try {
            RegistryByteBuf encoded = new RegistryByteBuf(backing, buf.getRegistryManager());
            ParticleTypes.PACKET_CODEC.encode(encoded, effect);
            int particleId = encoded.readVarInt();

            buf.writeVarInt(particleId);
            buf.writeBoolean(this.forceSpawn);
            buf.writeDouble(this.x);
            buf.writeDouble(this.y);
            buf.writeDouble(this.z);
            buf.writeFloat(this.offsetX);
            buf.writeFloat(this.offsetY);
            buf.writeFloat(this.offsetZ);
            buf.writeFloat(this.speed);
            buf.writeInt(this.count);

            buf.writeBytes(encoded);
        } finally {
            backing.release();
        }
    }
}
