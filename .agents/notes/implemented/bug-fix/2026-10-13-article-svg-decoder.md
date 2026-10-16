# Agent Note: 正文和相册通过统一加载器支持静态 SVG

Status: implemented

## Problem

话题3015506的第一张图是1200×420的SVG，CDN返回200和image/svg+xml，文件含嵌套svg、路径、裁切和文字。正文正确提取地址，但全局Coil加载器只注册位图与动画解码器，导致下载后解码失败，重试无效。

## Decision

全局加载器使用Coil 3.3.0的coil-svg，并在KudoApplication的同一个components块注册SvgDecoder.Factory。正文、相册和其他原生图片继续消费同一个加载器，按文件内容识别格式，保留网络Cookie边界、缓存、动图及生命周期行为。AndroidSVG 1.4的Apache-2.0授权文本随APK收录，开源致谢提供对应版本链接。

## Existing note audit

- [客户端加载器边界](../../implemented/architecture/2026-10-07-native-community-client.md)：部分重叠，沿用Application工厂和单次components配置，不替换受限网络和动画组件。
- [SVG签名](../../implemented/bug-fix/2026-10-10-svg-user-signatures.md)：部分重叠，签名的CSS动画仍由受限WebView图片容器保留；本次只增加原生静态SVG能力，不取代动画签名决定。
- [相册](../../implemented/feature/2026-10-09-image-viewer-motion.md)：部分重叠，复用已有缩放、采样、返回位置和来源动画，不改相册手势。其余图片布局、头像、导出记录仅背景相关，无同主题旧提案需要归档。

## Alternatives considered

- 为所有SVG建立WebView图片容器可保留浏览器动画，但正文与相册会增加网页实例、触摸和缩放协调成本；本帖是静态图，原生解码器可复用现有图库与缓存。
- 对单个SVG地址改用外部网页能立即看到原图，但不能修复其他SVG附件，也不符合正文和大图一致显示的目标。

## Verification

RMX2202 / Android13使用生产加载器先复现两项解码失败（artifacts/svg-device-repro.txt）：离线嵌套SVG与本帖CDN图片均由平台ImageDecoder报unimplemented。添加解码器后，离线SVG实际渲染红蓝像素，真实CDN图的嵌套标志、裁切与AAI / ECI / VS文字完整显示。

4项SVG真机用例通过：生产加载器的嵌套图形像素、真实CDN图片、无SVG后缀的正文缩略图与独立原图请求及2倍缩放关闭返回、深色主题的真实帖图正文与相册。最终结果见artifacts/svg-fixed-device-final.txt（6项，其中另2项是开源致谢的常规与窄屏大字滚动回归）。关联的3项正文加载/重试/动画生命周期和5项APNG / GIF / WebP / AVIF及Activity重建用例已通过（artifacts/svg-fixed-device.txt）；这些未变动用例没有再次执行，共14项相关真机用例通过。

测试夹具初次复制previous.components时也复制了绑定旧加载器的终端EngineInterceptor，导致后加的离线拦截器不执行。夹具改为保留生产fetcherFactories和decoderFactories，并在新引擎前注册拦截器；未为夹具错误修改产品代码或放宽图形断言。

人工查看artifacts/svg-topic-3015506-decoded.png、svg-topic-3015506-inline.png、svg-topic-3015506-gallery.png，以及svg-inline-light.png和svg-gallery-light.png。实际CDN测试只加载图片，不打开或提交帖子阅读状态。App和测试APK显式构建为0.2.2-dev / 2005；设备与新包证书SHA256均为95f95aff266413e4c3fb431bf7ae96552d0e5e601b986e4a53d2a116b6623e4f，adb install -r覆盖安装且不清数据，测试后重新打开MainActivity并保留App。

Debug构建及AndroidTest编译通过，构建记录为artifacts/svg-fixed-build-v2.txt、svg-fixed-build-final.txt与svg-ui-fixture-build-v2.txt。Lint无错误；未执行无关联的全量测试。

## Consequences

正文和大图可显示静态SVG，图片重试不再重复下载后必然解码失败。原生SVG解码器不保证CSS或SMIL动画、脚本、foreignObject等浏览器特性；现有动态SVG签名保留浏览器图片模式。新增模块增加包体积，解码尺寸继续受现有图片请求约束。签名浏览器容器比原生图片占用更多资源，生命周期规则仍由签名笔记管理；本次没有做内存、帧率或耗电基准，不能量化开启签名的性能成本。
