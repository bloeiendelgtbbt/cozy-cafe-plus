package io.github.chakyl.cozycafe.network;

import io.github.chakyl.cozycafe.CozyCafe;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Fabric replacement for the Forge SimpleChannel. Each packet keeps its original encode/decode logic;
 * handlers always run on the main (server or client) thread.
 */
public class EvilPacketsIHateThem {
    public interface CozyPacket {
        void encode(FriendlyByteBuf buffer);
    }

    public interface ServerHandled extends CozyPacket {
        void handle(ServerPlayer player);
    }

    public interface ClientHandled extends CozyPacket {
        void handleOnClient();
    }

    private static final Map<Class<?>, ResourceLocation> IDS = new HashMap<>();

    public static final ResourceLocation CAFE_ACTION = id(ServerBoundCafeActionPacket.class, "cafe_action");
    public static final ResourceLocation RENAME_CAFE = id(ServerBoundRenameCafePacket.class, "rename_cafe");
    public static final ResourceLocation ADD_WAITER = id(ServerBoundAddWaiterPacket.class, "add_waiter");
    public static final ResourceLocation REMOVE_WAITER = id(ServerBoundRemoveWaiterPacket.class, "remove_waiter");
    public static final ResourceLocation CAFE_FEEDBACK = id(ClientBoundCafeFeedbackPacket.class, "cafe_feedback");
    public static final ResourceLocation ROLES = id(ClientBoundRolesPacket.class, "roles");
    public static final ResourceLocation OPEN_ORDER = id(ClientBoundOpenOrderPacket.class, "open_order");
    public static final ResourceLocation PLACE_ORDER = id(ServerBoundPlaceOrderPacket.class, "place_order");
    public static final ResourceLocation FORGIVE_DEBT = id(ServerBoundForgiveDebtPacket.class, "forgive_debt");
    public static final ResourceLocation CAFE_WAYPOINTS = id(ClientBoundCafeWaypointsPacket.class, "cafe_waypoints");
    public static final ResourceLocation ORDER_STATUS = id(ClientBoundOrderStatusPacket.class, "order_status");
    public static final ResourceLocation CANCEL_ORDER = id(ServerBoundCancelOrderPacket.class, "cancel_order");

    private static ResourceLocation id(Class<?> packetClass, String path) {
        ResourceLocation id = new ResourceLocation(CozyCafe.MODID, path);
        IDS.put(packetClass, id);
        return id;
    }

    public static void registerServerReceivers() {
        serverbound(CAFE_ACTION, ServerBoundCafeActionPacket::new);
        serverbound(RENAME_CAFE, ServerBoundRenameCafePacket::new);
        serverbound(ADD_WAITER, ServerBoundAddWaiterPacket::new);
        serverbound(REMOVE_WAITER, ServerBoundRemoveWaiterPacket::new);
        serverbound(PLACE_ORDER, ServerBoundPlaceOrderPacket::new);
        serverbound(FORGIVE_DEBT, ServerBoundForgiveDebtPacket::new);
        serverbound(CANCEL_ORDER, ServerBoundCancelOrderPacket::new);
    }

    private static void serverbound(ResourceLocation id, Function<FriendlyByteBuf, ? extends ServerHandled> decoder) {
        ServerPlayNetworking.registerGlobalReceiver(id, (server, player, handler, buf, responseSender) -> {
            ServerHandled packet = decoder.apply(buf);
            server.execute(() -> packet.handle(player));
        });
    }

    /**
     * Client side only; called from the client entrypoint.
     */
    public static void registerClientReceivers() {
        clientbound(CAFE_FEEDBACK, ClientBoundCafeFeedbackPacket::new);
        clientbound(ROLES, ClientBoundRolesPacket::new);
        clientbound(OPEN_ORDER, ClientBoundOpenOrderPacket::new);
        clientbound(CAFE_WAYPOINTS, ClientBoundCafeWaypointsPacket::new);
        clientbound(ORDER_STATUS, ClientBoundOrderStatusPacket::new);
    }

    private static void clientbound(ResourceLocation id, Function<FriendlyByteBuf, ? extends ClientHandled> decoder) {
        ClientPlayNetworking.registerGlobalReceiver(id, (client, handler, buf, responseSender) -> {
            ClientHandled packet = decoder.apply(buf);
            client.execute(packet::handleOnClient);
        });
    }

    private static ResourceLocation idOf(CozyPacket message) {
        ResourceLocation id = IDS.get(message.getClass());
        if (id == null) throw new IllegalArgumentException("Unregistered packet " + message.getClass());
        return id;
    }

    private static FriendlyByteBuf write(CozyPacket message) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        message.encode(buf);
        return buf;
    }

    public static void sendToServer(CozyPacket message) {
        ClientPlayNetworking.send(idOf(message), write(message));
    }

    public static void sendToPlayer(CozyPacket message, ServerPlayer player) {
        ServerPlayNetworking.send(player, idOf(message), write(message));
    }
}
