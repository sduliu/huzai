# Agent Note: 外链 onebox 原生预览

Status: implemented

## Problem

网站已生成的 GitHub onebox 包含标题、摘要和缩略图，客户端只保留 header 中的域名链接，丢失预览信息。

## Decision

从 cooked 的 onebox 提取已提供的目标链接、网站来源、标题、摘要与 HTTPS 缩略图，原生显示整块可点击预览。只使用网站响应，不额外抓取外站页面，也不执行嵌入脚本。预览缩略图不进入正文相册，点击仍交给既有站内/网页分流。字段缺失时保留可用标题或链接，没有 metadata 的普通链接仍显示文字。

## Existing note audit

检索 onebox、外链、预览。[引用分组](../../implemented/bug-fix/2026-10-10-discourse-quotes.md)的 onebox 不当引用契约保留；[网页浏览](../../implemented/feature/2026-10-10-in-app-web-links.md)继续负责点击跳转；[折叠](../../implemented/bug-fix/2026-10-10-discourse-details.md)可包含预览。均为部分重叠，无需归档。

## Alternatives considered

- 客户端抓取所有外链的 OpenGraph 可覆盖没有 onebox 的网址，但滚动会触发额外跨站请求，元信息也与网站缓存不一致，本次复用网站现有信息。
- 原样嵌入 onebox HTML 最接近网页，但需要为每个链接创建 WebView，采用简单原生文字和图片卡片。

## Testing

本轮42项相关JVM测试全过，其中 OneboxPreviewTest 的2项使用实际 GitHub onebox 结构验证目标、标题、摘要、图片、来源与相册排除，并覆盖非法地址和缺字段回退（artifacts/content-about-checks.txt）。RMX2202 / Android13 的 QuoteContentUiTest 验证暗色130%字号下文字、缩略图与点击目的地通过（artifacts/content-about-fixed-device.txt），已查看 onebox-dark-large.png。Debug构建通过，Lint为0错误、30项既有警告和1项提示。

## Consequences

无法为网站没有生成预览的普通链接补出图片和摘要；远端缩略图失败仍保留文字。
