package cooppause.packets;

import cooppause.PauseActions;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Client to server: a pause menu button was pressed.
 *
 * <p>Permission is checked here, not just by hiding buttons, since the client cannot be trusted with it.
 */
public class PacketCoopPauseRequest extends Packet {

    public enum Action {
        PAUSE,
        UNPAUSE,
        SET_AFK_MINUTES
    }

    public final Action action;
    public final int value;

    public PacketCoopPauseRequest(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        int index = reader.getNextByteUnsigned();
        this.action = index < Action.values().length ? Action.values()[index] : null;
        this.value = reader.getNextInt();
    }

    public PacketCoopPauseRequest(Action action, int value) {
        this.action = action;
        this.value = value;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextByteUnsigned(action.ordinal());
        writer.putNextInt(value);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        if (client == null || this.action == null) {
            return;
        }

        if (!PauseActions.hasPermission(client)) {
            client.sendChatMessage(new LocalMessage("cooppause", "nopermission"));
            return;
        }

        GameMessage reply;
        switch (this.action) {
            case PAUSE:
                reply = PauseActions.pause(server, client);
                break;
            case UNPAUSE:
                reply = PauseActions.unpause(server, client);
                break;
            case SET_AFK_MINUTES:
                reply = PauseActions.setAfkMinutes(server, this.value);
                break;
            default:
                reply = null;
        }

        if (reply != null) {
            client.sendChatMessage(reply);
        }
    }
}
