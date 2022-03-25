# maimai-bot-guild

[maimai-bot](https://github.com/xszqxszq/maimai-bot) 的 QQ 频道扩展。

**如果您仅需部署在普通 QQ 群使用的 maimai bot，请移步 [maimai-bot](https://github.com/xszqxszq/maimai-bot)**

## 部署步骤

**（警告：此扩展插件的部署需要一定前置知识，流程可能比较复杂）**

### 1. 安装 [go-cqhttp](https://github.com/Mrs4s/go-cqhttp)

本插件依赖的 mirai-console-loader 使用 HTTP 方式与 go-cqhttp 通信，默认端口为 19198，反向通信端口为 19199。

**如果您没有特殊需求，可以直接替换 go-cqhttp 的 `config.yml` 中 `servers` 项为以下内容：**

```yaml
servers:
  - http:
      host: 127.0.0.1
      port: 19198
      timeout: 5
      long-polling:
        enabled: false
        max-queue-size: 2000
      middlewares:
        <<: *default
      post:
        - url: 'http://127.0.0.1:19199'
          secret: ''
```

如果您有其他插件也依赖 go-cqhttp，可以从`- http：`这一行开始复制并粘贴到 `servers` 项尾部。 若端口有冲突，可以在 mcl 安装目录中修改 `config/xyz.xszq.mirai-guild-cqhttp/config.yml`。

### 2. 在 mirai-console-loader 中安装 [maimai-bot](https://github.com/xszqxszq/maimai-bot) 和 [mirai-guild-cqhttp](https://github.com/xszqxszq/mirai-guild-cqhttp) 前置插件

本插件仅为扩展插件，请确保安装 [maimai-bot](https://github.com/xszqxszq/maimai-bot) 和 [mirai-guild-cqhttp](https://github.com/xszqxszq/mirai-guild-cqhttp) 两个插件。