package dev.orbit.music;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PartyStatePayload(
    String partyName,
    String trackId,
    int playbackState,
    long positionMillis
) implements CustomPacketPayload {
    public static final Type<PartyStatePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath("orbit", "party_state")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PartyStatePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(32), PartyStatePayload::partyName,
        ByteBufCodecs.stringUtf8(80), PartyStatePayload::trackId,
        ByteBufCodecs.VAR_INT, PartyStatePayload::playbackState,
        ByteBufCodecs.VAR_LONG, PartyStatePayload::positionMillis,
        PartyStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
