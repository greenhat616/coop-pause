# Coop Pause

Necesse 1.3.3 多人暂停 Mod：在玩家保持连接的情况下冻结整个世界。

## 指令

| 指令 | 权限 | 作用 |
|---|---|---|
| `/pause` | ADMIN | 暂停世界（怪物、NPC、作物、时间、弹幕），并通知所有客户端停止本地模拟 |
| `/unpause` | ADMIN | 恢复由 `/pause` 发起的暂停 |

独立服务器控制台中可以不带 `/` 直接输入。Host World 的房主是 OWNER，可以直接使用；其他玩家需要房主授予 ADMIN 权限后才能使用。

## 原理

冻结本身调用引擎自带的 `Server.pause()` / `resume()`；输入屏蔽用到 3 个方法补丁。详见 [docs/server-pause-research.md](docs/server-pause-research.md)。

- 服务端：`isPaused()` 为真时跳过 `world.serverTick()` 和 `world.frameTick()`；网络、Ping 保活、聊天命令照常处理。
- 客户端：通过 `PacketCoopPauseState` 调用 `Client.pause()`，停止本地预测。暂停期间新加入的玩家会补收到暂停状态。
- 输入屏蔽：
  - 客户端：暂停时关闭已打开的容器，并让游戏停在暂停菜单（与失去焦点时自动暂停是同一个状态，玩家操作、工具栏、背包都会被禁用），聊天框仍然可以用。`MainGame.setRunning` 补丁拦截 Esc 键和菜单里的「继续」按钮；`MainGame.frameTick` 补丁负责让暂停期间加入的玩家也停在菜单。
  - 服务端：`Server.processPacket` 补丁在暂停期间丢弃攻击、物品使用、交互、容器、合成、坐骑、技能、传送、直接修改地图等玩家操作包，恢复时在控制台输出丢弃的数量。连接、区域加载、聊天、Ping、移动包照常处理。

## 构建与运行

```bash
# 需要 JDK 17+ 来运行 Gradle（字节码目标为 Java 8）
gradle buildModJar                 # 输出 build/jar/CoopPause-1.3.3-1.1.jar
gradle runClient / runDevClient    # 双开客户端测试 Host World
gradle runServer                   # 独立服务器
# 游戏目录默认 D:/Program Files (x86)/Steam/steamapps/common/Necesse，可用 -PnecesseGameDir=... 覆盖
```

所有玩家都必须安装本 Mod（`clientside=false`）。

## 已知限制（初版）

- 暂停期间玩家不能关掉暂停菜单。只能打开设置或者退出游戏；恢复请在聊天框输入 `/unpause`。
- 服务端的丢弃列表是按数据包类名逐个列出的（见 `PausePacketFilter`），游戏更新新增操作包后需要同步补充。
- 暂停期间世界时间不前进，自动存档也会推迟到恢复之后。
- 单人模式下拒绝执行 `/pause`，请直接用菜单暂停。
