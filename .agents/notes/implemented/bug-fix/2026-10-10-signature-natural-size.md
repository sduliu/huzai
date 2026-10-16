# Agent Note: 签名图片满宽与真实比例

Status: implemented

## Problem

签名图片使用默认3.8比例及120dp高度上限，方图、竖图与未知尺寸图片无法按正文宽度显示。SVG使用固定图片壳高度，同样不能反映原图比例。

## Decision

签名占满正文可用宽度，优先使用实际解码尺寸，HTML尺寸仅用于加载前布局。移除高度上限，不裁切、不拉伸。SVG维持无脚本图片模式，通过原生WebView内容高度测量真实比例，图片壳使用width:100%、height:auto；未知SVG先以极小测量高度加载，避免视口最小高度污染图片尺寸。显示偏好、动画生命周期、失败隐藏与Cookie规则继续成立；默认开启且尊重已保存选择，见[用户装饰](../feature/2026-10-10-user-decoration.md)。

## Existing note audit

检索签名尺寸、SVG、引用与折叠。[用户装饰](../../implemented/feature/2026-10-10-user-decoration.md)和[SVG签名](../../implemented/bug-fix/2026-10-10-svg-user-signatures.md)的高度限制被部分取代，其余内容保留。[引用](../../implemented/bug-fix/2026-10-10-discourse-quotes.md)与[折叠](../../implemented/bug-fix/2026-10-10-discourse-details.md)的签名独立渲染约定继续成立。没有完全被取代的笔记。

## Alternatives considered

- 保留高度上限可让长签名不占用过多空间，但不能满足用户明确要求的满宽与自适应高度。
- 启用网页脚本读取SVG自然尺寸最直接，但会改变无脚本签名契约；原生内容尺寸测量不需要脚本或额外网络请求。
- 另行下载SVG解析viewBox能提前获取比例，但需要重复图片请求并另行处理Cookie、重定向与缓存，复用已加载的网页图片尺寸。

## Testing

RMX2202 / Android13先用位图和SVG两项用例复现固定比例/高度问题（artifacts/signature-sizing-repro-device.txt）。修复后10项相关真机测试全过（artifacts/signature-sizing-fixed-device.txt），覆盖宽图、方图、竖图满宽及真实比例，SVG 4:3比例、无脚本及资源限制、动图帧、后台暂停、失败隐藏、原生滚动与头像失败回退。Debug与测试包构建通过。

## Consequences

长签名会占据更大滚动空间。未知尺寸图片加载成功时会调整高度；SVG内容高度存在浏览器整CSS像素取整误差。
