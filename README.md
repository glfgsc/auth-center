# Auth Center

## 服务定位
认证中心 — BI 平台的统一身份认证与授权服务。实现 CAS SSO 协议、JWT 令牌签发、RBAC 角色权限管理,作为整个微服务体系的信任锚点。

## 模块结构

| 模块 | 说明 |
|------|------|
| `auth-center-server` | Spring Boot 服务:CAS 服务端、JWT 签发、用户/角色/权限管理、登录页面 |
| `auth-center-admin` | Vue 3 管理控制台 SPA,挂 `/admin` |
| `auth-center-sdk` | 轻量 SDK,供下游服务集成(JWT 验证 + 认证头过滤器) |

## 核心职责
- CAS 协议服务端 — Ticket 生成、Service 验票、单点登录/登出
- JWT 令牌签发 — RSA 签名,可配置过期时间
- JWKS 端点 — 公钥分发,供网关/服务验证
- RBAC 角色权限管理 — 用户 → 权限集 → 能力位绑定,能力目录由各产品自注册(`/api/auth/capabilities`)
- 企业 CAS 委派 — 可委派外部 CAS 认证(SOCKS5 代理穿透内网)
- 平台配置中心 — `auth_platform_config`,按产品(`auth_system`)分域
- 会话与令牌治理 — 在线会话查看/踢下线、登录历史(`/api/auth/admin/sessions`、`/api/auth/admin/login-history`)
- 统一活动审计汇入 — 各 BI 服务经 `/api/auth/audit/internal` 推送,落 `auth_audit_log`
- 外部应用与嵌入 — Connected App 密钥管理、embed 会话铸造(`/api/auth/admin/connected-apps`、`/api/auth/embed/internal`)
- 刷新令牌管理
- 登录界面(Thymeleaf 渲染)
- RSA 密钥对自动生成(首次启动)

## 技术栈
| 技术 | 说明 |
|------|------|
| Spring Boot 3.2 | 应用框架 |
| Spring Security | 安全框架 |
| JWT (JJWT + RSA) | 令牌签发与验证 |
| Thymeleaf | 登录页面渲染 |
| MyBatis-Plus | ORM |
| MySQL | 持久化存储 |
| Redis | 会话/票据存储 |
| Flyway | `auth_center` schema 迁移 |

## 数据库
- Schema: `auth_center` (MySQL)
- 核心表: `auth_user`, `auth_permission_set`, `auth_user_permission_set`, `auth_system`, `auth_system_capability`, `auth_sso_config`, `auth_group`, `auth_group_member`, `auth_connected_app(_secret)`, `auth_platform_config`, `auth_notification_credential`, `auth_audit_log`, `auth_login_history`, `auth_ai_trust_log`, `auth_ai_generation`, `auth_ai_content_signal`（刷新令牌存 Redis，非表）
- Flyway 迁移在 `auth-center-server/src/main/resources/db/migration/`

## 默认端口
`8090`

## 认证流程
1. 用户访问前端 → 网关拦截未认证请求 → 重定向 auth-center 登录页
2. 用户登录 → auth-center 签发 JWT (access + refresh)
3. 后续请求 → 网关校验 JWT → 注入身份头(`X-User-Id` / `X-Username` / `X-Tenant-Id` / `X-Workspace-Id` / `X-Permission-Set` / `X-Capabilities`)→ 下游信任;头名常量见 `auth-center-sdk` 的 `AuthUserDetails`
4. 网关闭环:剥离伪造身份头 + 注入 X-Gateway-Verify 密钥,下游校验

## 重要提示
- auth-center 是独立 Maven 工程,与 bi-backend 不共用 reactor(有自己的 `mvnw`);CI `deploy.yml` 覆盖它的全流程:装 `auth-center-sdk`、打 server 包、构建 admin SPA、出镜像并滚动更新
- 三个安全密钥(JWT secret / Gateway verify / Internal service token)生产环境必须更换
- 企业 CAS 委派场景下验票需经认证的 SOCKS5 代理(必须用 Authenticator,非系统属性)

## 构建 & 运行

`mvn package` 打出的 jar 里嵌的是入库的 `auth-center-server/src/main/resources/static/admin`;镜像里的管理台由 Dockerfile 用 `auth-center-admin/dist/` 覆盖同一路径,故改了控制台必须重新 `npm run build` 再出镜像。

```bash
cd auth-center
./mvnw -B package -DskipTests                         # 打包 server
java -jar auth-center-server/target/auth-center-server-*.jar

( cd auth-center-admin && npm ci && npm run build )   # 生成 admin dist,供镜像覆盖
```

Docker(context = `auth-center/`,Dockerfile 在 `auth-center-server/`,构建前须已生成 admin dist):

```bash
docker build -t auth-center -f auth-center-server/Dockerfile .
```

## 环境要求
- JDK 21 (Temurin)
- Maven 3.9+
- MySQL (`auth_center` schema)
- Redis
