package org.bacon.noviaversionkick.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import org.bacon.noviaversionkick.network.PacketConnectionAttachment;
import org.bacon.noviaversionkick.network.ViaBrandTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.util.Identifier;

@Mixin(ParticleS2CPacket.class)
public abstract class ParticleS2CPacketMixin implements PacketConnectionAttachment {
    @Final
    @Shadow private double x;
    @Final
    @Shadow private double y;
    @Final
    @Shadow private double z;
    @Final
    @Shadow private float offsetX;
    @Final
    @Shadow private float offsetY;
    @Final
    @Shadow private float offsetZ;
    @Final
    @Shadow private float speed;
    @Final
    @Shadow private int count;
    @Final
    @Shadow private boolean forceSpawn;
    @Final
    @Shadow private ParticleEffect parameters;
    @Unique private ClientConnection noviaversionkick$connection;
    @Unique private static final double noviaversionkick$SURFACE_THRESHOLD = 0.3D;
    @Unique private static final double noviaversionkick$AXIS_EPSILON = 1.0E-6D;

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
        if (noviaversionkick$shouldSuppress(effect)) {
            noviaversionkick$writeSuppressed(buf);
            return;
        }
        ByteBuf backing = Unpooled.buffer();
        try {
            RegistryByteBuf encoded = new RegistryByteBuf(backing, buf.getRegistryManager());
            ParticleTypes.PACKET_CODEC.encode(encoded, effect);
            int particleTypeId = Registries.PARTICLE_TYPE.getRawId(effect.getType());
            buf.writeVarInt(particleTypeId);
            buf.writeBoolean(this.forceSpawn);
            noviaversionkick$writeAlignedPosition(buf, effect);
            buf.writeFloat(this.offsetX);
            buf.writeFloat(this.offsetY);
            buf.writeFloat(this.offsetZ);
            buf.writeFloat(this.speed);
            buf.writeInt(this.count);

            encoded.readerIndex(0);
            buf.writeBytes(encoded);
        } finally {
            backing.release();
        }
    }

    @Unique
    private boolean noviaversionkick$shouldSuppress(ParticleEffect effect) {
        Identifier id = Registries.PARTICLE_TYPE.getId(effect.getType());
        if (id == null) {
            return false;
        }
        return id.getPath().startsWith("falling_");
    }

    @Unique
    private void noviaversionkick$writeSuppressed(RegistryByteBuf buf) {
        int fallbackId = Registries.PARTICLE_TYPE.getRawId(ParticleTypes.POOF);
        buf.writeVarInt(fallbackId);
        buf.writeBoolean(false);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(0.0F);
        buf.writeFloat(0.0F);
        buf.writeFloat(0.0F);
        buf.writeFloat(0.0F);
        buf.writeInt(0);
    }

    @Unique
    private void noviaversionkick$writeAlignedPosition(RegistryByteBuf buf, ParticleEffect effect) {
        if (!(effect instanceof BlockStateParticleEffect)) {
            buf.writeDouble(this.x);
            buf.writeDouble(this.y);
            buf.writeDouble(this.z);
            return;
        }

        double posX = this.x;
        double posY = this.y;
        double posZ = this.z;

        double baseX = Math.floor(posX);
        double baseY = Math.floor(posY);
        double baseZ = Math.floor(posZ);

        double fractionalX = posX - baseX;
        double fractionalY = posY - baseY;
        double fractionalZ = posZ - baseZ;

        double distanceX = Math.min(fractionalX, 1.0D - fractionalX);
        double distanceY = Math.min(fractionalY, 1.0D - fractionalY);
        double distanceZ = Math.min(fractionalZ, 1.0D - fractionalZ);

        double nearest = Math.min(distanceX, Math.min(distanceY, distanceZ));
        if (nearest <= noviaversionkick$SURFACE_THRESHOLD) {
            if (distanceX <= nearest + noviaversionkick$AXIS_EPSILON) {
                posX = baseX + (fractionalX < 0.5D ? 0.0D : 1.0D);
            } else if (distanceY <= nearest + noviaversionkick$AXIS_EPSILON) {
                posY = baseY + (fractionalY < 0.5D ? 0.0D : 1.0D);
            } else {
                posZ = baseZ + (fractionalZ < 0.5D ? 0.0D : 1.0D);
            }
        }

        buf.writeDouble(posX);
        buf.writeDouble(posY);
        buf.writeDouble(posZ);
    }
}
