package cooppause.commands;

import cooppause.CoopPauseMod;
import necesse.engine.Settings;
import necesse.engine.commands.CmdParameter;
import necesse.engine.commands.CommandLog;
import necesse.engine.commands.ModularChatCommand;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.commands.parameterHandlers.IntParameterHandler;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/** {@code /afkpause [minutes]}: shows or sets how long everyone must be idle before the world pauses; 0 disables. */
public class AfkPauseCommand extends ModularChatCommand {

    public AfkPauseCommand() {
        super("afkpause", "Shows or sets the minutes all players must be idle before auto-pausing (0 = off)",
            PermissionLevel.ADMIN, false, new CmdParameter("minutes", new IntParameterHandler(null), true));
    }

    @Override
    public void runModular(Client client, Server server, ServerClient serverClient, Object[] args, String[] errors, CommandLog logs) {
        Integer minutes = (Integer) args[0];
        if (minutes != null) {
            CoopPauseMod.SETTINGS.afkMinutes = Math.max(0, minutes);
            // Same split as the engine's pausewhenempty: a dedicated server keeps server settings, a host keeps
            // client settings. Both also write every mod's settings file.
            if (server.isHosted() || server.isSingleplayer()) {
                Settings.saveClientSettings();
            } else {
                Settings.saveServerSettings();
            }
        }

        int current = CoopPauseMod.SETTINGS.afkMinutes;
        if (current <= 0) {
            logs.add(new LocalMessage("cooppause", "afkoff"));
        } else {
            logs.add(new LocalMessage("cooppause", "afkon", "minutes", String.valueOf(current)));
        }
    }
}
