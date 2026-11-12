package dev.orbit.music;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import dev.orbit.ui.OrbitToast;

public final class PartySyncClient {
    private static volatile PartyStatePayload latestState;
    private static volatile long receivedAtMillis;

    private PartySyncClient() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(PartyStatePayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                latestState = payload;
                receivedAtMillis = System.currentTimeMillis();
                OrbitToast.show(formatUpdate(payload));
                if (context.client().player != null) {
                    context.client().player.displayClientMessage(
                        Component.literal(formatUpdate(payload)),
                        false
                    );
                }
            })
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static PartyStatePayload getLatestState() {
        return latestState;
    }

    public static long getEstimatedPositionMillis() {
        PartyStatePayload state = latestState;
        if (state == null) {
            return 0L;
        }
        if (state.playbackState() != PartySync.PLAYING) {
            return state.positionMillis();
        }
        return Math.max(0L, state.positionMillis() + System.currentTimeMillis() - receivedAtMillis);
    }

    private static String formatUpdate(PartyStatePayload payload) {
        String state = switch (payload.playbackState()) {
            case PartySync.PLAYING -> "playing";
            case PartySync.PAUSED -> "paused";
            case PartySync.STOPPED -> "stopped";
            default -> "idle";
        };
        String track = payload.trackId().isEmpty() ? "no track selected" : payload.trackId();
        return "ORBIT party '" + payload.partyName() + "': " + state + " - " + track;
    }

    private static void clear() {
        latestState = null;
        receivedAtMillis = 0L;
    }
}
