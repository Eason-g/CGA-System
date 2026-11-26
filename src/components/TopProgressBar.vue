<template>
  <div v-if="store.isLoading" class="top-progress-wrap" role="progressbar" aria-label="加载中">
    <div class="top-progress-bar" />
  </div>
</template>

<script setup>
import { useAppStore } from '@/stores/appStore'
const store = useAppStore()
</script>

<style scoped>
.top-progress-wrap {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: rgba(0, 120, 212, 0.16);
  z-index: 9999;
  overflow: hidden;
  pointer-events: none;
}

.top-progress-bar {
  width: 36%;
  height: 100%;
  background: var(--color-fluent-blue);
  animation: fluentIndeterminate 1.1s cubic-bezier(0.4, 0, 0.2, 1) infinite;
}

@keyframes fluentIndeterminate {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(380%);
  }
}
</style>
