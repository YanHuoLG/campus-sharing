# 校园闲置资源共享平台

依据《校园闲置资源共享平台 V1.0 开发设计文档》开发，当前已完成基础工程、用户模块、物品模块、预约交接，以及管理员用户管理与物品审核。后端已具备从物品发布到线下交接完成的核心流程；微信小程序首版位于独立目录 `E:\ess\campus-sharing-miniprogram`。

## 当前进度

- 已实现：Spring Boot 工程、统一响应和异常处理、数据库健康检查、分类查询、MyBatis-Plus 接入、数据库建表和分类初始化脚本、基础权限规则、集成测试。
- 已实现用户模块：手机号注册、BCrypt 密码、Session 登录退出、个人资料查询与修改、管理员用户查询、封禁/解封及审计日志。
- 已完成本机联调：创建实际 MySQL 项目库和 7 张表、初始化分类、验证中文读写和后端接口。
- 已实现物品模块：发布与修改、1–2 张图片上传与替换、分类和关键词搜索、详情、我的发布、软删除，以及管理员审核通过、驳回和下架。
- 已实现预约交接：留言申请、接受/拒绝、取消、我的申请/收到的申请、六位交接码、重新生成、一次性完成核验、错误次数限制、到期释放，以及物品编辑和管理员下架的预约联动。
- 微信小程序首版已实现 11 个页面及接口调用层，通过静态检查、15 项前端测试、WXML/WXSS 编译和真实后端请求联调；尚待实际 AppID 下的页面预览和真机验收。导入方式见 [小程序 README](../campus-sharing-miniprogram/README.md)。平台账号通过注册创建；项目不提供默认管理员密码。

技术：Java 21、Spring Boot 3.5.16、MyBatis-Plus 3.5.17、MySQL、Maven。自动化测试使用 H2 的 MySQL 兼容模式；另外已完成本机真实 MySQL 的基础接口联调。

## 在 IDEA 中打开

1. 选择 File → Open，打开 `E:\ess\campus-sharing\pom.xml`，作为 Maven 项目导入。
2. 将 Project SDK 和 Maven Runner JRE 设置为 Java 21。
3. Maven 使用项目自带的 Maven Wrapper，等待依赖同步结束。
4. 配置下文的数据库，运行 `cn.edu.campus.sharing.CampusSharingApplication`。

如果 IDEA 在 SQL 或 Mapper 中提示“无法解析表”，查看 [IDEA 数据库与 SQL 解析配置](docs/idea-database.md)。编辑器的数据源需要单独连接并同步项目库。

## 本机 MySQL 配置

2026-10-21 本机检查结果：MySQL267 服务正在运行，`127.0.0.1:3306` 可连接，服务器版本为 26.7.0（设计文档目标为 MySQL 8.x）。已通过管理员创建 `campus_sharing` 数据库，并给 `yanhuo@localhost` 授予仅针对该库的开发权限。7 张表、3 条分类记录已初始化；使用 `yanhuo` 验证中文数据写入、查询及事务回滚成功。

**这台机器已经完成数据库初始化，无需再次执行建表和分类脚本。** 可以直接运行 `scripts/run-local.ps1` 并输入 `yanhuo` 的数据库密码。下文保留新环境的初始化步骤；`docs/mysql-setup.sql` 供数据库管理员使用。

本机已生成被 Git 忽略的 `application-local.yml`，只保存地址和用户名。IDEA 可启用 `local` profile 并设置 `DB_PASSWORD`；也可以在完成初始化后运行 `scripts/run-local.ps1`，它会以隐藏输入方式读取密码。项目文件中不保存真实密码。

使用自己的 MySQL 管理账号新建独立数据库，不要选择已有业务数据库：

```sql
CREATE DATABASE campus_sharing CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

选择 `campus_sharing` 后依次执行：

1. `src/main/resources/db/schema.sql`
2. `src/main/resources/db/data.sql`

建表脚本只供空库执行一次，不含 DROP；重复执行会报已存在。后续结构变更应单独编写迁移脚本。应用不会自动修改本机数据库。

在 IDEA 的 Run/Debug Configuration → Environment variables 配置：

```text
DB_URL=jdbc:mysql://localhost:3306/campus_sharing?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai
DB_USERNAME=你的数据库账号
DB_PASSWORD=你的数据库密码
```

不要把真实密码写进提交的配置文件。默认账号名 `campus_app` 只是配置默认值，项目不创建或重置本机账号。数据库用户需有该库的读写权限。

应用默认监听 `127.0.0.1:8080`。浏览器打开根地址会自动跳转到 `/api/health`，无需登录；当前尚无业务首页。端口冲突可设置 `SERVER_PORT`。同一 Wi-Fi 真机联调使用 `scripts/run-local.ps1 -Lan`，详见 [同一 Wi-Fi 真机联调说明](同一Wi-Fi真机联调.md)。

## 构建与运行

容器部署使用根目录 [Dockerfile](Dockerfile)，构建、数据库连接和图片持久化步骤见 [Docker 部署说明](docs/docker.md)。

Windows PowerShell，在项目目录执行：

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
$env:DB_USERNAME = '你的数据库账号'
$env:DB_PASSWORD = '你的数据库密码'
.\mvnw.cmd spring-boot:run
```

测试不需要本机 MySQL。首次运行 Wrapper 和构建需要网络下载 Maven 与依赖。

当前开发机器也可使用已下载且经 SHA-512 校验的 Maven：

```powershell
.\.tools\apache-maven-3.9.11\bin\mvn.cmd '-Dmaven.repo.local=E:/ess/campus-sharing/.m2/repository' test
```

## 已实现接口

完整参数、权限、返回模型和联调示例见项目根目录的 [接口文档](接口文档.md)。

从环境准备、构建、运行到业务与并发验收，见 [构建运行与测试全流程](构建运行与测试全流程.md)。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 实际查询数据库；连接成功返回 200，失败返回 503 |
| GET | `/api/categories` | 按顺序返回学习资源、生活用品、专业工具 |
| GET | `/api/auth/csrf` | 获取写请求所需的 CSRF 凭证 |
| POST | `/api/auth/register`、`/api/auth/login`、`/api/auth/logout` | 注册、登录、退出 |
| GET/PATCH | `/api/users/me` | 当前用户资料 |
| GET | `/api/admin/users` | 管理员分页查询用户 |
| POST | `/api/admin/users/{id}/ban`、`/unban` | 封禁、解封学生账号 |
| POST/GET | `/api/items` | 含图片发布、分页搜索 |
| GET/PATCH/DELETE | `/api/items/{id}` | 详情、修改、下架 |
| POST | `/api/items/{id}/images` | 替换全部图片并重新审核 |
| GET | `/api/items/{id}/images/{imageId}` | 按物品权限读取图片 |
| GET | `/api/users/me/items` | 我的发布 |
| GET | `/api/admin/items` | 管理员物品列表 |
| POST | `/api/admin/items/{id}/approve`、`/reject` | 通过、驳回审核 |
| DELETE | `/api/admin/items/{id}` | 管理员下架 |
| POST | `/api/items/{id}/reservations` | 留言申请预约 |
| GET | `/api/users/me/reservations`、`/api/users/me/item-reservations` | 我发起、我收到的预约 |
| GET | `/api/reservations/{id}` | 参与者查看详情 |
| POST | `/api/reservations/{id}/accept`、`/reject`、`/cancel` | 接受、拒绝、取消 |
| POST | `/api/reservations/{id}/handover-code`、`/complete` | 重新生成码、核验交接 |

响应格式：

```json
{"code":0,"message":"成功","data":[{"id":1,"name":"学习资源"}]}
```

`code=0` 表示成功；失败时 `code` 使用 HTTP 状态码。根地址跳转、健康、分类和 CSRF 查询允许匿名访问；注册与登录允许匿名提交，但需要 CSRF。其他接口需要登录，`/api/admin/**` 需要管理员角色。详细调用顺序、PowerShell 示例和首个管理员设置见 [用户模块接口说明](docs/user-api.md)。

登录采用 Session，客户端需保存 Cookie；登录后重新获取 CSRF 凭证。所有写请求保留 CSRF 防护。会话闲置 30 分钟失效，服务器重启后需要重新登录。后端没有浏览器登录页；可使用接口工具或旁边的小程序项目，浏览器地址栏不能直接完成注册或登录。

物品表单、上传方式、权限和状态规则见 [物品与审核接口说明](docs/item-api.md)。图片默认保存到 `uploads`，可用 `UPLOAD_DIR` 指定目录；读取图片需要登录，并检查对应物品的可见权限。

完整预约调用示例和状态转换见 [预约与交接接口说明](docs/reservation-api.md)。默认接受后 24 小时内交接，累计最多输错 5 次；可以用 `HANDOVER_TTL_SECONDS` 和 `HANDOVER_MAX_ATTEMPTS` 配置。定时任务每分钟处理一批到期预约；请求内也会校验到期时间。重新生成码不延长时限、不重置错误次数。

## 数据与后续实现约定

- 审核与流转状态独立，默认先审后发。
- `items.contact_info` 保存发布时联系方式，后续接口仅向符合权限规则的登录用户提供。
- 图片槽位只允许 1、2；创建时必须上传 1–2 张，校验实际格式、大小和像素数，重新编码后保存。上传失败随事务回滚清理，替换成功后清理旧文件。
- 物品软删除字段保留预约历史；`updated_at` 在业务更新时显式维护。
- 交接凭证只存哈希；重新生成时更新同一条记录，旧码失效。
- 接受预约通过事务、行锁和物品条件更新，只有成功占用物品的请求才能成功，同事务拒绝其他待确认申请；并发测试已验证。
- 交接码核验与物品、预约完成在同一事务中执行；错误次数会持久化，达到上限或过期后释放占用。
- 修改物品内容或图片会取消旧待确认申请；管理员下架会取消未结束预约，已完成历史继续保留。
- 专业工具精度必填、其他分类不填，已在 DTO 和业务层校验。
- 所有主键使用 BIGINT；数据库字段约束和外键均保留。

## 下一阶段

下一阶段在实际 AppID 下完成微信开发者工具预览、页面交互和 Android/iOS 真机联调，尤其检查会话、双图上传和受保护图片。现有后端复用原有表，无需重建数据库；重启后端即可加载新增接口和过期任务。

## 本次验证结果

2026-10-21：54 项自动化测试通过，0 失败、0 错误，包括 19 项预约交接、13 项物品、13 项用户及 9 项基础工程用例，Maven `verify` 构建通过。

预约用例覆盖：自约/重复申请、权限与封禁、接受后拒绝其他申请、取消、交接码哈希、重生成失效与时限保持、错误次数提交、达到上限释放、过期、定时处理逻辑、编辑下架联动、列表分页，以及并发接受、并发完成、重复申请并发和取消/完成竞争。物品测试覆盖上传和格式校验、可见权限、审核、分页搜索、软删除、审计与回滚。原有用户和基础测试仍全部通过。

基础工程的真实 MySQL 联调已通过：`GET /api/health` 返回 200 且数据库状态为 UP，`GET /api/categories` 返回三条正确中文分类，匿名访问 `GET /api/users/me` 返回 401。

用户模块的真实 MySQL 联调也已通过：使用独立临时数据库和临时后端验证注册、手机号重复、错误密码、Session 登录、中文资料修改、学生与管理员权限隔离、封禁已有会话、封禁账号登录失败、解封、退出后 401 以及两条审计记录。测试服务、临时数据库和临时授权均已清理，正式项目库没有写入测试账号。本机 MySQL 版本为 26.7.0，尚未在 MySQL 8.x 上运行验证。

技术参考：[Spring Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[MyBatis-Plus 安装说明](https://baomidou.com/en/getting-started/install/)。

物品模块真实 MySQL 联调已通过：真实 multipart 上传、中文和特殊字符搜索、图片访问权限、审核通过与驳回、修改后重新审核、可空字段清除、替换提交后旧文件清理、软删除和审计。联调使用独立临时数据库及 18081 端口；临时库、授权和上传文件已清理。已有 8080 服务未被重启，使用新增接口前需在 IDEA 中重新运行后端。

预约交接真实 MySQL 联调已通过：使用不同登录会话同时接受两个申请，只有一个成功；同时核验交接码，只有一次成功；取消与完成竞争后状态保持一致。另验证错误次数确实提交、累计上限释放物品、重新生成码保留到期时间、参与者列表，以及实际定时任务释放过期预约。联调发现并修复首次返回的时间精度与 MySQL `DATETIME` 不一致的问题。测试使用本机 MySQL 26.7.0 的独立临时库，服务、数据库、临时授权和上传文件均已清理；尚未在 MySQL 8.x 上验证。
