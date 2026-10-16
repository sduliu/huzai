# Agent Note: 主题列表模式与作者元信息

Status: implemented

## Problem

只有带摘要和图片的信息流列表，缺少紧凑标题列表。昵称下只有时间，分类与文本标签置于正文下方，无法对照网页辨认带图标的分类和标签，详情没有真实编辑状态。

## Decision

界面设置持久化信息流和传统标题两种模式，默认信息流。首页、分类和搜索主题列表应用同一选择；传统行显示头像、标题、图标分类与标签、回复数和更新时间，以分隔线代替卡片间距，不显示摘要和预览图。持久化名称而非序号，缺失及未知值回退信息流。

置顶项默认使用[置顶帖精简显示](2026-10-13-compact-pinned-topics.md)的一行布局；关闭其独立开关后恢复本篇对应的完整列表样式。普通帖及原有完整行的未读标记规则保留。

信息流昵称下显示 bumped_at 更新时间和带图标分类；列表接口没有主帖编辑版本，遵循用户补充要求完全不显示编辑状态，也不逐帖请求详情。详情顶部楼主作者栏和回复昵称下仅显示各自 updated_at 与版本大于1时的“已编辑”，不显示分类；话题分类仅在标题下与标签一起展示。缺少 updated_at 时回退 created_at，时间变化本身不能判定编辑。深链仍由 pinnedOriginal 提供楼主更新时间和编辑状态。

两种列表共用蓝色状态标记：服务器 unread_posts > 0 显示新回复数字，其次 unseen=true 显示蓝点，其余隐藏。不用 highest_post_number 减 last_read 推算，因为通知级别低的主题即使有楼层差也返回0。缺字段不补逐帖请求。既有后台列表探测只同步当前页已存在主题的两个状态字段，不插入、删除、重排主题或重置滚动与分页；新主题仍走原有更新提示。

标签保留 name、slug 以及可选 icon/color，同时保留原 tags 名称列表的调用契约。列表、搜索和详情共享标签解析。胶囊允许换行，不再截断为前两个标签；无配置标签只显示文字。传统列表的分类与胶囊标签按 FlowRow 每行垂直居中，避免无底面的分类被默认顶部对齐而偏高。分类从 /site.json 获取，沿用原生 Font Awesome 图标。

官网 Tag Icons 主题按标签 slug 优先、name 次之解析。启动及账号切换时，同源读取 /latest HTML 的 data-preloaded.themeSettings，提取各主题 settings.tag_icon_list；只使用名称与颜色，不执行脚本。目录缓存于当前组合生命周期，辅助请求限8秒，失败不阻塞页面。内置2026-10-09核对的60个标签键作为离线回退，读取有效非空配置后使用完整远端目录，跟随目录内的配置增删。新增42个官方 Free 6.7.2 矢量，连同分类共65种；未知名称回退 Tag 图形，保留署名及完整许可。

## Existing note audit

- [数据契约](../../implemented/architecture/2026-10-07-discourse-data-contract.md)：部分重叠，同步标签结构、详情字段及同源辅助读取事实。
- [分类图标](../../implemented/feature/2026-10-08-category-web-icons.md)：部分重叠，保留分类元数据与原生矢量方案，目录扩充供标签复用。
- [设置弹窗](../../implemented/feature/2026-10-08-glass-settings-modals.md)：部分重叠，列表选择沿用现有 ActionSheet、即时保存和取消契约。
- [详情作者](../../implemented/feature/2026-10-09-topic-author-and-responses.md)：部分重叠，保留固定楼主及辅助主帖补齐，只扩展昵称下元信息。
- [信息流密度](../../implemented/feature/2026-10-07-coolapk-density-and-motion.md)：默认信息流仍适用，传统列表是独立选择；无完全吸收或过时提案。

## Alternatives considered

- 同一张卡片隐藏摘要和图片：组件少，但头像、计数和分隔线仍保持卡片层次，无法达到传统列表密度，采用独立传统行、共享元信息。
- 每项列表请求完整详情判断编辑状态：字段完整，但带来逐帖网络开销；接口不支持时用户允许隐藏，因此列表不补请求也不显示编辑标记。
- 只内置标签图标配置：离线稳定且没有辅助请求，但站点增删和改名无法跟随；内置用于失败回退，正常读取站点主题配置，图形仍使用已核实的本地矢量。
- 顶部作者栏同时保留分类：滚动正文后仍能辨认分类，但与标题下重复且占用日期和编辑状态的空间；按用户最新要求去掉顶部分类，保留标题下可点击的分类与标签。

## Verification

列表状态只读核查 /latest.json、/unseen.json、/new.json 均返回 unseen，已跟踪主题返回 unread_posts；实际普通通知级别主题的楼层差很大但 unread_posts=0，证据 artifacts/topic-list-status-audit.json。FeedRepositoryTest、FeedUpdatesTest、FeedPageCacheTest 通过（list-badge-build.txt），验证缺字段、负数、后台探测状态同步与分页顺序不变；4项列表真机测试通过（list-badge-device.txt），两种样式均在深色130%字号下显示蓝点、数字并在状态消失时隐藏。

TopicPresentationMetadataTest 的6项用例覆盖标签 slug、异常字段、更新与编辑版本、转义 preload、slug 优先匹配、内置纯水/人工智能/公告以及详情和搜索字段。106项 JVM 测试全部通过，Debug及测试包构建成功，Lint为0错误、30警告、1提示，日志 artifacts/presentation-final-build.txt。

RMX2202 / Android13 的46项集中界面回归全部通过（85.265秒），日志 artifacts/presentation-device-final-tests.txt；覆盖两种列表、保存恢复、图标与胶囊、详情编辑标记、窄屏深色130%字号、回复按钮对称内边距和第8楼定位。已查看 artifacts/presentation-ui/ 下 presentation-feed-light、presentation-detail-light、presentation-reply-width、presentation-title-dark-large 和 presentation-floor-eight 截图。初轮失败来自合并语义节点查询和 LazyColumn 未组合的设置项；改为正确语义树和先滚动定位，未削弱功能断言。

真实公开首页、传统列表与详情截图 presentation-live-home、presentation-live-title-final、presentation-live-detail-final 和 presentation-live-return 已查看。昵称下分类与实际公告、纯水、人工智能等标签图标正确；编辑过的真实主帖显示“已编辑”，可换行且无裁切。设置已恢复默认信息流，截图 presentation-live-settings-restored 与 presentation-live-feed-restored。APK保留原安装签名覆盖安装，未清理数据或修改签名文件。

用户截图指出传统列表的分类偏高。两项几何复现测试先失败：普通字号及深色130%下分类中心都比首个胶囊中心高9px，日志 artifacts/list-alignment-repro-tests.txt。FlowRow 改为 itemVerticalAlignment = Alignment.CenterVertically 后，7项展示与3项样式回归全部通过（10.889秒），日志 artifacts/list-alignment-fixed-tests.txt；新增用例验证同一行中心一致，并保留窄屏标签换行。已查看 artifacts/list-alignment-ui/ 下 presentation-title-aligned-light.png 与 presentation-title-aligned-dark-large.png。Debug与测试包构建通过，保留原签名覆盖安装。修正覆盖首页、分类和搜索共用的 TopicTitleRow；未增加固定高度，大字号仍能增高和换行。

## Consequences

Topic detail metadata now renders the resolved category before tag chips. Category and tag chips expose native navigation callbacks, while reply author rows omit the topic category.

分类目录由 CommunityRepository 按账号共享成功结果。辅助加载失败保留已成功目录，首次无目录时有限重试；回到前台或详情加载完成仍缺分类时可重试。账号切换清空目录并拒绝旧请求回写。标题下分类继续订阅该目录并可点击进入分类页；顶部作者栏只保留日期和“已编辑”，不再订阅分类目录。

2026-10-11（历史验证，顶部展示约定已被下述要求替代）：TopicPresentationUiTest 的9项真机用例通过，包括首次目录503后重试恢复顶部分类；同批3项总结布局用例通过，日志 summary-layout-fixed-device-20261011.txt。顶部恢复截图 topic-toolbar-category-recovered.png 已查看，日期、分类和编辑状态保持单行。

2026-10-11 用户要求移除顶部作者栏分类：两项复现测试在旧版真机上均失败，作者栏语义仍包含“开发调优”及分类图标，日志 artifacts/topic-toolbar-category-repro-device-20261011.txt。回归继续验证日期、真实编辑状态、分类目录首次503后重试及标题下分类和标签的顺序与原生导航。

修正后 RMX2202 / Android13 的9项 TopicPresentationUiTest 与3项作者栏、关注和昵称装饰用例全部通过（12项，16.239秒），日志 artifacts/topic-toolbar-category-fixed-device-20261011.txt。Debug、AndroidTest 构建与 Lint 通过，日志 artifacts/topic-toolbar-category-fixed-build-20261011.txt。已查看 artifacts/topic-toolbar-no-category-light-20261011.png 和 artifacts/topic-toolbar-category-recovered-20261011.png：顶部日期与编辑状态之间只有一个分隔符，分类只出现在标题下。核对签名一致后使用 adb install -r 保留数据覆盖安装，已启动正常应用；未发现新增问题。

列表保留真实活动时间和分类，但不会显示主帖编辑状态。详情以服务端版本为准，自动处理和用户编辑是否增加版本由站点决定。启动多两项有时限的辅助读取，失败仍显示内容及内置标签图标；新图标名在本地目录更新前使用通用 Tag 图形。未知分类元数据不编造分类名，读取成功后补入。
