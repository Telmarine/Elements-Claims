package com.tac5studios.elementsclaims.maps;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** The optional map channel (elements_claims:map). One NBT tag with an "op" each way. See docs/MAP_CHANNEL.md. */
public record MapPayload(CompoundTag tag) implements CustomPacketPayload {

    public static final Type<MapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("elements_claims", "map"));
    public static final StreamCodec<ByteBuf, MapPayload> CODEC = ByteBufCodecs.COMPOUND_TAG.map(MapPayload::new, MapPayload::tag);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
