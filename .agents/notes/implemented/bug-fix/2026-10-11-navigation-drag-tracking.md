# Agent Note: 高频触摸时底部玻璃镜片实时跟随

Status: implemented

## Problem

一加 13 用户报告底部导航拖动时镜片要等手指停下才追上，松手切页正常。现有 DampedDragAnimation.updateValue 在每次移动时取消位置任务并重启 spring；同一显示帧间多次更新可能使动画没有机会推进。旧测试主要在移动后停顿再检查位置，不能证明持续移动期间跟手。当前连接设备为 RMX2202，不能将它的结果视为一加 13 实测。

## Decision

拖动位置通过 `CoroutineStart.UNDISPATCHED` 立即启动 `Animatable.snapTo` 更新最新触点位置，不等待位置动画帧；下一显示帧读取最新位置。位置 spring 只负责按下另一入口、松手和取消后的吸附，并从当前可见位置开始。`animatePressedValue` 保留按下状态，`animateToValue` 复用位置弹簧并触发复位；手势检查器按下时产生的零位移不覆盖初始按下弹簧。这样普通点击保留过渡，真实移动才直接跟随。

按压弹簧、速度形变、三层采样材质、中央动作隔离与松手才切页保持各自职责。速度从实际拖动位置采样，最后一次移动后100ms触发原速度弹簧归零，不再依赖已移除的拖动位置动画回调；新输入取消待执行的停顿复位，抬起或取消立即转入原复位流程。来源、参数与许可继续由官方示例笔记负责。

手势pointerInput以DampedDragAnimation实例为键。动画的范围等配置变化创建新实例时，同步更换手势协程，不能仅用共用animationScope为键而继续调用旧实例闭包。楼层滑块从2楼切到3楼的高频回归先暴露镜片仍停在旧实例位置，修正实例键后连续范围切换通过；现有导航的立即跟随与吸附参数不变。

## Existing note audit

- [官方示例组件](../architecture/2026-10-07-backdrop-demo-navigation.md)：部分重叠，本篇接管拖动位置始终由 spring 驱动的决定；该篇保留来源、材质、按压参数、中央动作与取消边界，双方互链。
- [玻璃与原生阅读](../feature/2026-10-07-liquid-glass-ui.md)、[酷安密度与过渡](../feature/2026-10-07-coolapk-density-and-motion.md)：部分重叠，导航后续事实已有官方示例笔记承接，不重复改动。
- 分类左右切页、首页左右切页与酷安底部面板：检索命中“跟手”，但输入对象不同，与本次无关。
- 活跃 proposed / rejected 没有其他同主题提案。

## Alternatives considered

- 加大位置弹簧刚度：改动最少且能减少单次移动后的追赶时间，但每次输入取消并重启任务的问题仍存在，不能保证高频更新期间有进度。
- 每帧只重启一次位置弹簧：能合并高采样输入并保留完整阻尼感觉，但仍给手指与镜片引入追赶距离；拖动位置直接更新更符合用户的跟手要求，弹性留给形变和吸附。

## Verification

先在旧实现运行新增的两个复现测试，两项均失败：每帧四次目标更新后第一帧仍为0，预期0.1667；4ms真实指针移动中镜片中心166.043px、触点196.667px，误差超过0.15格，见 `artifacts/navigation-tracking-repro-device.txt`。测试在持续移动过程中逐帧采样，不等待手指停顿。按下过渡检查在初版直接更新所有位置时也失败，见 `navigation-press-repro-device.txt`；分离按下弹簧与拖动更新后通过。

最终正常动画21项导航回归通过，73.344秒，见 `artifacts/navigation-tracking-final-device.txt`。新增测试覆盖每显示帧四次目标更新，以及4ms / 8ms / 16ms输入间隔的双向真实移动，中心误差小于0.15格，移动期间页面与中央动作不触发；普通按下保留过渡。原回归继续检查连续点按、松手与取消复位、发布/搜索隔离、预选、品牌黄、灰面和折射。

关闭系统动画后，NavigationTapUiTest 与 NavigationMaterialUiTest 共10项通过，52.629秒，见 `navigation-tracking-reduced-motion.txt`；三项系统动画缩放均恢复1。Debug / 测试 APK 与 Lint通过，0 errors / 37 warnings / 1 hint，见 `navigation-tracking-final-build.txt`。

材质验收与自动化结果分开：`navigation-tracking-resting.png`、`navigation-tracking-cross-publish.png`、`navigation-tracking-rims.png` 已人工查看，静止中性灰、LogoYellow、凸出而未裁切的上下边缘、中央按钮真实前景折射保留。17.68秒慢拖与连续点击录屏 `navigation-tracking-motion-bt709.mp4` 和抽帧 `navigation-tracking-motion-contact.png` 已核对；视频仅修正设备编码器的色彩元数据，实际颜色以直接截图和像素测试为依据，不据此给出稳定帧率结论。保留账号与数据覆盖安装后的真实首页 `navigation-tracking-live-home.png` 已查看，中央入口仍为原先保存的搜索偏好。

## Consequences

高频输入不再反复等待位置 spring 推进，点击与松手保留弹性；移动速度采样与停止后的复位需要单独维护。拖动中心采用直接位置，旧的拖动位置阻尼手感随之改变，速度拉伸、按压与最多4dp整栏偏移继续提供弹性。测试仍有显示帧与墙钟同步要求。

输入采样率模拟不等于一加 13 的厂商触摸、调度和显示管线，最终需用户在该机验证。录屏与像素测试只能证明观察到的动作和材质，不能证明所有机型帧率。后续如一加仍滞后，应在该机记录输入到绘制的时间与线程负载，再定位厂商调度或渲染性能，不以RMX2202的通过结果代替该机结论。
