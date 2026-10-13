package cooppause.commands;

import cooppause.PauseActions;
import necesse.engine.commands.CmdParameter;
import necesse.engine.commands.CommandLog;
import necesse.engine.commands.ModularChatCommand;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * {@code /unpause}: resumes a world paused with {@code /pause}, or by the AFK monitor.
 *
 * <p>Works while paused because chat commands arrive as packets, and the server drains packets in
 * {@code Server.frameTick} whether or not the world is paused.
 */
public class UnpauseCommand extends ModularChatCommand {

    public UnpauseCommand() {
        super("unpause", "Resumes a world paused with /pause or by AFK auto-pause", PauseActions.REQUIRED_PERMISSION, false, new CmdParameter[0]);
    }

    @Override
    public void runModular(Client client, Server server, ServerClient serverClient, Object[] args, String[] errors, CommandLog logs) {
        GameMessage refusal = PauseActions.unpause(server, serverClient);
        if (refusal != null) {
            logs.add(refusal);
        }
    }
}
