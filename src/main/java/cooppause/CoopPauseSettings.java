package cooppause;

import necesse.engine.modLoader.ModSettings;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

/** Saved to {@code cfg/mods/a632079.cooppause.cfg} by the engine. */
public class CoopPauseSettings extends ModSettings {

    /** Minutes every player must be idle before the world pauses itself; 0 disables it. */
    public int afkMinutes = 5;

    @Override
    public void addSaveData(SaveData data) {
        data.addInt("afkMinutes", afkMinutes);
    }

    @Override
    public void applyLoadData(LoadData data) {
        afkMinutes = Math.max(0, data.getInt("afkMinutes", afkMinutes, false));
    }
}
