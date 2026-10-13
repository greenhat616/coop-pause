package cooppause.patches;

import cooppause.PauseMenuButtons;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.PauseMenuForm;
import net.bytebuddy.asm.Advice;

/** Appends this mod's buttons each time the pause menu rebuilds its main column. */
@ModMethodPatch(target = PauseMenuForm.class, name = "setupMainForm", arguments = {})
public class PauseMenuSetupPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This PauseMenuForm menu) {
        PauseMenuButtons.addTo(menu);
    }
}
