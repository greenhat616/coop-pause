package cooppause.commands;

import cooppause.PauseState;
import necesse.engine.commands.CmdParameter;
import necesse.engine.commands.CommandLog;
import necesse.engine.commands.ModularChatCommand;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/** {@code /pause}: freezes the world for everyone until {@code /unpause}. */
public class PauseCommand extends ModularChatCommand {

    public PauseCommand() {
        super("pause", "Pauses the world for all players until /unpause", PermissionLevel.ADMIN, false, new CmdParameter[0]);
    }

    @Override
    public void runModular(Client client, Server server, ServerClient serverClient, Object[] args, String[] errors, CommandLog logs) {
        if (server.isSingleplayer()) {
            // Singleplayer already pauses with the menu, and the menu would resume over this anyway.
            logs.add(new LocalMessage("cooppause", "singleplayer"));
            return;
        }

        if (PauseState.isPaused(server)) {
            logs.add(new LocalMessage("cooppause", "alreadypaused", "name", PauseState.getPausedBy()));
            return;
        }

        PauseState.pause(server, issuerName(serverClient));
        logs.addConsole("World paused by " + issuerName(serverClient));
    }

    static String issuerName(ServerClient serverClient) {
        return serverClient == null ? "Server" : serverClient.getName();
    }
}
