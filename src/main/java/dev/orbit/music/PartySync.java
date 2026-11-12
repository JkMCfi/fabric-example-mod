package dev.orbit.music;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class PartySync {
    public static final int IDLE = 0;
    public static final int PLAYING = 1;
    public static final int PAUSED = 2;
    public static final int STOPPED = 3;

    private static final Pattern VALID_NAME = Pattern.compile("[a-zA-Z0-9_-]{1,32}");
    private static final Pattern VALID_TRACK_ID = Pattern.compile("[a-zA-Z0-9._-]{1,80}");
    private static final Map<String, Party> PARTIES = new HashMap<>();
    private static final Map<UUID, String> MEMBERSHIPS = new HashMap<>();

    private PartySync() {
    }

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(PartyStatePayload.TYPE, PartyStatePayload.CODEC);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("orbit")
                .then(Commands.literal("party")
                    .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(context -> create(
                                context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "name"),
                                context.getSource()
                            ))))
                    .then(Commands.literal("join")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(context -> join(
                                context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "name"),
                                context.getSource()
                            ))))
                    .then(Commands.literal("leave")
                        .executes(context -> leave(context.getSource().getPlayerOrException(), context.getSource())))
                    .then(Commands.literal("status")
                        .executes(context -> status(context.getSource().getPlayerOrException(), context.getSource())))
                    .then(Commands.literal("play")
                        .then(Commands.argument("track-id", StringArgumentType.word())
                            .executes(context -> setTrack(
                                context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "track-id"),
                                context.getSource()
                            ))))
                    .then(Commands.literal("pause")
                        .executes(context -> setPlayback(context.getSource().getPlayerOrException(), PAUSED, context.getSource())))
                    .then(Commands.literal("resume")
                        .executes(context -> setPlayback(context.getSource().getPlayerOrException(), PLAYING, context.getSource())))
                    .then(Commands.literal("stop")
                        .executes(context -> setPlayback(context.getSource().getPlayerOrException(), STOPPED, context.getSource()))))
            )
        );

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> removeMember(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
    }

    private static int create(ServerPlayer player, String name, net.minecraft.commands.CommandSourceStack source) {
        if (!VALID_NAME.matcher(name).matches()) {
            return reject(source, "Party names may contain letters, numbers, underscores, and hyphens (up to 32 characters).");
        }

        String normalized = name.toLowerCase(Locale.ROOT);
        if (PARTIES.containsKey(normalized)) {
            return reject(source, "A party with that name already exists.");
        }
        if (MEMBERSHIPS.containsKey(player.getUUID())) {
            return reject(source, "Leave your current party before creating another.");
        }

        Party party = new Party(normalized, source.getServer());
        PARTIES.put(normalized, party);
        MEMBERSHIPS.put(player.getUUID(), normalized);
        party.members.add(player.getUUID());
        sendSnapshot(party, player);
        return respond(source, "Created party '" + normalized + "'. Invite friends with /orbit party join " + normalized + ".");
    }

    private static int join(ServerPlayer player, String name, net.minecraft.commands.CommandSourceStack source) {
        String normalized = name.toLowerCase(Locale.ROOT);
        Party party = PARTIES.get(normalized);
        if (party == null) {
            return reject(source, "That party does not exist.");
        }
        if (MEMBERSHIPS.containsKey(player.getUUID())) {
            return reject(source, "Leave your current party before joining another.");
        }

        party.members.add(player.getUUID());
        MEMBERSHIPS.put(player.getUUID(), normalized);
        sendSnapshot(party, player);
        return respond(source, "Joined party '" + normalized + "'.");
    }

    private static int leave(ServerPlayer player, net.minecraft.commands.CommandSourceStack source) {
        if (!removeMember(player)) {
            return reject(source, "You are not in a party.");
        }
        return respond(source, "You left your party.");
    }

    private static int status(ServerPlayer player, net.minecraft.commands.CommandSourceStack source) {
        Party party = partyFor(player);
        if (party == null) {
            return reject(source, "You are not in a party.");
        }
        String state = switch (party.playbackState) {
            case PLAYING -> "playing";
            case PAUSED -> "paused";
            case STOPPED -> "stopped";
            default -> "idle";
        };
        String track = party.trackId.isEmpty() ? "no track selected" : "track " + party.trackId;
        return respond(source, "Party '" + party.name + "' is " + state + " (" + track + ").");
    }

    private static int setTrack(ServerPlayer player, String trackId, net.minecraft.commands.CommandSourceStack source) {
        if (!VALID_TRACK_ID.matcher(trackId).matches()) {
            return reject(source, "Track IDs may contain letters, numbers, periods, underscores, and hyphens (up to 80 characters).");
        }
        Party party = partyFor(player);
        if (party == null) {
            return reject(source, "Join a party first with /orbit party join <name>.");
        }

        party.trackId = trackId;
        party.positionMillis = 0L;
        party.changedAtMillis = System.currentTimeMillis();
        party.playbackState = PLAYING;
        broadcast(party);
        return respond(source, "Party track set to '" + trackId + "'. Each listener needs a local copy with this track ID.");
    }

    private static int setPlayback(ServerPlayer player, int playbackState, net.minecraft.commands.CommandSourceStack source) {
        Party party = partyFor(player);
        if (party == null) {
            return reject(source, "Join a party first with /orbit party join <name>.");
        }
        if (playbackState == PLAYING && party.trackId.isEmpty()) {
            return reject(source, "Select a track first with /orbit party play <track-id>.");
        }

        party.positionMillis = currentPosition(party);
        party.changedAtMillis = System.currentTimeMillis();
        party.playbackState = playbackState;
        broadcast(party);

        String label = switch (playbackState) {
            case PLAYING -> "resumed";
            case PAUSED -> "paused";
            default -> "stopped";
        };
        return respond(source, "Party playback " + label + ".");
    }

    private static Party partyFor(ServerPlayer player) {
        String name = MEMBERSHIPS.get(player.getUUID());
        return name == null ? null : PARTIES.get(name);
    }

    private static boolean removeMember(ServerPlayer player) {
        String partyName = MEMBERSHIPS.remove(player.getUUID());
        if (partyName == null) {
            return false;
        }

        Party party = PARTIES.get(partyName);
        if (party == null) {
            return false;
        }
        party.members.remove(player.getUUID());
        if (party.members.isEmpty()) {
            PARTIES.remove(partyName);
        } else {
            broadcast(party);
        }
        return true;
    }

    private static void broadcast(Party party) {
        for (UUID memberId : new ArrayList<>(party.members)) {
            ServerPlayer member = party.server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                sendSnapshot(party, member);
            }
        }
    }

    private static void sendSnapshot(Party party, ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, PartyStatePayload.TYPE)) {
            player.displayClientMessage(Component.literal("Install the ORBIT client mod to receive party playback updates."), false);
            return;
        }
        ServerPlayNetworking.send(player, new PartyStatePayload(
            party.name,
            party.trackId,
            party.playbackState,
            currentPosition(party)
        ));
    }

    private static long currentPosition(Party party) {
        if (party.playbackState != PLAYING) {
            return party.positionMillis;
        }
        return Math.max(0L, party.positionMillis + System.currentTimeMillis() - party.changedAtMillis);
    }

    private static int respond(net.minecraft.commands.CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int reject(net.minecraft.commands.CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static void clear() {
        PARTIES.clear();
        MEMBERSHIPS.clear();
    }

    private static final class Party {
        private final String name;
        private final Set<UUID> members = new LinkedHashSet<>();
        private final MinecraftServer server;
        private String trackId = "";
        private int playbackState = IDLE;
        private long positionMillis;
        private long changedAtMillis = System.currentTimeMillis();

        private Party(String name, MinecraftServer server) {
            this.name = name;
            this.server = server;
        }
    }
}
