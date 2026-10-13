package cooppause;

import cooppause.packets.PacketCoopPauseState;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Server-side record of a pause requested through this mod.
 *
 * <p>Kept separate from {@code Server.isPaused()} on purpose: that also reports true for the engine's own
 * pause-when-empty and spawn pauses, which this mod must neither claim nor undo.
 *
 * <p>Only touched from the server thread (chat commands and connect events both run there).
 */
public final class PauseState {

    private static Server pausedServer;
    private static String pausedBy;

    private PauseState() {
    }

    public static boolean isPaused(Server server) {
        return server != null && pausedServer == server;
    }

    public static String getPausedBy() {
        return pausedBy;
    }

    public static void pause(Server server, String by) {
        pausedServer = server;
        pausedBy = by;
        PausePacketFilter.takeDropped();
        server.pause();
        server.network.sendToAllClients(new PacketCoopPauseState(true, by));
    }

    public static void unpause(Server server, String by) {
        pausedServer = null;
        pausedBy = null;
        server.resume();
        server.network.sendToAllClients(new PacketCoopPauseState(false, by));

        int dropped = PausePacketFilter.takeDropped();
        if (dropped > 0) {
            System.out.println("Coop Pause: dropped " + dropped + " player action packets while paused");
        }
    }

    /** Tells a newly connected client that the world is paused, if it is. */
    public static void syncTo(ServerClient client) {
        if (client != null && isPaused(client.getServer())) {
            client.sendPacket(new PacketCoopPauseState(true, pausedBy));
        }
    }

    public static void clear(Server server) {
        if (pausedServer == server) {
            pausedServer = null;
            pausedBy = null;
        }
    }
}
