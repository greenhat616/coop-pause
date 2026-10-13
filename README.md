# Coop Pause

Necesse 1.3.3 多人暂停 Mod：在玩家保持连接的情况下冻结整个世界。

## 指令

| 指令 | 权限 | 作用 |
|---|---|---|
| `/pause` | ADMIN | 暂停世界（怪物、NPC、作物、时间、弹幕），并通知所有客户端停止本地模拟 |
| `/unpause` | ADMIN | 恢复由 `/pause` 发起的暂停 |

独立服务器控制台中可以不带 `/` 直接输入。Host World 的房主是 OWNER，可以直接使用；其他玩家需要房主授予 ADMIN 权限后才能使用。

## 原理

调用引擎自带的 `Server.pause()` / `resume()`，不依赖任何方法补丁。详见 [docs/server-pause-research.md](docs/server-pause-research.md)。

- 服务端：`isPaused()` 为真时跳过 `world.serverTick()` 和 `world.frameTick()`；网络、Ping 保活、聊天命令照常处理。
- 客户端：通过 `PacketCoopPauseState` 调用 `Client.pause()`，停止本地预测。暂停期间新加入的玩家会补收到暂停状态。

## 构建与运行

```bash
# 需要 JDK 17+ 来运行 Gradle（字节码目标为 Java 8）
gradle buildModJar                 # 输出 build/jar/CoopPause-1.3.3-1.0.jar
gradle runClient / runDevClient    # 双开客户端测试 Host World
gradle runServer                   # 独立服务器
# 游戏目录默认 D:/Program Files (x86)/Steam/steamapps/common/Necesse，可用 -PnecesseGameDir=... 覆盖
```

所有玩家都必须安装本 Mod（`clientside=false`）。

## 已知限制（初版）

- 暂停期间**没有屏蔽玩家输入**：玩家仍可以攻击、放置物品、操作容器，服务端也仍会处理这些数据包。计划在下一版加入客户端遮罩和服务端数据包白名单。
- 暂停期间世界时间不前进，自动存档也会推迟到恢复之后。
- 单人模式下拒绝执行 `/pause`，请直接用菜单暂停。
