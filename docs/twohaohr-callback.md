# 2号人事部回调接入说明

## 用途

2号人事部回调用于接收第三方平台主动推送的变更事件，例如员工、部门、审批结果等变化。

当前实现不会通过回调向 2号人事部写数据。回调只负责通知本系统有数据变化，本系统收到事件后会创建 AI 数据源增量同步任务，再通过 2号人事部 OpenAPI 拉取最新数据并写入知识库。

## 回调地址

本地开发：

```text
http://127.0.0.1:48080/open-api/ai/twohaohr/callback?tenantId=1&dataSourceId=7
```

正式配置时需要使用公网 HTTPS 地址，例如：

```text
https://your-domain.example.com/open-api/ai/twohaohr/callback?tenantId=1&dataSourceId=7
```

参数说明：

- `tenantId`：租户 ID，用于限定只同步对应租户的数据源。
- `dataSourceId`：AI 数据源 ID，用于限定只同步指定的 2号人事部 API 数据源。
- `token`：可选回调 Token。如果后端配置了 `TWO_HAO_HR_CALLBACK_TOKEN`，回调请求必须携带相同 token。

Token 可以通过 query 参数或请求头传入：

```text
?token=${TWO_HAO_HR_CALLBACK_TOKEN}
```

或：

```text
X-2HaoHr-Token: ${TWO_HAO_HR_CALLBACK_TOKEN}
```

## 回调响应

回调接收成功后立即返回：

```json
{
  "result_code": "SUCCESS",
  "result_msg": "OK"
}
```

同步任务会在后台执行，不阻塞 2号人事部回调请求。

## 当前事件映射

| 2号人事部事件 | 同步对象 |
| --- | --- |
| `event_test` | 只验证回调连通性，不触发同步 |
| `employee_*` | 员工基础信息 |
| `intent_employee_*` | 员工基础信息 |
| `sign_electronic_contract` | 员工基础信息 |
| `dept_*` | 组织架构 |
| `company_leader_update` | 组织架构 |
| `approve_result` | 审批记录 |

未映射事件只记录日志，不创建同步任务。

## 环境变量

```powershell
[Environment]::SetEnvironmentVariable("TWO_HAO_HR_CALLBACK_TOKEN", "<your-callback-token>", "User")
```

不要把真实 token 写入代码、配置样例或文档。

## 联调命令

测试回调连通性：

```powershell
$body = @{ key = "event_test"; data = @() } | ConvertTo-Json
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:48080/open-api/ai/twohaohr/callback?tenantId=1&dataSourceId=7" `
  -ContentType "application/json" `
  -Body $body
```

测试员工变更增量同步：

```powershell
$body = @{ key = "employee_update"; data = @("example-employee-id") } | ConvertTo-Json
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:48080/open-api/ai/twohaohr/callback?tenantId=1&dataSourceId=7" `
  -ContentType "application/json" `
  -Body $body
```

