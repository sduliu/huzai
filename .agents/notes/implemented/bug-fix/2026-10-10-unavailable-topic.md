# Agent Note: 区分不可访问内容与网络验证

Status: implemented

## Problem

帖子被删除或缺少权限时网站可能返回404或重定向 /404。现有客户端把HTTP404归为SERVER，把成功返回的404网页当验证，界面提示不准确。

## Decision

新增 NOT_FOUND 类型并识别HTTP404、同源 /404 重定向和 Discourse not_found 错误。优先保留明确的登录与 Cloudflare challenge 语义；404用“内容不存在或暂无权限”，不推断具体删除原因。直接站内 /404 链接使用同一原生空态，WebView 内仍保持网页导航。原生帖子刷新遇到永久不可用时移除旧正文和操作入口，网络错误可保留现有内容。

## Alternatives considered

- 全部显示重试：实现简单且网络抖动可恢复，但无法解释删除/权限导致的永久失败。
- 自动打开网站404页面：保持网页原样，但丢失原生返回、登录与状态管理；使用统一原生空态。

## Testing

先写复现测试，覆盖JSON/HTML404、重定向404、200 not_found、403登录/权限/验证、直接链接及旧正文清理，再修复并通过关联测试。

UnavailableTopicTest 先复现5项失败（artifacts/profile-data-and-404-repro.txt），修复后6项全部通过。TopicViewModelTest 覆盖已显示正文刷新为404后清除正文、作者和操作来源；DeepLinkUiTest 验证外部与原生正文 /404 都进入原生不可访问页并可返回；PublicProfileUiTest 验证返回动作及不显示错误的网页验证/无限重试入口。最终46项关联JVM、Lint及构建通过（artifacts/profile-contract-final-build.txt），16项首轮真机回归通过（profile-device-final.txt）；个人主页刷新修复后8项相关真机测试通过（profile-refresh-fixed-device.txt）。只读真实 /404 深链进入原生状态页并断言文案，截图 public-profile-live-404.png 已人工检查（profile-live-final.txt）。

## Consequences

站点出于隐私隐藏具体原因，文案不得宣称已删除。不能把普通HTML或任意403都当404，不能跟随跨源重定向传递会话。

404和403权限失败统一提供可理解的状态与登录状态刷新，具体删除原因不由客户端推断。清除失效正文避免继续互动；普通网络错误保留已有内容及重试。网页内导航仍由WebView处理，相关边界见[应用内网页](../feature/2026-10-10-in-app-web-links.md)。检索历史的数据契约、链接和楼层导航笔记部分重叠，继续保留各自职责，没有完全取代或过时提案。
