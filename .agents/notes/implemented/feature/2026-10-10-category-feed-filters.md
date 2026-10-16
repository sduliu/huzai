# Agent Note: 分类内完整筛选

Status: implemented

## Problem

分类详情缺少我的帖子、已读和书签，不能在当前分类内浏览完整的个人筛选。

## Decision

分类与首页使用同一套登录权限和八项筛选顺序，分类仍默认最新；访客只有最新、热门和排行榜。每个请求和服务端分页均保留分类范围，缓存依旧属于当前账号和分类 ViewModel，分类不请求全站新话题计数。

## Related decisions

已检索分类、FeedFilter、Pager、会话与滚动位置。[会话筛选](../../implemented/feature/2026-10-08-session-feed-filters.md)部分重叠，其分类内排除三个个人列表的决定由本篇替代，登录边界和默认项保留；[首页分页](../../implemented/feature/2026-10-10-home-pager.md)的数据缓存机制继续复用；[分类图标](../../implemented/feature/2026-10-08-category-web-icons.md)仅负责分类目录，不受本筛选改动影响。无完全吸收或过时提案。

## Alternatives considered

- 保留分类现有五项：入口少且不用新增验证，但用户已确认需要分类内完整筛选，Discourse 本身为全部筛选生成 category 路由。
- 将个人筛选跳到全站列表：复用入口最直接，但会失去当前分类范围，使用 `/c/{id}/l/{filter}.json` 与服务端游标。

## Testing

FeedSessionTest 先复现分类权限少三个标签（artifacts/category-filter-repro.txt），再移除分类特有白名单。FeedRepositoryTest 覆盖全部八项分类首屏及带参数的服务端游标；FeedPageCacheTest 验证分类个人页重访不请求、无全站计数、退出清空缓存且拒绝个人请求。三个测试类共13项通过，日志 artifacts/category-filters-tests.txt。

## Consequences

分类与首页权限一致，减少两套筛选定义。模拟接口能验证客户端路径与隔离，真实登录接口仍需站点会话验收。
