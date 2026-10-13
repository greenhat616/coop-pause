package cooppause.commands;

import cooppause.PauseActions;
import necesse.engine.commands.CmdParameter;
import necesse.engine.commands.CommandLog;
import necesse.engine.commands.ModularChatCommand;
import necesse.engine.commands.parameterHandlers.IntParameterHandler;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/** {@code /afkpause [minutes]}: shows or sets how long everyone must be idle before the world pauses; 0 disables. */
public class AfkPauseCommand extends ModularChatCommand {

    public AfkPauseCommand() {
        super("afkpause", "Shows or sets the minutes all players must be idle before auto-pausing (0 = off)",
            PauseActions.REQUIRED_PERMISSION, false, new CmdParameter("minutes", new IntParameterHandler(null), true));
    }

    @Override
    public void runModular(Client client, Server server, ServerClient serverClient, Object[] args, String[] errors, CommandLog logs) {
        Integer minutes = (Integer) args[0];
        logs.add(minutes == null ? PauseActions.describeAfk() : PauseActions.setAfkMinutes(server, minutes));
    }
}
