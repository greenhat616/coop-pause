package cooppause;

import java.util.HashMap;
import java.util.Map;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Pauses the world when every connected player has been idle for {@link CoopPauseConfig#getAfkMinutes()} minutes, and
 * resumes it as soon as any of them does something.
 *
 * <p>Activity is reported by the clients themselves ({@code PacketCoopActivity}) rather than inferred from
 * gameplay packets: a client sends a movement packet every two seconds even when standing still, and an
 * auto-paused player must be able to wake the world with a key press that produces no gameplay packet at all.
 *
 * <p>Server thread only: the tick patch, packet processing and connect events all run on it.
 */
public final class AfkMonitor {

    private static final int CHECK_INTERVAL_TICKS = 20;

    private static final Map<Long, Long> LAST_ACTIVE = new HashMap<>();
    private static int tickCounter;

    private AfkMonitor() {
    }

    /** A connecting player counts as active, so joining never immediately counts toward an AFK pause. */
    public static void onConnect(ServerClient client) {
        LAST_ACTIVE.put(client.authentication, System.currentTimeMillis());
    }

    public static void markActive(Server server, ServerClient client) {
        LAST_ACTIVE.put(client.authentication, System.currentTimeMillis());
        if (PauseState.isPaused(server, PauseState.Mode.AFK)) {
            PauseState.unpause(server, client.getName());
        }
    }

    /** Called after every {@code Server.tick()}, which keeps running while the world is paused. */
    public static void tick(Server server) {
        if (++tickCounter < CHECK_INTERVAL_TICKS) {
            return;
        }

        tickCounter = 0;
        int minutes = CoopPauseConfig.getAfkMinutes();
        // Leave any existing pause alone, including the engine's own, which would otherwise be claimed as ours.
        if (minutes <= 0 || server.isSingleplayer() || server.isPaused() || PauseState.isPaused(server)) {
            return;
        }

        long now = System.currentTimeMillis();
        long threshold = minutes * 60_000L;
        boolean anyone = false;
        for (ServerClient client : server.getClients()) {
            anyone = true;
            Long lastActive = LAST_ACTIVE.get(client.authentication);
            if (lastActive == null) {
                // Connected before the mod could see it; start counting from now.
                LAST_ACTIVE.put(client.authentication, now);
                return;
            }

            if (now - lastActive < threshold) {
                return;
            }
        }

        if (anyone) {
            System.out.println("Coop Pause: all players idle for " + minutes + " minutes, pausing");
            PauseState.pause(server, null, PauseState.Mode.AFK);
        }
    }

    public static void onDisconnect(ServerClient client) {
        LAST_ACTIVE.remove(client.authentication);
    }

    public static void clear() {
        LAST_ACTIVE.clear();
        tickCounter = 0;
    }
}
