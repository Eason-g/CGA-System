<template>
  <div class="f-drawer-overlay" @click.self="$emit('close')">
    <aside class="f-drawer" role="dialog" aria-modal="true" :aria-label="title">
      <header class="drawer-header">
        <div>
          <div v-if="subtitle" class="text-xs text-muted">{{ subtitle }}</div>
          <h2 class="text-sub mt-4">{{ title }}</h2>
        </div>
        <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="$emit('close')" aria-label="关闭抽屉">
          <FluentIcon name="close" :size="18" />
        </button>
      </header>

      <div class="drawer-body">
        <slot />

        <section v-if="auditLogs && auditLogs.length" class="drawer-audit-section">
          <div class="section-heading">
            <FluentIcon name="history" :size="16" />
            <span>全流程审计留痕时间轴（{{ auditLogs.length }}）</span>
          </div>
          <div class="f-timeline mt-12">
            <div v-for="log in auditLogs" :key="log.id" class="f-timeline-item">
              <span class="f-timeline-dot" :class="dotTone(log.action)" />
              <div class="timeline-head">
                <span class="fw-600">{{ log.action }}</span>
                <span class="text-xs text-muted tabular-nums">{{ log.timestamp }}</span>
              </div>
              <div class="text-xs text-muted mt-4">
                {{ log.operatorName }} · {{ log.operatorRole }}
              </div>
              <div v-if="log.opinion" class="timeline-opinion mt-6">
                {{ log.opinion }}
              </div>
              <div v-if="log.beforeValue || log.afterValue" class="text-xs text-muted mt-4">
                变更：<span>{{ log.beforeValue || '-' }}</span> → <span class="text-blue fw-600">{{ log.afterValue || '-' }}</span>
              </div>
            </div>
          </div>
        </section>
      </div>

      <footer v-if="$slots.footer" class="drawer-footer">
        <slot name="footer" />
      </footer>
    </aside>
  </div>
</template>

<script setup>
import FluentIcon from './FluentIcon.vue'

defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  auditLogs: { type: Array, default: () => [] }
})

defineEmits(['close'])

function dotTone(action = '') {
  if (action.includes('驳回') || action.includes('未录用') || action.includes('拦截')) return 'error'
  if (action.includes('特批')) return 'caution'
  if (action.includes('通过') || action.includes('录用') || action.includes('发放') || action.includes('签署')) return 'success'
  return ''
}
</script>

<style scoped>
.drawer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--color-border-light);
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.drawer-footer {
  padding: 16px 24px;
  border-top: 1px solid var(--color-border-light);
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.drawer-audit-section {
  padding-top: 16px;
  border-top: 1px solid var(--color-border-light);
}

.section-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--text-body);
  font-weight: var(--weight-semibold);
  color: var(--color-text);
}

.timeline-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.timeline-opinion {
  font-size: var(--text-label);
  color: var(--color-text);
  background: var(--surface-subtle);
  border-radius: 8px;
  padding: 9px 12px;
}

.mt-4 { margin-top: 4px; }
.mt-6 { margin-top: 6px; }

@media (max-width: 768px) {
  .drawer-header { padding: 14px 16px 12px; }
  .drawer-body { padding: 16px; gap: 16px; overscroll-behavior: contain; }
  .drawer-footer { padding: 12px 16px calc(12px + env(safe-area-inset-bottom)); }
  .timeline-head { flex-wrap: wrap; }
  .timeline-opinion { font-size: 14px; overflow-wrap: anywhere; }
}
</style>
