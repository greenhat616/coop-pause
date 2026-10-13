package cooppause.commands;

import cooppause.PauseActions;
import necesse.engine.commands.CmdParameter;
import necesse.engine.commands.CommandLog;
import necesse.engine.commands.ModularChatCommand;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/** {@code /pause}: freezes the world for everyone until {@code /unpause}. */
public class PauseCommand extends ModularChatCommand {

    public PauseCommand() {
        super("pause", "Pauses the world for all players until /unpause", PauseActions.REQUIRED_PERMISSION, false, new CmdParameter[0]);
    }

    @Override
    public void runModular(Client client, Server server, ServerClient serverClient, Object[] args, String[] errors, CommandLog logs) {
        GameMessage refusal = PauseActions.pause(server, serverClient);
        if (refusal != null) {
            logs.add(refusal);
        }
    }
}
