package cooppause.patches;

import cooppause.ClientPauseState;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.state.MainGame;
import net.bytebuddy.asm.Advice;

/** Keeps the pause menu open while paused: Esc and the menu's resume button both go through here. */
@ModMethodPatch(target = MainGame.class, name = "setRunning", arguments = {boolean.class})
public class MainGameSetRunningPatch {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean onEnter(@Advice.This MainGame mainGame, @Advice.Argument(0) boolean running) {
        return running && ClientPauseState.blocksResume(mainGame.getClient());
    }
}
