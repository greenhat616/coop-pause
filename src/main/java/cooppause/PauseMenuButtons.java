package cooppause;

import cooppause.packets.PacketCoopPauseRequest;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.client.Client;
import necesse.engine.state.MainGame;
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.FormManager;
import necesse.gfx.forms.MainGameFormManager;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.localComponents.FormLocalLabel;
import necesse.gfx.forms.components.localComponents.FormLocalSlider;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.presets.PauseMenuForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

/**
 * Adds a pause/resume button and a settings page to the game's pause menu.
 *
 * <p>The pause menu is the natural home: during a manual pause every player is held in it, so the resume button
 * is right where they are. Players without {@link PauseActions#REQUIRED_PERMISSION} see the buttons disabled
 * with a tooltip; the server checks permission again in {@link PacketCoopPauseRequest}.
 */
public final class PauseMenuButtons {

    private static final int BUTTON_HEIGHT = 40;

    /** The permission the menu was last built for, to rebuild when an admin grants or revokes it. */
    private static int builtForPermission = -1;

    private PauseMenuButtons() {
    }

    /** Called after {@code PauseMenuForm.setupMainForm()}, which clears and rebuilds the menu's main column. */
    public static void addTo(PauseMenuForm menu) {
        Client client = menu.client;
        if (client == null || client.isSingleplayer()) {
            return;
        }

        boolean allowed = hasPermission(client);
        builtForPermission = permissionOf(client);
        GameMessage deniedTip = allowed ? null : new LocalMessage("cooppause", "nopermission");
        Form form = menu.mainForm;
        int width = form.getWidth() - 8;
        FormFlow flow = new FormFlow(form.getHeight() + 10);

        boolean paused = ClientPauseState.isPaused(client);
        FormLocalTextButton toggle = form.addComponent(new FormLocalTextButton(
            new LocalMessage("cooppause", paused ? "resumebutton" : "pausebutton"), deniedTip,
            4, flow.next(BUTTON_HEIGHT), width, FormInputSize.SIZE_32_TO_40, paused ? ButtonColor.GREEN : ButtonColor.BASE));
        toggle.setActive(allowed);
        toggle.onClicked(e -> {
            e.from.startCooldown(500);
            send(client, paused ? PacketCoopPauseRequest.Action.UNPAUSE : PacketCoopPauseRequest.Action.PAUSE, 0);
        });

        FormLocalTextButton settings = form.addComponent(new FormLocalTextButton(
            new LocalMessage("cooppause", "settingsbutton"), deniedTip,
            4, flow.next(BUTTON_HEIGHT), width, FormInputSize.SIZE_32_TO_40, ButtonColor.BASE));
        settings.setActive(allowed);
        settings.onClicked(e -> openSettings(menu));

        form.setHeight(flow.next());
        recenter(form);
    }

    /** Rebuilds the menu if it exists, so the buttons follow pause state, settings and permission changes. */
    public static void refresh(MainGame mainGame) {
        PauseMenuForm menu = menuOf(mainGame);
        if (menu != null) {
            menu.setupMainForm();
        }
    }

    /** Called every frame: permission can change while the menu is open, through another player's command. */
    public static void tick(MainGame mainGame) {
        if (mainGame != null && builtForPermission != -1 && permissionOf(mainGame.getClient()) != builtForPermission) {
            refresh(mainGame);
        }
    }

    private static void openSettings(PauseMenuForm menu) {
        Client client = menu.client;
        Form form = new Form("cooppausesettings", 400, 200);
        FormFlow flow = new FormFlow(10);
        form.addComponent(new FormLocalLabel(new LocalMessage("cooppause", "settingstitle"), new FontOptions(20), FormLabel.ALIGN_MID,
            form.getWidth() / 2, flow.next(30)));

        int current = ClientPauseState.getServerAfkMinutes();
        int start = current < 0 ? 0 : current;
        FormLocalSlider slider = form.addComponent(new FormLocalSlider(new LocalMessage("cooppause", "afkslider"),
            10, flow.next(), start, 0, CoopPauseConfig.MAX_AFK_MINUTES, form.getWidth() - 20));
        flow.next(slider.getTotalHeight() + 10);
        form.addComponent(new FormLocalLabel(new LocalMessage("cooppause", "afksliderhint"), new FontOptions(12), FormLabel.ALIGN_LEFT,
            10, flow.next(20), form.getWidth() - 20));

        int buttonWidth = form.getWidth() / 2 - 6;
        int buttonY = flow.next(BUTTON_HEIGHT);
        form.addComponent(new FormLocalTextButton(new LocalMessage("ui", "backbutton"), 4, buttonY, buttonWidth))
            .onClicked(e -> menu.makeCurrent(menu.main));
        FormLocalTextButton apply = form.addComponent(new FormLocalTextButton(new LocalMessage("cooppause", "applybutton"),
            form.getWidth() / 2 + 2, buttonY, buttonWidth));
        apply.setActive(hasPermission(client));
        apply.onClicked(e -> {
            send(client, PacketCoopPauseRequest.Action.SET_AFK_MINUTES, slider.getValue());
            menu.makeCurrent(menu.main);
        });

        form.setHeight(flow.next(4));
        recenter(form);
        // Temporary, so it is removed again whenever the menu switches away from it.
        menu.addAndMakeCurrentTemporary(form);
    }

    private static void send(Client client, PacketCoopPauseRequest.Action action, int value) {
        client.network.sendPacket(new PacketCoopPauseRequest(action, value));
    }

    private static boolean hasPermission(Client client) {
        return permissionOf(client) >= PauseActions.REQUIRED_PERMISSION.getLevel();
    }

    private static int permissionOf(Client client) {
        return client == null || client.getPermissionLevel() == null ? 0 : client.getPermissionLevel().getLevel();
    }

    private static void recenter(Form form) {
        GameWindow window = WindowManager.getWindow();
        if (window != null) {
            form.setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
        }
    }

    private static PauseMenuForm menuOf(MainGame mainGame) {
        if (mainGame == null) {
            return null;
        }

        FormManager formManager = mainGame.getFormManager();
        return formManager instanceof MainGameFormManager ? ((MainGameFormManager) formManager).pauseMenu : null;
    }
}
