package cooppause;

import cooppause.commands.PauseCommand;
import cooppause.commands.UnpauseCommand;
import cooppause.packets.PacketCoopPauseState;
import necesse.engine.GameEventListener;
import necesse.engine.GameEvents;
import necesse.engine.commands.CommandsManager;
import necesse.engine.events.ServerClientConnectedEvent;
import necesse.engine.events.ServerStopEvent;
import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.registries.PacketRegistry;

/**
 * Freezes the whole world on demand while every player stays connected.
 *
 * <p>No method patches are needed for the freeze itself: {@code Server.isPaused()} already gates
 * {@code world.serverTick()} and {@code world.frameTick()}, while packets, pings and chat commands keep being
 * processed. This mod only flips that flag and tells every client to stop its local simulation too.
 */
@ModEntry
public class CoopPauseMod {

    public void init() {
        PacketRegistry.registerPacket(PacketCoopPauseState.class);

        CommandsManager.registerServerCommand(new PauseCommand());
        CommandsManager.registerServerCommand(new UnpauseCommand());

        // A player joining a paused world must freeze too, or their client would simulate on its own.
        GameEvents.addListener(ServerClientConnectedEvent.class, new GameEventListener<ServerClientConnectedEvent>() {
            @Override
            public void onEvent(ServerClientConnectedEvent event) {
                PauseState.syncTo(event.client);
            }
        });

        // State belongs to one server instance; hosting another world must start unpaused.
        GameEvents.addListener(ServerStopEvent.class, new GameEventListener<ServerStopEvent>() {
            @Override
            public void onEvent(ServerStopEvent event) {
                PauseState.clear(event.server);
            }
        });
    }
}
