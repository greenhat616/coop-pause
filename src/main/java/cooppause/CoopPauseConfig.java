package cooppause;

import customsettingslib.components.CustomModSetting;
import customsettingslib.components.settings.IntSetting;
import customsettingslib.settings.CustomModSettings;
import necesse.engine.Settings;
import necesse.engine.modLoader.ModSettings;
import necesse.engine.network.server.Server;

/**
 * Settings, shown on Mod Settings Lib's page under Settings > Mod settings and saved to
 * {@code cfg/mods/a632079.cooppause.cfg}.
 *
 * <p><b>Why the server reads the setting object directly, not through the lib's getter.</b> The lib marks server
 * settings read-only while in a world ({@code CustomModSetting.isEnabled()} is true only in the main menu) and
 * sends their values to a client once, when it spawns. Its getter returns that once-synced copy whenever the
 * current state is {@code MainGame}, which on a host includes the server thread. The pause menu changes the
 * value live through {@link PauseActions}, so reading the copy would miss those changes. Clients learn the
 * live value from {@code PacketCoopPauseConfig} instead.
 */
public final class CoopPauseConfig {

    public static final String AFK_MINUTES = "cooppauseafkminutes";
    public static final int MAX_AFK_MINUTES = 120;
    private static final int DEFAULT_AFK_MINUTES = 5;

    private static CustomModSettings settings;

    private CoopPauseConfig() {
    }

    public static ModSettings create() {
        settings = new CustomModSettings()
            .addTextSeparator("cooppausesection")
            .addIntSetting(AFK_MINUTES, DEFAULT_AFK_MINUTES, 0, MAX_AFK_MINUTES, IntSetting.DisplayMode.INPUT_BAR)
            .addParagraph("cooppauseafkhint");
        settings.addServerSettings(AFK_MINUTES);
        return settings;
    }

    /** The server's own value; see the class comment for why this bypasses the lib's getter. */
    public static int getAfkMinutes() {
        Object value = afkSetting().getValue();
        return value instanceof Integer ? (Integer) value : DEFAULT_AFK_MINUTES;
    }

    @SuppressWarnings("unchecked")
    public static void setAfkMinutes(Server server, int minutes) {
        ((CustomModSetting<Integer>) afkSetting()).changeValue(Math.max(0, Math.min(MAX_AFK_MINUTES, minutes)));
        // Same split as the engine's pausewhenempty: a dedicated server keeps server settings, a host keeps
        // client settings. Both also write every mod's settings file.
        if (server.isHosted() || server.isSingleplayer()) {
            Settings.saveClientSettings();
        } else {
            Settings.saveServerSettings();
        }
    }

    private static CustomModSetting<?> afkSetting() {
        return settings.settingsMap.get(AFK_MINUTES);
    }
}
