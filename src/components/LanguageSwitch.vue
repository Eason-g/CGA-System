<template>
  <!-- 中英切换按钮：放在学生端顶栏、管理端顶栏/侧栏、登录页、公开评价页，任何页面都能切换，选择会被记住 -->
  <button
    type="button"
    class="lang-switch"
    :class="{ compact }"
    :title="t('lang.switchTitle')"
    :aria-label="t('lang.switchTitle')"
    @click="toggleLocale"
  >
    <span class="lang-icon" aria-hidden="true">文/A</span><!-- i18n-ignore -->
    <span v-if="!compact" class="lang-text">{{ t('lang.switchTo') }}</span>
  </button>
</template>

<script setup>
import { useI18n } from '@/i18n'

defineProps({
  /** 紧凑模式：只显示图标（手机顶栏等空间紧张的位置） */
  compact: { type: Boolean, default: false }
})

const { t, toggleLocale } = useI18n()
</script>

<style scoped>
.lang-switch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 4px 10px;
  border: 1px solid var(--color-border-light, #e1e4ea);
  border-radius: 999px;
  background: var(--color-surface, #fff);
  color: var(--color-text-secondary, #555);
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  white-space: nowrap;
}
.lang-switch:hover { border-color: var(--color-primary, #0f6cbd); color: var(--color-primary, #0f6cbd); }
.lang-switch:focus-visible { outline: 2px solid var(--color-primary, #0f6cbd); outline-offset: 2px; }
.lang-switch.compact { padding: 4px 8px; }
.lang-icon { font-weight: 600; letter-spacing: -0.5px; }
@media (max-width: 768px) {
  .lang-switch { min-height: 40px; }
}
</style>
