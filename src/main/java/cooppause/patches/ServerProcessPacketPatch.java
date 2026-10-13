package cooppause.patches;

import cooppause.PausePacketFilter;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.server.Server;
import net.bytebuddy.asm.Advice;

/**
 * Drops world-changing player actions while paused. See {@link PausePacketFilter}.
 *
 * <p>{@code processPacket} is private, but the loader matches it by name and arguments like any other method,
 * and advice is inlined into {@code Server}, so access is not an issue.
 */
@ModMethodPatch(target = Server.class, name = "processPacket", arguments = {NetworkPacket.class})
public class ServerProcessPacketPatch {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean onEnter(@Advice.This Server server, @Advice.Argument(0) NetworkPacket packet) {
        // Truthy plus skipOn means "skip the original".
        return PausePacketFilter.shouldDrop(server, packet);
    }
}
