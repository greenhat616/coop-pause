package cooppause;

import cooppause.commands.AfkPauseCommand;
import cooppause.commands.PauseCommand;
import cooppause.commands.UnpauseCommand;
import cooppause.packets.PacketCoopActivity;
import cooppause.packets.PacketCoopPauseConfig;
import cooppause.packets.PacketCoopPauseRequest;
import cooppause.packets.PacketCoopPauseState;
import necesse.engine.GameEventListener;
import necesse.engine.GameEvents;
import necesse.engine.commands.CommandsManager;
import necesse.engine.events.ServerClientConnectedEvent;
import necesse.engine.events.ServerClientDisconnectEvent;
import necesse.engine.events.ServerStopEvent;
import necesse.engine.modLoader.ModSettings;
import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.registries.PacketRegistry;

/**
 * Freezes the whole world on demand, or when every player is idle, while everyone stays connected.
 *
 * <p>No method patches are needed for the freeze itself: {@code Server.isPaused()} already gates
 * {@code world.serverTick()} and {@code world.frameTick()}, while packets, pings and chat commands keep being
 * processed. This mod flips that flag, tells every client to stop its local simulation too, and patches only
 * what blocking input and detecting idleness require.
 */
@ModEntry
public class CoopPauseMod {

    public ModSettings initSettings() {
        return CoopPauseConfig.create();
    }

    public void init() {
        PacketRegistry.registerPacket(PacketCoopPauseState.class);
        PacketRegistry.registerPacket(PacketCoopActivity.class);
        PacketRegistry.registerPacket(PacketCoopPauseConfig.class);
        PacketRegistry.registerPacket(PacketCoopPauseRequest.class);

        CommandsManager.registerServerCommand(new PauseCommand());
        CommandsManager.registerServerCommand(new UnpauseCommand());
        CommandsManager.registerServerCommand(new AfkPauseCommand());

        GameEvents.addListener(ServerClientConnectedEvent.class, new GameEventListener<ServerClientConnectedEvent>() {
            @Override
            public void onEvent(ServerClientConnectedEvent event) {
                AfkMonitor.onConnect(event.client);
                event.client.sendPacket(new PacketCoopPauseConfig(CoopPauseConfig.getAfkMinutes()));
                // A player joining a paused world must freeze too, or their client would simulate on its own.
                PauseState.syncTo(event.client);
            }
        });

        GameEvents.addListener(ServerClientDisconnectEvent.class, new GameEventListener<ServerClientDisconnectEvent>() {
            @Override
            public void onEvent(ServerClientDisconnectEvent event) {
                AfkMonitor.onDisconnect(event.client);
            }
        });

        // State belongs to one server instance; hosting another world must start unpaused.
        GameEvents.addListener(ServerStopEvent.class, new GameEventListener<ServerStopEvent>() {
            @Override
            public void onEvent(ServerStopEvent event) {
                PauseState.clear(event.server);
                AfkMonitor.clear();
            }
        });
    }
}
