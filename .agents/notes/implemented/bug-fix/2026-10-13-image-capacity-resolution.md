# Agent Note: 缺失附件元信息时读取原图容量

Status: implemented

## Problem

部分动图正文只有 img 标签，没有 lightbox 容量元信息。2998063 的第一张 WebP 原图响应和下载文件均为 2401362 字节，但保存菜单原先只读取 sizeLabel，无法展示容量。

## Decision

ImageDownloader 提供原图容量读取：优先使用同 URL 的原图磁盘缓存或已导出的真实字节数，缺失时对当前原图进行可取消、5秒限时的 HEAD 请求，只接受成功图片响应中的正数 Content-Length，拒绝 HTML 和压缩传输长度。结果按原图 URL 复用，不读取响应体或预下载整图。打开菜单只为当前图片补请求，其他相册图片只读已有缓存；批量容量全部已知才汇总。已有附件容量仍作为回退。

菜单立即可操作，异步得到容量后更新括号；获取不到时按用户要求只显示“保存原图”或“保存所有图片”。关闭菜单取消探测，不影响后续真实导出和动图字节。

## Existing note audit

检索容量、保存原图、Content-Length 与图片导出。[图片导出菜单](../../implemented/feature/2026-10-10-image-export-menu.md)部分重叠：本篇接管容量来源和按需 HEAD 的决定，保留原图导出、文件共享、相册边界、未知不显示括号和批量不完整不合计的约束。其他命中为全局 Toast，互不改变；无完全吸收或过时提案。

## Alternatives considered

- 只使用正文附件信息：无额外网络，列表立即可用，但普通 img 和部分动图缺少元信息，无法满足这次显示真实容量的要求。
- 提前下载所有原图再统计：即使缺少响应头也能准确计数，但为一个菜单下载整个相册，流量和等待成本过高。
- 对整个相册发送 HEAD：能尽早得到批量总量，但图片多时会同时触发大量请求；仅探测当前原图，其他图片只复用已加载缓存。

## Testing

ImageExportTest 的8项单测通过（`artifacts/image-capacity-reader-tests.txt`），覆盖缓存命中不请求网络、当前原图 HEAD 读取与 URL 复用、缺失/非法容量、HTML/失败/压缩响应拒绝、导出原始字节记忆，以及原有动画原字节、大小限制、批量失败和取消清理。

ImageExportUiTest 的6项真机用例通过（`artifacts/image-capacity-resolution-device.txt`）。新增场景验证当前 originalUrl 的 HEAD 得到 2401362 字节后显示“2.3 MB”，其他相册图不发请求；生产菜单实际读取 Coil 原图缓存 4 KB，并忽略预览缓存的 1 KB。既有未知省略、批量汇总、动画字节导出和失败清理继续通过。

已查看 `artifacts/image-capacity-from-head.png` 和 `artifacts/image-capacity-omitted.png`，已知容量括号与缺失时无括号的排版均正常；对应2项截图用例通过（`artifacts/image-capacity-screenshot-device.txt`）。

菜单接入沿用同一个 ImageDownloader 实例；Coil DiskCache 以 originalUrl 打开快照并在 IO 线程读取 data 文件长度，不使用预览尺寸。LaunchedEffect 绑定菜单目标，关闭或切换时取消读取；缓存可提供批量已知项，只有当前缺失项会发送 HEAD。菜单获取期间保持可点击，响应后只更新容量文案。

## Consequences

HEAD 可能被服务器拒绝或没有 Content-Length，这时不展示容量，仍允许保存。缓存必须以 originalUrl 精确匹配，不能将缩略图字节数当原图大小。单图补查最多增加一次轻量请求，关闭菜单取消，已知正数在当前查看器生命周期内复用。
