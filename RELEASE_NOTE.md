# Fakeplayer 0.4.3-pre

## Changes

- Added a paginated chest GUI for `/fp list`. Selecting a fake player opens its settings, with controls shown according to the viewer's permissions. The console list remains text-based.
- Added quick access to inventory, status, and teleport actions, plus a confirmation step before removing a fake player.
- Fixed GUI titles, item names, and lore to render using the viewer's locale instead of showing translation keys.
- When `invsee-implement` is `AUTO` (the default) and OpenInv is enabled, fake player inventories now open through OpenInv. The built-in editor remains the fallback when OpenInv is unavailable; `SIMPLE` remains available as an explicit configuration.

<details>
<summary>中文发布说明</summary>

## 更新内容

- `/fp list` 改为分页箱子 GUI。点击假人图标可打开设置页，并根据查看者权限显示可用操作；控制台仍显示文本列表。
- 设置页增加背包、状态和传送快捷入口，移除假人前需要二次确认。
- 修复 GUI 标题、物品名称和描述直接显示翻译键的问题，现在会按查看者语言渲染。
- `invsee-implement` 默认为 `AUTO`。检测到 OpenInv 已启用时，假人背包会通过 OpenInv 打开；未安装 OpenInv 时回退到内置编辑器。仍可显式设置为 `SIMPLE`。

</details>
