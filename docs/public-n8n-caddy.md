# n8n 和 2 号人事部公网 HTTPS 反代配置

## 目标链路

```text
ChatGPT / 外网
  -> https://n8n.leman-tech.com
  -> 公网 IP 60.216.83.130:443
  -> 路由器/NAT 转发到 Windows 虚拟机 8443
  -> Caddy
  -> http://127.0.0.1:5678
  -> n8n
```

```text
2 号人事部 / 外网回调
  -> https://twohaohr.leman-tech.com
  -> 公网 IP 60.216.83.130:443
  -> 路由器/NAT 转发到 Windows 虚拟机 8443
  -> Caddy
  -> http://127.0.0.1:48080
  -> yudao-server
```

## DNS 配置

阿里云 DNS 使用 A 记录，不切换主域名 NS：

| 类型 | 主机记录 | 记录值 | 用途 |
| --- | --- | --- | --- |
| A | n8n | 60.216.83.130 | n8n Webhook 和 ChatGPT Actions |
| A | twohaohr | 60.216.83.130 | 2 号人事部回调 |

## NAT 转发

路由器、防火墙或出口网关需要配置：

| 外网端口 | 内网目标 |
| --- | --- |
| TCP 80 | Windows 虚拟机 IP:8080 |
| TCP 443 | Windows 虚拟机 IP:8443 |

说明：外部访问仍然使用标准地址 `https://n8n.leman-tech.com` 和 `https://twohaohr.leman-tech.com`，只是内网转发目标端口使用 8080/8443。

## Caddy 配置

配置文件：

```text
F:\GitHub\leman-AI-demo\deploy\caddy\Caddyfile.n8n
```

当前 Caddy 按域名分流：

| 域名 | 目标服务 |
| --- | --- |
| `n8n.leman-tech.com` | n8n `127.0.0.1:5678` |
| `twohaohr.leman-tech.com/open-api/ai/twohaohr/callback*` | 后端 `127.0.0.1:48080` |
| `twohaohr.leman-tech.com` 其它路径 | 返回 404 |

启动命令：

```powershell
F:\GitHub\leman-AI-demo\scripts\start-caddy-n8n.ps1
```

当前用户登录自启动项：

```text
HKCU\Software\Microsoft\Windows\CurrentVersion\Run\LemanCaddyN8n
```

## n8n 环境变量

```text
WEBHOOK_URL=https://n8n.leman-tech.com/
N8N_HOST=n8n.leman-tech.com
N8N_PROTOCOL=https
```

## 2 号人事部回调地址

2 号人事部后台应配置为：

```text
https://twohaohr.leman-tech.com/open-api/ai/twohaohr/callback?tenantId=1&dataSourceId=7&token=<your-callback-token>
```

`token` 使用 `TWO_HAO_HR_CALLBACK_TOKEN` 对应的值，不要写入代码或文档。
