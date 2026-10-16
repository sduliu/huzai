# 酷DO 开发清单

已批准：Kotlin / Jetpack Compose 原生客户端，包名 `com.kudo.android`，最低 Android 8.0，主色 `#FFB003`。

## 开发单元

- [x] A1 工程：Gradle、应用清单、启动入口、中文资源与图标。
- [x] A2 视觉：黄色主题、三种材质、实时玻璃背景、悬浮导航。
- [x] B1 数据：Discourse 模型、请求与错误处理、Cookie / CSRF 边界。
- [x] B2 浏览：首页筛选、分类、搜索、刷新与分页。
- [x] B3 阅读：帖子、楼层分页、HTML 内容、图片与分享。
- [x] C1 会话：官网登录、验证页、会话恢复、退出与个人资料。
- [x] D1 互动：点赞、收藏、收藏列表、通知与已读状态。
- [x] D2 创作：发帖、回复、图片上传、持久草稿、防重复发送。
- [x] E1 设置：主题、字号、材质、关于页与第三方许可。
- [x] E2 集中验证：单元测试、Lint、APK、真机与视觉检查；真实账号操作待用户验收。
- [x] F1 酷安对照：按列表、作者、长文分别调整字号与行距，收紧首页和详情间距。
- [x] F2 过渡与反馈：加快胶囊与按压动画，帖子淡变缩放，修复拖动松手跳回与首帧重复位移。
- [x] F3 真机复查：浅色、深色 130% 字号、拖动、帖子过渡，以及关闭系统动画。
- [x] F4 透明预选：运动胶囊透明，当前与经过标签同时高亮，松手收敛；补复现测试并完成真机慢拖检查。
- [x] F5 首页顶栏：社区 slogan、未登录 Linux.do Logo、移除右侧名称；真机普通字号与深色 130% 检查。
- [x] F6 玻璃材质：品牌黄图标与文字、中性灰选中面、凸起镜片与前景折射；三个复现测试和同机慢拖对照。
- [x] F7 凸面玻璃：整栏抬升放大、镜片中心放大与弧面边缘折射、收窄镜片凸出高度、静止高光归零；两项新回归和同机动静对照。
- [x] F8 官方示例：直接复用 Backdrop LiquidBottomTabs 的图层、阻尼、速度拉伸和触点高光，微调主题与胶囊比例；正常/关闭系统动画各13项真机回归，同机 demo 慢拖对照，保留完整许可。
- [x] F9 详情操作栏：采用官方 Surface Liquid Button 的轻表面、边缘折射、触点高光与按压放大，保留四个独立动作和权限；正常/关闭系统动画各18项真机回归，官方按钮动静对照及真实详情滚动检查，移除旧 glass。
- [x] F10 设置模态：主题/材质/字号用底部ActionSheet，说明与退出确认用官方玻璃Dialog；取消/遮罩/返回、背景隔离、焦点与短窗口滚动，正常/关闭系统动画各25项真机回归，保留来源许可并安装交付。
- [x] F11 静止留白与透色：静止镜片等于标签格宽，首尾外侧与上下均4dp；玻璃底栏表面色20%、模糊4dp，两层采样同步；补复现与低动态速度形变修复，正常/关闭系统动画各28项真机通过，实景截图与安装包核验完成。
- [x] F12 首页标签：40dp紧凑栏，删除横幅及快捷入口，上滑搜索折叠、Tab吸顶、反向展开；先复现两个失败后修复，真机实景检查完成。
- [x] F13 账号筛选：两组Tab及默认项、个人列表、真实新话题计数、退出及换号隔离；24项JVM与32项真机界面测试通过，安装包核验完成。

各单元继续按构建配置、数据契约、页面组件等小批次实施，每个小功能连同笔记中文提交。功能开发优先，集中运行完整检查；发现缺陷先补复现测试。

## 验收用例

1. 未登录可浏览公开内容；401 提示登录，403 网页验证与网络失败分别展示，不伪造话题。
2. 切换首页筛选、搜索或分类时，旧请求不覆盖新结果；分页按服务端游标加载且不重复。
3. 帖子可加载后续楼层；代码块、引用、图片与链接可读可操作。
4. 官网完成登录后验证当前用户；重启恢复；退出后清除用户相关状态。
5. 点赞和收藏以服务端成功为准；请求失败可重试；操作执行中禁止重复点击。
6. 发帖和回复失败保留草稿；成功或进入审核队列后明确反馈；上传图片插入 Markdown。
7. 浅色与深色、大字号、键盘和系统栏不遮挡操作；玻璃背景跟随滚动。
8. Android 13+ 玻璃折射、Android 12 模糊、较早版本半透明可降级；低动态模式下仍可操作。
9. 首页作者和时间清晰，摘要紧凑、详情正文留足行距；放大至 130% 时标签随文字增高，长标题换行，搜索框可单行省略。
10. 胶囊拖动松手后从当前位置吸附，在发布入口松手或取消拖动时回原标签；切页不触发发布。帖子进入 / 返回过渡正常；系统关闭动画后仍能浏览和操作。
11. 从首页慢拖经过分类、发布入口与消息：运动胶囊透明，首页与当前经过的标签同时高亮，松手前页面不变，经过发布入口不发布；在消息松手后只消息高亮，取消拖动回原标签。浅色与深色 130% 字号下分别检查，点击切页时也观察透明过渡。
12. 未登录首页左侧显示黑白黄 Linux.do Logo，搜索栏为“真诚 · 友善 · 团结 · 专业”，右侧无“酷DO”；点击分别进入登录与搜索页。登录状态显示账号头像；普通字号与深色 130% 下检查文案和搜索图标。
13. 高亮图标和文字均为品牌黄，静止选中面为透明中性灰。采用官方示例后整栏放大比例为 `1 + 16dp / 宽度`，RMX2202 上约1.05倍；镜片稳定运动约93.9×64dp，略高于58.8dp底栏，并叠加官方速度形变。镜片中心图标和文字放大，弧面边缘折射、压缩和色散，经过发布按钮时能看到轮廓折射。松手与取消后平滑收回，静止时关闭镜片高光。以同机 demo 对照和录屏判断材质，慢拖两端不得裁切；位置保留阻尼跟随与少量弹性偏移。
14. 使用实际触摸点按分类与发布；分别从发布入口、未选中标签开始拖动，在发布格松手不发布、不切页。无障碍和布局树中每个入口唯一，隐藏采样层不暴露第二份入口。普通与关闭系统动画下分别验证。
15. 详情两块胶囊使用官方 Surface 样式，按压内容放大，取消后复位且不触发动作；右组赞、收藏、分享分别触发一次，空白容器无多余点击。话题关闭时禁用写回复，忙碌或缺主帖时禁用赞/收藏，分享仍可用。按住后停用再启用不残留放大；深色130%和三种材质下文字完整、图标可读，正文滚动改变真实玻璃背景。互动只在夹具中验证。
16. 设置选项从底部进入，当前项有黄色勾；点击选项保存一次，取消/遮罩/返回不保存也不退出页面。说明完整可滚动，点圆角内部空白不关闭；弹窗打开时背景入口隐藏且不能触发。退出只确认一次，动画中重复点击不再次退出。连续Tab焦点留在模态，深色130%和三种材质可读，短窗口仍能滚动到致谢链接且固定操作可见；关闭系统动画后仍可操作。
17. 分别选中首尾标签，灰面外侧与上下均留4dp；普通宽度/字号、280dp宽深色130%和三种材质下等边。玻璃模式用实际正文与图片检查透色，拖动仍放大并在取消后恢复；半透明兼容模式保持原遮盖。普通与关闭系统动画下，将手指停在多个标签位置，镜片均只略高于底栏，不被速度残留压扁或过度拉高；黑底夹具只判断白面强度。

18. 首页没有社区横幅与四个快捷入口。普通字号Tab约40dp；连续上滑后搜索行完全收起，Tab稳定在状态栏下，反向滑动搜索立即展开；空列表也可下拉刷新。
19. 未登录为最新、热门、排行榜、类别；登录为未读、最新、新（真实数量）、热门、排行榜、我的帖子、已读、书签、类别，默认项分别为最新和未读。逐个横向滚动、点击、刷新和分页；类别进入已有分类页面。退出、换号与分页请求交错后不出现旧个人话题，未知数量只显示“新”。深色130%与280dp宽窗口文字完整。

## 验证记录

2026-10-08 更新（未改动的接口检查保留此前结果）：

| 检查 | 结果 |
| --- | --- |
| JVM 回归 | 37项通过，新增分类图标元数据与缺失/异常字段；保留此前35项回归 |
| Android Lint | 0 errors；27 项依赖升级 / API 使用建议等 warnings，新增 Shapes 升级建议 |
| Debug APK | 构建成功，0.1.0，约 25 MiB，已覆盖安装到新真机 |
| RMX2202 真机 | Android 13 / API 33，WebView 153，1080×2400 |
| Compose 界面 | 37项通过，79.933秒：新增分类图标渲染、唯一跳转、状态栏安全区、错误恢复、深色大字号窄屏2项，原有35项保留 |
| 真实只读浏览 | 首页及图片、分类、分类规范地址跳转、帖子与多楼层正文均已加载；访客真实推送→刷新提示→回顶刷新已走通，另1项线上探针通过 |
| 网页验证 | 用户手动完成人机验证，官网登录表单成功加载；部分接口仍可能再次验证 |
| 搜索 | 已发送真实查询，站点要求登录；已补 `not_logged_in` 回归与登录引导 |
| 视觉对比 | 同机酷安Tab高40dp；酷DO为19 / 16sp、普通字号40dp，搜索折叠后Tab贴状态栏；导航及卡片密度保持此前结果 |
| 字号与行距 | PNG 实测摘要基线间距 22.0 / 21.8dp，详情正文 32.0dp；作者 15sp / 时间 12sp 保留；浅色与深色 130% 首页、长标题和正文无裁切 |
| 动画与低动态 | 系统关闭动态效果后确认三项缩放为0，28项测试通过，结束恢复1；零时长停用速度附加形变，按压放大与折射保留；此前按钮、Dialog与帖子过渡检查保留 |
| 首页顶栏 | 横幅和快捷入口已移除，真实首页与排行榜正常；吸顶与反向展开截图已看，Tab触控与账号顺序通过夹具；280dp宽深色130%搜索省略、标签完整 |
| 玻璃与双高亮 | 首尾静止4dp等边；玻璃表面20% / 4dp模糊；实景图片透色、拖动中心放大与边缘折射、双黄色高亮及收回截图已检查；此前同机demo慢拖对照保留 |
| 来源与交付 | 应用开源致谢显示官方示例与 Shapes；完整 Apache-2.0 许可已核实进入 APK；构建、分发与设备安装 APK 的 SHA256 一致 |
| 设置模态 | ActionSheet黄色勾选、玻璃Dialog、实际触摸与背景隔离、三种材质深色130%与短窗口滚动均通过；连续Tab测试显式键盘模式 |
| 旧机兼容 | MI 6X / Android 9 安装与半透明材质正常，当时网络失败，未完成在线验收 |

仪器测试通过 adb 指定新真机执行，保留应用数据；本轮13项输出为 `artifacts/backdrop-final-instrumentation.txt` / `backdrop-reduced-motion.txt`。初次回归为 `backdrop-initial-instrumentation.txt`：10%灰令黄色像素变暗，改为6%后通过；两项几何测试改读实际图层边界后通过。新的实际触摸与几何三项先独立通过，输出为 `backdrop-touch-repro.txt`。此前自绘底栏的失败与回归日志仍留在 `navigation-scale-*`、`navigation-center-*`、`navigation-material-*`、`navigation-preview-*` 和 `realme-density-*`。测试前停止 `com.android.cli.interact.instrumentation`，避免 UI 自动化争用。

详情按钮加入后的18项输出为 `surface-final-instrumentation.txt` / `surface-reduced-motion.txt`；5项按钮截图复查为 `surface-screenshot-instrumentation.txt`，10.258秒。初轮字号误报为 `surface-button-instrumentation.txt` / `surface-overflow-repro.txt`，文案未裁切，改用字形边界断言后通过。按钮浅/深色130%截图为 `topic-surface-light.png` / `topic-surface-liquid-dark-large.png`；官方 demo 与夹具动静图为 `surface-demo-resting.png` / `surface-demo-pressed.png` 和 `surface-button-resting.png` / `surface-button-pressed.png`；最终安装后的真实详情与滚动为 `surface-real-topic.png` / `surface-real-topic-scrolled.png`，均已人工检查。

设置模态加入后的25项输出为 `modal-final-instrumentation.txt` / `modal-reduced-motion.txt`，构建为 `modal-integration-build.txt` / `modal-final-build.txt`。初轮6项3个测试条件误报见 `modal-initial-instrumentation.txt`，补滚动/等待/有效空白坐标后6项通过；键盘模式独立通过见 `modal-focus-repro-keyboard.txt`。材料为 `settings-sheet-light.png` / `settings-dialog-light.png`、`settings-sheet-liquid-dark-large.png` / `settings-dialog-liquid-dark-large.png` 与其他材质截图；官方Dialog为 `modal-demo-dialog.png`，最终安装的选项面板为 `modal-final-sheet.png`，均已人工检查。

本轮首尾与透明度复现为 `navigation-insets-repro.txt`，参数修正为 `navigation-insets-fixed.txt` / `navigation-insets-motion.txt`。关闭系统动画的速度形变失败与修复为 `navigation-insets-reduced-motion.txt` / `navigation-low-motion-repro.txt` / `navigation-low-motion-fixed.txt`；新增停顿测试的墙钟校准为 `navigation-material-initial.txt` / `navigation-material-clock-fixed.txt`，保持几何断言。最终28项为 `navigation-material-final.txt` / `navigation-material-reduced-motion.txt`，构建为 `navigation-low-motion-fix-build.txt` / `navigation-material-test-clock-build.txt`。实景为 `navigation-material-real-home.png` / `-real-scrolled.png` / `-real-drag.png` / `-real-last-tab.png`；黑底夹具为 `navigation-transparency-liquid-resting.png` / `-pressed.png`，兼容深色130%为 `navigation-insets-dark-large.png`。均已人工检查；黑底仅判断白面强度，不作浅色标签可读性结论。

此前导航同机录像为 `backdrop-demo-motion.mp4` / `navigation-glass-backdrop-kudo-motion.mp4`，对照图为 `backdrop-demo-kudo-comparison-00.png` / `-01.png`；动静图为 `backdrop-kudo-scale-comparison.png`。本轮设备没有screenrecord可执行文件，保留慢拖中截图及此前录像。最终 APK 为 `artifacts/Kudo-0.1.0-debug.apk`，24.94MiB；SHA256 为 `bbc58255d62b6bfc660be459d0b5a77d9f2f60f731f6927e86bc23eb0754f519`，与构建和设备安装包一致。最新许可与哈希验证输出为 `navigation-insets-apk-verification.txt`；此前详情与弹窗来源核验为 `surface-credits-verification.txt` / `modal-apk-verification.txt`。

本轮材质截图为 `navigation-glass-home.png`、`navigation-raised-lens.png` / `navigation-material-dark-large.png`，动静对照为 `navigation-scale-resting.png` / `navigation-scale-center.png` / `navigation-scale-before-after.png`；同机慢拖为 `navigation-glass-coolapk-motion.mp4` / `navigation-glass-kudo-motion.mp4`，逐帧对照为 `navigation-glass-comparison-00.png` 至 `-02.png`。首页顶栏截图为 `home-header-light.png` / `home-header-dark-large.png`。此前字号 / 帖子对照为 `density-home-comparison.png` 和 `density-topic-comparison.png`，帖子过渡录屏为 `density-motion-final.mp4`。录屏用于动作与材质检查，不作为正式帧率基准。所有 artifacts 均为本地产物，不提交 Git。

首页调整记录：复现日志为 `home-tabs-repro.txt`（2项失败）与 `home-tabs-data-repro.txt`（个人列表白名单失败），修复后布局2项及最终32项均通过；完整结果 `home-tabs-final-instrumentation.txt`，构建/JVM/Lint为 `home-tabs-final-build.txt`。大字号初次hasVisualOverflow误报为 `home-tabs-ui-test.txt`，逐字几何检查与截图确认无裁切，见 `home-tabs-glyph-test.txt`；真实窄屏截图为 `home-tabs-dark-large-narrow.png`。此前3项JVM超时属于虚拟时钟与真实网络不匹配，改为实际超时，见 `home-tabs-session-build.txt` / `home-tabs-session-checks.txt`。

同机酷安对照 `home-tabs-coolapk-top.png` / `home-tabs-coolapk-scrolled.png`；真实首页、吸顶排行榜与反向展开为 `home-tabs-real-top.png` / `home-tabs-real-pinned.png` / `home-tabs-real-expanded.png`，已人工检查。两组账号Tab的夹具截图在 `home-tabs-ui-images/`，5096只作为界面测试数字，不进入正式数据源。最终 `artifacts/Kudo-0.1.0-debug.apk` 为24.94MiB，SHA256 `80f0765583f500657322c12a65f6e1ee5ff326f674a49ec56a2edec38be6ecaf`；构建、分发与设备安装包一致，记录为 `home-tabs-apk-verification.txt`。结束返回真实首页。

真实账号的个人首页列表、新话题数量、搜索结果、登录持久化、发帖、回复、图片上传、点赞、收藏和通知尚待用户验收。“已读”依赖服务器已有阅读记录，本次不新增阅读进度写入；数量在主动刷新时更新。自动化使用本地模拟服务和测试夹具，不向社区发布测试内容。Android 12 模糊分支、平板、折叠屏及正式帧率基准尚未实测，不能从当前真机结果推定通过。

## 首页更新提示验收（2026-10-08）

Linux.do 实际使用 MessageBus 长轮询。当前筛选有新话题或内容更新时，首页图标与文案变为刷新；收到消息不替换现有列表。后台和详情停止订阅，恢复时检查遗漏。登录凭据来自本机主站HTML，仅共享会话密钥送往固定推送域名，主站Cookie不跨域。

新增手动用例：

1. 首页向下滚动，等待站点新数据；底栏变为黄色刷新图标和“刷新”，当前话题位置不变，顶部Tab仍吸顶。点击后回顶，成功恢复首页；不要通过向真实社区发测试帖制造数据。
2. 提示出现后断网再点刷新，失败仍保留提示；恢复网络重试后清除。重复推送不重复检查，刷新请求期间再到更新不会被旧响应误清。
3. 切换未读、最新、新、热门、排行榜及个人列表，仅当前列表确实出现新内容才提示；仅浏览量/点赞变化不提示。换号或退出清除旧提示，旧账号在途请求不得回写。
4. 退后台或打开详情，返回后仍在原阅读位置；从其他底栏页面点击刷新回首页顶部。检查三种材质、深色130%及280dp宽窗口：入口唯一、黄色完整，静止中性灰，慢拖镜片突出不裁切且发布按钮真实折射。

35项JVM和35项真机界面回归通过；Lint为0 errors / 27 warnings。输出为 `live-refresh-final-build.txt` / `live-refresh-ui-tests.txt`。返回位置旧逻辑的失败复现为 `live-refresh-scroll-repro.txt`，修正后包含在35项通过项中。登录或发布的刷新事件重复消费复现为 `live-refresh-epoch-repro.txt`，同一事件返回首页不再请求，新事件仍能刷新，修复包含在最终35项JVM内；最终包7项首页界面回归再次通过（16.218秒），见 `live-refresh-final-home-ui.txt`。

线上只读探针默认跳过，使用 `-e liveReadOnlyProbe true -e class com.kudo.android.LiveUpdatesProbeTest` 显式执行；本轮21.297秒通过，收到3次真实消息后确认首页有更新，见 `live-refresh-online-probe.txt` / `live-refresh-online-events.txt`。它依赖60秒内站点有可见更新，不作为离线CI门禁。随后正式首页出现“刷新”，同一可见话题的边界保持 `[60,627][588,690]`，点击回顶后回复数1175变1176并恢复“首页”；截图 `live-refresh-real-prompt.png` / `live-refresh-real-clicked.png` 已查看。

材质验收单独记录：真实静止截图、12秒同机慢拖 `live-refresh-material-motion.mp4` 及抽帧 `live-refresh-material-rest.png` / `-drag.png` / `-end.png` 已检查，高亮黄色、透明中性灰、镜片凸出和发布按钮前景折射保持正常。三种材质深色大字号由界面测试覆盖。录屏不作为正式性能结论。

最终 APK `artifacts/Kudo-0.1.0-debug.apk` 已保留数据安装，构建/分发/设备包 SHA256 一致：`cab9cc981232c221f7ede530e4e3ce8cf9927f149b7cb1a1e170adeaf1631d91`，记录 `live-refresh-apk-verification.txt`。当前真实验收为访客链路；登录私有频道、令牌失效后线上续接仍需账号验收。提示按15秒合并检查，并受网络/站点数据可见性影响。

## 分类图标与顶部清理验收（2026-10-08）

分类读取 /site.json 的 icon 名称；23种图标的路径和 viewBox 已逐一核对与官网及 Font Awesome Free 6.7.2 相同，转为原生矢量并随APK提供。删除发现社区、顶部刷新按钮和找到你的同好，卡片介绍及跳转保留。

手动用例：

1. 打开分类页，第一张卡片在状态栏下，顶部没有独立标题/刷新/副标题。开发调优为代码、国产替代为幼苗、资源荟萃为分享、网盘资源为硬盘、文档共建为书本、非我莫属为公文包；颜色跟随分类配置。
2. 点击分类并返回，仍可正常浏览。网络失败保留“重新加载”，需要站点验证时保留验证入口；卡片介绍没有删除。遇到空白、异常或未收录图标显示默认井号。
3. 深色130%和280dp宽窗口检查标题、图标及点击范围；长简介仍最多两行省略，列表末尾保留底栏避让。开源致谢包含 Fonticons 署名及许可入口。

37项JVM、37项真机界面测试通过（79.933秒），Lint 0 errors / 27 warnings；日志为 `categories-final-build.txt` / `categories-final-instrumentation.txt`。图标字段丢失先复现后修复，见 `category-metadata-repro.txt` / `category-metadata-checks.txt`。界面测试遍历23种图标及默认图标、所有点击回调、重试和验证入口，既有设置与底栏回归保留。

截图 `categories-icons-light.png` / `categories-icons-dark-large.png` / `categories-real-top.png` 已查看。真实分类页显示对应图标并移除顶部区域，点击开发调优成功加载分类列表，随后返回分类页；记录 `categories-real-open.json` / `categories-real-loaded.json`。本轮未改底栏材质，不重复慢拖录屏。

新版 `artifacts/Kudo-0.1.0-debug.apk` 已保留数据覆盖安装，构建/分发/设备包 SHA256 均为 `36ea572348457d2ed16e44f2c1e2f02302ad3a2ee5a1cf836b56950702f8d4d1`，见 `categories-apk-verification.txt`。19,484字节的完整 CC BY 4.0 许可已核实进入 APK。官网以后新增未收录的图标时会回退默认图标，需要更新本地图形目录。

## 动态头像与状态资质验收（2026-10-08）

统一头像读取官网 animated_avatar 与 avatar_template，支持APNG、GIF和动态WebP，原动图失败时回退静态头像。Neo在所有原生入口使用10%圆角方形，其余用户圆形；只有头像本身裁切，右下角角标保持完整。首页账户入口和“我的”只显示有效状态，公开内容优先资质，没有资质才显示状态；自己的帖子与公开个人页也遵循公开规则。

手动用例：

1. 在首页列表、帖子楼层和个人页查看Neo与普通作者，检查形状一致，图片资质或图标位于右下角且不被裁切。查看同一动图头像一段时间，应能换帧；原动图资源失效时应继续显示静态头像。
2. 使用同时设置状态与资质的账号登录，在首页头像和“我的”查看状态，再打开自己的公开个人页与帖子查看资质。公开作者没有资质时才显示状态；到期状态应消失，无状态时账户入口不补显示资质。
3. 自定义表情应从官网表情目录加载；滚动折叠首页头部、离开页面和退后台时停止动画，返回可见处恢复。换号或退出后不沿用上一个账号的角标缓存；弱网时头像与列表仍可阅读。
4. 检查浅色、深色130%字号下的角标、长昵称与点击范围。完整透明的原图显示底色属于正常内容，不应误报为动图解码失败。

新增20项JVM与9项头像真机用例，最终共57项JVM、46项真机测试全部通过，真机耗时101.177秒；Lint为0 errors / 28 warnings。自制APNG/GIF/WebP在实际ImageLoader里输出红绿蓝三帧，并在API33强制验证旧版WebP解码路径。后台未停和图片加载器初始化顺序崩溃先复现后修复，日志为 `avatar-ui-instrumentation.txt` / `avatar-lifecycle-instrumentation.txt`；完整结果为 `avatar-final-build.txt` / `avatar-final-instrumentation.txt`。

`avatar-light-gallery.png` / `avatar-dark-large-fallback.png` 的夹具截图与 `avatar-real-home.png` / `avatar-real-topic.png` / `avatar-real-neo-profile.png` / `avatar-real-list-frame-a.png` 的真实页面已人工检查，真实首页刷新提示仍正常。线上只读核对发现一位作者使用全透明静态PNG，源文件118字节、Alpha全为0，见 `avatar-blank-source.json`，无需产品修复。临时网页检查页已关闭。

当前手机为访客；本人账号入口优先级以测试夹具验证，登录后的真实账号、API26/27真机与真实站点APNG样本仍未验收。未知资质图标名显示通用认证图标，辅助用户卡片请求受站点权限和网络影响。状态更新在重新进入页面与缓存刷新时读取，不单独订阅状态推送。实现边界与备选理由见[头像笔记](.agents/notes/implemented/feature/2026-10-08-avatar-presentation.md)。

新版 `artifacts/Kudo-0.1.0-debug.apk` 为22,870,695字节，已保留数据安装；构建、分发与设备包SHA256均为 `c1b8c09ae6411f3aeb9408a5609fe2841afa5657753cabb4511d0a36eb04d5aa`，记录为 `avatar-apk-verification.txt`。
