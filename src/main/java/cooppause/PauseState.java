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
 * <p>Only touched from the server thread (chat commands, packets, connect events and the tick all run there).
 */
public final class PauseState {

    public enum Mode {
        /** From {@code /pause}: clients are held in the pause menu until {@code /unpause}. */
        MANUAL,
        /** From {@link AfkMonitor}: clients are frozen but not held, and any player input resumes. */
        AFK
    }

    private static Server pausedServer;
    private static Mode mode;
    private static String pausedBy;

    private PauseState() {
    }

    public static boolean isPaused(Server server) {
        return server != null && pausedServer == server;
    }

    public static boolean isPaused(Server server, Mode expected) {
        return isPaused(server) && mode == expected;
    }

    public static String getPausedBy() {
        return pausedBy;
    }

    /** Pauses, or switches the mode of an existing pause (a {@code /pause} during an AFK pause makes it manual). */
    public static void pause(Server server, String by, Mode newMode) {
        if (!isPaused(server)) {
            PausePacketFilter.takeDropped();
        }

        pausedServer = server;
        mode = newMode;
        pausedBy = by;
        server.pause();
        server.network.sendToAllClients(new PacketCoopPauseState(true, newMode, by));
    }

    public static void unpause(Server server, String by) {
        Mode endedMode = mode;
        pausedServer = null;
        mode = null;
        pausedBy = null;
        server.resume();
        server.network.sendToAllClients(new PacketCoopPauseState(false, endedMode, by));

        int dropped = PausePacketFilter.takeDropped();
        if (dropped > 0) {
            System.out.println("Coop Pause: dropped " + dropped + " player action packets while paused");
        }
    }

    /** Tells a newly connected client that the world is paused, if it is. */
    public static void syncTo(ServerClient client) {
        if (client != null && isPaused(client.getServer())) {
            client.sendPacket(new PacketCoopPauseState(true, mode, pausedBy));
        }
    }

    public static void clear(Server server) {
        if (pausedServer == server) {
            pausedServer = null;
            mode = null;
            pausedBy = null;
        }
    }
}
