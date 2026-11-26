#!/usr/bin/env node
/**
 * CGA-System 后端压测脚本（P2 D.3）。Node 18+（开发用 Node 24），无第三方依赖：fetch + Promise 并发。
 *
 * 用法（在仓库根目录）：
 *   node scripts/perf-test.mjs --base http://localhost:8090 --concurrency 50 --duration 60
 *   node scripts/perf-test.mjs --concurrency 200 --duration 30 --scenario login-snapshot
 *
 * 参数：
 *   --base URL           后端地址，默认 http://localhost:8090（也可用环境变量 PERF_BASE）
 *   --concurrency N      并发虚拟用户数，默认 50
 *   --duration S         压测时长（秒），默认 30
 *   --scenario NAME      mixed（默认：登录、各角色快照、岗位申请、导师录用、薪酬核算混合）
 *                        login-snapshot（只做登录 + 拉快照）
 *   --jobs N             mixed 场景准备阶段新建并审批发布的岗位数，默认 4
 *   --password P         演示密码，默认 Cga@2026
 *   --json FILE          另把结果写成 JSON 文件
 *
 * 注意：
 *   - 压测会写入数据（新岗位、申请、合同、薪酬），只能对演示/测试库运行；
 *   - 所有请求来自同一 IP，后端默认的登录 IP 限流（每分钟 300 次）会被触发，
 *     压测时请用 -Dapp.security.login.ip-max-per-minute=1000000 启动后端（见 docs/非功能验证.md）；
 *   - 结果分三类：成功（2xx）、业务拒绝（400/403/404/409，例如重复投递、超工时被拦截，属于预期行为）、
 *     错误（401/429/5xx、网络错误、超时），“错误”应为 0。
 */

const args = parseArgs(process.argv.slice(2));
const BASE = (args.base || process.env.PERF_BASE || 'http://localhost:8090').replace(/\/$/, '');
const CONCURRENCY = Number(args.concurrency || 50);
const DURATION_S = Number(args.duration || 30);
const SCENARIO = args.scenario || 'mixed';
const JOB_COUNT = Number(args.jobs || 4);
const PASSWORD = args.password || 'Cga@2026';
const REQUEST_TIMEOUT_MS = 30_000;

const ACCOUNTS = {
  student: ['alex.chen', 'lin.zhiyuan', 'su.yutong', 'elena.rossi'],
  mentor: ['zhang.mingyuan', 'chen.shouren', 'zhao.minghua'],
  deptAdmin: ['li.wanqing'],
  intl: ['wang.haitao'],
  school: ['zhou.guoqiang'],
};
const ALL_ACCOUNTS = Object.values(ACCOUNTS).flat();

function parseArgs(list) {
  const out = {};
  for (let i = 0; i < list.length; i++) {
    const a = list[i];
    if (!a.startsWith('--')) continue;
    const [k, v] = a.slice(2).split('=');
    out[k] = v !== undefined ? v : (list[i + 1] && !list[i + 1].startsWith('--') ? list[++i] : 'true');
  }
  return out;
}

// ------------------------------------------------------------------ 统计

const stats = new Map(); // 接口名 → { lat: [], ok, rejected, errors, errorSamples }

function record(name, ms, kind, sample, status) {
  let s = stats.get(name);
  if (!s) {
    s = { lat: [], ok: 0, rejected: 0, errors: 0, errorSamples: new Map(), rejectSamples: new Map() };
    stats.set(name, s);
  }
  s.lat.push(ms);
  s[kind]++;
  if (kind === 'rejected' && sample) {
    const key = `HTTP ${status} ${sample}`;
    s.rejectSamples.set(key, (s.rejectSamples.get(key) || 0) + 1);
  }
  if (kind === 'errors' && sample) {
    s.errorSamples.set(sample, (s.errorSamples.get(sample) || 0) + 1);
  }
}

function classify(status) {
  if (status >= 200 && status < 300) return 'ok';
  if ([400, 403, 404, 409].includes(status)) return 'rejected';
  return 'errors';
}

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[Math.max(0, idx)];
}

// ------------------------------------------------------------------ HTTP

// parse=false 时成功响应只读完响应体、不做 JSON 解析：快照可达数百 KB，客户端单线程解析会先于服务端成为瓶颈
async function call(name, method, path, { token, body, measure = true, parse = true } = {}) {
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const t0 = performance.now();
  let status = 0;
  let json = null;
  let errText = null;
  try {
    const res = await fetch(BASE + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    status = res.status;
    if (parse || !res.ok) {
      const text = await res.text();
      try { json = JSON.parse(text); } catch { json = null; }
    } else {
      await res.arrayBuffer();
    }
  } catch (e) {
    errText = e.name === 'TimeoutError' ? '超时' : `网络错误 ${e.cause?.code || e.message}`;
  }
  const ms = performance.now() - t0;
  if (measure) {
    const kind = errText ? 'errors' : classify(status);
    const sample = errText || (kind === 'errors' ? `HTTP ${status} ${json?.message ?? ''}`
      : kind === 'rejected' ? String(json?.message ?? '').slice(0, 40) : null);
    record(name, ms, kind, sample, status);
  }
  return { status, json, ms, error: errText };
}

async function login(account, measure = true) {
  const r = await call('POST /api/auth/login', 'POST', '/api/auth/login',
    { body: { account, password: PASSWORD }, measure });
  if (r.status !== 200) {
    if (!measure) throw new Error(`准备阶段登录 ${account} 失败：${r.status} ${r.json?.message ?? r.error}`);
    return null;
  }
  return r.json.data.token;
}

// ------------------------------------------------------------------ 准备阶段

const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
const tokens = {};
const ctx = { jobIds: [], openApps: [], month: null };

async function prepare() {
  for (const a of ALL_ACCOUNTS) tokens[a] = await login(a, false);
  const snap = await call('prep', 'GET', '/api/snapshot', { token: tokens['zhou.guoqiang'], measure: false });
  if (snap.status !== 200) throw new Error(`无法获取快照：${snap.status}`);
  const today = snap.json.data?.meta?.today;
  ctx.month = today ? today.slice(0, 7) : new Date().toISOString().slice(0, 7);

  if (SCENARIO !== 'mixed') return;
  // 新建并审批发布若干岗位，供申请和录用场景使用（每次压测前建议重建演示库）
  const runTag = Date.now().toString(36);
  for (let i = 0; i < JOB_COUNT; i++) {
    const draft = {
      type: 'TA', title: `压测课程助教 ${runTag}-${i + 1}`, deptId: 'DEPT_CS', budgetId: 'B_CS_TA',
      quota: 1, hourlyRate: 30, weeklyHours: 8, weeks: 4, allowInternational: true,
      skills: ['Python'], description: '压测数据',
      extraFields: { courseCode: `PERF${i + 1}`, courseName: '压测课程', semester: '2026-2027 春季学期', minLangScore: 6.0 },
    };
    const created = await call('prep', 'POST', '/api/jobs', { token: tokens['zhang.mingyuan'], body: draft, measure: false });
    if (created.status !== 200) {
      console.warn(`  建岗失败（跳过）：${created.status} ${created.json?.message}`);
      continue;
    }
    let job = created.json.data;
    for (let step = 0; step < 5 && job.status.startsWith('PENDING'); step++) {
      let advanced = false;
      for (const reviewer of ['li.wanqing', 'wang.haitao', 'zhou.guoqiang']) {
        const r = await call('prep', 'POST', `/api/jobs/${job.id}/review`,
          { token: tokens[reviewer], body: { decision: 'APPROVE', opinion: '压测审批' }, measure: false });
        if (r.status === 200) { job = r.json.data; advanced = true; break; }
      }
      if (!advanced) break;
    }
    if (job.status === 'PUBLISHED') ctx.jobIds.push(job.id);
    else console.warn(`  岗位 ${job.id} 未能发布（状态 ${job.status}）`);
  }
  console.log(`准备完成：发布压测岗位 ${ctx.jobIds.length} 个，核算月份 ${ctx.month}`);
}

// ------------------------------------------------------------------ 场景

const SCENARIOS = {
  async login() {
    await login(pick(ALL_ACCOUNTS));
  },
  async snapshotStudent() {
    await call('GET /api/snapshot（学生）', 'GET', '/api/snapshot', { token: tokens[pick(ACCOUNTS.student)], parse: false });
  },
  async snapshotMentor() {
    await call('GET /api/snapshot（导师）', 'GET', '/api/snapshot', { token: tokens[pick(ACCOUNTS.mentor)], parse: false });
  },
  async snapshotDeptAdmin() {
    await call('GET /api/snapshot（院系管理员）', 'GET', '/api/snapshot', { token: tokens['li.wanqing'], parse: false });
  },
  async snapshotIntl() {
    await call('GET /api/snapshot（国际生办）', 'GET', '/api/snapshot', { token: tokens['wang.haitao'], parse: false });
  },
  async snapshotSchool() {
    await call('GET /api/snapshot（校级管理员）', 'GET', '/api/snapshot', { token: tokens['zhou.guoqiang'], parse: false });
  },
  async apply() {
    if (ctx.jobIds.length === 0) return;
    const r = await call('POST /api/applications', 'POST', '/api/applications', {
      token: tokens[pick(ACCOUNTS.student)], body: { jobId: pick(ctx.jobIds), statement: '压测投递' },
    });
    if (r.status === 200) ctx.openApps.push(r.json.data.id);
  },
  async hire() {
    if (ctx.openApps.length === 0) return;
    // 随机取一个待选拔申请，多个虚拟用户可能同时录用同一学生到不同岗位
    const idx = Math.floor(Math.random() * ctx.openApps.length);
    const appId = ctx.openApps[idx];
    const r = await call('POST /api/applications/{id}/decision', 'POST', `/api/applications/${appId}/decision`, {
      token: tokens['zhang.mingyuan'], body: { score: 90, decision: 'HIRE', comment: '压测录用' },
    });
    if (r.status === 200 || r.status === 400) {
      const i = ctx.openApps.indexOf(appId);
      if (i >= 0) ctx.openApps.splice(i, 1);
    }
  },
  async payroll() {
    await call('POST /api/payrolls/generate', 'POST', '/api/payrolls/generate', {
      token: tokens['li.wanqing'], body: { month: ctx.month }, parse: false,
    });
  },
};

const MIXES = {
  // 权重：读多写少，接近真实使用（学生最多，管理端次之）
  mixed: { login: 10, snapshotStudent: 25, snapshotMentor: 12, snapshotDeptAdmin: 8, snapshotIntl: 5, snapshotSchool: 8,
    apply: 15, hire: 10, payroll: 7 },
  'login-snapshot': { login: 30, snapshotStudent: 30, snapshotMentor: 15, snapshotDeptAdmin: 10, snapshotIntl: 5, snapshotSchool: 10 },
};

function weightedPicker(mix) {
  const entries = Object.entries(mix);
  const total = entries.reduce((s, [, w]) => s + w, 0);
  return () => {
    let r = Math.random() * total;
    for (const [name, w] of entries) {
      if ((r -= w) < 0) return name;
    }
    return entries[0][0];
  };
}

// ------------------------------------------------------------------ 主流程

async function main() {
  const mix = MIXES[SCENARIO];
  if (!mix) throw new Error(`未知场景 ${SCENARIO}，可选：${Object.keys(MIXES).join(', ')}`);
  console.log(`目标 ${BASE}，场景 ${SCENARIO}，并发 ${CONCURRENCY}，时长 ${DURATION_S}s`);
  await prepare();

  const nextScenario = weightedPicker(mix);
  const deadline = performance.now() + DURATION_S * 1000;
  const t0 = performance.now();
  const vu = async () => {
    while (performance.now() < deadline) {
      await SCENARIOS[nextScenario()]();
    }
  };
  await Promise.all(Array.from({ length: CONCURRENCY }, vu));
  const elapsed = (performance.now() - t0) / 1000;
  report(elapsed);
}

function report(elapsed) {
  const rows = [];
  let total = 0;
  let totalErr = 0;
  for (const [name, s] of [...stats.entries()].sort()) {
    const sorted = s.lat.slice().sort((a, b) => a - b);
    const avg = sorted.reduce((a, b) => a + b, 0) / (sorted.length || 1);
    total += sorted.length;
    totalErr += s.errors;
    rows.push({
      接口: name, 请求数: sorted.length, 成功: s.ok, 业务拒绝: s.rejected, 错误: s.errors,
      'avg(ms)': Math.round(avg), 'p50(ms)': Math.round(percentile(sorted, 50)),
      'p95(ms)': Math.round(percentile(sorted, 95)), 'max(ms)': Math.round(sorted[sorted.length - 1] || 0),
    });
  }
  console.log(`\n完成：${total} 个请求，用时 ${elapsed.toFixed(1)}s，吞吐 ${(total / elapsed).toFixed(1)} req/s，错误 ${totalErr}`);
  console.table(rows);
  for (const [name, s] of stats) {
    for (const [msg, n] of s.errorSamples) console.log(`  错误样例 [${name}] ×${n}：${msg}`);
    const top = [...s.rejectSamples.entries()].sort((a, b) => b[1] - a[1]).slice(0, 3);
    for (const [msg, n] of top) console.log(`  业务拒绝 [${name}] ×${n}：${msg}`);
  }
  // Markdown 表格，便于直接贴进 docs/非功能验证.md
  console.log('\n| 接口 | 请求数 | 成功 | 业务拒绝 | 错误 | avg | p50 | p95 | max |');
  console.log('|---|---:|---:|---:|---:|---:|---:|---:|---:|');
  for (const r of rows) {
    console.log(`| ${r.接口} | ${r.请求数} | ${r.成功} | ${r.业务拒绝} | ${r.错误} | ${r['avg(ms)']} | ${r['p50(ms)']} | ${r['p95(ms)']} | ${r['max(ms)']} |`);
  }
  if (args.json) {
    import('node:fs').then((fs) => fs.writeFileSync(args.json, JSON.stringify({
      base: BASE, scenario: SCENARIO, concurrency: CONCURRENCY, duration: DURATION_S, elapsed, total, errors: totalErr, rows,
    }, null, 2)));
  }
  process.exitCode = totalErr > 0 ? 1 : 0;
}

main().catch((e) => {
  console.error(e.message);
  process.exit(2);
});
