# 发布到 Steam 创意工坊

Necesse 没有独立的上传工具。上传是在**游戏内**完成的：用开发模式启动游戏，然后在「Mod」页面对开发 Mod 点「上传到 Steam 创意工坊」。以下流程依据反编译的 `SteamDevModProvider` 和 `LoadedDevMod`（游戏 1.3.3）整理。

## 材料清单

| 文件 | 用途 |
|---|---|
| `src/main/resources/preview.png` | 640×640 预览图。会被打进 jar 的 `resources/preview.png`，游戏上传时自动用作创意工坊封面。**jar 里没有这张图时，游戏不会显示上传按钮**（`mod.preview != null` 是显示条件） |
| `docs/workshop/description.bbcode` | 创意工坊描述（Steam BBCode，中英双语，约 3300 字符；游戏上传框上限 5000 字符） |
| `docs/workshop/images/01-pause.png` 等 3 张 | 1280×720 功能介绍图，上传后在 Steam 网页上作为附加图片添加 |
| `tools/make_workshop_images.py` | 重新生成以上图片：`python tools/make_workshop_images.py .`（需要 Pillow 和 Windows 自带字体） |

> 这 3 张介绍图是示意图，不是游戏截图（菜单那张是抽象画的按钮列）。建议发布前在游戏里补几张真实截图：暂停菜单里的两个按钮、Coop Pause 设置面板、聊天框里的暂停提示。

## 首次发布

1. **构建**（确认 `mods/` 目录中有 Mod Settings Lib 的 jar，见 README）：
   ```bash
   gradle buildModJar
   ```
   确认 `build/jar/` 目录里**只有一个 jar**。游戏要求开发 Mod 目录中除 `preview.png` 外只能有一个 jar 文件，不能有子目录（`validateDevFolderAndReturnJar`），否则会提示「开发模组必须是一个目录，里面有一个 jar 文件」。`buildModJar` 每次会清掉旧 jar，正常情况下满足要求。

2. **以开发模式启动游戏**（Steam 必须正在运行，并已订阅 Mod Settings Lib）：
   ```bash
   gradle runClient
   ```
   这个任务等价于 `-dev -mod "build/jar/"`。

3. 在主菜单进入 **Mod**，选中 **Coop Pause**（来源显示为开发模组），点 **「上传到 Steam 创意工坊」**。

4. 在弹出的窗口中：
   - 选择 **「创建新项目」**。
   - 勾选「更新描述」，把 `description.bbcode` 的内容粘贴进去。游戏默认会填 `mod.info` 里的简短描述。
   - 标签建议勾选 **New features**；可以再加 **Interface**（加了暂停菜单按钮）。游戏只提供这几个：Translation / Texture pack / Interface / New features / New content / Tweaks / Miscellaneous。
   - 点「继续」，同意 Steam 创意工坊服务条款，并确认内容是你自己创作的。

5. 看到「成功地创建/更新了模组」后，到 Steam 网页上完成剩下的设置（Steam 客户端 → 社区 → 创意工坊 → 你的文件，或者 Necesse 创意工坊 → 「你的创意工坊文件」）：
   - **必需物品**：「添加/移除必需物品」→ 添加 **Mod Settings Lib**（`3558238376`）。订阅者会被提示同时订阅它。
   - **图片和视频**：「添加/编辑图片和视频」→ 上传 `docs/workshop/images/` 里的 3 张图和你的游戏截图。
   - **可见性**：确认设为「公开」（或先设为「仅好友」测试）。
   - 新上传的物品需要接受过 Steam 创意工坊法律协议，否则页面会提示，并且只有你自己能看到。

6. 记下物品 ID（网址中的 `?id=...`），补到 README 里。

## 发布更新

1. 在 `build.gradle` 中提高 `modVersion`，例如 `1.3` → `1.4`。版本号变化后，服务端和客户端的 Mod 一致性检查会要求所有玩家都更新。
2. `gradle buildModJar`，然后 `gradle runClient`。
3. Mod → Coop Pause → 上传 → 在下拉框中**选择已有的项目**（显示为「Coop Pause (物品ID)」）。
4. 「更新描述」：如果你在 Steam 网页上改过描述，**取消勾选**，否则会被覆盖。
5. 游戏会自动把更新说明写成 `Mod version <版本> for game version <游戏版本>`。需要更详细的说明，可以在 Steam 网页的「更新日志」里编辑。

## 首个版本的更新日志参考

```
1.3
- /pause and /unpause freeze the world while everyone stays connected
- Input is blocked while paused (pause menu held, server drops player actions)
- AFK auto-pause when all players are idle, any key resumes; /afkpause [minutes]
- Pause menu buttons for admins: pause/resume and live settings
- Settings page via Mod Settings Lib
```

## 发布前自检

- [ ] 在 Host World 中双人实测：暂停、恢复、暂停期间聊天框输入 `/unpause`、暂停期间加入、挂机自动暂停后按键唤醒
- [ ] 非管理员看到的是灰色按钮，提示文字正确
- [ ] 独立服务器（`gradle runServer` 或 `StartServer-nogui.bat`）可以正常加载本 Mod 和 Mod Settings Lib
- [ ] `build/jar/` 中只有一个 jar，jar 内包含 `resources/preview.png` 和 `mod.info`（其中 `dependencies = [aizsave.modsettingslib]`）
