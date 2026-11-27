<template>
  <div class="empty-state">
    <div class="empty-icon">
      <FluentIcon :name="icon" :size="48" />
    </div>
    <p class="empty-desc">{{ description || $t('empty.default') }}</p>
    <button v-if="actionText" type="button" class="f-btn f-btn-primary" @click="$emit('action')">
      {{ actionText }}
    </button>
  </div>
</template>

<script setup>
import FluentIcon from './FluentIcon.vue'

defineProps({
  icon: { type: String, default: 'file' },
  description: { type: String, default: '' },
  actionText: { type: String, default: '' }
})

defineEmits(['action'])
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 48px 24px;
  gap: 12px;
}

.empty-icon {
  color: #a19f9d;
  display: flex;
}

.empty-desc {
  font-size: var(--text-body);
  color: var(--color-muted);
  max-width: 360px;
}
</style>
