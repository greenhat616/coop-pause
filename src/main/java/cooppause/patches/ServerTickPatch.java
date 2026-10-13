package cooppause.patches;

import cooppause.AfkMonitor;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.Server;
import net.bytebuddy.asm.Advice;

/**
 * Gives {@link AfkMonitor} a periodic hook on the server thread.
 *
 * <p>{@code Server.tick()} keeps running while the world is paused (only the world half inside it is skipped),
 * so the monitor is reached in every state it cares about.
 */
@ModMethodPatch(target = Server.class, name = "tick", arguments = {})
public class ServerTickPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This Server server) {
        AfkMonitor.tick(server);
    }
}
