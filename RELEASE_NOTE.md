# Fakeplayer 0.4.1

This release adds Minecraft 26.3 support and reorganizes the Gradle build scripts. In-game behavior on previously supported Minecraft versions is unchanged.

## Highlights

### Minecraft 26.3 support

- Fake players now work on Minecraft 26.3.
- As with 26.2, Minecraft 26.3 requires Java 25 and CommandAPI 12.0.0 or newer.

## Build changes

These changes affect how the plugin is built, not how it behaves in game.

- Migrated the Gradle scripts to the Kotlin DSL.
- Moved the build conventions into an included build, with per-module configuration declared in typed extensions.
- Moved the per-version NMS modules under `versions/`, separated distribution packaging into its own module, and removed the unused Maven descriptors.
- Spigot compile dependencies are now downloaded automatically, and the version catalog pins released OpenInv and PlaceholderAPI versions instead of snapshots.
- Simplified the CI workflow.

## Requirements

| Minecraft version | Java | CommandAPI |
| --- | --- | --- |
| 1.20.x–26.1.x | Java 21 | Any supported release except 10.0.0 |
| 26.2, 26.3 | Java 25 | 12.0.0 or newer |

Paper or a compatible Paper downstream such as Purpur is required.

## Upgrade notes

1. Stop the server.
2. Replace the previous Fakeplayer jar with the 0.4.1 jar.
3. On Minecraft 26.2 or 26.3, update CommandAPI to 12.0.0 or newer and run the server with Java 25.
4. Start the server and run `/fp test` as an operator to check the active server implementation.

<details>
<summary>中文发布说明</summary>

## Fakeplayer 0.4.1

本次发布新增 Minecraft 26.3 支持，并调整了 Gradle 构建脚本的组织方式。已支持版本的游戏内行为保持不变。

### 主要更新

#### Minecraft 26.3 支持

- 假人现已支持 Minecraft 26.3。
- 与 26.2 相同，Minecraft 26.3 需要 Java 25 及 CommandAPI 12.0.0 或更高版本。

### 构建调整

以下改动只影响插件的构建方式，不影响游戏内行为。

- Gradle 脚本迁移至 Kotlin DSL。
- 构建约定移入独立构建，各模块配置改用类型安全的扩展声明。
- 各版本 NMS 模块移入 `versions/` 目录，发布打包拆分为独立模块，并删除不再使用的 Maven 描述文件。
- Spigot 编译依赖改为自动下载；版本目录中的 OpenInv 与 PlaceholderAPI 由快照改为正式发布版本。
- 精简 CI 流程。

### 运行要求

| Minecraft 版本 | Java | CommandAPI |
| --- | --- | --- |
| 1.20.x–26.1.x | Java 21 | 除 10.0.0 外的受支持版本 |
| 26.2、26.3 | Java 25 | 12.0.0 或更高版本 |

需要使用 Paper 或兼容的 Paper 下游服务端，例如 Purpur。

### 升级步骤

1. 停止服务器。
2. 使用 0.4.1 jar 替换旧版 Fakeplayer。
3. Minecraft 26.2 或 26.3 需要将 CommandAPI 更新至 12.0.0 或更高版本，并使用 Java 25 启动服务器。
4. 启动服务器后，以管理员身份运行 `/fp test` 检查当前服务端实现。

</details>
