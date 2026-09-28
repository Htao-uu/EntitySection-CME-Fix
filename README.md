# EntitySection CME Fix

**English** | [中文](#中文)

A fix-only Minecraft mod that removes the vanilla `ConcurrentModificationException` thrown by
`PersistentEntitySectionManager` — the one that shows up as a random server crash:
`Exception in server tick loop` + `java.util.ConcurrentModificationException`, with
`PersistentEntitySectionManager` / `ChunkMap` / `DistanceManager` in the stack trace.

* Minecraft **1.20.1** / Forge (>= 47)
* modId: `cme_fix`
* Install on whichever side runs the **logical server** (see below). Prebuilt jar: see the
  [Releases](https://github.com/Htao-uu/EntitySection-CME-Fix/releases) page.

---

## English

### What it does

In 1.20.1, `PersistentEntitySectionManager.m_157527_` (and the related methods) call
`stream.forEach(...)` over a chunk's entity stream, while the callback itself adds and removes
entities (entities entering/leaving the TICKING list). If the backing `ArrayList` is modified during
that iteration, the outer iterator throws a CME — and since the collection being *iterated* is the one
that reports the error, the real culprit never appears in the stack trace.

This mod redirects those `Stream.forEach` calls to a snapshot-first iteration:

```java
List<T> snapshot = stream.toList();   // materialize first
snapshot.forEach(consumer);           // then iterate the snapshot
```

Additions/removals during the traversal can no longer affect the ongoing iteration.

### Design notes

* Targets are referenced as **class name strings** (`@Mixin(targets = "...")`), so no target class is
  loaded early — another mod may safely mix into the same class.
* Method names are the **SRG names** (`m_157527_`, `m_157552_`, `m_157559_`, `m_157543_`) with
  `remap = false`. Production runtimes already use SRG names, so no refmap is required; in a dev
  environment the injection simply does not match and only a warning is logged.
* `required = false` + `defaultRequire = 0`: if another mod already injects at the same call site,
  *this* mod fails with a warning and the other mod is unaffected.
* The handler is an **instance** method (the target methods are instance methods — a `static` handler
  would make the whole mixin fail to apply).

### Installation

The patched `PersistentEntitySectionManager` belongs to the **logical server**, so install the mod on
whichever side runs it:

| Setup | Install on |
| --- | --- |
| Dedicated server | **server** (client optional) |
| Singleplayer / LAN host | **client** (the integrated server runs inside the client process) |
| Normal client on a remote server | harmless but useless |

`mods.toml` sets `displayTest = "IGNORE_ALL_VERSION"`, so a one-sided install does not raise a
"mod mismatch" warning. Requires Forge 1.20.1 (>= 47). No hard dependency on any other mod.

### Build

JDK 17 + Gradle 8.6 (this repository does not bundle a Gradle wrapper):

```bash
gradle build
```

The first build needs network access (ForgeGradle downloads the MCP configuration). Output:
`build/libs/cme_fix-1.0.0.jar`, which the `copyJarToRoot` task also copies next to the project root.

### Credits and license

* Ported from [EntitySectionManager_CME_Fix / EntityGuardian](https://github.com/alppp/EntitySectionManager_CME_Fix)
  by **Dan Turcu (alppp)** — MIT.
* Upstream fixes the 1.21 `updateChunkStatus` path; this mod is the **1.20.1** counterpart.
* Licensed under the **MIT** license (see [LICENSE](LICENSE)); the upstream copyright notice is kept
  verbatim.

---

## 中文

纯修复型 mod：**消除原版 `PersistentEntitySectionManager` 的 ConcurrentModificationException**
（表现为服务端随机崩服，日志里 `Exception in server tick loop` +
`java.util.ConcurrentModificationException`，栈里能看到 `PersistentEntitySectionManager` /
`ChunkMap` / `DistanceManager`）。

### 原理

1.20.1 的 `PersistentEntitySectionManager.m_157527_` 对「该区块的实体流」调用 `stream.forEach(...)`，
而回调内部会去增删实体（实体进入/离开 TICKING 列表）。一旦底层 `ArrayList` 在这期间被修改，
外层迭代器就抛 CME —— **谁被遍历谁报错，所以栈里永远看不到真凶**。

本 mod 用 Mixin 把这几处 `Stream.forEach` 重定向为「先快照再遍历」：

```java
List<T> snapshot = stream.toList();   // 先物化
snapshot.forEach(consumer);           // 再遍历快照
```

遍历期间的任何增删都不再影响本次迭代。

### 设计取舍

* 目标类写成**字符串**（`@Mixin(targets = "...")`），不提前加载任何类，别的 mod 可以安全地往同一个类注入。
* 方法名用 **SRG 名**（`m_157527_` / `m_157552_` / `m_157559_` / `m_157543_`）配 `remap = false`：
  正式运行环境本来就是 SRG 名，因此**无需 refmap**；开发环境匹配不上，只留一条警告。
* `required = false` + `defaultRequire = 0`：若同一调用点已有别的 mod 注入，**失败的是本 mod**
  且只记警告，不影响对方。
* 处理器必须是**实例方法**（目标方法是实例方法，写成 static 会让整个 mixin 应用失败）。

### 来源

移植自 [EntityGuardian / EntitySectionManager_CME_Fix](https://github.com/alppp/EntitySectionManager_CME_Fix)
（作者 **Dan Turcu (alppp)**，MIT 许可）：上游针对 1.21 的 `updateChunkStatus`；本 mod 是 **1.20.1**
的对应方法（SRG 名 + `remap = false`，无需 refmap）。

### 安装（装哪一侧）

要修的 `PersistentEntitySectionManager` 属于**逻辑服务端**，所以**哪一侧跑逻辑服务端就装哪一侧**：

| 玩法 | 装在哪 |
| --- | --- |
| 专用服务器 | **服务端**（客户端装不装都行） |
| 单人 / 局域网（自己是主机） | **客户端**（集成服务端跑在客户端进程里） |
| 联机时的普通客户端 | 装了无害也无用 |

`mods.toml` 里设了 `displayTest = "IGNORE_ALL_VERSION"`，所以「只有一侧装了」不会弹 mod 不匹配警告。
要求 Forge 1.20.1（≥47）。与其它 mod 无硬依赖；若同一调用点已有别的 mod 注入，本 mod 的注入会失败
并只有一条警告，不影响对方。

### 构建

需要 JDK 17 + Gradle 8.6（仓库不带 Gradle Wrapper）。在项目根目录执行：

```bash
gradle build
```

首次构建需要联网（ForgeGradle 要下载 MCP 配置）。产物：`build/libs/cme_fix-1.0.0.jar`，
并由 `copyJarToRoot` 任务复制到项目根目录同名 jar。

### 许可

MIT 许可（见 [LICENSE](LICENSE)），上游版权声明原样保留。
