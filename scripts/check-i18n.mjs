// 双语检查：node scripts/check-i18n.mjs [--strict]
// 1. 中英词条结构必须完全一致（缺词条 / 多词条都报错）
// 2. 扫描页面与组件代码里残留的中文字面量（排除注释）。--strict 时有残留即失败。
//    以下情况允许保留中文，不计入残留：
//    - src/i18n/ 词条文件本身、src/mock/（演示数据与 mock 后端）
//    - 行内带 `i18n-ignore` 注释的行（如必须原样显示的数据示例）
import { readFileSync, readdirSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const strict = process.argv.includes('--strict')
let failed = false

// ---------------------------------------------------------------- 1. 词条结构
function flatten(obj, prefix = '', out = new Map()) {
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k
    if (v && typeof v === 'object' && !Array.isArray(v)) flatten(v, key, out)
    else out.set(key, v)
  }
  return out
}

const localeDir = path.join(root, 'src', 'i18n', 'locales')
const modules = readdirSync(path.join(localeDir, 'zh-CN')).filter((f) => f.endsWith('.js'))
for (const mod of modules) {
  const zh = flatten((await import(pathToFileURL(path.join(localeDir, 'zh-CN', mod)).href)).default)
  const en = flatten((await import(pathToFileURL(path.join(localeDir, 'en-US', mod)).href)).default)
  const missingEn = [...zh.keys()].filter((k) => !en.has(k))
  const extraEn = [...en.keys()].filter((k) => !zh.has(k))
  const emptyEn = [...en.entries()].filter(([, v]) => v === '' || v === null).map(([k]) => k)
  // 英文词条里出现中文，多半是忘了翻译
  const cjkInEn = [...en.entries()].filter(([, v]) => typeof v === 'string' && /[一-鿿]/.test(v)
    && !/^中文$/.test(v)).map(([k]) => k)
  console.log(`词条 ${mod}: 中文 ${zh.size} 条，英文 ${en.size} 条`)
  for (const [label, list] of [['英文缺少', missingEn], ['英文多出', extraEn], ['英文为空', emptyEn], ['英文词条含中文', cjkInEn]]) {
    if (list.length) {
      failed = true
      console.log(`  ✗ ${label} ${list.length} 条：${list.slice(0, 20).join(', ')}${list.length > 20 ? ' …' : ''}`)
    }
  }
}

// ---------------------------------------------------------------- 2. 残留中文
function walk(dir, files = []) {
  for (const name of readdirSync(dir)) {
    const full = path.join(dir, name)
    if (statSync(full).isDirectory()) walk(full, files)
    else if (/\.(vue|js)$/.test(name)) files.push(full)
  }
  return files
}

const skipDirs = [path.join(root, "src", "i18n"), path.join(root, "src", "mock")]
// mock 后端（api.js 的 mockApi 部分）的提示保持中文：mock 只用于没装后端时的离线演示，见 docs/双语设计.md
// ruleEngine.js 的校验文案按设计同时保留中文与英文（labelEn/detailEn/messageEn），中英一致由
// scripts/gen-rule-en-parity.mjs 与后端 RuleEngineEnParityTest 逐字比对保证，不计入残留
const skipFiles = [path.join(root, "src", "services", "api.js"), path.join(root, "src", "services", "ruleEngine.js")]
const files = walk(path.join(root, "src")).filter((f) => !skipDirs.some((d) => f.startsWith(d)) && !skipFiles.includes(f))
const report = []
for (const file of files) {
  const lines = readFileSync(file, 'utf8').split(/\r?\n/)
  let inBlock = false
  let inStyle = false
  let hits = 0
  const samples = []
  lines.forEach((raw, i) => {
    let line = raw
    if (/<style/.test(line)) inStyle = true
    if (inStyle) { if (/<\/style>/.test(line)) inStyle = false; return }
    if (line.includes('i18n-ignore')) return
    // 去掉注释：/* */、<!-- -->、// 行尾注释（粗略处理，足够发现遗漏）
    if (inBlock) { const end = line.indexOf('*/'); if (end < 0) return; line = line.slice(end + 2); inBlock = false }
    line = line.replace(/\/\*.*?\*\//g, '').replace(/<!--.*?-->/g, '')
    if (line.includes('/*')) { line = line.slice(0, line.indexOf('/*')); inBlock = true }
    if (/<!--/.test(line)) line = line.slice(0, line.indexOf('<!--'))
    line = line.replace(/(^|[^:'"`])\/\/.*$/, '$1')
    if (/^\s*\*/.test(line)) return
    if (/[一-鿿]/.test(line)) {
      hits += 1
      if (samples.length < 3) samples.push(`${i + 1}: ${line.trim().slice(0, 60)}`)
    }
  })
  if (hits) report.push({ file: path.relative(root, file), hits, samples })
}
report.sort((a, b) => b.hits - a.hits)
const total = report.reduce((n, r) => n + r.hits, 0)
console.log(`\n残留中文：${report.length} 个文件，共 ${total} 行`)
for (const r of report) {
  console.log(`  ${String(r.hits).padStart(4)}  ${r.file}`)
  for (const s of r.samples) console.log(`          ${s}`)
}
if (strict && total > 0) failed = true

console.log(failed ? '\n检查未通过' : '\n检查通过')
process.exit(failed ? 1 : 0)
