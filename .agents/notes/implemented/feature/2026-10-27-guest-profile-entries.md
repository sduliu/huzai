# Agent Note: 我的页未登录态补齐——身份占位卡与功能行匿名口径

Status: implemented

## Problem

「我的」底栏 tab（`ProfileScreen` → `GuestProfileScreen`）未登录态此前只有登录按钮卡 + 设置/下载/关于三行：
无身份占位（头像/昵称/等级位）、无功能行，官方 App 未登录我的页的常规形态（登录入口 + 功能行）缺失。
逐入口核验发现本地浏览历史（`HupuPrefs.loadHistory`）持续写入但展示页 `HistoryPage` 仅从无人调用的
`hupuui/pages/ProfilePage` 挂载——数据在记、入口是死的。

## Decision

1. `GuestIdentityHeader`：镜像 `MemberHeader` 结构的占位卡——灰圆头像 + 「未登录」+ `Lv.—` 等级槽 +
   四项统计槽全「—」（不编数据），整卡点按去登录。
2. 功能行镜像 `MemberProfileScreen` 分组：快捷宫格（话题/回复、我的收藏、我的点赞、浏览历史）+
   行组（我的草稿、私信、聊天、我的待审核、我的徽章、等级要求，均带「需登录」尾部提示）。
3. 浏览历史 = 唯一本地可读功能数据（`HupuPrefs` 常驻写入）：接 `hupuui/pages/HistoryPage` 盖入页
   （epoch 键控挂载，与 ProfilePage 同款），盖入期间置 `HupuUiSignals.zoneDetailOpen` 隐藏底栏药丸。
   其余入口点按走原有导航路由——每个目标页本身已有显式登录 EmptyState（复核确认，见下）。

## Alternatives considered

- 浏览历史路由到 `personal/HISTORY`（linux.do /read.json）：与会员页同一入口，但需登录——
  本地已有一份真实在记的浏览数据不用、却把用户推向登录墙，违背「本地可读的接本地渲染」。
- 登录提示用 Toast 代替进目标页：目标页的 EmptyState 信息量更完整（带登录按钮），且复用既有
  导航门禁，不另造一套提示。
- INVITES（邀请链接）：会员页也仅在 trustLevel≥3 时显示，未登录无等级——不显示该入口。

## Consequences

- 匿名可达性复核结论（2026-10-27，代码逐路由核验）：设置/下载管理/关于=本地直达；
  浏览历史=本地渲染；收藏/点赞/话题回复/草稿/私信/聊天/待审核/徽章/等级要求=目标页显式登录提示
  （`EmptyState`+登录按钮，非静默、非假数据）。
- `destinationIcon` 由 private 改 internal 供访客页宫格复用（MemberProfileScreen.kt 一行）。
- `zoneDetailOpen` 语义放宽：原仅专区详情盖入用，现任意全屏盖入页均可借它隐藏底栏药丸；
  GuestProfileScreen 在盖入期间持有、退出时复位（DisposableEffect 无条件复位，防时序残留）。
