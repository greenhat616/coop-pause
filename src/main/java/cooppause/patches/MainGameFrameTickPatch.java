package cooppause.patches;

import cooppause.ClientActivity;
import cooppause.ClientPauseState;
import cooppause.PauseMenuButtons;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.state.MainGame;
import necesse.engine.window.GameWindow;
import net.bytebuddy.asm.Advice;

/**
 * Per-frame client hooks: applying a pause that arrived before {@code MainGame} existed (a player joining a
 * paused world), following permission changes in the pause menu, and reporting player input for AFK detection.
 */
@ModMethodPatch(target = MainGame.class, name = "frameTick", arguments = {TickManager.class, GameWindow.class})
public class MainGameFrameTickPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.This MainGame mainGame) {
        ClientPauseState.enforce(mainGame);
        PauseMenuButtons.tick(mainGame);
    }

    @Advice.OnMethodExit
    static void onExit(@Advice.This MainGame mainGame, @Advice.Argument(1) GameWindow window) {
        ClientActivity.observe(mainGame, window);
    }
}
