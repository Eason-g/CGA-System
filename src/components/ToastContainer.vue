<template>
  <div class="toast-stack" aria-live="polite">
    <div
      v-for="t in store.toasts"
      :key="t.id"
      class="toast-item"
      :class="t.type"
    >
      <FluentIcon
        :name="t.type === 'success' ? 'check-circle' : t.type === 'error' ? 'x-circle' : t.type === 'caution' ? 'alert' : 'bell'"
        :size="16"
      />
      <span class="toast-msg">{{ t.message }}</span>
      <button class="toast-close" type="button" @click="store.dismissToast(t.id)" aria-label="关闭通知">
        <FluentIcon name="close" :size="14" />
      </button>
    </div>
  </div>
</template>

<script setup>
import { useAppStore } from '@/stores/appStore'
import FluentIcon from './FluentIcon.vue'

const store = useAppStore()
</script>

<style scoped>
.toast-stack {
  position: fixed;
  top: 16px;
  right: 20px;
  z-index: 1200;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 400px;
  pointer-events: none;
}

.toast-item {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(24px);
  border: 1px solid var(--color-border-light);
  border-left: 3px solid var(--color-fluent-blue);
  border-radius: 4px;
  box-shadow: var(--shadow-card-hover);
  font-size: var(--text-body);
  animation: toastSlide 180ms ease-out;
}

.toast-item.success {
  border-left-color: var(--color-success);
  color: var(--color-text);
}

.toast-item.success svg {
  color: var(--color-success);
}

.toast-item.error {
  border-left-color: var(--color-error);
}

.toast-item.error svg {
  color: var(--color-error);
}

.toast-item.caution {
  border-left-color: var(--color-caution);
}

.toast-item.caution svg {
  color: var(--color-caution-text);
}

.toast-item.info svg {
  color: var(--color-fluent-blue);
}

.toast-msg {
  flex: 1;
  line-height: 1.4;
}

.toast-close {
  border: none;
  background: transparent;
  color: var(--color-muted);
  cursor: pointer;
  padding: 2px;
  display: inline-flex;
  border-radius: 2px;
}

.toast-close:hover {
  background: var(--surface-hover);
  color: var(--color-text);
}

@keyframes toastSlide {
  from {
    transform: translateX(20px);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

@media (max-width: 768px) {
  .toast-stack { top: 12px; left: 12px; right: 12px; max-width: none; }
}
</style>
