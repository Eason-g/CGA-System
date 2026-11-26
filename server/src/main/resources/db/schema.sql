-- =====================================================================
-- 三助岗位一体化管理平台 建表脚本（兼容 MySQL 5.7，utf8mb4）
-- 每个前端集合一张表，列名为前端字段的 snake_case；数组/嵌套对象用 JSON 列。
-- meta 不建表，由 TimeService 实时生成。
-- 本脚本先删后建，执行即重建全部表（数据由 data.sql 导入）。
-- =====================================================================

SET NAMES utf8mb4;

-- P1 第三批：材料附件、财务流水
DROP TABLE IF EXISTS finance_transactions;
DROP TABLE IF EXISTS attachments;
DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS payrolls;
DROP TABLE IF EXISTS evaluations;
DROP TABLE IF EXISTS timesheets;
DROP TABLE IF EXISTS contracts;
DROP TABLE IF EXISTS applications;
DROP TABLE IF EXISTS jobs;
DROP TABLE IF EXISTS sys_config;
DROP TABLE IF EXISTS budgets;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS departments;

-- 院系
CREATE TABLE departments (
  id           VARCHAR(32)  NOT NULL COMMENT '院系ID',
  name         VARCHAR(64)  NOT NULL COMMENT '院系全称',
  short_name   VARCHAR(32)  NOT NULL COMMENT '简称',
  quota_limit  INT          NOT NULL DEFAULT 0 COMMENT '三助岗位名额上限',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='院系';

-- 用户（学生、导师、各级管理员）
CREATE TABLE users (
  id                VARCHAR(32)   NOT NULL,
  username          VARCHAR(64)   NOT NULL COMMENT '登录名',
  password_hash     VARCHAR(100)  NULL COMMENT 'BCrypt 哈希；种子数据为空，启动时设为演示密码',
  name              VARCHAR(64)   NOT NULL,
  role              VARCHAR(20)   NOT NULL COMMENT 'STUDENT/MENTOR/DEPT_ADMIN/INTL_OFFICER/SCHOOL_ADMIN',
  role_label        VARCHAR(32)   NULL,
  portal            VARCHAR(16)   NULL COMMENT 'student/admin',
  preset            TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否预置演示账号',
  preset_desc       VARCHAR(128)  NULL,
  dept_id           VARCHAR(32)   NULL,
  is_international  TINYINT(1)    NOT NULL DEFAULT 0,
  visa_type         VARCHAR(64)   NULL,
  visa_valid        TINYINT(1)    NULL,
  visa_expiry       DATE          NULL,
  language_cert     VARCHAR(64)   NULL,
  language_level    DECIMAL(3,1)  NULL,
  language_valid    TINYINT(1)    NULL,
  language_cert_status VARCHAR(16) NULL COMMENT '语言证明核验状态 PENDING/VERIFIED/REJECTED（空=种子数据）',
  max_weekly_hours  INT           NULL COMMENT '个人周工时上限（只能比全局规则更严）',
  student_no        VARCHAR(32)   NULL,
  degree            VARCHAR(32)   NULL,
  major             VARCHAR(64)   NULL,
  gpa               VARCHAR(16)   NULL,
  skills            JSON          NULL,
  email             VARCHAR(128)  NULL,
  phone             VARCHAR(32)   NULL,
  title             VARCHAR(64)   NULL COMMENT '职称/职务（教职工）',
  bio               VARCHAR(500)  NULL COMMENT '个人简介',
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username),
  UNIQUE KEY uk_users_student_no (student_no),
  KEY idx_users_dept (dept_id),
  KEY idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 经费池
CREATE TABLE budgets (
  id             VARCHAR(32)    NOT NULL,
  code           VARCHAR(32)    NOT NULL COMMENT '经费编号',
  source_type    VARCHAR(20)    NOT NULL COMMENT 'TA_FUND/RA_PROJECT/GA_FUND',
  source_label   VARCHAR(32)    NULL,
  dept_id        VARCHAR(32)    NOT NULL,
  name           VARCHAR(128)   NOT NULL,
  total_amount   DECIMAL(12,2)  NOT NULL DEFAULT 0,
  used_amount    DECIMAL(12,2)  NOT NULL DEFAULT 0 COMMENT '已发放',
  frozen_amount  DECIMAL(12,2)  NOT NULL DEFAULT 0 COMMENT '岗位预留冻结',
  PRIMARY KEY (id),
  KEY idx_budgets_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经费池';

-- 系统配置：workflows / evaluationTemplates / globalRules 各一行
CREATE TABLE sys_config (
  config_key    VARCHAR(64)  NOT NULL,
  config_value  JSON         NOT NULL,
  PRIMARY KEY (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可配置化中心';

-- 岗位
CREATE TABLE jobs (
  id                    VARCHAR(32)    NOT NULL,
  type                  VARCHAR(8)     NOT NULL COMMENT 'TA/RA/GA',
  title                 VARCHAR(128)   NOT NULL,
  dept_id               VARCHAR(32)    NOT NULL,
  mentor_id             VARCHAR(32)    NOT NULL,
  budget_id             VARCHAR(32)    NOT NULL,
  quota                 INT            NOT NULL DEFAULT 0,
  hired_count           INT            NOT NULL DEFAULT 0,
  hourly_rate           DECIMAL(10,2)  NOT NULL DEFAULT 0,
  weekly_hours          INT            NOT NULL DEFAULT 0,
  weeks                 INT            NOT NULL DEFAULT 0,
  allow_international   TINYINT(1)     NOT NULL DEFAULT 0,
  status                VARCHAR(32)    NOT NULL,
  workflow_instance_id  VARCHAR(32)    NULL,
  workflow_steps        JSON           NULL,
  current_step_index    INT            NOT NULL DEFAULT 0,
  deadline              DATE           NULL,
  created_at            DATETIME       NULL,
  skills                JSON           NULL,
  description           TEXT           NULL,
  extra_fields          JSON           NULL,
  reserved_amount       DECIMAL(12,2)  NOT NULL DEFAULT 0 COMMENT '岗位预留预算（quota×时薪×周工时×周数，驳回后为 0）',
  PRIMARY KEY (id),
  KEY idx_jobs_dept (dept_id),
  KEY idx_jobs_mentor (mentor_id),
  KEY idx_jobs_budget (budget_id),
  KEY idx_jobs_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位';

-- 岗位申请
CREATE TABLE applications (
  id                       VARCHAR(32)   NOT NULL,
  job_id                   VARCHAR(32)   NOT NULL,
  student_id               VARCHAR(32)   NOT NULL,
  status                   VARCHAR(32)   NOT NULL,
  applied_at               DATETIME      NULL,
  statement                TEXT          NULL,
  resume_file_name         VARCHAR(128)  NULL,
  attachment_ids           JSON          NULL COMMENT '申请附件 ID 列表（attachments.id）',
  compliance_check_result  JSON          NULL,
  is_special_approval      TINYINT(1)    NOT NULL DEFAULT 0,
  special_reason           TEXT          NULL,
  special_status           VARCHAR(16)   NULL COMMENT 'PENDING/APPROVED/REJECTED',
  interview_info           JSON          NULL,
  score                    INT           NULL,
  mentor_comment           TEXT          NULL,
  PRIMARY KEY (id),
  KEY idx_app_job (job_id),
  KEY idx_app_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位申请';

-- 电子聘用协议
CREATE TABLE contracts (
  id                  VARCHAR(32)    NOT NULL,
  application_id      VARCHAR(32)    NULL,
  student_id          VARCHAR(32)    NOT NULL,
  job_id              VARCHAR(32)    NOT NULL,
  dept_id             VARCHAR(32)    NOT NULL,
  start_date          DATE           NULL,
  end_date            DATE           NULL,
  weekly_hours        INT            NOT NULL DEFAULT 0,
  hourly_rate         DECIMAL(10,2)  NOT NULL DEFAULT 0,
  status              VARCHAR(20)    NOT NULL COMMENT 'PENDING_SIGN/ACTIVE/...',
  signed_at           DATETIME       NULL,
  signature_data_url  MEDIUMTEXT     NULL COMMENT '手写签名图片 dataURL',
  signature_text      VARCHAR(64)    NULL,
  compliance_agreed   TINYINT(1)     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_con_student (student_id),
  KEY idx_con_job (job_id),
  KEY idx_con_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电子聘用协议';

-- 周工时
CREATE TABLE timesheets (
  id              VARCHAR(32)   NOT NULL,
  contract_id     VARCHAR(32)   NOT NULL,
  student_id      VARCHAR(32)   NOT NULL,
  job_id          VARCHAR(32)   NOT NULL,
  dept_id         VARCHAR(32)   NOT NULL,
  week_no         INT           NOT NULL,
  month           VARCHAR(7)    NOT NULL COMMENT 'yyyy-MM',
  hours           DECIMAL(5,1)  NOT NULL DEFAULT 0,
  work_log        TEXT          NULL,
  milestone_ref   VARCHAR(255)  NULL,
  status          VARCHAR(16)   NOT NULL COMMENT 'PENDING/APPROVED/REJECTED',
  submitted_at    DATETIME      NULL,
  mentor_comment  TEXT          NULL,
  PRIMARY KEY (id),
  KEY idx_ts_contract (contract_id),
  KEY idx_ts_student_week (student_id, week_no),
  KEY idx_ts_job (job_id),
  KEY idx_ts_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='周工时';

-- 周期考核
CREATE TABLE evaluations (
  id                VARCHAR(32)   NOT NULL,
  contract_id       VARCHAR(32)   NOT NULL,
  student_id        VARCHAR(32)   NOT NULL,
  job_id            VARCHAR(32)   NOT NULL,
  dept_id           VARCHAR(32)   NOT NULL,
  period            VARCHAR(64)   NULL,
  job_type          VARCHAR(8)    NULL,
  dimensions        JSON          NULL,
  total_score       DECIMAL(5,1)  NULL,
  grade             VARCHAR(16)   NULL,
  coefficient       DECIMAL(4,2)  NULL,
  renewal_eligible  TINYINT(1)    NULL,
  course_eval_sync  JSON          NULL,
  evaluator_name    VARCHAR(64)   NULL,
  comment           TEXT          NULL,
  evaluated_at      DATETIME      NULL,
  PRIMARY KEY (id),
  KEY idx_ev_contract (contract_id),
  KEY idx_ev_student (student_id),
  KEY idx_ev_job (job_id),
  KEY idx_ev_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='周期考核';

-- 月度薪酬
CREATE TABLE payrolls (
  id              VARCHAR(32)    NOT NULL,
  student_id      VARCHAR(32)    NOT NULL,
  contract_id     VARCHAR(32)    NOT NULL,
  job_id          VARCHAR(32)    NOT NULL,
  dept_id         VARCHAR(32)    NOT NULL,
  month           VARCHAR(7)     NOT NULL,
  total_hours     DECIMAL(7,1)   NOT NULL DEFAULT 0,
  hourly_rate     DECIMAL(10,2)  NOT NULL DEFAULT 0,
  coefficient     DECIMAL(4,2)   NOT NULL DEFAULT 1,
  amount          DECIMAL(12,2)  NOT NULL DEFAULT 0,
  budget_id       VARCHAR(32)    NOT NULL,
  source_type     VARCHAR(20)    NULL,
  finance_status  VARCHAR(16)    NOT NULL COMMENT 'PENDING_CALC/PENDING_PAY/SUBMITTED/PAY_FAILED/PAID',
  calculated_at   DATETIME       NULL,
  paid_at         DATETIME       NULL,
  voucher_no      VARCHAR(32)    NULL,
  finance_serial_no VARCHAR(40)  NULL COMMENT '最近一次提交财务的流水号',
  submitted_at    DATETIME       NULL COMMENT '最近一次提交财务时间',
  fail_reason     VARCHAR(255)   NULL COMMENT '发放失败原因',
  PRIMARY KEY (id),
  KEY idx_pay_student (student_id),
  KEY idx_pay_contract_month (contract_id, month),
  KEY idx_pay_job (job_id),
  KEY idx_pay_dept (dept_id),
  KEY idx_pay_budget (budget_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月度薪酬';

-- 站内信
CREATE TABLE notifications (
  id          VARCHAR(32)   NOT NULL,
  user_id     VARCHAR(32)   NOT NULL,
  channel     VARCHAR(32)   NULL,
  title       VARCHAR(255)  NOT NULL,
  content     TEXT          NULL,
  is_read     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '前端字段 read（READ 是 MySQL 保留字）',
  created_at  DATETIME      NULL,
  PRIMARY KEY (id),
  KEY idx_notif_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内信';

-- 审计日志
CREATE TABLE audit_log (
  id             VARCHAR(32)   NOT NULL,
  target_type    VARCHAR(20)   NOT NULL COMMENT 'JOB/APPLICATION/CONTRACT/TIMESHEET/PAYROLL/EVALUATION/CONFIG/USER',
  target_id      VARCHAR(64)   NOT NULL,
  operator_id    VARCHAR(32)   NULL COMMENT '操作人用户ID（种子数据为空）',
  operator_name  VARCHAR(64)   NULL,
  operator_role  VARCHAR(32)   NULL,
  action         VARCHAR(64)   NOT NULL,
  opinion        TEXT          NULL,
  before_value   VARCHAR(255)  NULL,
  after_value    VARCHAR(255)  NULL,
  log_time       DATETIME      NULL COMMENT '前端字段 timestamp',
  PRIMARY KEY (id),
  KEY idx_audit_target (target_type, target_id),
  KEY idx_audit_operator (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';

-- 材料附件（P1 第三批）：文件存 app.upload-dir，库里只存元数据
CREATE TABLE attachments (
  id              VARCHAR(32)   NOT NULL,
  owner_id        VARCHAR(32)   NOT NULL COMMENT '上传人',
  kind            VARCHAR(16)   NOT NULL COMMENT 'RESUME/LANG_CERT/VISA/OTHER',
  original_name   VARCHAR(255)  NOT NULL COMMENT '原文件名',
  stored_name     VARCHAR(64)   NOT NULL COMMENT '磁盘文件名（随机 ID）',
  content_type    VARCHAR(64)   NOT NULL COMMENT '按文件头识别的真实类型',
  size_bytes      BIGINT        NOT NULL,
  sha256          CHAR(64)      NOT NULL,
  verify_status   VARCHAR(16)   NULL COMMENT '语言证明/签证材料核验状态 PENDING/VERIFIED/REJECTED',
  verify_opinion  VARCHAR(255)  NULL,
  verified_by     VARCHAR(32)   NULL,
  verified_at     DATETIME      NULL,
  uploaded_at     DATETIME      NOT NULL,
  PRIMARY KEY (id),
  KEY idx_att_owner (owner_id),
  KEY idx_att_kind_status (kind, verify_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='材料附件';

-- 财务流水（P1 第三批）：每次“提交财务”一条，回调按流水号幂等
CREATE TABLE finance_transactions (
  serial_no      VARCHAR(40)    NOT NULL COMMENT '财务流水号',
  payroll_id     VARCHAR(32)    NOT NULL,
  amount         DECIMAL(12,2)  NOT NULL,
  status         VARCHAR(16)    NOT NULL COMMENT 'SUBMITTED/SUCCESS/FAILED',
  voucher_no     VARCHAR(32)    NULL,
  fail_reason    VARCHAR(255)   NULL,
  submitted_by   VARCHAR(32)    NULL,
  submitted_at   DATETIME       NOT NULL,
  callback_at    DATETIME       NULL,
  PRIMARY KEY (serial_no),
  KEY idx_fin_payroll (payroll_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务流水';
