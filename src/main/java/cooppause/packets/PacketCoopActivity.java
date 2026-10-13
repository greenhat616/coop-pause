package cooppause.packets;

import cooppause.AfkMonitor;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/** Client to server: the player has pressed something recently. See {@link AfkMonitor}. */
public class PacketCoopActivity extends Packet {

    public PacketCoopActivity(byte[] data) {
        super(data);
    }

    public PacketCoopActivity() {
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        if (client != null) {
            AfkMonitor.markActive(server, client);
        }
    }
}
