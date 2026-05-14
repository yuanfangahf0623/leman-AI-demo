# 前端访问地址

更新时间：2026-05-13

## 地址清单

| 类型 | 地址 | 说明 |
| --- | --- | --- |
| 本地前端地址 | http://localhost | 本机浏览器访问，当前前端服务监听 `0.0.0.0:80`。 |
| 内网前端地址 | http://192.168.19.36 | 同一内网设备访问，需要本机网络和防火墙允许 80 端口访问。 |
| 外网前端地址 | https://eos-reflections-gray-surveys.trycloudflare.com | 当前 Cloudflare quick tunnel 映射到 `http://localhost:80`。 |

## 使用说明

- 本地和内网地址依赖前端开发服务保持运行。
- 当前本地开发模式下，前端通过同源 `/admin-api` 代理访问后端 `http://127.0.0.1:48080`。局域网用户不要直接访问 `http://localhost:48080`，因为 `localhost` 会指向访问者自己的电脑。
- 外网地址由 `cloudflared tunnel --url http://localhost:80` 生成，属于 quick tunnel 临时地址，不保证长期固定。
- 如果 cloudflared 进程退出或重新启动，外网地址可能会变化，需要重新记录。

## 当前连通性检查

本次写入前已检查 3 个地址，均返回 HTTP 200。

## 登录排查

- 如果局域网设备能打开页面但登录失败，优先检查浏览器 Network 里的登录请求地址。
- 正确地址应为 `http://192.168.19.36/admin-api/...`，不应为 `http://localhost:48080/admin-api/...`。
- 修改前端代理配置后，需要重启前端开发服务，并让局域网用户强制刷新页面。
