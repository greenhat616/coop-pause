package cooppause.packets;

import necesse.engine.localization.Localization;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.gfx.GameColor;

/**
 * Server to client: the world has been paused or resumed.
 *
 * <p>The server alone stops simulating the world, but each client keeps predicting mobs, projectiles and its
 * own world clock locally. {@code Client.pause()} stops that local simulation while leaving packet handling
 * and the not-responding timer running, so the connection survives. On the host, the same call also reaches
 * the local server, which is harmless since the server is already paused.
 */
public class PacketCoopPauseState extends Packet {

    public final boolean paused;
    public final String by;

    public PacketCoopPauseState(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.paused = reader.getNextBoolean();
        this.by = reader.getNextString();
    }

    public PacketCoopPauseState(boolean paused, String by) {
        this.paused = paused;
        this.by = by == null ? "" : by;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextBoolean(this.paused);
        writer.putNextString(this.by);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        if (this.paused) {
            client.pause();
            client.chat.addMessage(GameColor.YELLOW.getColorCode() + Localization.translate("cooppause", "paused", "name", this.by));
        } else {
            client.resume();
            client.chat.addMessage(GameColor.GREEN.getColorCode() + Localization.translate("cooppause", "resumed", "name", this.by));
        }
    }
}
