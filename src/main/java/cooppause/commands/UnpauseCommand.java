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

/**
 * {@code /unpause}: resumes a world paused with {@code /pause}.
 *
 * <p>Works while paused because chat commands arrive as packets, and the server drains packets in
 * {@code Server.frameTick} whether or not the world is paused.
 */
public class UnpauseCommand extends ModularChatCommand {

    public UnpauseCommand() {
        super("unpause", "Resumes a world paused with /pause", PermissionLevel.ADMIN, false, new CmdParameter[0]);
    }

    @Override
    public void runModular(Client client, Server server, ServerClient serverClient, Object[] args, String[] errors, CommandLog logs) {
        if (!PauseState.isPaused(server)) {
            logs.add(new LocalMessage("cooppause", "notpaused"));
            return;
        }

        String name = PauseCommand.issuerName(serverClient);
        PauseState.unpause(server, name);
        logs.addConsole("World resumed by " + name);
    }
}
