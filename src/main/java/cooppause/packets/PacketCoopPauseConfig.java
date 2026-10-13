package cooppause.packets;

import cooppause.ClientPauseState;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;

/**
 * Server to client: the server's current settings, sent on connect and whenever they change.
 *
 * <p>The settings live on the server. A remote client's own copy of the mod settings file is never used, so
 * the settings menu must show these values instead.
 */
public class PacketCoopPauseConfig extends Packet {

    public final int afkMinutes;

    public PacketCoopPauseConfig(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.afkMinutes = reader.getNextInt();
    }

    public PacketCoopPauseConfig(int afkMinutes) {
        this.afkMinutes = afkMinutes;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(afkMinutes);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        ClientPauseState.setServerAfkMinutes(client, this.afkMinutes);
    }
}
