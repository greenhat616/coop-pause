package cooppause.patches;

import cooppause.ClientPauseState;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.state.MainGame;
import necesse.engine.window.GameWindow;
import net.bytebuddy.asm.Advice;

/** Applies a pause that arrived before {@code MainGame} existed, such as for a player joining a paused world. */
@ModMethodPatch(target = MainGame.class, name = "frameTick", arguments = {TickManager.class, GameWindow.class})
public class MainGameFrameTickPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.This MainGame mainGame) {
        ClientPauseState.enforce(mainGame);
    }
}
