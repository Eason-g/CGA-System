// Structured Seed Data for 高校“三助”（TA/RA/GA）岗位一体化管理与招聘平台
// Date anchor: 2026-11-20 (Fall Semester 2026-2027, Week 12)

export const CURRENT_WEEK_NO = 12
export const CURRENT_MONTH = '2026-11'
export const TODAY_STR = '2026-11-20'

export function createSeedData() {
  return {
    meta: {
      version: '1.1.0',
      currentWeekNo: CURRENT_WEEK_NO,
      currentMonth: CURRENT_MONTH,
      seededAt: '2026-11-20 08:30:00'
    },

    departments: [
      { id: 'DEPT_CS', name: '计算机科学与技术学院', shortName: '计算机学院', quotaLimit: 22 },
      { id: 'DEPT_MATH', name: '数学科学学院', shortName: '数学学院', quotaLimit: 14 },
      { id: 'DEPT_ECON', name: '经济管理学院', shortName: '经管学院', quotaLimit: 12 },
      { id: 'DEPT_OFFICE', name: '校级行政与教学保障部', shortName: '校级机关', quotaLimit: 12 }
    ],

    // 6 Preset Demo Accounts + Supporting Accounts for Multi-Dept & Isolation Demos
    users: [
      {
        id: 'U_INTL_STU',
        username: 'alex.chen',
        name: 'Alex Chen（陈亚历）',
        role: 'STUDENT',
        roleLabel: '国际生申请人',
        portal: 'student',
        preset: true,
        presetDesc: '外籍硕士生 · X1签证 · 上限20h/周 · IELTS 7.0',
        deptId: 'DEPT_CS',
        isInternational: true,
        visaType: 'X1学习居留许可',
        visaValid: true,
        visaExpiry: '2027-07-31',
        languageCert: 'IELTS 7.0',
        languageLevel: 7.0,
        languageValid: true,
        maxWeeklyHours: 20,
        studentNo: '2025210891',
        degree: '硕士研究生',
        major: '人工智能',
        gpa: '3.82 / 4.0',
        skills: ['Python', '机器学习', '英语授课', '算法', 'PyTorch'],
        email: 'alex.chen@univ.edu.cn',
        phone: '138-0010-2091'
      },
      {
        id: 'U_CN_STU',
        username: 'lin.zhiyuan',
        name: '林致远',
        role: 'STUDENT',
        roleLabel: '多岗中国学生',
        portal: 'student',
        preset: true,
        presetDesc: '计算机学院本科生 · 已任1个RA(12h/周)',
        deptId: 'DEPT_CS',
        isInternational: false,
        visaType: null,
        visaValid: true,
        visaExpiry: null,
        languageCert: 'CET-6 586',
        languageLevel: 6.5,
        languageValid: true,
        maxWeeklyHours: 20,
        studentNo: '2023011420',
        degree: '本科生（大四）',
        major: '计算机科学与技术',
        gpa: '3.91 / 4.0',
        skills: ['C++', '分布式系统', 'Python', '机器学习', '办公自动化'],
        email: 'lin.zhiyuan@univ.edu.cn',
        phone: '139-1024-8812'
      },
      {
        id: 'U_MENTOR',
        username: 'zhang.mingyuan',
        name: '张明远教授',
        role: 'MENTOR',
        roleLabel: '科研/课程导师',
        portal: 'admin',
        preset: true,
        presetDesc: '计算机学院教授 · 发布TA/RA · 选拔与工时审核',
        deptId: 'DEPT_CS',
        isInternational: false,
        title: '计算机学院教授 / 博士生导师',
        email: 'zhang.my@univ.edu.cn'
      },
      {
        id: 'U_DEPT_ADMIN',
        username: 'li.wanqing',
        name: '李婉清',
        role: 'DEPT_ADMIN',
        roleLabel: '院系管理员',
        portal: 'admin',
        preset: true,
        presetDesc: '计算机学院教务/学工办 · 仅见本院系数据',
        deptId: 'DEPT_CS',
        isInternational: false,
        title: '计算机学院教务与学工办主任',
        email: 'li.wq@univ.edu.cn'
      },
      {
        id: 'U_INTL_OFFICER',
        username: 'wang.haitao',
        name: '王海涛',
        role: 'INTL_OFFICER',
        roleLabel: '国际生办审核员',
        portal: 'admin',
        preset: true,
        presetDesc: '国际合作交流处 · 国际生TA会签与特批留痕',
        deptId: 'DEPT_OFFICE',
        isInternational: false,
        title: '国际合作交流处合规审核专员',
        email: 'wang.ht@univ.edu.cn'
      },
      {
        id: 'U_SCHOOL_ADMIN',
        username: 'zhou.guoqiang',
        name: '周国强',
        role: 'SCHOOL_ADMIN',
        roleLabel: '校级系统/财务管理员',
        portal: 'admin',
        preset: true,
        presetDesc: '研究生院 + 财务处 · 全校视图 · 预算发放与配置',
        deptId: 'DEPT_OFFICE',
        isInternational: false,
        title: '研究生院及财务处联合管理员',
        email: 'zhou.gq@univ.edu.cn'
      },
      // Background users for multi-student and cross-department isolation demos
      {
        id: 'U_STU_SU',
        username: 'su.yutong',
        name: '苏雨桐',
        role: 'STUDENT',
        roleLabel: '博士生助教',
        portal: 'student',
        preset: false,
        deptId: 'DEPT_CS',
        isInternational: false,
        visaType: null,
        visaValid: true,
        languageCert: 'TOEFL 104',
        languageLevel: 7.5,
        languageValid: true,
        maxWeeklyHours: 20,
        studentNo: '2024110305',
        degree: '博士研究生',
        major: '计算机系统结构',
        gpa: '3.88 / 4.0',
        skills: ['Python', '机器学习', '英语授课', '分布式系统'],
        email: 'su.yt@univ.edu.cn',
        phone: '136-2290-1120'
      },
      {
        id: 'U_STU_ELENA',
        username: 'elena.rossi',
        name: 'Elena Rossi（艾莲娜）',
        role: 'STUDENT',
        roleLabel: '外籍硕士生',
        portal: 'student',
        preset: false,
        deptId: 'DEPT_MATH',
        isInternational: true,
        visaType: 'X1学习居留许可',
        visaValid: true,
        visaExpiry: '2027-06-30',
        languageCert: 'IELTS 6.0',
        languageLevel: 6.0,
        languageValid: true,
        maxWeeklyHours: 20,
        studentNo: '2025220418',
        degree: '硕士研究生',
        major: '应用数学',
        gpa: '3.76 / 4.0',
        skills: ['微积分', '线性代数', 'LaTeX', '英语授课'],
        email: 'elena.rossi@univ.edu.cn',
        phone: '137-0082-3319'
      },
      {
        id: 'U_MENTOR_MATH',
        username: 'chen.shouren',
        name: '陈守仁教授',
        role: 'MENTOR',
        roleLabel: '科研/课程导师',
        portal: 'admin',
        preset: false,
        deptId: 'DEPT_MATH',
        isInternational: false,
        title: '数学科学学院教授',
        email: 'chen.sr@univ.edu.cn'
      },
      {
        id: 'U_MENTOR_ECON',
        username: 'zhao.minghua',
        name: '赵明华副教授',
        role: 'MENTOR',
        roleLabel: '科研/课程导师',
        portal: 'admin',
        preset: false,
        deptId: 'DEPT_ECON',
        isInternational: false,
        title: '经济管理学院副教授',
        email: 'zhao.mh@univ.edu.cn'
      }
    ],

    budgets: [
      {
        id: 'B_CS_TA',
        code: 'JW-2026-CS01',
        sourceType: 'TA_FUND',
        sourceLabel: '教务经费',
        deptId: 'DEPT_CS',
        name: '计算机学院本科教学助教专项经费',
        totalAmount: 120000,
        usedAmount: 58200,
        frozenAmount: 17280
      },
      {
        id: 'B_CS_RA',
        code: 'NSFC-62572018',
        sourceType: 'RA_PROJECT',
        sourceLabel: '科研项目经费',
        deptId: 'DEPT_CS',
        name: '国自然面上项目-大模型高效推理课题',
        totalAmount: 96000,
        usedAmount: 42800,
        frozenAmount: 15360
      },
      {
        id: 'B_CS_RA_TIGHT',
        code: 'KY-2026-CS09',
        sourceType: 'RA_PROJECT',
        sourceLabel: '科研项目经费',
        deptId: 'DEPT_CS',
        name: '青年探索微额科研基金（余额紧张）',
        totalAmount: 18000,
        usedAmount: 15800,
        frozenAmount: 1000
      },
      {
        id: 'B_GA_SCHOOL',
        code: 'XZ-2026-GA01',
        sourceType: 'GA_FUND',
        sourceLabel: '行政专项',
        deptId: 'DEPT_OFFICE',
        name: '校级勤工助学与行政助管专项经费',
        totalAmount: 150000,
        usedAmount: 72400,
        frozenAmount: 10080
      },
      {
        id: 'B_MATH_TA',
        code: 'JW-2026-MA01',
        sourceType: 'TA_FUND',
        sourceLabel: '教务经费',
        deptId: 'DEPT_MATH',
        name: '数学科学学院公共基础课助教经费',
        totalAmount: 90000,
        usedAmount: 77400,
        frozenAmount: 3600
      },
      {
        id: 'B_ECON_RA',
        code: 'KY-2026-EC02',
        sourceType: 'RA_PROJECT',
        sourceLabel: '科研项目经费',
        deptId: 'DEPT_ECON',
        name: '数字经济交叉实验室科研助研经费',
        totalAmount: 75000,
        usedAmount: 31200,
        frozenAmount: 7200
      }
    ],

    // Configurable Workflow & Evaluation Engine (P1 可配置化中心)
    configs: {
      workflows: {
        TA: [
          { key: 'MENTOR_SUBMIT', label: '导师提交申请', role: 'MENTOR' },
          { key: 'DEPT_REVIEW', label: '院系名额与经费初审', role: 'DEPT_ADMIN' },
          { key: 'INTL_REVIEW', label: '国际生办专项会签', role: 'INTL_OFFICER', conditional: 'allowInternational' },
          { key: 'PUBLISHED', label: '正式发布上线', role: 'SYSTEM' }
        ],
        RA: [
          { key: 'MENTOR_SUBMIT', label: '导师提交申请', role: 'MENTOR' },
          { key: 'DEPT_REVIEW', label: '院系名额与课题审核', role: 'DEPT_ADMIN' },
          { key: 'PUBLISHED', label: '正式发布上线', role: 'SYSTEM' }
        ],
        GA: [
          { key: 'MENTOR_SUBMIT', label: '行政科室申报', role: 'MENTOR' },
          { key: 'DEPT_REVIEW', label: '部门初审', role: 'DEPT_ADMIN' },
          { key: 'SCHOOL_REVIEW', label: '校级专项经费终审', role: 'SCHOOL_ADMIN' },
          { key: 'PUBLISHED', label: '正式发布上线', role: 'SYSTEM' }
        ]
      },
      evaluationTemplates: {
        TA: [
          { key: 'mentor', label: '主讲导师教学履职评价', weight: 70 },
          { key: 'service', label: '授课学生课程评教联动', weight: 30 }
        ],
        RA: [
          { key: 'mentor', label: '课题导师科研产出评价', weight: 75 },
          { key: 'service', label: '里程碑交付与团队协作评价', weight: 25 }
        ],
        GA: [
          { key: 'mentor', label: '科室负责人值班考核', weight: 60 },
          { key: 'service', label: '服务师生满意度评价', weight: 40 }
        ]
      },
      globalRules: {
        maxWeeklyHoursDefault: 20,
        maxWeeklyHoursIntl: 20,
        expiryWarningDays: 15,
        cautionHoursRatio: 0.8,
        // P2 C.2 智能推荐权重：技能匹配 / 剩余周工时 / 语言要求 / 同院系 / 截止临近（按比例归一到 0–100 分）
        recommendWeights: { skill: 40, hours: 25, language: 15, dept: 10, deadline: 10 }
      },
      // P1：岗位类型差异化字段。发布页按它渲染表单，后端建岗按它校验（必填、下拉选项、经费来源）
      // 字段 type：text / textarea / number / select / lines（多行，存为字符串数组）/ date
      // 派生字段由后端生成：TA 的 languageRequirement（由 minLangScore 得出），RA 的 milestones（由 milestonesText 每行一条）
      jobTypeSchemas: {
        TA: {
          label: '助教（TA）',
          desc: '绑定课程号、语言要求与教学周排班',
          allowedFundSources: ['TA_FUND'],
          defaults: { hourlyRate: 45, weeklyHours: 8, weeks: 16, allowInternational: true },
          intlToggle: {
            label: '面向国际生开放（勾选后自动触发国际生办专项会签）',
            hint: '国际生申请人将自动校验 X1 有效学习居留许可、语言证明与 20h 周工时上限'
          },
          fields: [
            { key: 'courseCode', label: '绑定课程编号', type: 'text', required: true, placeholder: '如 CS3090', defaultValue: 'CS3066' },
            { key: 'semester', label: '授课学期', type: 'text', required: true, placeholder: '如 2026-2027 春季学期', defaultValue: '2026-2027 春季学期' },
            { key: 'courseName', label: '课程名称', type: 'text', required: true, placeholder: '如 操作系统原理（双语）', defaultValue: '计算机体系结构（双语）' },
            {
              key: 'minLangScore',
              label: '语言能力最低要求',
              type: 'select',
              required: false,
              defaultValue: 6.5,
              options: [
                { value: 0, label: '无特殊语言限制' },
                { value: 6.0, label: 'IELTS 6.0 / HSK 5' },
                { value: 6.5, label: 'IELTS 6.5 / CET-6 550+' },
                { value: 7.0, label: 'IELTS 7.0 / TOEFL 95+' },
                { value: 7.5, label: 'IELTS 7.5 / TOEFL 105+' }
              ]
            },
            {
              key: 'scheduleText',
              label: '教学周排班安排',
              type: 'text',
              required: false,
              placeholder: '如 周二 14:00-16:00 上机实验；周四 10:00-12:00 答疑',
              defaultValue: '周二 14:00-16:00 实验辅导；周四 10:00-12:00 答疑'
            }
          ]
        },
        RA: {
          label: '助研（RA）',
          desc: '绑定科研项目、课题经费与里程碑',
          allowedFundSources: ['RA_PROJECT'],
          defaults: { hourlyRate: 48, weeklyHours: 10, weeks: 16, allowInternational: false },
          intlToggle: {
            label: '允许国际研究生申请该科研助研岗位',
            hint: '仍将强制执行国际生签证与 20h 累计工时校验'
          },
          fields: [
            { key: 'projectCode', label: '绑定科研项目编号', type: 'text', required: true, placeholder: '如 NSFC-62601234', defaultValue: 'NSFC-62572018' },
            { key: 'researchDirection', label: '研究方向', type: 'text', required: true, placeholder: '如 多模态推理与系统优化', defaultValue: 'AI 系统与高性能计算' },
            { key: 'projectName', label: '课题名称', type: 'text', required: true, placeholder: '', defaultValue: '面向异构集群的大模型低延迟推理优化' },
            {
              key: 'milestonesText',
              label: '阶段性里程碑（Milestone，每行一条）',
              type: 'textarea',
              required: false,
              placeholder: '每行一条，截止日期按聘期自动均分',
              defaultValue: 'M1：完成异构算子基准测试报告\nM2：实现多卡通信重叠优化原型\nM3：完成实验验证与论文图表整理'
            }
          ]
        },
        GA: {
          label: '助管（GA）',
          desc: '绑定行政科室、值班时段与专项经费',
          allowedFundSources: ['GA_FUND'],
          defaults: { hourlyRate: 35, weeklyHours: 6, weeks: 16, allowInternational: false },
          intlToggle: null,
          fields: [
            { key: 'officeDept', label: '绑定行政科室', type: 'text', required: true, placeholder: '如 研究生院培养办', defaultValue: '研究生院综合服务大厅' },
            { key: 'location', label: '办公地点', type: 'text', required: true, placeholder: '如 行政楼 B306', defaultValue: '行政楼 A204' },
            { key: 'dutyShift', label: '值班时段', type: 'text', required: true, placeholder: '如 周二/周四 14:00-17:00', defaultValue: '周二、周四 14:00-17:00' },
            { key: 'fundNote', label: '专项经费说明', type: 'text', required: false, placeholder: '', defaultValue: '校级勤工助学专项经费保障' }
          ]
        }
      }
    },

    jobs: [
      {
        id: 'JOB_01',
        type: 'TA',
        title: '《机器学习导论（双语）》课程助教',
        deptId: 'DEPT_CS',
        mentorId: 'U_MENTOR',
        budgetId: 'B_CS_TA',
        quota: 3,
        hiredCount: 1,
        hourlyRate: 45,
        weeklyHours: 8,
        weeks: 16,
        allowInternational: true,
        status: 'PUBLISHED',
        workflowInstanceId: 'WF_JOB_01',
        workflowSteps: ['MENTOR_SUBMIT', 'DEPT_REVIEW', 'INTL_REVIEW', 'PUBLISHED'],
        currentStepIndex: 3,
        deadline: '2026-12-10',
        createdAt: '2026-10-12 09:30:00',
        skills: ['Python', '机器学习', '英语授课'],
        description: '协助主讲教师组织双语实验课、批改算法编程作业、每周提供2次课后答疑并维护课程评测平台。',
        extraFields: {
          courseCode: 'CS3042',
          courseName: '机器学习导论（双语）',
          semester: '2026-2027 秋季学期',
          languageRequirement: 'IELTS 6.5 或同等水平',
          minLangScore: 6.5,
          scheduleText: '周二 14:00-16:00 实验课；周四 10:00-12:00 习题答疑',
          scheduleSlots: [
            { day: '周二', time: '14:00-16:00', room: '计算中心 302', task: '双语实验指导' },
            { day: '周四', time: '10:00-12:00', room: '明理楼 B108', task: '习题课与作业答疑' }
          ]
        }
      },
      {
        id: 'JOB_02',
        type: 'TA',
        title: '《高级算法与数据结构（全英文）》首席助教',
        deptId: 'DEPT_CS',
        mentorId: 'U_MENTOR',
        budgetId: 'B_CS_TA',
        quota: 2,
        hiredCount: 0,
        hourlyRate: 52,
        weeklyHours: 12,
        weeks: 16,
        allowInternational: true,
        status: 'PUBLISHED',
        workflowInstanceId: 'WF_JOB_02',
        workflowSteps: ['MENTOR_SUBMIT', 'DEPT_REVIEW', 'INTL_REVIEW', 'PUBLISHED'],
        currentStepIndex: 3,
        deadline: '2026-12-15',
        createdAt: '2026-10-18 14:15:00',
        skills: ['算法', 'C++', '英语授课'],
        description: '面向国际本科班全英文授课辅助，负责核心算法实验设计、OJ 题库维护及期中期末阅卷协调。',
        extraFields: {
          courseCode: 'CS4018',
          courseName: '高级算法与数据结构（全英文）',
          semester: '2026-2027 秋季学期',
          languageRequirement: 'IELTS 7.0 及以上',
          minLangScore: 7.0,
          scheduleText: '周一/周三 16:00-18:00 全英文习题课；周五线上答疑',
          scheduleSlots: [
            { day: '周一', time: '16:00-18:00', room: '逸夫楼 405', task: '全英文算法研讨' },
            { day: '周三', time: '16:00-18:00', room: '计算中心 201', task: '上机实验辅导' }
          ]
        }
      },
      {
        id: 'JOB_03',
        type: 'TA',
        title: '《国际顶会学术写作工作坊》助教',
        deptId: 'DEPT_CS',
        mentorId: 'U_MENTOR',
        budgetId: 'B_CS_TA',
        quota: 1,
        hiredCount: 0,
        hourlyRate: 55,
        weeklyHours: 6,
        weeks: 12,
        allowInternational: true,
        status: 'PUBLISHED',
        workflowInstanceId: 'WF_JOB_03',
        workflowSteps: ['MENTOR_SUBMIT', 'DEPT_REVIEW', 'INTL_REVIEW', 'PUBLISHED'],
        currentStepIndex: 3,
        deadline: '2026-12-18',
        createdAt: '2026-10-22 10:00:00',
        skills: ['英语授课', '学术写作', '机器学习'],
        description: '负责研究生英文论文润色辅导与 LaTeX 排版指导，需具备高水平英语读写与表达能力。',
        extraFields: {
          courseCode: 'CS6009',
          courseName: '国际学术论文写作工作坊',
          semester: '2026-2027 秋季学期',
          languageRequirement: 'IELTS 7.5 或 TOEFL 105+',
          minLangScore: 7.5,
          scheduleText: '周四 18:30-20:30 论文写作研讨',
          scheduleSlots: [
            { day: '周四', time: '18:30-20:30', room: '信息楼 510', task: '英文论文 Peer Review 指导' }
          ]
        }
      },
      {
        id: 'JOB_04',
        type: 'RA',
        title: '大模型分布式推理引擎科研助研',
        deptId: 'DEPT_CS',
        mentorId: 'U_MENTOR',
        budgetId: 'B_CS_RA',
        quota: 2,
        hiredCount: 1,
        hourlyRate: 48,
        weeklyHours: 12,
        weeks: 18,
        allowInternational: false,
        status: 'PUBLISHED',
        workflowInstanceId: 'WF_JOB_04',
        workflowSteps: ['MENTOR_SUBMIT', 'DEPT_REVIEW', 'PUBLISHED'],
        currentStepIndex: 2,
        deadline: '2026-12-20',
        createdAt: '2026-09-05 11:20:00',
        skills: ['C++', '分布式系统', 'Python'],
        description: '参与国家自然科学基金重点课题，开展多卡推理调度与 KV-Cache 内存优化原型开发及基准评测。',
        extraFields: {
          projectCode: 'NSFC-62572018',
          projectName: '面向异构集群的大模型低延迟推理优化',
          researchDirection: 'AI 系统架构与分布式推理',
          milestones: [
            { id: 'MS_01', title: 'M1：基线推理吞吐与显存瓶颈分析报告', dueDate: '2026-10-25', done: true },
            { id: 'MS_02', title: 'M2：动态 KV-Cache 压缩算子原型实现', dueDate: '2026-11-30', done: false },
            { id: 'MS_03', title: 'M3：千亿参数集群评测与顶会投稿实验', dueDate: '2027-01-10', done: false }
          ]
        }
      },
      {
        id: 'JOB_05',
        type: 'GA',
        title: '研究生院综合服务大厅行政助管',
        deptId: 'DEPT_OFFICE',
        mentorId: 'U_SCHOOL_ADMIN',
        budgetId: 'B_GA_SCHOOL',
        quota: 3,
        hiredCount: 1,
        hourlyRate: 35,
        weeklyHours: 6,
        weeks: 16,
        allowInternational: false,
        status: 'PUBLISHED',
        workflowInstanceId: 'WF_JOB_05',
        workflowSteps: ['MENTOR_SUBMIT', 'DEPT_REVIEW', 'SCHOOL_REVIEW', 'PUBLISHED'],
        currentStepIndex: 3,
        deadline: '2026-12-12',
        createdAt: '2026-10-08 09:00:00',
        skills: ['办公自动化', '沟通协调'],
        description: '协助研究生院教务窗口处理学籍证明打印、培养方案咨询引导、档案电子化归档与会议保障。',
        extraFields: {
          officeDept: '研究生院综合服务大厅',
          dutyShift: '周一、周三 14:00-17:00（两班轮值）',
          location: '行政楼 A204',
          fundNote: '校级勤工助学专项经费保障'
        }
      },
      {
        id: 'JOB_06',
        type: 'TA',
        title: '《人工智能伦理与治理（国际班）》助教',
        deptId: 'DEPT_CS',
        mentorId: 'U_MENTOR',
        budgetId: 'B_CS_TA',
        quota: 2,
        hiredCount: 0,
        hourlyRate: 45,
        weeklyHours: 8,
        weeks: 16,
        allowInternational: true,
        status: 'PENDING_DEPT_REVIEW',
        workflowInstanceId: 'WF_JOB_06',
        workflowSteps: ['MENTOR_SUBMIT', 'DEPT_REVIEW', 'INTL_REVIEW', 'PUBLISHED'],
        currentStepIndex: 1,
        deadline: '2026-12-25',
        createdAt: '2026-11-18 16:40:00',
        skills: ['英语授课', '人工智能'],
        description: '协助全英文前沿研讨课组织案例辩论、维护中外学生小组课题进度与课堂考勤记录。',
        extraFields: {
          courseCode: 'CS5031',
          courseName: '人工智能伦理与治理（国际班）',
          semester: '2026-2027 春季预招',
          languageRequirement: 'IELTS 6.5 及以上',
          minLangScore: 6.5,
          scheduleText: '周三 14:00-16:00 研讨课',
          scheduleSlots: [
            { day: '周三', time: '14:00-16:00', room: '明理楼 C202', task: '国际班案例研讨组织' }
          ]
        }
      },
      {
        id: 'JOB_07',
        type: 'TA',
        title: '《深度学习实践（英文）》实验助教',
        deptId: 'DEPT_CS',
        mentorId: 'U_MENTOR',
        budgetId: 'B_CS_TA',
        quota: 2,
        hiredCount: 0,
        hourlyRate: 48,
        weeklyHours: 8,
        weeks: 16,
        allowInternational: true,
        status: 'PENDING_INTL_REVIEW',
        workflowInstanceId: 'WF_JOB_07',
        workflowSteps: ['MENTOR_SUBMIT', 'DEPT_REVIEW', 'INTL_REVIEW', 'PUBLISHED'],
        currentStepIndex: 2,
        deadline: '2026-12-22',
        createdAt: '2026-11-16 11:10:00',
        skills: ['PyTorch', 'Python', '英语授课'],
        description: '负责深度学习 GPU 实验环境配置、英文实验手册编写与课程大作业答辩组织。',
        extraFields: {
          courseCode: 'CS4088',
          courseName: '深度学习实践（英文）',
          semester: '2026-2027 春季预招',
          languageRequirement: 'IELTS 6.5 及以上',
          minLangScore: 6.5,
          scheduleText: '周五 13:30-16:30 GPU 实验室辅导',
          scheduleSlots: [
            { day: '周五', time: '13:30-16:30', room: '智算实验楼 104', task: 'PyTorch 实验上机指导' }
          ]
        }
      },
      {
        id: 'JOB_08',
        type: 'TA',
        title: '《高等微积分（全英文荣誉班）》助教',
        deptId: 'DEPT_MATH',
        mentorId: 'U_MENTOR_MATH',
        budgetId: 'B_MATH_TA',
        quota: 2,
        hiredCount: 1,
        hourlyRate: 44,
        weeklyHours: 10,
        weeks: 16,
        allowInternational: true,
        status: 'PUBLISHED',
        workflowInstanceId: 'WF_JOB_08',
        workflowSteps: ['MENTOR_SUBMIT', 'DEPT_REVIEW', 'INTL_REVIEW', 'PUBLISHED'],
        currentStepIndex: 3,
        deadline: '2026-12-14',
        createdAt: '2026-10-03 15:00:00',
        skills: ['微积分', '英语授课', 'LaTeX'],
        description: '数学科学学院国际荣誉班公共基础课助教，负责习题课讲解与每周作业批改。',
        extraFields: {
          courseCode: 'MATH1012',
          courseName: '高等微积分（英文荣誉班）',
          semester: '2026-2027 秋季学期',
          languageRequirement: 'IELTS 6.5 及以上',
          minLangScore: 6.5,
          scheduleText: '周二/周四 08:00-10:00 习题辅导',
          scheduleSlots: [
            { day: '周二', time: '08:00-10:00', room: '理科楼 203', task: '微积分英文习题课' }
          ]
        }
      },
      {
        id: 'JOB_09',
        type: 'RA',
        title: '数字经济面板数据计量建模助研',
        deptId: 'DEPT_ECON',
        mentorId: 'U_MENTOR_ECON',
        budgetId: 'B_ECON_RA',
        quota: 2,
        hiredCount: 0,
        hourlyRate: 42,
        weeklyHours: 8,
        weeks: 16,
        allowInternational: false,
        status: 'PUBLISHED',
        workflowInstanceId: 'WF_JOB_09',
        workflowSteps: ['MENTOR_SUBMIT', 'DEPT_REVIEW', 'PUBLISHED'],
        currentStepIndex: 2,
        deadline: '2026-12-19',
        createdAt: '2026-10-25 10:30:00',
        skills: ['Python', '计量经济', '数据清洗'],
        description: '经济管理学院交叉课题，负责宏观高频数据清洗、面板回归验证及政策报告图表整理。',
        extraFields: {
          projectCode: 'KY-2026-EC02',
          projectName: '区域数字要素流动测度研究',
          researchDirection: '数字经济与计量建模',
          milestones: [
            { id: 'MS_E1', title: 'M1：多源统计面板数据清洗入库', dueDate: '2026-12-05', done: false },
            { id: 'MS_E2', title: 'M2：空间计量模型稳健性检验', dueDate: '2027-01-15', done: false }
          ]
        }
      },
      {
        id: 'JOB_10',
        type: 'RA',
        title: '跨语言多模态知识图谱构建助研',
        deptId: 'DEPT_CS',
        mentorId: 'U_MENTOR',
        budgetId: 'B_CS_RA',
        quota: 2,
        hiredCount: 1,
        hourlyRate: 46,
        weeklyHours: 10,
        weeks: 16,
        allowInternational: true,
        status: 'PUBLISHED',
        workflowInstanceId: 'WF_JOB_10',
        workflowSteps: ['MENTOR_SUBMIT', 'DEPT_REVIEW', 'PUBLISHED'],
        currentStepIndex: 2,
        deadline: '2026-12-01',
        createdAt: '2026-08-25 09:00:00',
        skills: ['Python', 'PyTorch', '人工智能'],
        description: '面向中英多语种学术语料进行实体对齐与知识图谱推理实验，撰写技术文档。',
        extraFields: {
          projectCode: 'NSFC-62572018',
          projectName: '跨语言知识图谱与大模型对齐',
          researchDirection: '自然语言处理与知识工程',
          milestones: [
            { id: 'MS_K1', title: 'M1：双语语料清洗与标注规范制定', dueDate: '2026-10-15', done: true },
            { id: 'MS_K2', title: 'M2：跨语言实体对齐基准评测完成', dueDate: '2026-11-28', done: false }
          ]
        }
      },
      {
        id: 'JOB_11',
        type: 'TA',
        title: '《计算机系统基础（双语）》课后答疑助教',
        deptId: 'DEPT_CS',
        mentorId: 'U_MENTOR',
        budgetId: 'B_CS_TA',
        quota: 1,
        hiredCount: 1,
        hourlyRate: 42,
        weeklyHours: 2,
        weeks: 12,
        allowInternational: true,
        status: 'PUBLISHED',
        workflowInstanceId: 'WF_JOB_11',
        workflowSteps: ['MENTOR_SUBMIT', 'DEPT_REVIEW', 'INTL_REVIEW', 'PUBLISHED'],
        currentStepIndex: 3,
        deadline: '2026-11-30',
        createdAt: '2026-11-01 10:00:00',
        skills: ['C++', '英语授课'],
        description: '每周固定 2 小时双语在线/线下答疑，协助批改系统编程小测验。',
        extraFields: {
          courseCode: 'CS2011',
          courseName: '计算机系统基础（双语）',
          semester: '2026-2027 秋季学期',
          languageRequirement: 'IELTS 6.5 及以上',
          minLangScore: 6.5,
          scheduleText: '周五 15:00-17:00 答疑室',
          scheduleSlots: [
            { day: '周五', time: '15:00-17:00', room: '计算中心 105', task: '双语实验课答疑' }
          ]
        }
      }
    ],

    applications: [
      {
        id: 'APP_01',
        jobId: 'JOB_04',
        studentId: 'U_CN_STU',
        status: 'HIRED',
        appliedAt: '2026-09-08 10:20:00',
        statement: '熟悉 C++ 与 CUDA 并发编程，参与过分布式键值存储开源项目，每周可稳定投入 12 小时科研工作。',
        resumeFileName: '林致远_计算机学院_科研简历.pdf',
        complianceCheckResult: { passed: true, checks: [{ key: 'hours', label: '跨岗累计工时校验', passed: true, detail: '当前 0h + 本岗 12h = 12h ≤ 20h' }] },
        isSpecialApproval: false,
        specialReason: '',
        specialStatus: null,
        interviewInfo: { time: '2026-09-10 14:30', location: '信息楼 412 实验室', note: '携带过往项目代码演示' },
        score: 94,
        mentorComment: '系统底层基础扎实，代码工程能力突出，同意录用。'
      },
      {
        id: 'APP_02',
        jobId: 'JOB_10',
        studentId: 'U_INTL_STU',
        status: 'HIRED',
        appliedAt: '2026-08-28 15:10:00',
        statement: 'Master student in AI with strong Python/PyTorch skills and bilingual background in NLP knowledge graphs.',
        resumeFileName: 'Alex_Chen_CV_2026.pdf',
        complianceCheckResult: {
          passed: true,
          checks: [
            { key: 'visa', label: '国际生签证类型校验', passed: true, detail: 'X1学习居留许可有效（至 2027-07-31）' },
            { key: 'hours', label: '跨岗累计工时校验', passed: true, detail: '当前 0h + 本岗 10h = 10h ≤ 20h' }
          ]
        },
        isSpecialApproval: false,
        specialReason: '',
        specialStatus: null,
        interviewInfo: { time: '2026-08-30 10:00', location: '信息楼 412', note: '英文技术面' },
        score: 91,
        mentorComment: '双语能力优秀，NLP 实验经验契合课题需求。'
      },
      {
        id: 'APP_03',
        jobId: 'JOB_11',
        studentId: 'U_INTL_STU',
        status: 'HIRED',
        appliedAt: '2026-11-05 14:00:00',
        statement: 'Interested in bilingual office hours TA role; strong background in computer systems.',
        resumeFileName: 'Alex_Chen_CV_2026.pdf',
        complianceCheckResult: {
          passed: true,
          checks: [
            { key: 'visa', label: '国际生签证类型校验', passed: true, detail: 'X1学习居留许可有效' },
            { key: 'lang', label: '语言能力证明校验', passed: true, detail: 'IELTS 7.0 ≥ 要求 6.5' },
            { key: 'hours', label: '跨岗累计工时校验', passed: true, detail: '当前 10h + 本岗 2h = 12h ≤ 20h' }
          ]
        },
        isSpecialApproval: false,
        specialReason: '',
        specialStatus: null,
        interviewInfo: { time: '2026-11-10 15:00', location: '明理楼 B201', note: '双语答疑试讲 10 分钟' },
        score: 89,
        mentorComment: '表达清晰耐心，同意录用，请尽快完成电子协议签署。'
      },
      {
        id: 'APP_04',
        jobId: 'JOB_01',
        studentId: 'U_STU_SU',
        status: 'HIRED',
        appliedAt: '2026-10-14 09:40:00',
        statement: '计算机系统结构博士生，曾任两学期机器学习助教，熟悉评测平台与双语授课。',
        resumeFileName: '苏雨桐_博士简历.pdf',
        complianceCheckResult: {
          passed: true,
          checks: [
            { key: 'lang', label: '语言能力证明校验', passed: true, detail: 'TOEFL 104 满足要求' },
            { key: 'hours', label: '跨岗累计工时校验', passed: true, detail: '当前 10h + 本岗 8h = 18h ≤ 20h' }
          ]
        },
        isSpecialApproval: false,
        specialReason: '',
        specialStatus: null,
        interviewInfo: { time: '2026-10-16 10:30', location: '信息楼 412', note: '教学试讲' },
        score: 95,
        mentorComment: '往届评教优秀，直接录用。'
      },
      {
        id: 'APP_05',
        jobId: 'JOB_02',
        studentId: 'U_STU_SU',
        status: 'SUBMITTED',
        appliedAt: '2026-11-17 16:05:00',
        statement: '熟悉高级算法与 OJ 系统开发，申请协助全英文算法班课程辅导。',
        resumeFileName: '苏雨桐_博士简历.pdf',
        complianceCheckResult: {
          passed: true,
          checks: [
            { key: 'lang', label: '语言能力证明校验', passed: true, detail: 'TOEFL 104 满足要求' },
            { key: 'hours', label: '跨岗累计工时校验', passed: true, detail: '当前 8h + 本岗 12h = 20h ≤ 20h' }
          ]
        },
        isSpecialApproval: false,
        specialReason: '',
        specialStatus: null,
        interviewInfo: null,
        score: null,
        mentorComment: ''
      },
      {
        id: 'APP_06',
        jobId: 'JOB_08',
        studentId: 'U_STU_ELENA',
        status: 'PENDING_SPECIAL_APPROVAL',
        appliedAt: '2026-11-18 11:25:00',
        statement: 'Applied for English Honors Calculus TA; native European academic background in applied mathematics.',
        resumeFileName: 'Elena_Rossi_Math_CV.pdf',
        complianceCheckResult: {
          passed: false,
          checks: [
            { key: 'visa', label: '国际生签证类型校验', passed: true, detail: 'X1学习居留许可有效（至 2027-06-30）' },
            { key: 'lang', label: '语言能力证明校验', passed: false, detail: '已认证 IELTS 6.0，低于岗位要求 IELTS 6.5' },
            { key: 'hours', label: '跨岗累计工时校验', passed: true, detail: '当前 8h + 本岗 10h = 18h ≤ 20h（临近上限）' }
          ]
        },
        isSpecialApproval: true,
        specialReason: '本人本科阶段在欧洲某理工大学接受全英文数学授课（附英文成绩单），实际英文数学表达流利，申请国际生办豁免 IELTS 0.5 分差额特批。',
        specialStatus: 'PENDING',
        interviewInfo: null,
        score: null,
        mentorComment: ''
      }
    ],

    contracts: [
      {
        id: 'CON_01',
        applicationId: 'APP_01',
        studentId: 'U_CN_STU',
        jobId: 'JOB_04',
        deptId: 'DEPT_CS',
        startDate: '2026-09-14',
        endDate: '2027-01-18',
        weeklyHours: 12,
        hourlyRate: 48,
        status: 'ACTIVE',
        signedAt: '2026-09-12 16:40:00',
        signatureDataUrl: null,
        signatureText: '林致远',
        complianceAgreed: true
      },
      {
        id: 'CON_02',
        applicationId: 'APP_02',
        studentId: 'U_INTL_STU',
        jobId: 'JOB_10',
        deptId: 'DEPT_CS',
        startDate: '2026-09-01',
        endDate: '2026-12-01', // Expires in 11 days from 2026-11-20 -> triggers <= 15 days warning!
        weeklyHours: 10,
        hourlyRate: 46,
        status: 'ACTIVE',
        signedAt: '2026-08-31 11:20:00',
        signatureDataUrl: null,
        signatureText: 'Alex Chen',
        complianceAgreed: true
      },
      {
        id: 'CON_03',
        applicationId: 'APP_03',
        studentId: 'U_INTL_STU',
        jobId: 'JOB_11',
        deptId: 'DEPT_CS',
        startDate: '2026-11-23',
        endDate: '2027-02-15',
        weeklyHours: 2,
        hourlyRate: 42,
        status: 'PENDING_SIGN', // Waiting for Alex Chen to sign with Canvas + Intl Compliance Declaration!
        signedAt: null,
        signatureDataUrl: null,
        signatureText: null,
        complianceAgreed: false
      },
      {
        id: 'CON_04',
        applicationId: 'APP_04',
        studentId: 'U_STU_SU',
        jobId: 'JOB_01',
        deptId: 'DEPT_CS',
        startDate: '2026-10-19',
        endDate: '2027-02-08',
        weeklyHours: 8,
        hourlyRate: 45,
        status: 'ACTIVE',
        signedAt: '2026-10-18 19:05:00',
        signatureDataUrl: null,
        signatureText: '苏雨桐',
        complianceAgreed: true
      },
      {
        id: 'CON_05',
        applicationId: null,
        studentId: 'U_STU_ELENA',
        jobId: 'JOB_08',
        deptId: 'DEPT_MATH',
        startDate: '2026-09-15',
        endDate: '2026-12-02', // Expires in 12 days -> warning
        weeklyHours: 10,
        hourlyRate: 44,
        status: 'ACTIVE',
        signedAt: '2026-09-14 14:15:00',
        signatureDataUrl: null,
        signatureText: 'Elena Rossi',
        complianceAgreed: true
      }
    ],

    timesheets: [
      {
        id: 'TS_01',
        contractId: 'CON_01',
        studentId: 'U_CN_STU',
        jobId: 'JOB_04',
        deptId: 'DEPT_CS',
        weekNo: 10,
        month: '2026-11',
        hours: 12,
        workLog: '完成 vLLM 与自研推理框架在 A100 节点上的首 token 延迟对比测试，整理 4 组消融实验数据。',
        milestoneRef: 'M1：基线推理吞吐与显存瓶颈分析报告',
        status: 'APPROVED',
        submittedAt: '2026-11-06 18:10:00',
        mentorComment: '数据详实，按期达成 M1 里程碑。'
      },
      {
        id: 'TS_02',
        contractId: 'CON_01',
        studentId: 'U_CN_STU',
        jobId: 'JOB_04',
        deptId: 'DEPT_CS',
        weekNo: 11,
        month: '2026-11',
        hours: 12,
        workLog: '实现 KV-Cache 动态量化压缩 CUDA 核函数初版，并通过单元测试验证精度无损。',
        milestoneRef: 'M2：动态 KV-Cache 压缩算子原型实现',
        status: 'APPROVED',
        submittedAt: '2026-11-13 19:20:00',
        mentorComment: '核函数性能达预期，审核通过。'
      },
      {
        id: 'TS_03',
        contractId: 'CON_01',
        studentId: 'U_CN_STU',
        jobId: 'JOB_04',
        deptId: 'DEPT_CS',
        weekNo: 12,
        month: '2026-11',
        hours: 11,
        workLog: '联调多卡 NCCL 通信优化模块，完成 70B 模型长上下文显存占用压测。',
        milestoneRef: 'M2：动态 KV-Cache 压缩算子原型实现',
        status: 'PENDING',
        submittedAt: '2026-11-19 20:05:00',
        mentorComment: ''
      },
      {
        id: 'TS_04',
        contractId: 'CON_02',
        studentId: 'U_INTL_STU',
        jobId: 'JOB_10',
        deptId: 'DEPT_CS',
        weekNo: 10,
        month: '2026-11',
        hours: 10,
        workLog: 'Cleaned 25,000 bilingual academic entity pairs and updated alignment validation scripts.',
        milestoneRef: 'M1：双语语料清洗与标注规范制定',
        status: 'APPROVED',
        submittedAt: '2026-11-06 17:30:00',
        mentorComment: '高质量完成双语对齐脚本。'
      },
      {
        id: 'TS_05',
        contractId: 'CON_02',
        studentId: 'U_INTL_STU',
        jobId: 'JOB_10',
        deptId: 'DEPT_CS',
        weekNo: 11,
        month: '2026-11',
        hours: 10,
        workLog: 'Ran cross-lingual embedding benchmark across 3 models and drafted Section 3 tables.',
        milestoneRef: 'M2：跨语言实体对齐基准评测完成',
        status: 'APPROVED',
        submittedAt: '2026-11-14 15:40:00',
        mentorComment: '评测结果清晰，审核通过。'
      },
      // Note: Alex Chen intentionally has NO week 12 timesheet yet -> triggers "本周未填报工时" anomaly warning!
      {
        id: 'TS_06',
        contractId: 'CON_04',
        studentId: 'U_STU_SU',
        jobId: 'JOB_01',
        deptId: 'DEPT_CS',
        weekNo: 11,
        month: '2026-11',
        hours: 8,
        workLog: '周二双语实验课指导（42人），批改第 4 次梯度下降编程作业并汇总常见误区。',
        milestoneRef: '周二 14:00-16:00 实验课；周四 10:00-12:00 答疑',
        status: 'APPROVED',
        submittedAt: '2026-11-13 21:00:00',
        mentorComment: '批改及时，学生反馈良好。'
      },
      {
        id: 'TS_07',
        contractId: 'CON_04',
        studentId: 'U_STU_SU',
        jobId: 'JOB_01',
        deptId: 'DEPT_CS',
        weekNo: 12,
        month: '2026-11',
        hours: 8,
        workLog: '组织期中复习习题课，解答支持向量机与神经网络反向传播推导问题。',
        milestoneRef: '周四 10:00-12:00 习题答疑',
        status: 'PENDING',
        submittedAt: '2026-11-19 18:45:00',
        mentorComment: ''
      },
      {
        id: 'TS_08',
        contractId: 'CON_05',
        studentId: 'U_STU_ELENA',
        jobId: 'JOB_08',
        deptId: 'DEPT_MATH',
        weekNo: 12,
        month: '2026-11',
        hours: 17, // 17h/20h = 85% -> triggers >=80% international student hours caution!
        workLog: 'Led midterm review tutorials for Honors Calculus sections A & B and graded 95 problem sets.',
        milestoneRef: '周二/周四 习题辅导与期中批卷',
        status: 'APPROVED',
        submittedAt: '2026-11-19 16:30:00',
        mentorComment: '期中周工作量饱满，请注意控制周工时勿超 20h 红线。'
      }
    ],

    evaluations: [
      {
        id: 'EV_01',
        contractId: 'CON_01',
        studentId: 'U_CN_STU',
        jobId: 'JOB_04',
        deptId: 'DEPT_CS',
        period: '2026-11 阶段考核',
        jobType: 'RA',
        dimensions: [
          { key: 'mentor', label: '课题导师科研产出评价', weight: 75, score: 94 },
          { key: 'service', label: '里程碑交付与团队协作评价', weight: 25, score: 92 }
        ],
        totalScore: 93.5,
        grade: '优秀',
        coefficient: 1.1,
        renewalEligible: true,
        evaluatorName: '张明远教授',
        comment: '核心算子研发进度超前，实验严谨，推荐下周期续聘并给予优秀绩效系数。',
        evaluatedAt: '2026-11-15 17:00:00'
      },
      {
        id: 'EV_02',
        contractId: 'CON_04',
        studentId: 'U_STU_SU',
        jobId: 'JOB_01',
        deptId: 'DEPT_CS',
        period: '2026-11 期中教学考核',
        jobType: 'TA',
        dimensions: [
          { key: 'mentor', label: '主讲导师教学履职评价', weight: 70, score: 92 },
          { key: 'service', label: '授课学生课程评教联动', weight: 30, score: 95 }
        ],
        totalScore: 92.9,
        grade: '优秀',
        coefficient: 1.1,
        renewalEligible: true,
        courseEvalSync: { responseCount: 46, satisfactionRate: 96.4, avgRating: 4.82 },
        evaluatorName: '张明远教授',
        comment: '课堂答疑极具耐心，选课学生匿名评教分列全院前 5%。',
        evaluatedAt: '2026-11-16 14:20:00'
      }
    ],

    payrolls: [
      {
        id: 'PAY_01',
        studentId: 'U_CN_STU',
        contractId: 'CON_01',
        jobId: 'JOB_04',
        deptId: 'DEPT_CS',
        month: '2026-10',
        totalHours: 46,
        hourlyRate: 48,
        coefficient: 1.0,
        amount: 2208,
        budgetId: 'B_CS_RA',
        sourceType: 'RA_PROJECT',
        financeStatus: 'PAID',
        calculatedAt: '2026-11-01 10:00:00',
        paidAt: '2026-11-05 15:20:00',
        voucherNo: 'CW-20261105-0084'
      },
      {
        id: 'PAY_02',
        studentId: 'U_INTL_STU',
        contractId: 'CON_02',
        jobId: 'JOB_10',
        deptId: 'DEPT_CS',
        month: '2026-10',
        totalHours: 40,
        hourlyRate: 46,
        coefficient: 1.0,
        amount: 1840,
        budgetId: 'B_CS_RA',
        sourceType: 'RA_PROJECT',
        financeStatus: 'PAID',
        calculatedAt: '2026-11-01 10:05:00',
        paidAt: '2026-11-05 15:22:00',
        voucherNo: 'CW-20261105-0085'
      },
      {
        id: 'PAY_03',
        studentId: 'U_CN_STU',
        contractId: 'CON_01',
        jobId: 'JOB_04',
        deptId: 'DEPT_CS',
        month: '2026-11',
        totalHours: 24,
        hourlyRate: 48,
        coefficient: 1.1,
        amount: 1267.2,
        budgetId: 'B_CS_RA',
        sourceType: 'RA_PROJECT',
        financeStatus: 'PENDING_PAY',
        calculatedAt: '2026-11-18 09:30:00',
        paidAt: null,
        voucherNo: null
      },
      {
        id: 'PAY_04',
        studentId: 'U_INTL_STU',
        contractId: 'CON_02',
        jobId: 'JOB_10',
        deptId: 'DEPT_CS',
        month: '2026-11',
        totalHours: 20,
        hourlyRate: 46,
        coefficient: 1.0,
        amount: 920,
        budgetId: 'B_CS_RA',
        sourceType: 'RA_PROJECT',
        financeStatus: 'PENDING_CALC',
        calculatedAt: null,
        paidAt: null,
        voucherNo: null
      },
      {
        id: 'PAY_05',
        studentId: 'U_STU_SU',
        contractId: 'CON_04',
        jobId: 'JOB_01',
        deptId: 'DEPT_CS',
        month: '2026-11',
        totalHours: 8,
        hourlyRate: 45,
        coefficient: 1.1,
        amount: 396,
        budgetId: 'B_CS_TA',
        sourceType: 'TA_FUND',
        financeStatus: 'PENDING_PAY',
        calculatedAt: '2026-11-18 11:00:00',
        paidAt: null,
        voucherNo: null
      },
      {
        id: 'PAY_06',
        studentId: 'U_STU_ELENA',
        contractId: 'CON_05',
        jobId: 'JOB_08',
        deptId: 'DEPT_MATH',
        month: '2026-11',
        totalHours: 17,
        hourlyRate: 44,
        coefficient: 1.0,
        amount: 748,
        budgetId: 'B_MATH_TA',
        sourceType: 'TA_FUND',
        financeStatus: 'PENDING_PAY',
        calculatedAt: '2026-11-19 17:10:00',
        paidAt: null,
        voucherNo: null
      }
    ],

    notifications: [
      {
        id: 'NOTIF_01',
        userId: 'U_INTL_STU',
        channel: '站内信 + 模拟邮件',
        title: '录用通知：《计算机系统基础（双语）》答疑助教',
        content: '恭喜您通过张明远教授的面试选拔！请前往「工时与合同」完成电子聘用协议手写签名及《国际生合法工时合规声明》确认。',
        read: false,
        createdAt: '2026-11-12 10:15:00'
      },
      {
        id: 'NOTIF_02',
        userId: 'U_INTL_STU',
        channel: '系统预警',
        title: '聘期临期与周工时填报提醒',
        content: '您的《跨语言多模态知识图谱构建助研》聘期将于 11 天后到期（2026-12-01），且第 12 周工时尚未填报。',
        read: false,
        createdAt: '2026-11-20 08:00:00'
      },
      {
        id: 'NOTIF_03',
        userId: 'U_CN_STU',
        channel: '站内信 + 模拟邮件',
        title: '阶段考核评定为「优秀」（系数 1.1x）',
        content: '张明远教授已完成您 2026-11 阶段科研助研考核，综合得分 93.5 分，11 月薪酬已自动应用 1.1 绩效系数。',
        read: false,
        createdAt: '2026-11-15 17:05:00'
      }
    ],

    auditLogs: [
      {
        id: 'LOG_01',
        targetType: 'JOB',
        targetId: 'JOB_01',
        operatorName: '张明远教授',
        operatorRole: '科研/课程导师',
        action: '提交岗位发布申请',
        opinion: '申请《机器学习导论（双语）》助教 3 名，预计支出 ¥17,280，预算充足。',
        beforeValue: '草稿',
        afterValue: '待院系初审',
        timestamp: '2026-10-12 09:30:00'
      },
      {
        id: 'LOG_02',
        targetType: 'JOB',
        targetId: 'JOB_01',
        operatorName: '李婉清',
        operatorRole: '院系管理员',
        action: '院系初审通过',
        opinion: '核验计算机学院名额余量与教务助教经费池余额通过，转国际生办会签。',
        beforeValue: '待院系初审',
        afterValue: '待国际生办会签',
        timestamp: '2026-10-12 14:20:00'
      },
      {
        id: 'LOG_03',
        targetType: 'JOB',
        targetId: 'JOB_01',
        operatorName: '王海涛',
        operatorRole: '国际生办审核员',
        action: '国际生办会签通过',
        opinion: '语言要求（IELTS 6.5）及每周 8h 工时设置符合来华留学生勤工助学规范，准予发布。',
        beforeValue: '待国际生办会签',
        afterValue: '正式发布',
        timestamp: '2026-10-13 09:50:00'
      },
      {
        id: 'LOG_04',
        targetType: 'JOB',
        targetId: 'JOB_07',
        operatorName: '张明远教授',
        operatorRole: '科研/课程导师',
        action: '提交岗位发布申请',
        opinion: '面向国际生开放《深度学习实践（英文）》实验助教 2 名。',
        beforeValue: '草稿',
        afterValue: '待院系初审',
        timestamp: '2026-11-16 11:10:00'
      },
      {
        id: 'LOG_05',
        targetType: 'JOB',
        targetId: 'JOB_07',
        operatorName: '李婉清',
        operatorRole: '院系管理员',
        action: '院系初审通过',
        opinion: '名额与教务经费校验通过，流转至国际合作交流处会签。',
        beforeValue: '待院系初审',
        afterValue: '待国际生办会签',
        timestamp: '2026-11-17 09:15:00'
      },
      {
        id: 'LOG_06',
        targetType: 'JOB',
        targetId: 'JOB_06',
        operatorName: '张明远教授',
        operatorRole: '科研/课程导师',
        action: '提交岗位发布申请',
        opinion: '提交 2026-2027 春季《人工智能伦理与治理（国际班）》助教岗位，预计支出 ¥11,520。',
        beforeValue: '草稿',
        afterValue: '待院系初审',
        timestamp: '2026-11-18 16:40:00'
      },
      {
        id: 'LOG_07',
        targetType: 'APPLICATION',
        targetId: 'APP_01',
        operatorName: '张明远教授',
        operatorRole: '科研/课程导师',
        action: '面试评分并确认录用',
        opinion: '面试得分 94 分，系统底层基础扎实，自动生成电子聘用协议并发送站内信与邮件通知。',
        beforeValue: '待面试',
        afterValue: '已录用',
        timestamp: '2026-09-11 10:12:00'
      },
      {
        id: 'LOG_08',
        targetType: 'APPLICATION',
        targetId: 'APP_06',
        operatorName: 'Elena Rossi（艾莲娜）',
        operatorRole: '外籍硕士生',
        action: '发起国际生办特批申请',
        opinion: '语言证明 IELTS 6.0 触发硬校验拦截，申请人提交英文授课背景证明发起特批。',
        beforeValue: '合规拦截',
        afterValue: '待国际生办特批',
        timestamp: '2026-11-18 11:25:00'
      },
      {
        id: 'LOG_09',
        targetType: 'CONTRACT',
        targetId: 'CON_01',
        operatorName: '林致远',
        operatorRole: '多岗中国学生',
        action: '完成电子聘用协议手写签署',
        opinion: '确认岗位职责、时薪标准及工时红线约束。',
        beforeValue: '待签署',
        afterValue: '在岗履约中',
        timestamp: '2026-09-12 16:40:00'
      },
      {
        id: 'LOG_10',
        targetType: 'PAYROLL',
        targetId: 'PAY_01',
        operatorName: '周国强',
        operatorRole: '校级系统/财务管理员',
        action: '财务打款回写并扣减经费池',
        opinion: '2026-10 助研薪酬 ¥2,208.00 已通过财务凭证 CW-20261105-0084 发放。',
        beforeValue: '待财务打款（经费已用 ¥40,592）',
        afterValue: '已发放（经费已用 ¥42,800）',
        timestamp: '2026-11-05 15:20:00'
      },
      {
        id: 'LOG_11',
        targetType: 'EVALUATION',
        targetId: 'EV_01',
        operatorName: '张明远教授',
        operatorRole: '科研/课程导师',
        action: '提交阶段考核评价',
        opinion: '加权得分 93.5 分（优秀），续聘资格：可续聘，下周期薪酬系数 1.1x。',
        beforeValue: '待考核',
        afterValue: '优秀（1.1x）',
        timestamp: '2026-11-15 17:00:00'
      }
    ],

    // P1：岗位模板（deptId 为空 = 全校通用）；payload 为除岗位名称外的发布表单字段
    jobTemplates: [
      {
        id: 'JT_TA_01',
        name: '双语课程助教（通用）',
        type: 'TA',
        deptId: null,
        payload: {
          type: 'TA',
          quota: 2,
          hourlyRate: 45,
          weeklyHours: 8,
          weeks: 16,
          allowInternational: true,
          skills: ['英语授课', '作业批改'],
          description: '协助主讲教师组织双语实验课与习题课、批改作业并每周提供课后答疑。',
          extraFields: {
            courseCode: '',
            courseName: '',
            semester: '2026-2027 春季学期',
            minLangScore: 6.5,
            scheduleText: '每周两次课后答疑，具体时段与主讲教师商定'
          }
        },
        createdBy: 'U_SCHOOL_ADMIN',
        createdAt: '2026-09-01 09:00:00'
      },
      {
        id: 'JT_RA_01',
        name: '科研项目助研（通用）',
        type: 'RA',
        deptId: null,
        payload: {
          type: 'RA',
          quota: 1,
          hourlyRate: 48,
          weeklyHours: 10,
          weeks: 16,
          allowInternational: false,
          skills: ['文献调研', '数据分析'],
          description: '参与课题组科研任务，负责文献调研、实验数据整理与阶段报告撰写。',
          extraFields: {
            projectCode: '',
            projectName: '',
            researchDirection: '',
            milestonesText: 'M1：完成文献综述与实验方案\nM2：完成核心实验与数据分析\nM3：完成阶段总结报告'
          }
        },
        createdBy: 'U_SCHOOL_ADMIN',
        createdAt: '2026-09-01 09:05:00'
      },
      {
        id: 'JT_GA_01',
        name: '行政窗口助管（通用）',
        type: 'GA',
        deptId: null,
        payload: {
          type: 'GA',
          quota: 2,
          hourlyRate: 35,
          weeklyHours: 6,
          weeks: 16,
          allowInternational: false,
          skills: ['办公自动化', '沟通协调'],
          description: '协助行政窗口接待来访师生、办理证明材料并做好业务咨询登记。',
          extraFields: {
            officeDept: '',
            location: '',
            dutyShift: '周一至周五任选两个半天',
            fundNote: '校级勤工助学专项经费保障'
          }
        },
        createdBy: 'U_SCHOOL_ADMIN',
        createdAt: '2026-09-01 09:10:00'
      }
    ]
  }
}
