# CGA-System 后端（cga-server）

三助（TA/RA/GA）岗位一体化管理平台的 Java 后端。规格见 `docs/后端设计.md`。

技术栈：Java 17（`maven.compiler.release=17`，JDK 17/21/25 均可编译）、Spring Boot 4.1.2、MyBatis-Plus 3.5.18（boot4 starter）、MySQL 5.7+、Spring Security + JJWT 0.13.0、Lombok、Apache POI 5.5.1（报表导出）。

## 1. 建库

本机需要 MySQL 5.7 及以上，监听 `localhost:3306`。

1. 用有建库权限的账号执行建库脚本（创建 `cga`、`cga_test` 两个库和专用账号 `cga_app`）：

   ```bash
   mysql -u root -p < server/db/create-app-user.local.sql
   ```

   该脚本只在本机使用、不入库（`*.local.sql` 已被 `server/.gitignore` 忽略）。

2. 确认 `server/src/main/resources/application-local.yml` 中的账号密码与脚本一致（该文件同样不入库）：

   ```yaml
   spring:
     datasource:
       username: cga_app
       password: <与建库脚本一致>
   ```

表结构与种子数据不需要手动导入：应用首次启动发现 `users` 表不存在时，会自动执行
`db/schema.sql` + `db/data.sql`，并把所有用户的空密码设为演示密码。

## 2. 启动

```bash
cd server
mvn spring-boot:run
```

服务端口 `8088`（可用环境变量 SERVER_PORT 修改）。CORS 已放行前端开发地址 `http://localhost:5173` 与 `http://localhost:5174`。

快速验证：

```bash
curl -X POST http://localhost:8088/api/auth/login -H "Content-Type: application/json" \
     -d "{\"account\":\"zhou.guoqiang\",\"password\":\"Cga@2026\"}"
# 用返回的 data.token 访问：
curl http://localhost:8088/api/snapshot -H "Authorization: Bearer <token>"
```

## 3. 测试

```bash
cd server
mvn test
```

集成测试使用 `src/test/resources/application-test.yml`，连接 `cga_test` 库（账号同样取自 `application-local.yml`），
每个测试类开始前会删表重建并导入种子数据，**不要把 `cga_test` 指向有用的数据**。

| 测试类 | 内容 | 是否需要数据库 |
|---|---|---|
| `TimeServiceTest` | 种子日期 2026-11-20 → 第 12 周、2026-11 | 否 |
| `UnitChecksTest` | 实体 JSON 字段名、SqlCondition 组合、打码、配置绑定 | 否 |
| `AuthIntegrationTest` | 登录成功、密码错误 401、无 token 401、学生调管理接口 403、演示数据重置 | 是 |
| `DataScopeIntegrationTest` | 数学学院导师看不到计算机学院岗位、按 ID 取外院岗位 404、学生看不到别人的申请和薪酬、院系管理员/国际生办范围 | 是 |
| `SnapshotIntegrationTest` | 校级管理员快照条数与 `seedData.js` 一致、字段名与 mock 的 db 对象一致 | 是 |
| `RuleEngineTest` | 规则引擎每个函数的通过/拦截分支及中文文案（与 `ruleEngine.js` 一致） | 否 |
| `MainFlowIntegrationTest` | 建岗 → 院系初审 → 国际生办会签 → 申请 → 面试 → 录用 → 签约 → 填报 → 审核 → 核算 → 发放，断言经费 frozen/used 与审计日志条数 | 是 |
| `P0FixesIntegrationTest` | 三个已修漏洞：连续录用第三个岗位被拦截、签证无效发起特批 403、国际生上限 12h 拦截 14h 岗位 | 是 |
| `BudgetIntegrationTest` | 建岗超预算/超名额拦截且冻结额不变、驳回释放预留、核算超预算拦截、发放 used/frozen 变化、考核改系数超预算保留原金额、配置校验 | 是 |
| `ConcurrentHireIntegrationTest` | 两个线程各自独立事务同时录用同一学生到两个岗位，只有一个成功（重复 3 次） | 是 |
| `WritePermissionIntegrationTest` | 每个写接口的错角色 403 / 越权 404 / 参数 400，快照 `hoursSummary` | 是 |
| `ReportExportIntegrationTest` | P1 报表导出：5 种报表 × xlsx/csv（POI 读回校验表头和行数）、csv 带 BOM、公式注入转义、导师/院系管理员数据范围、学生 403、导出写审计日志 | 是 |
| `AuditChainIntegrationTest` | P1 审计防篡改：种子日志补算、改 opinion / 删日志 / 改 prev_hash 能被校验报出、回滚不留空洞、20 线程并发追加链不分叉、老库自动补列、非校级管理员 403 | 是 |

只跑不依赖数据库的测试：`mvn test -Dtest='TimeServiceTest,UnitChecksTest,RuleEngineTest*'`。

## 4. 演示账号

所有账号的演示密码均为 **`Cga@2026`**（配置项 `app.demo-password`）。登录时 account 可填用户名、学号或邮箱。

| 用户名 | 姓名 | 角色 | 说明 |
|---|---|---|---|
| `alex.chen`（学号 2025210891） | Alex Chen（陈亚历） | STUDENT | 国际生，有一份待签合同 |
| `lin.zhiyuan`（学号 2023011420） | 林致远 | STUDENT | 国内生，已任 1 个 RA |
| `zhang.mingyuan` | 张明远教授 | MENTOR | 计算机学院导师 |
| `li.wanqing` | 李婉清 | DEPT_ADMIN | 计算机学院管理员 |
| `wang.haitao` | 王海涛 | INTL_OFFICER | 国际生办审核员 |
| `zhou.guoqiang` | 周国强 | SCHOOL_ADMIN | 校级系统/财务管理员 |
| `su.yutong`、`elena.rossi` | — | STUDENT | 背景学生 |
| `chen.shouren`、`zhao.minghua` | — | MENTOR | 数学学院、经管学院导师（院系隔离演示） |

## 5. 配置项（application.yml）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `spring.profiles.include` | `local` | 加载 `application-local.yml` 中的数据库账号 |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/cga?...` | 测试环境改为 `cga_test` |
| `app.today` | `2026-11-20` | 演示日期，与前端种子数据对齐；**留空则使用系统日期** |
| `app.semester-start` | `2026-08-31` | 学期第一天，当前周次 = (today − 该日期) 天数 / 7 + 1 |
| `app.demo-password` | `Cga@2026` | 启动时把 `password_hash` 为空的用户设为该密码的 BCrypt 哈希 |
| `app.jwt.secret` | 开发默认值 | HS256 密钥，至少 32 字节；**生产环境必须通过环境变量 `APP_JWT_SECRET` 覆盖** |
| `app.jwt.ttl` | `8h` | token 有效期 |
| `app.db.init` | `if-empty` | `if-empty`：users 表不存在时建表导数据；`always`：每次启动重建；`never`：不动 |
| `app.demo.reset-enabled` | `true` | 是否允许校级管理员调用 `POST /api/demo/reset` |
| `app.cors.allowed-origins` | 5173、5174 | 允许跨域的前端地址 |

## 6. 已实现接口（第一批）

| 接口 | 权限 | 说明 |
|---|---|---|
| `POST /api/auth/login` `{account, password}` | 公开 | 返回 `{ token, user }`；失败统一 401「账号或密码错误」 |
| `GET /api/auth/me` | 已登录 | 当前用户 |
| `GET /api/snapshot` | 已登录 | 与 mock `db` 同结构，按角色在 SQL 层裁剪（见 `scope/DataScope.java`） |
| `POST /api/demo/reset` | SCHOOL_ADMIN | 重建全部表与种子数据，返回新的快照 |

返回体统一为 `{ code, data, message }`，HTTP 状态码与 `code` 一致。

## 6.1 业务写接口（第二批）

| 接口 | 权限（操作人一律取自 JWT） |
|---|---|
| `POST /api/jobs` | MENTOR、DEPT_ADMIN 只能为本院系建岗，SCHOOL_ADMIN 不限；经费须在本人可见范围内 |
| `POST /api/jobs/{id}/review` `{decision, opinion}` | 岗位须待审；当前节点角色才可审（院系初审→本院系 DEPT_ADMIN，国际生办会签→INTL_OFFICER，校级终审→SCHOOL_ADMIN），SCHOOL_ADMIN 可代审；外院岗位 404 |
| `POST /api/jobs/{id}/milestones/{msId}/toggle` | 岗位导师本人、SCHOOL_ADMIN |
| `POST /api/applications` | STUDENT 本人 |
| `POST /api/applications/{id}/special-review` | INTL_OFFICER、SCHOOL_ADMIN；申请须待特批 |
| `POST /api/applications/{id}/interview` | 岗位导师本人、SCHOOL_ADMIN；状态须为 SUBMITTED / SPECIAL_APPROVED |
| `POST /api/applications/{id}/decision` | 岗位导师本人、SCHOOL_ADMIN；HIRE 走录用复核 |
| `POST /api/contracts/{id}/sign` | 合同所属学生；状态须为 PENDING_SIGN |
| `POST /api/timesheets` | STUDENT 本人，合同须 ACTIVE，周次不晚于当前周 |
| `POST /api/timesheets/{id}/review` | 岗位导师本人、本院系 DEPT_ADMIN、SCHOOL_ADMIN；状态须为 PENDING |
| `POST /api/payrolls/generate` `{month}` | DEPT_ADMIN（只核算本院系）、SCHOOL_ADMIN；返回 `{ count, blocked }` |
| `POST /api/payrolls/{id}/payout` | SCHOOL_ADMIN |
| `POST /api/evaluations` | 岗位导师本人、本院系 DEPT_ADMIN、SCHOOL_ADMIN |
| `PUT /api/configs` | SCHOOL_ADMIN；考核权重和为 100，工时上限为 1–40 的整数 |
| `POST /api/notifications/read-all` | 本人 |
| `PUT /api/users/me` `{email, phone, skills, bio}` | 本人 |

不在数据范围内的 ID 一律返回 404；角色不符返回 403；被规则拦截时 `data` 为校验结果对象（如 `checks`）。

**并发控制**：每个写接口一个事务（隔离级别 READ COMMITTED），涉及名额、工时、预算的检查前先 `SELECT … FOR UPDATE`
锁行（SQL 见 `domain/mapper/*Mapper.java`）：录用锁学生行 → 岗位行 → 申请行；申请、工时填报锁学生行；
建岗锁院系行 → 经费行；驳回锁岗位行 → 经费行；核算、发放、考核改系数锁经费行 → 薪酬行。
使用 READ COMMITTED 是为了让“拿到锁之后”的普通查询能读到其他事务刚提交的数据（REPEATABLE READ 下会读到加锁前的快照）。

**快照补充**：`users` 中每个学生带 `hoursSummary: { allocated, max }`，allocated 按该生全校全部生效和待签合同汇总，
只给合计、不给合同明细。

## 6.2 报表与审计（P1 第二批）

| 接口 | 权限 | 说明 |
|---|---|---|
| `GET /api/reports/{type}/export?format=xlsx\|csv&month=&deptId=` | MENTOR、DEPT_ADMIN、INTL_OFFICER、SCHOOL_ADMIN（学生 403） | type：`jobs` 岗位统计 / `hires` 聘用名单 / `timesheets` 工时汇总 / `payrolls` 薪酬发放 / `budgets` 经费执行。内容用 DataScope 同一套 SQL 条件过滤，与快照范围一致；`deptId` 只能在范围内再收窄。`month` 筛选工时、薪酬的月份，聘用名单取聘期覆盖该月的合同，岗位和经费不按月筛选。文件名如 `聘用名单_2026-11.xlsx`（`filename*` 编码中文，未传 month 时用当前月份）。每次导出写一条 `target_type=REPORT` 的审计日志 |
| `GET /api/audit/verify` | SCHOOL_ADMIN | 从头重算哈希链，返回 `{ ok, checked, brokenAt: { id, seq, reason, detail } }`，reason 为「内容被改 / 链断开 / 被删除导致序号不连续」 |

- **xlsx**：Apache POI `poi-ooxml` 5.5.1（2025-11 发布的 5.x 维护版，与 Boot 4.1.2 无冲突；未选 2026-10 刚发布的 6.0.0 大版本）。表头加粗、冻结首行、开启筛选；列宽按内容估算（中文按 2 个字符宽），没有用 `autoSizeColumn`，因为它依赖 AWT 字体，对中文估算不准。
- **csv**：UTF-8 带 BOM、CRLF 换行。两种格式都对以 `= + - @`、制表符、回车开头的文本单元格加单引号防公式注入（数值单元格不处理）。
- **哈希链**：`audit_log` 新增 `seq`、`prev_hash`、`hash`（seq 唯一索引），单行表 `audit_chain_head` 记录链尾。`AuditService.log` 统一交给 `AuditChainService.append`：在业务事务内时日志先暂存，到**提交前（beforeCommit）**才 `SELECT … FOR UPDATE` 锁链头、分配序号、计算哈希并插入。这样链头锁总是事务里最后拿的锁，不会和业务的锁顺序交叉造成死锁；业务回滚时日志一起丢弃，序号不留空洞。`seq` 不用 AUTO_INCREMENT，同样是为了回滚不产生空洞。
- **初始化**：重建或启动时把 `seq` 为空的日志（种子数据）按 `log_time, id` 顺序补算接到链尾；P0 时建的老库缺列时会自动 `ALTER TABLE` 补列并建链头表（`app.db.init=never` 时也会做这一步，否则写日志会失败）。
- 时间戳截断到秒再参与哈希，与 DATETIME 精度一致。
- 局限：哈希链能**发现**篡改，但不能**阻止**拥有数据库权限的人把整条链重算；要防住这一点需要把链头哈希定期存证到库外。

## 7. 种子数据

`src/main/resources/db/data.sql` 由前端种子数据生成，**不要手改**。修改 `src/mock/seedData.js` 后在仓库根目录执行：

```bash
node scripts/gen-seed-sql.mjs
```

岗位的 `reserved_amount` 按 `quota × hourlyRate × weeklyHours × weeks` 计算（与 `ruleEngine.js` 的 `calcJobBudgetCost` 一致），已驳回岗位为 0。

## 8. 与设计文档的差异说明

- JSON 列使用自写的 `domain/JsonColumnTypeHandler`（基于 Jackson 3）。Spring Boot 4 默认使用 Jackson 3（`tools.jackson`），而 MyBatis-Plus 的 `JacksonTypeHandler` 基于 Jackson 2。
- 引入了 Lombok 以减少实体样板代码；JDK 23 起默认不执行类路径上的注解处理器，`pom.xml` 中已显式配置 `annotationProcessorPaths`。
- JJWT 的 `jjwt-jackson` 运行时依赖 Jackson 2，与应用使用的 Jackson 3 包名不同，可以共存。
- 数据库初始化器实现 `SmartInitializingSingleton`（而非 `ApplicationRunner`），在内嵌 Web 服务器开始监听端口之前完成建表与演示密码设置，避免第一次启动时登录偶发 401。
- 薪酬发放除规格要求的 `amount ≤ total − used` 外，还要求超出本岗位剩余预留的部分不超过经费可用额，防止挪用其他岗位的预留。
