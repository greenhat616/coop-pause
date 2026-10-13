# Necesse 多人服务端暂停（CoopPause）调研报告

- 日期：2026-10-13
- 目标：在 Host World（游戏内主持世界）与独立服务器上，实现「玩家在线时整个世界冻结、网络连接保持、可随时恢复」
- 调研对象：
  - 本机游戏：`D:\Program Files (x86)\Steam\steamapps\common\Necesse`，版本 **1.3.3**（`Server.addClient` 中硬编码版本校验 `"1.3.3"`），自带 JRE `Temurin-17.0.19`，ByteBuddy `1.12.8`
  - 参考对话：ChatGPT 分享链接（关于多人暂停 Mod 的可行性讨论）
  - 参考项目：[EliasVahlberg/necesse-headless-harness](https://github.com/EliasVahlberg/necesse-headless-harness)（MIT，Alpha）
  - 官方模板：[DrFair/ExampleMod](https://github.com/DrFair/ExampleMod)
- 方法：使用 Vineflower 1.11.1（运行于 JDK 25）反编译整个 `Necesse.jar`（6493 个类，0 个失败）；逐个阅读 harness 的补丁源码，并与反编译结果交叉核对。

> 下文中的源码路径均指**反编译产物**（`necesse/...`），行号对应 Vineflower 的输出，仅作定位参考，不等同于官方源码。

---

## 1. 结论摘要

| # | 结论 | 置信度 |
|---|------|--------|
| 1 | **引擎本身已经有服务端暂停机制**：`Server.pause()` / `resume()` / `isPaused()`。暂停后会跳过世界模拟，网络、Ping 保活、命令处理照常运行。单人模式的 Esc 暂停和 `pausewhenempty` 用的都是这一套。 | 高（源码确认） |
| 2 | 因此 CoopPause **不需要**照搬 harness 那 10 多个 ByteBuddy 补丁。harness 需要的是「确定性、逐 tick 推进」，而我们只需要「停住，然后恢复」。核心暂停逻辑只要调用一次 `server.pause()`。 | 高 |
| 3 | 真正要做的工作在三处：① **把暂停状态同步给远程客户端**（否则客户端仍在本地预测，画面里的怪物会继续走）；② **在暂停期间屏蔽玩家输入和服务端的玩法类数据包**（原版暂停期间仍会处理这些包，玩家依然能放方块、开箱子）；③ **权限控制和 UI 提示**。 | 高 |
| 4 | `/pausewhenempty` 对 Host World **基本没用**：房主本人一直在线，在线人数永远不会归零。 | 高 |
| 5 | 原版要求服务端和客户端的非 `clientside` Mod 完全一致（不一致时会弹出 Mod 不匹配提示）。因此所有玩家都要安装 CoopPause，正好可以借此注册自定义数据包。 | 高 |
| 6 | 总体可行性：**高**。最小可用版本大约是 4 个类、1 个数据包、0～1 个方法补丁。 | 中高（尚未实机验证） |

---

## 2. 参考材料的要点与核实

### 2.1 ChatGPT 对话的主张，与本次核实结果

| 对话中的说法 | 核实结果 |
|---|---|
| 多人模式下按 Esc 不会暂停 | ✅ `MainGame.setRunning()` 只有在 `client.isSingleplayer()` 为真时才会调用 `client.pause()`（`necesse/engine/state/MainGame.java:885-900`） |
| `/pausewhenempty 1` 可以在无人时暂停 | ✅ 权限为 ADMIN；默认值 `Settings.pauseWhenEmpty = true`（`Settings.java:130`）。对话没有指出它在 Host World 下不起作用（见结论 4） |
| 没有原生的 `/pause` 命令 | ✅ 命令确实没有；但对话没有发现**原生 API** `Server.pause()` 是存在的 |
| harness 通过拦截 `Server.tick()`，停止模拟但保留网络 | ✅ 属实。但这是为测试设计的方案，对我们的需求来说过重（见 §4） |
| 恢复指令可能永远执行不了 | ⚠️ 这只对 harness 自己的命令队列成立（队列原本在 tick 里消费）。原版聊天命令走 `PacketChatMessage`，在 `frameTick` 的收包循环里处理，**暂停期间同样会执行**（`PacketChatMessage.java:54` → `Server.sendCommand`） |
| 目标版本 1.3.3、Java 8 语言级别、Gradle 模板 | ✅ ExampleMod 中 `gameVersion = "1.3.3"`、`sourceCompatibility = 1.8`，并提供 `runClient` / `runDevClient` / `runServer` / `buildModJar` 任务 |
| 存档位置 `%APPDATA%\Necesse\saves\` | 未核实（不影响本方案） |

### 2.2 harness 的机制（阅读源码后的总结）

harness 的目标是测试时**按需逐 tick 推进**，所以它打了一整套补丁：

| 补丁 | 作用 | CoopPause 是否需要 |
|---|---|---|
| `ServerTickPatch` → `Server.tick()` | 根据配额决定是否执行游戏 tick | ❌ 原生 `isPaused()` 已经能跳过世界 tick |
| `ServerFrameTickPatch` → `Server.frameTick(TickManager)` exit | 每帧消费命令队列，并抓取 `TickManager` | ❌ 原版聊天命令本来就在 frameTick 中执行 |
| `WorldFrameTickPatch` → `World.frameTick` | 冻结世界时钟和实体移动 | ❌ 原生暂停已经跳过 `world.frameTick` |
| `TickManager*Patch`（6 个） | 改写 `getDelta`、`getTotalTicks`、`getTick` 等，让「授予的 tick」等于游戏时间 | ❌ 只有逐 tick 推进时才需要；整体暂停不需要 |
| `LevelServerTickPatch` | 统计 tick 次数 | ❌ |
| `ServerAutoSavePatch` | 关闭自动存档 | ❌（见 §3.4） |
| `ServerThreadTasks` | 控制台线程的任务转交到服务端线程执行，避免锁顺序反转 | ⚠️ 思路值得借鉴：**修改游戏状态的代码必须在 Server Thread 上运行**。聊天命令本来就在 Server Thread 上；如果以后从控制台或别的线程触发暂停，需要同样转交 |

harness 中值得吸取的经验：
- 补丁通过 `@ModMethodPatch(target, name, arguments)` 绑定**精确的方法签名**，游戏更新后容易失效。补丁越少越好。
- ByteBuddy 的 advice 会内联进目标类，advice 调用的方法必须是 `public`，否则运行时抛出 `IllegalAccessError`。
- 不要用 `TickManager.globalTimeMod` 来实现暂停或加速：它也会缩放 `getDelta()`，导致移动积分出错。
- 游戏只允许加载 1 个 dev mod；`LoadedMod.loadClasses` 会主动加载 jar 内所有 `.class`。

---

## 3. 游戏程序研究：服务端循环与暂停的实际行为

### 3.1 线程与循环结构

```
ServerTickThread ("Server Thread")            necesse/engine/server/ServerTickThread.java
 └─ ServerGameLoop.update()  (maxFPS=60)      necesse/engine/gameLoop/ServerGameLoop.java
     ├─ if (isGameTick())  server.tick()       ← 20 TPS，由 TickManager 按实际时间累积
     └─ 每次迭代            server.frameTick(this)
```

Host World 与独立服务器使用**同一条循环**：`Server` 的构造函数总会创建 `ServerTickThread`（`Server.java:146`）。Host World 只是 `isHosted = true`，并持有本地 `Client` 引用（`makeHosted`）。所以对话里担心的「Host World 不走同一个循环」**不成立**。

### 3.2 `Server.frameTick`（每帧）—— `Server.java:278-298`

```java
packetManager.tickNetworkManager();            // 网络收发：始终执行
for (p = nextPacket(); ...) processPacket(p);  // 处理所有数据包（含聊天命令、玩家操作）：始终执行
if (!isPaused()) {
    world.frameTick(tickManager);              // 世界时钟 + 实体移动
    for (client) client.tickMovement(delta);   // 玩家移动积分
}
```

### 3.3 `Server.tick()`（每个游戏 tick）—— `Server.java:300-384`

始终执行的部分：
- `tickSaveHandler`、`tickAutoSave`
- `network.tickUnknownPacketTimeouts()`
- 对每个客户端调用 `client.tickTimeConnected()`：**每 2 秒发送一次 Ping、每 10 秒发送一次 `PacketWorldData`、检测超时并踢出**（`ServerClient.java:1170-1213`）
- 维护 `pauseCounter`（在线人数为 0 时递增）

只在 `!isPaused()` 时执行的部分：
- `world.serverTick()`：所有 Level、怪物、NPC、作物、事件、任务
- `client.tick()`：玩家统计、`PacketPlayerSync`、区域保活等
- Level 卸载扫描

### 3.4 `isPaused()` 的判定 —— `Server.java:425-439`

```java
public boolean isPaused() {
   if (pauseForSpawnedPlayer && (isSingleplayer() || isHosted())) return true;  // 房主角色尚未生成时
   return Settings.pauseWhenEmpty && pauseCounter >= 200 ? true : this.isPaused; // 无人约 10 秒后，或手动标志
}
public void pause()  { this.isPaused = true;  }
public void resume() { this.isPaused = false; }
```

可以推出的行为：
- **网络保活不受影响**：Ping 和 WorldData 在 `tickTimeConnected` 中，暂停期间照常发送；客户端回应 Ping 是在客户端 frameTick 的收包循环中，同样不受暂停影响。远程客户端的「服务器无响应」计数（`Client.tick` 里的 `waitAttempts`，10 秒报警，60 秒断开）每收到一个包就清零，因此**不会掉线**。
- **自动存档会暂停**：`tickAutoSave` 用 `worldEntity.getTime()` 做比较，而世界时间在暂停时不前进（时间推进位于 `WorldEntity.serverFrameTick → tickTime`）。结果是长时间暂停期间不会自动存档。这是合理的，但最好在暂停前主动存一次。
- **原生暂停本身没有缺陷**：单人模式每天都在用这条路径。只是「有远程玩家在线时暂停」这个组合，开发者在原版里从未提供入口，**属于未经原版测试的状态**。

### 3.5 客户端侧 —— `necesse/engine/network/client/Client.java`

```java
public void pause()  { isPaused = true;  if (server != null) server.pause();  }   // ≈ L479
public void resume() { isPaused = false; if (server != null) server.resume(); }   // ≈ L486
```

- 客户端暂停时，`frameTick` 跳过 `worldEntity.clientFrameTick` 和 `level.frameTick`，`tick` 跳过 `levelManager.clientTick`、玩家 tick、移动包发送等。**收包循环和超时检测不受影响。**
- 远程客户端的 `server == null`，调用 `client.pause()` 只会冻结本地模拟，不会碰到服务端。
- 房主的 Client 持有本地 `server`，所以房主调用 `client.pause()` 会顺带暂停服务端。
- `Client.startedHosting()` 中会调用 `resume()`（从单人模式切换为主持时会解除暂停）。

### 3.6 关键缺口：输入没有被暂停拦截

- `MainGame` 中的玩家输入（`player.tickControls`、`submitInputEvent`、`tryAttack`，`MainGame.java:~434`）只受 `MainGame.isRunning()` 控制，**不检查 `client.isPaused()`**。单人模式下没有问题，因为暂停时会显示暂停菜单，`isRunning = false`。
- 服务端的 `processPacket` 在暂停期间**仍会执行所有玩法数据包**（放置/破坏物体、容器操作、攻击、物品使用等）。
- 结论：只调用 `server.pause()` 的话，玩家仍然能改动世界，而且服务端状态和客户端表现会出现不一致。

### 3.7 Mod 一致性

- `ModLoader.generateModsHash()`（`ModLoader.java:271-280`）只统计非 `clientside` 的 Mod；不一致时会返回 `PacketModsMismatch`。
- 所以 CoopPause 应该设为 `clientside = false`，所有玩家都安装。这样就能放心注册自定义数据包（`PacketRegistry.registerPacket(...)`，`PacketRegistry.java:587-595`）。

### 3.8 补丁机制的能力

`ModPatchClasses` 用 `isMethod().named(name).takesArguments(args)` 匹配方法（`ModPatchClasses.java:77-79`）。理论上也能匹配 **private** 方法，例如 `Server.processPacket(NetworkPacket)`。advice 是内联的，所以没有访问权限问题。**这一点需要实机验证。**

---

## 4. 方案对比

| 维度 | A. 原生暂停（推荐） | B. 照搬 harness 拦截 tick |
|---|---|---|
| 核心调用 | `server.pause()` / `resume()` | 补丁 `Server.tick` 与 `World.frameTick`，自己维护标志 |
| 方法补丁数量 | 0（核心）+ 可选 1 个（数据包过滤） | ≥ 2，若要求时钟正确则 ≥ 8 |
| 网络和 Ping 保活 | 原生保证 | 需要自己保证 `tickTimeConnected` 仍在执行（跳过整个 `Server.tick` 会**同时停掉 Ping 发送和自动存档**，还有被踢的风险） |
| 与 pausewhenempty / 单人暂停的兼容性 | 共用同一个标志，一致 | 两套机制叠加 |
| 游戏更新后的维护成本 | 低（`pause/resume/isPaused` 是 public API） | 高（绑定内部签名） |
| 适用场景 | 玩家联机 AFK | 自动化测试、逐 tick 调试 |

> 注意：方案 B 直接跳过整个 `Server.tick()`，会连带跳过 `tickTimeConnected` 中的 Ping 发送和超时检测。harness 之所以没有出问题，是因为它的测试玩家是没有 socket 的「假客户端」。真实的远程玩家在这种情况下是否会因为没有 Ping 而触发连接重置，没有经过验证。这是不选用方案 B 的另一个理由。

---

## 5. CoopPause 设计草案（方案 A）

### 5.1 组件

```
coopause/
 ├─ CoopPauseMod.java             @ModEntry：init() 注册命令与数据包；监听玩家连接事件
 ├─ PauseState.java               服务端权威状态：paused、pausedBy、since
 ├─ PacketCoopPauseState.java     S→C：广播暂停/恢复（含发起人名字），客户端据此 pause()/resume() 并切换 UI
 ├─ command/PauseCommand.java     /cooppause   ModularChatCommand，权限可配置（默认 ADMIN 或 USER）
 ├─ command/ResumeCommand.java    /coopresume
 ├─ ui/PauseOverlayForm.java      客户端遮罩：显示「已被 X 暂停」，拦截输入
 └─ patch/ServerProcessPacketPatch.java   （可选）暂停期间丢弃玩法类数据包，只放行白名单
```

### 5.2 流程

```
房主或授权玩家在聊天框输入 /cooppause
   → PacketChatMessage（frameTick 收包，暂停期间也会处理）
   → PauseCommand.runModular()  [在 Server Thread 上]
        1. 校验权限
        2. 可选：server.startFullSave(...) 或至少存一次 WorldEntity（防止长时间暂停时崩溃丢档）
        3. server.pause()
        4. network.sendToAllClients(new PacketCoopPauseState(true, name))
   → 每个客户端 processClient：
        client.pause()（房主的 Client 会顺带再调一次 server.pause()，幂等）
        显示 PauseOverlayForm，屏蔽输入
/coopresume → 顺序相反；客户端 resume() 后，等服务端的 PlayerSync / 移动包来纠正位置
```

### 5.3 必须处理的边界情况

| 场景 | 处理方式 |
|---|---|
| 暂停期间有新玩家加入 | 监听 `ServerClientConnectedEvent`（`GameEvents.addListener`），在玩家生成后补发 `PacketCoopPauseState(true)` |
| 房主客户端的 `Client.resume()` 被原版调用（如 `startedHosting`） | 恢复时会顺带调用 `server.resume()`，绕过 Mod 的状态。可在客户端收到状态包时重新应用，或给 `Client.resume` 加一个补丁检查 `PauseState` |
| 房主打开 Esc 菜单再关闭 | 多人模式下 `setRunning` 不会调用 pause/resume，无影响 |
| `pausewhenempty` 叠加 | `isPaused()` 是取「或」的关系，互不干扰；恢复时只清除自己的标志 |
| 发起人掉线 | 可选策略：保持暂停，任何管理员都能恢复；或发起人断开后自动恢复 |
| 输入屏蔽 | 客户端：遮罩 Form 吃掉输入事件，或把 `MainGame.setRunning(false)` 打开的暂停菜单作为遮罩（需验证在 `isRunning=false` 时聊天框能否打字）。服务端：用 `processPacket` 补丁做白名单（聊天 / Ping / 断开 / 设置类包放行），防止客户端绕过 |
| 玩家位置不同步 | 暂停时客户端停止发送移动包；恢复后由服务端的 PlayerSync 纠正。需要实测是否会出现瞬移或回弹 |
| 长时间暂停后恢复 | 世界时间没有前进，`delta` 由 `TickManager` 按两帧间隔计算，最大 100ms，不会出现大跳变。基于 `System.currentTimeMillis` 的逻辑（如 `ServerClient.spawnedCheckTimer`）需逐一排查 |
| 后台执行器 | `Level.executor()` / `worldEntity.executor()` 中已经提交的异步任务不会被暂停。影响较小，需要观察 |

### 5.4 可选增强

- **全员 AFK 自动暂停**：服务端统计每个 `ServerClient` 最近一次输入类数据包的时间，所有人都超过 N 分钟没有操作时自动 `pause()`，任何人有输入就 `resume()`。这正好对应「时常 AFK」的需求，而且不需要手动输入命令。
- 快捷键（客户端发请求包，服务端校验权限）、投票暂停、暂停倒计时、HUD 上的计时显示。

---

## 6. 需要做的工作与准备

### 6.1 环境准备

| 项目 | 说明 | 状态 |
|---|---|---|
| JDK | 编译：JDK 8～17 均可（目标为 1.8 字节码）；运行：游戏自带 JRE 17 | 本机已有 temurin8/11/17/25 |
| 构建 | 克隆 DrFair/ExampleMod，修改 `modID`、`modName`，`gameDirectory` 指向本机游戏目录 | 待做 |
| IDE | IntelliJ IDEA；把 `Necesse.jar` 和 `lib/` 作为依赖，便于跳转查看反编译代码 | 待做 |
| 反编译副本 | 已在临时目录生成完整源码。建议重新生成到 `G:\Programs\Java\necesse-mods\.decompiled\`（加入 .gitignore，**不要提交或分发**） | 可复现：`java -jar vineflower.jar -dgs=1 Necesse.jar out/` |
| 测试存档 | 复制一份专门用于测试的世界 | 待做 |
| 双开测试 | 用 `runClient` 和 `runDevClient` 启动两个客户端：一个主持，一个加入。Steam 联机可能需要两个账号，也可以改用独立服务器（`runServer`）加直连 IP | 待验证 |

### 6.2 工作分解（WBS）

| 阶段 | 任务 | 验收标准 | 估计工时 |
|---|---|---|---|
| P0 验证 | 用最小 Mod 注册 `/cooppause`，只调用 `server.pause()`，在 Host World 双人环境下观察 | 世界冻结、房主不掉线、远程客户端 2 分钟以上不掉线、`/coopresume` 能恢复 | 0.5 天 |
| P1 同步 | `PacketCoopPauseState` 加客户端 `client.pause()`，以及新加入玩家的补发 | 远程客户端画面中怪物和弹幕静止，新加入的玩家也处于暂停状态 | 0.5～1 天 |
| P2 输入屏蔽 | 客户端遮罩加服务端数据包白名单补丁 | 暂停期间无法放置、破坏、攻击、开箱；聊天和恢复命令可用 | 1～2 天 |
| P3 稳定性 | 测试矩阵（见 §6.3）、存档、日志 | 全部通过 | 1 天 |
| P4 增强 | 全员 AFK 自动暂停、快捷键、本地化 | 可选 | 1～2 天 |

### 6.3 测试矩阵

- 模式：Host World（Steam）、独立服务器（`StartServer-nogui.bat`）
- 暂停时长：10 秒、5 分钟、30 分钟以上（覆盖 `maxClientLatencySeconds` 的踢人阈值，以及自动存档周期）
- 暂停时的状态：Boss 战中、袭击（raid）中、NPC 工作中、弹幕在空中、玩家骑乘或死亡中、正在切换 Level（下洞）
- 恢复之后：作物生长时间、日夜时间、Buff 剩余时间是否连贯；存档并读档后是否正常
- 异常情况：暂停期间房主退出游戏、远程玩家断线重连、同时触发 `pausewhenempty`

---

## 7. 风险与未知项

| 风险 | 级别 | 缓解措施 |
|---|---|---|
| 「暂停时仍有远程玩家」是原版未测试过的组合，个别子系统可能依赖实际时间 | 中 | 先做 P0 实机验证；对 `System.currentTimeMillis` 在服务端玩法代码中的用法做专项排查 |
| `processPacket` 是 private 方法，补丁能否生效未验证 | 中 | 备选：只在客户端屏蔽，或改为补丁具体数据包的 `processServer` |
| 客户端遮罩期间的输入事件路由（聊天框是否仍可输入） | 低 | 实测；最差情况下改用快捷键请求包来恢复 |
| 游戏版本更新后签名变化 | 低 | 核心只依赖 public API；补丁类集中管理，每次版本更新后复查 |
| 反编译代码的版权 | — | 只在本地研究，仓库中不存放反编译源码 |

---

## 8. 参考来源

- 本机反编译：`Necesse.jar` 1.3.3。主要文件：
  - `necesse/engine/network/server/Server.java`
  - `necesse/engine/network/server/ServerClient.java`
  - `necesse/engine/network/client/Client.java`
  - `necesse/engine/gameLoop/ServerGameLoop.java`
  - `necesse/engine/gameLoop/tickManager/TickManager.java`
  - `necesse/engine/state/MainGame.java`
  - `necesse/engine/world/World.java`、`WorldEntity.java`
  - `necesse/engine/commands/serverCommands/PauseWhenEmptyServerCommand.java`
  - `necesse/engine/modLoader/ModLoader.java`、`classes/ModPatchClasses.java`
- EliasVahlberg, 2026, *necesse-headless-harness*，README 及 `src/main/java/necesseheadlessharness/*Patch.java`、`ManualTicks.java`、`ServerThreadTasks.java` — https://github.com/EliasVahlberg/necesse-headless-harness
- DrFair, *ExampleMod* `build.gradle` — https://github.com/DrFair/ExampleMod
- ChatGPT 分享对话（作为线索来源，结论已逐条核实，见 §2.1）— https://chatgpt.com/share/6acde190-4fb0-83e9-830f-41ec516acc42

---

## 9. 实现状态（v1.0，2026-10-13）

已实现（项目目录 `coop-pause/`）：

| 组件 | 文件 |
|---|---|
| Mod 入口：注册数据包、指令、连接与停服事件监听 | `src/main/java/cooppause/CoopPauseMod.java` |
| 服务端暂停状态（与 pausewhenempty 分离） | `src/main/java/cooppause/PauseState.java` |
| S→C 状态同步包 | `src/main/java/cooppause/packets/PacketCoopPauseState.java` |
| `/pause`、`/unpause`（ADMIN） | `src/main/java/cooppause/commands/` |
| 中英文本地化 | `src/main/resources/locale/{en,zh-CN}.lang` |

验证情况：
- ✅ `gradle buildModJar` 构建成功（JDK 25 运行 Gradle，目标 Java 8）。
- ✅ 独立服务器无界面冒烟测试（`-datadir` 指向隔离目录，`-port 14199`）：Mod 正常加载；`pause` → `pause`（提示已暂停）→ `unpause` → `unpause`（提示未暂停）→ `stop`，日志中没有异常。运行约 75 秒、其中暂停约 10 秒，Game time 为 63.9 秒，与「暂停期间世界时间停止」一致（只是粗略佐证）。
- ⏳ 尚未验证：Host World 双人联机下的实际冻结效果、长时间暂停不掉线、新玩家加入时的同步。

未实现（对应 §5.3 和 §6.2 的 P2～P4）：输入屏蔽（客户端遮罩加服务端数据包白名单）、暂停前主动存档、全员 AFK 自动暂停。

### 9.1 输入屏蔽（v1.1）

实现时修订了 §5.3 的设计：

- **客户端没有另做遮罩，而是复用原版的暂停菜单。** 依据：`MainGame` 中玩家操作（`tickControls`、`tryAttack`、`runClientInteract`）和快捷键都只在 `isRunning()` 为真时执行；`MainGameFormManager.updateActive` 在 `!isRunning` 时会隐藏工具栏、背包、合成栏；失去焦点时的自动暂停走的也是 `closeContainer` 加 `setRunning(false)`。容器窗口（focus form）不受 `isRunning` 控制，所以暂停时会先关闭它。
  - `MainGameSetRunningPatch`：暂停期间拒绝 `setRunning(true)`，并在聊天框提示（最多每 3 秒一次）。
  - `MainGameFrameTickPatch`：每帧检查一次，处理暂停状态在 `MainGame` 创建之前就到达的情况（暂停期间加入的玩家）。
  - 恢复时，只有在暂停之前游戏是运行状态，才把它恢复成运行，不会把玩家自己打开的菜单关掉。
- **服务端用黑名单，没有用 §5.3 写的白名单。** 理由：暂停期间加入或保持在线的玩家需要连接握手、区域、Level、Mob 数据请求、聊天、设置等大量数据包，白名单漏掉任何一个都会卡住加载；而需要拦截的玩家操作数量有限、容易列全。核实结果：只有 `PacketPing`、`PacketServerStatusRequest`、`PacketNetworkUpdate` 注册为 processInstantly，其余数据包都经过 `Server.processPacket`，所以这个补丁覆盖得到所有玩法包。
- 冒烟测试（独立服务器）：日志中 3 个补丁都显示 `transformed`，包括私有方法 `Server.processPacket(NetworkPacket)`；`MainGame` 补丁在服务端只是被织入、不会执行，没有报错；`pause` / `unpause` 正常。
- ⏳ 待在游戏里实测：暂停菜单显示时聊天框能否打开并输入 `/unpause`；Esc 和「继续」按钮是否被拦截；暂停期间加入的玩家是否停在菜单；房主（Host World）这一侧的表现。

### 9.2 全员挂机自动暂停（v1.2）

设计要点：

- **活动由客户端上报，服务端不从玩法数据包推断。** 依据：`Client.tick` 中即使玩家站着不动，也会每 2 秒发送一次移动包（`movementTimer > 2000`），无法区分有没有人；而且挂机暂停期间，玩家回来按的那一下键可能根本不产生玩法数据包。所以客户端在 `MainGame.frameTick` 结束时检查本帧的输入事件（键盘、鼠标点击、滚轮、手柄；窗口必须有焦点；鼠标移动不算），发送 `PacketCoopActivity`。
- **暂停分两种模式**（`PauseState.Mode`）：`MANUAL` 会把客户端锁在暂停菜单；`AFK` 只冻结世界、不锁菜单，任何玩家有活动就恢复。挂机暂停期间输入 `/pause` 会转为 `MANUAL`。状态数据包里多了一个模式字段。
- **检查放在 `Server.tick` 补丁里**，每 20 tick（约 1 秒）检查一次。`Server.tick` 在暂停期间仍然运行，但只要 `server.isPaused()` 为真就跳过检查，因此不会和原版的无人暂停、角色生成暂停冲突，也不会把它们当成自己发起的暂停。
- 玩家连接时把最近活动时间记为当前时间，断开时删除记录；停服时清空。
- 设置通过 `ModSettings` 保存。参照原版 `pausewhenempty` 的做法：独立服务器调用 `saveServerSettings`，房主调用 `saveClientSettings`，两者都会写入各个 Mod 的配置文件。

验证：
- ✅ 独立服务器冒烟测试：4 个补丁都显示 `transformed`；`afkpause`（查询）→ `afkpause 1` → `pause` → `unpause` → `afkpause 0` → `afkpause 3` 输出都正确；停服后 `cfg/mods/a632079.cooppause.cfg` 中为 `afkMinutes = 3`。
- ⏳ 「所有人超时 → 自动暂停 → 按键唤醒」整条链路需要真实客户端，尚未实测（无界面服务器上没有玩家，检查不会触发）。

### 9.3 设置界面与暂停菜单按钮（v1.3）

**调研：Mod Settings Lib**（AizSave，Steam 创意工坊 3558238376，mod id `aizsave.modsettingslib`，本机安装版本 1.2.7，声明的 gameVersion 为 1.0.2）。依据：GitHub `AizSave/CustomSettingsLib` 源码（最新提交 2025-10-18），以及对本机 jar 的 `javap` 反汇编，两者行为一致。注意：README 中写的 id `aizsave.customsettingslib` 与实际 `mod.info` 不符，以 jar 为准。

| 能力 | 结论 |
|---|---|
| 设置界面 | 在原版「设置」菜单里加「Mod 设置」入口（补丁 `SettingsForm.setupMenuForm`），按 Mod 列出；支持 bool、int（输入框/滑条）、string、下拉、颜色、分隔标题、段落 |
| 持久化 | 继承原版 `ModSettings`，写入同一个 `cfg/mods/<modid>.cfg`；点「保存」时调用 `Settings.saveClientSettings()` |
| 客户端设置是否实时生效 | **是**：保存时 `onSave()` 把值写回设置对象，`getter` 每次都读当前值 |
| 服务器设置是否实时生效 | **否**：`isEnabled()` 只在当前状态是 `MainMenu` 时为真，进入世界后服务器设置变成灰色、不能修改；值只在客户端出生时（`ClientClient.applySpawned` 补丁）向服务端请求一次，之后不会再同步；独立服务器没有界面，只能编辑 cfg 后重启 |
| 带权限的操作按钮 | 不提供（它只做设置页） |
| 依赖代价 | `clientside=false`（注册了数据包），所有玩家都必须安装 |

**实现**：

- 挂机分钟数改为通过 Mod Settings Lib 注册（`CoopPauseConfig`，键 `cooppauseafkminutes`，范围 0～120，标记为服务器设置），在主菜单里可以改默认值。
- 游戏中实时修改和暂停/恢复，用自己加在暂停菜单（`PauseMenuForm.setupMainForm` 补丁）里的按钮：
  - 「为所有人暂停世界 / 恢复世界」和「Coop Pause 设置」（滑条 + 应用）。
  - 客户端发送 `PacketCoopPauseRequest`；服务端用 `PauseActions.hasPermission`（`PermissionLevel ≥ ADMIN`）再校验一次，与聊天指令走同一套逻辑。
  - 服务端用 `PacketCoopPauseConfig` 在玩家连接时和设置变化时下发当前值，暂停菜单据此显示。暂停状态、设置值或本地玩家权限变化时，会重建菜单按钮。
- 服务端**不通过库的 getter 读取**这个值：在 Host World 中，服务端线程的当前状态也是 `MainGame`，getter 会返回出生时同步的旧副本，看不到游戏中的实时修改。所以直接读设置对象（`settingsMap.get(id).getValue()`），写入时用 `changeValue` 后再保存。

验证：
- ✅ 带库的独立服务器冒烟测试：两个 Mod 都正常加载，本 Mod 的 5 个补丁和库的 4 个补丁都显示 `transformed`；`afkpause` → 5（默认），`afkpause 7` → 7，`afkpause 500` → 被限制为 120；`pause` / `unpause` 正常；cfg 中写入 `cooppauseafkminutes = 120`。首次启动时出现一条 `Could not load int cooppauseafkminutes` 警告，原因是旧 cfg 里还没有这个新键，属于正常现象。
- ⏳ 待在游戏里实测：暂停菜单按钮的布局和位置；非管理员看到的灰色按钮和提示；设置面板的滑条和应用；主菜单「Mod 设置」页的显示。
