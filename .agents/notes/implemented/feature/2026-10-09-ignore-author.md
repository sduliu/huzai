# Agent Note: 屏蔽使用网站忽略用户

Status: implemented

## Problem

用户用网站个人主页截图明确屏蔽应使用“已忽略”：隐藏作者内容与相关通知，需要与网站账号同步，不能仅隐藏当前主题。

## Decision

此决定替代[主题操作](../../implemented/feature/2026-10-09-topic-actions.md)中尚未实现的本机隐藏方案，其余历史和举报契约保持。列表屏蔽打开作者确认及期限选择，PUT /u/{username}/notification_level.json，notification_level=ignore，expiring_at 使用 ISO 时间。新增忽略遵循 can_ignore_users，恢复 normal 不要求仍有新增权限，禁止自己，服务器继续校验目标权限。期限覆盖一天、两周、一月、半年、一年、永久；永久遵循网站时间快捷项的远期日期。

确认成功后更新当前账号的 ignored_users 并刷新列表；列表与详情按账号忽略集合隐藏作者内容，详情占位允许手动展开。设置列出忽略用户并用 notification_level=normal 恢复。账号变化时清空旧集合并重新读取网站，不使用独立本机屏蔽持久化。

## Alternatives considered

本机只隐藏主题可以保护通知偏好、支持访客且实现撤销简单，是初版最强理由；用户新截图明确需要网站忽略用户，因此采用账号级忽略，明确提示范围和期限。

## Testing

最终123项JVM及34项界面回归通过；新增界面覆盖明确作者、期限必选、两种列表隐藏、详情单楼展开。真实忽略与恢复未写入社区，网络字段和失败行为由模拟服务器验证。

UserIgnoreContractTest 的恢复权限复现先失败，修正后通过；覆盖 ignore/normal、期限、自己与无权限防护、账号忽略集合和永久日期。原生设置已列出网站 ignored_users，可逐个恢复。

模拟服务器验证 ignore / normal 字段、期限、中文或特殊用户名编码、失败不更新账号；真机只查看确认弹窗，不对真实作者提交屏蔽。退出和切换账号不泄漏忽略集合。

## Consequences

网站可能禁止忽略管理员或信任等级不足，呈现实际错误。网络结果未知时不重发。远端过期及外部变更需要重新同步会话。
