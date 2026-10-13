package cooppause;

import cooppause.packets.PacketCoopPauseConfig;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Everything a player may ask of this mod, shared by the chat commands and the pause menu buttons, so both
 * enforce the same rules. Server thread only.
 */
public final class PauseActions {

    /** Needed to pause, resume or change settings, from chat or from the menu. */
    public static final PermissionLevel REQUIRED_PERMISSION = PermissionLevel.ADMIN;

    private PauseActions() {
    }

    /** The server console, or a player with at least {@link #REQUIRED_PERMISSION}. */
    public static boolean hasPermission(ServerClient client) {
        return client == null || client.getPermissionLevel().getLevel() >= REQUIRED_PERMISSION.getLevel();
    }

    /** @return a message explaining why nothing happened, or null if the world was paused */
    public static GameMessage pause(Server server, ServerClient by) {
        if (server.isSingleplayer()) {
            // Singleplayer already pauses with the menu, and the menu would resume over this anyway.
            return new LocalMessage("cooppause", "singleplayer");
        }

        // An AFK pause may be turned into a manual one, so players coming back do not wake the world.
        if (PauseState.isPaused(server, PauseState.Mode.MANUAL)) {
            return new LocalMessage("cooppause", "alreadypaused", "name", PauseState.getPausedBy());
        }

        String name = nameOf(by);
        PauseState.pause(server, name, PauseState.Mode.MANUAL);
        System.out.println("World paused by " + name);
        return null;
    }

    /** @return a message explaining why nothing happened, or null if the world was resumed */
    public static GameMessage unpause(Server server, ServerClient by) {
        if (!PauseState.isPaused(server)) {
            return new LocalMessage("cooppause", "notpaused");
        }

        String name = nameOf(by);
        PauseState.unpause(server, name);
        System.out.println("World resumed by " + name);
        return null;
    }

    /**
     * Applies and saves the AFK threshold, then tells every client so open settings menus show the new value.
     *
     * <p>Takes effect at once: {@link AfkMonitor} reads the setting on every check rather than caching it.
     */
    public static GameMessage setAfkMinutes(Server server, int minutes) {
        CoopPauseConfig.setAfkMinutes(server, minutes);
        server.network.sendToAllClients(new PacketCoopPauseConfig(CoopPauseConfig.getAfkMinutes()));
        return describeAfk();
    }

    public static GameMessage describeAfk() {
        int minutes = CoopPauseConfig.getAfkMinutes();
        return minutes <= 0
            ? new LocalMessage("cooppause", "afkoff")
            : new LocalMessage("cooppause", "afkon", "minutes", String.valueOf(minutes));
    }

    private static String nameOf(ServerClient client) {
        return client == null ? "Server" : client.getName();
    }
}
