# Agent Note: 采用 Backdrop 官方示例玻璃组件

Status: implemented

## Problem

用户安装 Backdrop demo 后确认其材质和动作就是目标，要求直接复用、仅微调参数。现有底栏自行组织采样和中心缩放，虽满足前轮几何回归，观感仍反复偏离参考。Backdrop 发布包只有绘制基础 API，LiquidBottomTabs 是 catalog 示例源码。

详情页原有两块操作胶囊使用独立 `.glass()`，表面色62%、8dp blur和18 / 28dp lens，与官方 Surface Liquid Button 的轻表面和按压动作不同。用户提供酷安与酷DO操作栏截图，要求同样替换为官方按钮示例。

用户静止截图指出灰色选中面首尾横向留白小于上下留白，底栏仍偏白。原镜片静止就比格宽多8dp并向外偏4dp，首尾外侧留白为0，上下为4dp；40%表面色与8dp模糊使真实背景较弱。先增加实际边界与黑色背景透色测试复现，参数修正保持官方拖动图层与动作。

后续截图指出静止灰过浅、拖动镜片上下中间的内凹折射带不明显。仅检查中性色偏和镜片外边界凸出不能覆盖这两个要求；灰度与前景亮度分别采样，折射使用固定彩色背景检查上下边缘，并与同机官方示例对照，避免把不同背景的可见度差异误判为组件版本差异。

## Decision

`ui/components/liquid/` 接入 AndroidLiquidGlass 的 Android 1.0.6 catalog 六份源码，来源提交 `896a94a3ade1cc1a940b92365f942a34971fecda`，与现有 Backdrop 1.0.6 依赖保持一致。GlassNavigation 只提供酷DO入口内容；LiquidBottomTabs 采用官方可见底栏、隐藏放大采样层、组合镜片，DampedDragAnimation 与 InteractiveHighlight 提供官方阻尼、速度拉伸和触点高光。源码保留来源、Apache-2.0 许可和修改说明；完整许可随 APK 放入 assets。

点击与吸附的位置、速度、按压进度和横纵缩放沿用官方弹簧，分别为 (1,1000)、(0.5,300)、(1,1000)、(0.6,250)、(0.7,250)。初始接入的拖动位置弹簧由[高频触摸跟随修复](../bug-fix/2026-10-11-navigation-drag-tracking.md)部分替代，改用触点直接更新；材质、按压与吸附决定保留。整栏运动比例为 `1 + 16dp / 宽度`，隐藏标签从1放大到1.2；只在隐藏采样层放大内容，不使用手写 onDrawBackdrop 中心缩放。

适配参数为品牌黄、五入口、56dp 底栏、48dp 静止镜片、约66.9dp 运动镜片、22dp 图标和10sp文字。静止灰面宽度等于标签格，首尾外侧与上下均留4dp；按压进度从0到1时，镜片增加0到8dp宽并向外偏0到4dp，保持中心与官方动作。镜片保留官方78 / 56的按压比例，10 / 14dp折射和8dp内阴影同步乘48 / 56，即约8.57 / 12dp与6.86dp；RMX2202 上约62.4×48dp静止、98×66.9dp稳定运动，速度拉伸另叠加。静止中性灰为12%，放在隐藏采样层的表面与标签内容之间：最终镜片只在选中区域显示它，图标和文字保持原始品牌色。灰面随按压淡出，运动灰仍为3%黑并保留官方高光、内外阴影与色散。可见与隐藏表面同步使用20%主题表面色、4dp blur和24 / 24dp lens，减少白色遮盖并保留背景细节；隐藏表面的lens随按压进度变化。半透明兼容分支仍为94%表面。

光学参数必须随静止镜片高度缩放。旧48dp镜片使用64 / 48放大和未缩放10 / 14dp折射时，中心线上边缘采样最远仅离中心约22.9dp，没有越过隐藏采样层24dp半高；因此上下不能折回底栏外的背景，只在外沿卷入标签碎片。按官方比例适配后，采样最远约25.3dp，上下各形成真实的背景折回与高光弧带。Capsule轮廓、三层采样结构和官方shader保持原有职责，彩色背景固定后单独判断弧带，不用整体凸出高度替代光学验证。

隐藏采样标签使用品牌黄，中央发布保留黄底黑色加号；不对整层使用官方的 accent ColorFilter，以免发布图标和黄底变为同色。增加官方相同版本的 `shapes:1.2.0` 编译依赖使用 Capsule，原依赖只作为 Backdrop 的 runtime 依赖可用。兼容模式遵守现有 API 33折射、31模糊、26半透明边界。

系统动画时长缩放为0时，镜片停用速度附加拉伸，基础按压放大与折射保留。动画瞬移仍会进入官方按墙钟计算的速度估计，残留正/负速度会使停下的镜片压扁或过高。观察系统 ANIMATOR_DURATION_SCALE 并读取作用域 MotionDurationScale，任一为0时不使用速度形变；ContentObserver 随组件释放，后台改变设置后也能更新，正常动画沿用官方参数。

直接点击和拖动结束都必须收敛至静止镜片。位置、速度和按压复位任务按最新交互取消旧任务，不能让上一次触点的速度回调在复位后重新写入形变；触摸抬起或取消后速度回到0，缩放回到1。纯点击不采样拖动速度，拖动仍保留现有液态形变。连续点击、点击打断拖动以及触摸取消使用真实指针与同步墙钟帧的回归验证，不能只用程序修改 selected 代替触摸验收。

## Interaction boundary

页面选择与滑动预选分离：松手才切页；取消或在发布入口松手返回原页；中央发布仅通过点击触发。隐藏采样层不能接收点击或暴露重复可访问节点。手势放到固定外容器以覆盖所有入口，移动越过系统触摸阈值后消费事件，阻止拖动误触发布。

手势辅助增加独立取消回调。高光检查器位于手势链内层，在 Main pass 中先读移动，随后阻尼检查器消费移动，防止高光因事件已消费而提前取消。拖动位置遵循上述高频跟随修复，点击、吸附与形变仍用官方动画，保留最多4dp的弹性面板偏移。

## Surface Liquid Button

详情底栏左侧“写回复”与右侧“赞 / 收藏 / 分享”操作组采用同一版本 catalog 的 LiquidButton。官方示例的 Surface 样式即 LiquidButton 加30%表面色，并不需要另一个 Surface 组件。沿用2dp blur、12 / 24dp lens、Capsule、触点高光、按压时 `1 + 4dp / 高度` 放大与 tanh 阻尼平移，不叠加旧 glass 的边框或62%遮罩；浅深色使用各自主题的 surface。

组件适配 enabled 和 nullable onClick：左侧作为独立按钮，话题关闭时不能回复；右侧只是玻璃容器，三个子按钮分别可点击，空白处不能产生第四个点击入口。缺失主帖或请求忙碌时保持回应/收藏禁用，分享仍可用。停用或更改交互模式时重建高光状态，避免在按下过程中禁用后留下放大。最小48dp高度保留大字号增高能力。TopicBottomBar 在360dp宽设备采用约133dp回复胶囊、1:1.32左右比例、20dp四周边距和12dp间距；回复图标20dp、14sp普通字重，右侧图标22dp，匹配酷安同机尺寸。兼容模式继续遵守现有API边界；[详情交互](../feature/2026-10-09-topic-author-and-responses.md)负责关注、回应和菜单状态。

详情操作栏迁移后，旧 `Modifier.glass` 已无调用，从 Glass.kt 移除。该文件只保留导航入口适配，实际材质由官方 LiquidBottomTabs / LiquidButton 负责，避免后续页面误用旧的浓表面参数。

## Existing note audit

- [玻璃与原生阅读](../feature/2026-10-07-liquid-glass-ui.md)：部分重叠，本篇接管导航渲染决策；该篇保留主题、阅读、网络和历史取舍，双方互链，不整篇归档。
- [酷安密度与过渡](../feature/2026-10-07-coolapk-density-and-motion.md)：部分重叠，保留文字与页面过渡；导航动画和图层的后续事实由本篇维护。
- [客户端验证](../testing/2026-10-07-client-verification.md)：部分重叠，继续保留真实渲染、只用真机、夹具不写社区的验证契约；本篇记录源码来源和接入验证。
- [设置玻璃模态](../feature/2026-10-08-glass-settings-modals.md)：复用相同版本的官方Dialog材质，另记同窗口采样、可访问隔离与关闭契约；本篇继续负责底栏和详情按钮，互不覆盖。
- Android 构建与原生社区架构：无关，不改变工具链、网络或整体导航结构。检索时活跃 proposed / rejected 无其他同主题提案。

## Alternatives considered

- 继续微调手写采样中心缩放：文件少且前轮行为与几何已通过，但用户确认官方 demo 已符合目标；不断推测私有效果会增加不必要分歧，改用示例实际图层和动作。
- 直接使用最新 KMP 2.0.1 示例：可以对齐上游最新代码，但其 Kotlin 2.4.10、Compose 1.12.0 和平台 Shader API 超出当前工具链；Android 1.0.6 示例已有相同 LiquidBottomTabs 结构，可避免无关升级。
- 继续给详情操作栏调旧 glass 参数：可沿用现有 TextButton / IconButton，改动少，但用户已指定官方 Surface 样式，旧材质还有独立边框、浓表面和缺少真实触点形变的问题；直接接入 LiquidButton，并保留原有业务回调。
- 放宽关闭动画时的镜片高度测试：最少改动即可通过，但实际镜片会被残留瞬移速度压进底栏或拉得过高；停用零时长下的速度形变，继续检查稳定几何。
- 仅读取作用域 MotionDurationScale：纯Compose、能尊重自定义缩放且无需系统观察，但作用域并不保证提供系统当前值，仪器测试上下文也由测试框架控制；系统关闭动画后独立复现仍失败，增加系统设置观察，并保留作用域检查。
- 继续减淡镜片最上层的静止灰：只改透明度即可保护黄色亮度，但同时削弱选中灰底，无法同时满足两项视觉要求。静止灰移到采样内容下方，按白底参考图校准对比；自动测试分别检查实际灰度和最终画面的品牌黄像素。
- 只恢复官方78 / 56按压比例：改动最少且能恢复整体抬升，但48dp镜片若仍保留原10 / 14dp折射，采样最低点仍贴着24dp的隐藏层边界，没有足够空间形成稳定弧带；镜片高度、折射距离和内阴影按同一比例适配。

## Consequences

复用实际图层和动画减少效果猜测；酷DO保留当前/预选双高亮和发布隔离，因此交互不完全等同四标签示例。系统时长缩放仍由 Compose Animatable 支持。

示例是源码而非稳定组件 API，需要维护少量适配和许可。隐藏采样增加一层绘制；帧率、Android12和更宽窄设备仍不能用单台真机结果推定。参数以已连接 RMX2202 的观感为依据。

`THIRD_PARTY_NOTICES.md` 记录源码版本、六份接入文件、修改范围与完整许可路径；应用“开源致谢”明确底栏与详情操作栏来自 Backdrop 官方示例，并列出 Shapes。接口与酷安仅作为研究参考，不包含它们的实现。

## Verification

2026-10-09 最终交付：刷新入口改用水平翻转的 Material ReplayCircleFilled，呈圆底镂空顺时针回转箭头；浅色与深色130%截图 `artifacts/navigation-refresh-final.png` 已人工查看，沿用原点击、颜色和隐藏采样。57项JVM测试、Debug / 测试APK与Lint通过，0 errors / 28 warnings，日志 `navigation-final-build.txt`；最终21项导航与首页刷新真机测试通过，51.294秒，日志 `navigation-final-ui.txt`。

材质验收独立于测试结果：真实首页静止图 `navigation-final-real-rest.png`、分类与发布处的拖动截图、对照图 `navigation-final-real-comparison.png` 及12.14秒录屏 `navigation-final-real-motion.mp4` 已核对。录屏抽帧 `navigation-final-motion-frames.png` 显示上下内凹弧带、两端完整轮廓、发布按钮前景折射、双高亮和松手恢复；背景不同于官方壁纸，固定背景的前后像素对照另见 `navigation-rims-comparison.png`。录屏仅用于材质和动作，不提供帧率结论。保留数据覆盖安装后，构建、分发 `artifacts/kudo-navigation-fixed.apk` 与设备APK的SHA256均为 `11e0f68a4e6952c85ee9c9345dfd4f17cc9a3dad1101c623354444185857c947`，记录 `navigation-final-apk-verification.json`。

2026-10-09 内凹折射复核：固定青蓝背景下旧实现的上缘对比仅0.188，且上下最暗采样红分量分别0.380 / 0.416，均未接近底栏外背景0.082，日志 `artifacts/navigation-rims-repro.txt`。同比修正后上下对比分别0.573 / 0.565，最暗采样0.067 / 0.075，两侧都能折回外部背景；`navigation-rims-comparison.png` 已人工检查，旧边沿的标签碎片被清楚的上下内凹弧带替代。像素回归仅在API33及以上要求完整折射，同时检查弧带对比与外部背景取样，避免标签碎片让单纯对比度检查误过。

正常动画的18项导航回归通过，44.799秒，日志 `navigation-rims-normal.txt`；关闭系统动画后18项通过，39.871秒，日志 `navigation-rims-reduced.txt`，结束三项缩放均恢复1。首次18项回归暴露旧几何测试的虚拟/墙钟不一致，单独修正并记录于[客户端验证](../testing/2026-10-07-client-verification.md)，不放宽凸出范围。此前不同背景录屏的“弧面折射一致”描述只覆盖一般边缘扭曲和整体运动，未单独验证内凹弧带；本次固定背景复现纠正这一验收缺口。

2026-10-08 静止灰度复核：新增根画面像素测试在旧实现上失败，白底选中面亮度为0.941，日志 `artifacts/navigation-gray-repro.txt`。静止灰改到采样内容下方后，三种材质均满足0.85–0.92的亮度范围和中性色偏限制，并保留足量未被压暗的 LogoYellow 图标像素。NavigationMaterialUiTest 与 CommunityUiTest 共17项真机回归通过，43.242秒，日志 `navigation-gray-fixed.txt`；`navigation-gray-fixed.png` 已人工检查灰面、黄色前景和四边留白。验收构建使用与现有真机安装一致的本机默认调试证书，避免独立签名变更导致卸载或丢失数据；项目正式签名配置不变。

静止边距与透明度先在真机复现两项失败：首个灰面左边距为0、上下12px（4dp），黑色背景下底栏白面为0.4。输出 `artifacts/navigation-insets-repro.txt`。NavigationMaterialUiTest 检查首尾边距与上下相同、两种宽度/字号和三种材质；黑底实际根画面采样检查静止与按压透色，允许官方按压高光并检查取消恢复。

参数修正后两项真机回归通过，11.246秒，输出 `artifacts/navigation-insets-fixed.txt`。Debug / 测试APK与Lint通过，仍为0 errors / 27 warnings。正常动画完整27项通过，54.887秒，输出 `navigation-insets-final.txt`。动态透色采样随后补强为实际拖动、冻结时钟推进并断言底栏已放大，两项再次通过，11.07秒，输出 `navigation-insets-motion.txt`。

关闭系统动画后的27项初轮26项通过，镜片高度为175.65px、放大底栏为176.40px，低于应有凸出高度。新增停在多个位置的复现也检测到反向速度令镜片过高，输出 `navigation-insets-reduced-motion.txt` / `navigation-low-motion-repro.txt`；失败后系统缩放均恢复1。

系统设置观察修复后，关闭动画的独立复现通过，2.051秒，输出 `navigation-low-motion-fixed.txt`。新增多位置测试的正常动画初轮因测试时钟瞬间推进900ms、而官方速度估计使用墙钟，未给速度采样同等停顿；原有27项通过，输出 `navigation-material-initial.txt`。该测试按实际16ms间隔逐帧推进，保持原几何断言，使动画时钟和速度采样经历相同停顿；独立测试通过，6.939秒，输出 `navigation-material-clock-fixed.txt`。

最终28项正常动画63.441秒、关闭系统动画51.013秒全部通过，输出 `navigation-material-final.txt` / `navigation-material-reduced-motion.txt`，结束三项缩放恢复1。新增3项覆盖首尾等边、两种宽度/字号及三种材质、真实按压透色与多个拖动停顿位置；原有25项导航、详情按钮和模态回归完整保留。

最终安装后真实首页、滚动图片、拖动双高亮和末端静止分别为 `navigation-material-real-home.png` / `-real-scrolled.png` / `-real-drag.png` / `-real-last-tab.png`，均已人工检查：背景随滚动改变，首尾留白一致，黄色标签清晰，边缘折射和放大保留。实景与黑底材质夹具分别判断；黑底只测白面遮盖，不用于声称浅色主题在纯黑底的可读性。此机没有 screenrecord 可执行文件，本轮保留慢拖中截图及此前同机录屏，不提供新帧率结论。

分发APK覆盖安装成功，24.94MiB；构建、分发和设备安装包SHA256均为 `bbc58255d62b6bfc660be459d0b5a77d9f2f60f731f6927e86bc23eb0754f519`，完整上游Apache-2.0许可与assets一致，Lint仍为0 errors / 27 warnings，输出 `navigation-insets-apk-verification.txt`。

Surface Liquid Button 的编译、Debug / 测试 APK、Lint 通过，0 errors / 27 warnings。新增5项 SurfaceLiquidUiTest 与原有13项合计18项真机回归通过，28.73秒，输出为 `artifacts/surface-final-instrumentation.txt`。检查真实触摸的四个独立动作、容器无多余点击入口、关闭/忙碌/缺主帖禁用、按压内容放大、取消复位、按下期间禁用再启用，以及深色130%下三种材质。

初轮5项中4项通过，字号检查因 `TextLayoutResult.didOverflowWidth` 比较段落可用宽222px与实际文本宽168px而报错，截图文案完整。断言改读每个字符的实际边界，检查一行、未省略及全部字形在文本尺寸内；不修改已正确显示的布局来绕过测试。测试截图等待400ms让系统涟漪退出，冻结Compose时钟的按压截图仍保留按下状态。初轮与排查输出为 `surface-button-instrumentation.txt` / `surface-overflow-repro.txt`。

调整截图等待后，5项按钮测试再次独立通过，10.258秒，输出为 `surface-screenshot-instrumentation.txt`；浅色静止按钮没有残留涟漪圆，深色130%文字和三个动作完整，按压/静止的条纹夹具截图显示真实边缘折射与表面形变。

2026-10-08：详情按钮加入后，通过无障碍界面关闭系统动画，确认三项缩放为0，完整18项测试通过，25.076秒，输出为 `artifacts/surface-reduced-motion.txt`。结束通过系统界面恢复三项缩放为1。

同机官方 demo 的 Buttons 页静止/按压截图为 `surface-demo-resting.png` / `surface-demo-pressed.png`，与条纹夹具的 `surface-button-resting.png` / `surface-button-pressed.png` 人工检查：轻表面、边缘折射、按压内容放大与触点高光一致，背景不同，未作为像素相等或帧率结论。最终安装后真实详情与滚动截图为 `surface-real-topic.png` / `surface-real-topic-scrolled.png`，玻璃后的正文随滚动改变，未触发社区写操作。

移除无调用旧 glass 后编译通过；补齐应用与文档致谢后的 Debug / 测试 APK、Lint通过，仍为0 errors / 27 warnings。分发 APK 已覆盖安装，构建、分发和设备包 SHA256 均为 `9dce24461f7ca685c385af57546cab79649ca355a371f100151e489ca97f2bb1`，完整上游许可与 assets 内文本一致，记录为 `artifacts/surface-apk-verification.txt`。

辅助代码的 Kotlin 编译通过。替换后初次12项回归有3项失败：10%静止灰令黄色图标像素变暗；两项几何断言仍读取未缩放的布局边界。颜色改为6%灰后通过；几何改用 boundsInRoot，镜片高度与可见整栏比较，中心位置允许官方弹性偏移，不再断言手写动画的绝对触点坐标。

新增实际触摸回归，覆盖真实点按分类/发布、从发布入口开始拖动、从未选中入口拖至发布，以及隐藏采样的唯一可访问入口。RMX2202 / Android13 上完整13项 Compose 测试通过，23.749秒，输出为 `artifacts/backdrop-final-instrumentation.txt`。Debug / AndroidTest APK 与 Lint 通过，0 errors / 27 warnings；新增warning为shapes版本升级建议。静止灰、黄图标、中心放大、凸出/复位、双高亮、松手连续、深色130%与发布隔离均有回归。

2026-10-08：通过系统无障碍界面关闭动画，确认三项全局缩放为0后，13项测试全部通过，17.544秒，输出为 `artifacts/backdrop-reduced-motion.txt`。结束恢复三项缩放为1。实际安装的 demo 与酷DO各录12秒双向慢拖，`backdrop-demo-kudo-comparison-00.png` / `-01.png` 已人工检查：镜片中心放大、弧面折射、运动透明和收回行为一致，酷DO保留双高亮与发布按钮颜色；慢拖两端轮廓可见。白底动静图 `backdrop-kudo-scale-comparison.png` 和深色130%测试截图已检查，真实首页为 `backdrop-kudo-home.png`。这些材料验证动作和材质，不证明稳定帧率。

## 点击与松手回归

NavigationTapUiTest 的松手复现先失败：位置春动结束后 velocity 仍为 0.004144829，没有恢复零速不变量。修复持有并取消位置、速度、形态任务，只在真正拖动时采样；release 在调度复位前停止采样，位置动画结束也归零速度。7项真机测试通过（artifacts/navigation-fixed-device.txt），包括真实指针连点、松手复位和5项已有材质回归。此复现证明残留速度路径，不将这次微小残留声称为用户截图形变幅度的完整重现。

同机动静验收：navigation-fixed-taps-resting.png 的黄色图标和文字保持 LogoYellow，中性灰镜片四周留白正常；navigation-fixed-cross-publish.png 的镜片上下凸出且未裁切，发布前景出现放大和真实折射。18秒慢拖加连续点击录屏 navigation-fixed-motion-bt709.mp4 及抽帧 navigation-fixed-motion-contact.png 已检查，取消和点击结束均恢复静止。设备视频编码器带错误色彩元数据，bt709 文件仅修正 H.264 元数据、不改变视频帧；颜色以直接截图和像素测试判断。以上材质观察与62项界面回归结果（artifacts/feed-reading-final-device.txt）分别记录。
