package cooppause;

import cooppause.packets.PacketCoopActivity;
import necesse.engine.input.InputEvent;
import necesse.engine.input.controller.ControllerInput;
import necesse.engine.network.client.Client;
import necesse.engine.state.MainGame;
import necesse.engine.window.GameWindow;

/**
 * Tells the server when this player is at the keyboard, for {@link AfkMonitor}.
 *
 * <p>Counts key presses, mouse clicks, scrolling and controller input, and only while the window has focus.
 * Mouse movement is ignored: the cursor passing over an unfocused window on another monitor is not someone
 * coming back to the game.
 *
 * <p>Throttled, because the server only needs minute resolution, except during an AFK pause, where the first
 * press should wake the world at once.
 */
public final class ClientActivity {

    private static final long REPORT_INTERVAL_MS = 5_000L;
    private static final long WAKE_INTERVAL_MS = 500L;

    private static long lastReport;

    private ClientActivity() {
    }

    /** Called at the end of every {@code MainGame.frameTick}, on the client thread. */
    public static void observe(MainGame mainGame, GameWindow window) {
        if (window == null || !window.isFocused() || !hasInput(window)) {
            return;
        }

        Client client = mainGame.getClient();
        if (client == null || client.getSlot() == -1) {
            return;
        }

        long now = System.currentTimeMillis();
        long interval = ClientPauseState.isPaused(client, PauseState.Mode.AFK) ? WAKE_INTERVAL_MS : REPORT_INTERVAL_MS;
        if (now - lastReport >= interval) {
            lastReport = now;
            client.network.sendPacket(new PacketCoopActivity());
        }
    }

    private static boolean hasInput(GameWindow window) {
        for (InputEvent event : window.getInput().getEvents()) {
            if (event.state && (event.isKeyboardEvent() || event.isMouseClickEvent())) {
                return true;
            }

            if (event.isMouseWheelEvent()) {
                return true;
            }
        }

        return !ControllerInput.getEvents().isEmpty();
    }
}
