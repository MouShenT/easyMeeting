<template>
  <div class="action-card" @click="$emit('click')">
    <div class="card-icon">
      <slot name="icon">
        <component :is="icon" v-if="icon" />
      </slot>
    </div>
    <span class="card-label">{{ label }}</span>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  label: string
  icon?: Component
}>()

defineEmits<{
  click: []
}>()
</script>

<style scoped>
.action-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-md);
  padding: var(--spacing-xl) var(--spacing-lg);
  background: var(--color-bg-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: border-color var(--transition-normal), background var(--transition-normal);
  min-height: 140px;
}

.action-card:hover {
  border-color: var(--color-border-hover);
  background: var(--color-bg-white);
}

.action-card:hover .card-icon {
  color: var(--color-accent);
}

.card-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  transition: color var(--transition-normal);
}

.card-icon :deep(svg) {
  width: 100%;
  height: 100%;
}

.card-label {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-text-primary);
}
</style>
