# Agent Note: 正文 details 折叠与相册定位

Status: implemented

## Problem

话题2894260的更新日志使用 details/summary，原生解析将标题和子内容平铺，隐藏内容直接显示。

## Decision

增加独立 Details 块，保留 summary 富文本、子块和 open 属性。正文默认遵循网页折叠状态，点击标题切换，恢复页面保留状态。相册递归收集折叠内图片但不计入标题图标，所有图片序号保持稳定；相册返回隐藏图片先展开其所有引用和折叠祖先再定位。

折叠路径使用直接保存 ArrayList 的 Saver，空集合也必须保存。listSaver 将空集合视为无可保存值，所有折叠已展开时会丢失用户选择，页面恢复后重新应用默认关闭状态。引用和 details 共用此状态，非空路径与空路径均遵守同一恢复规则。

## Existing note audit

检索 details、折叠、引用及相册。[引用分组](../../implemented/bug-fix/2026-10-10-discourse-quotes.md)部分重叠，复用不可变路径与 Saver；[用户签名](../../implemented/feature/2026-10-10-user-decoration.md)保持独立尺寸，其高度上限后由[签名真实尺寸](2026-10-10-signature-natural-size.md)接管。没有被完全吸收或过时提案。

## Alternatives considered

- 将 details 当引用，复用全部渲染，但引用默认展开、标题语义与原帖跳转不同，无法忠实保留 open 属性。
- 用 WebView 渲染整篇能自然支持 HTML 折叠，但会放弃现有正文、相册锚点和阅读曝光，新增原生块更适合既有结构。

## Testing

DetailsParserTest 先复现实际更新日志被分为3块而非一个容器（artifacts/details-parser-repro.txt）。本轮42项相关JVM测试全过，包括3项折叠解析以及嵌套图片序号（artifacts/content-about-checks.txt）。RMX2202 / Android13 的 QuoteContentUiTest 共6项全过，覆盖展开/收起、状态恢复、嵌套引用与折叠相册返回；另有1项相册页面恢复用例通过（artifacts/content-about-fixed-device.txt）。

真机恢复测试先复现全部展开后重新关闭（artifacts/about-row-details-diagnosis.txt 与当时的 details-restored.png），修正 Saver 后同一断言通过，恢复截图保留正文和图片。Debug与测试APK构建通过。

## Consequences

折叠中的正文直到展开才参与实际阅读；大量嵌套结构仍增加解析工作。
