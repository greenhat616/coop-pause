package cooppause;

import necesse.engine.GlobalData;
import necesse.engine.localization.Localization;
import necesse.engine.network.client.Client;
import necesse.engine.state.MainGame;
import necesse.engine.state.State;
import necesse.gfx.GameColor;

/**
 * Client-side half of a pause: freezes local simulation and, for a manual pause, holds the game in its pause menu.
 *
 * <p>Holding the menu is what blocks input. {@code MainGame} only runs player controls, attacks and item use
 * while {@code isRunning()}, and the form manager hides the toolbar and inventory when it is not; the same
 * state the engine enters on focus loss. Chat stays usable, so {@code /unpause} can still be typed.
 *
 * <p>An AFK pause does not hold the menu: the player must be able to wake the world just by pressing something
 * (see {@link ClientActivity}). Actions taken in that moment are dropped by the server's packet filter.
 *
 * <p>Methods are public because the patches that call them are inlined into game classes.
 */
public final class ClientPauseState {

    private static Client pausedClient;
    private static PauseState.Mode mode;
    /** Whether this class put the game into its menu, and so must take it out again on resume. */
    private static boolean holdingMenu;
    /** Whether the game was running when the menu was first held, so resuming does not close a menu the player opened. */
    private static boolean resumeRunning = true;
    private static long lastBlockedHint;

    private ClientPauseState() {
    }

    public static boolean isPaused(Client client) {
        return client != null && pausedClient == client;
    }

    public static boolean isPaused(Client client, PauseState.Mode expected) {
        return isPaused(client) && mode == expected;
    }

    public static void pause(Client client, PauseState.Mode newMode) {
        if (!isPaused(client)) {
            holdingMenu = false;
        }

        pausedClient = client;
        mode = newMode;
        client.pause();

        MainGame mainGame = mainGameOf(client);
        if (newMode == PauseState.Mode.MANUAL && !holdingMenu) {
            resumeRunning = mainGame == null || mainGame.isRunning();
            holdingMenu = true;
        }

        enforce(mainGame);
    }

    public static void resume(Client client) {
        boolean releaseMenu = isPaused(client) && holdingMenu && resumeRunning;
        if (isPaused(client)) {
            pausedClient = null;
            mode = null;
            holdingMenu = false;
        }

        client.resume();
        MainGame mainGame = mainGameOf(client);
        if (releaseMenu && mainGame != null) {
            mainGame.setRunning(true);
        }
    }

    /**
     * Puts the game back into its menu if anything took it out during a manual pause.
     *
     * <p>Called every frame as well as on pause, because a player who joins a paused world receives the pause
     * before {@code MainGame} exists, and a running state can come from paths other than {@code setRunning}.
     */
    public static void enforce(MainGame mainGame) {
        if (mainGame == null || !mainGame.isRunning()) {
            return;
        }

        Client client = mainGame.getClient();
        if (!isPaused(client) || !holdingMenu) {
            return;
        }

        // Same order as the engine's own pause on focus loss: a container form would stay usable under the menu.
        if (client.hasFocusForm()) {
            client.closeContainer(true);
        }

        mainGame.setRunning(false);
    }

    /** Whether {@code MainGame.setRunning(true)} must be refused, telling the player why at most every few seconds. */
    public static boolean blocksResume(Client client) {
        if (!isPaused(client) || !holdingMenu) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastBlockedHint > 3000L) {
            lastBlockedHint = now;
            client.chat.addMessage(GameColor.YELLOW.getColorCode() + Localization.translate("cooppause", "stillpaused"));
        }

        return true;
    }

    private static MainGame mainGameOf(Client client) {
        State state = GlobalData.getCurrentState();
        if (state instanceof MainGame && ((MainGame) state).getClient() == client) {
            return (MainGame) state;
        }

        return null;
    }
}
