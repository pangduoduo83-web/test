<template>
  <span>{{ shown }}</span>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'

/** 数字滚动:值变化时从旧值平滑过渡到新值,大屏刷新数据时有"跳动"感 */
const props = defineProps({
  value: { type: [Number, String], default: 0 },
  duration: { type: Number, default: 900 },
  /** 后缀,如 % */
  suffix: { type: String, default: '' },
  /** 非数字(如 '–')原样显示 */
  decimals: { type: Number, default: 0 }
})

const current = ref(Number(props.value) || 0)
let raf = null

const animate = (from, to) => {
  cancelAnimationFrame(raf)
  const start = performance.now()
  const step = (t) => {
    const p = Math.min(1, (t - start) / props.duration)
    const eased = 1 - Math.pow(1 - p, 3)
    current.value = from + (to - from) * eased
    if (p < 1) raf = requestAnimationFrame(step)
  }
  raf = requestAnimationFrame(step)
}

watch(() => props.value, (v, old) => {
  const to = Number(v)
  if (Number.isNaN(to)) return
  animate(Number(old) || 0, to)
})

const shown = computed(() => {
  if (props.value === null || props.value === undefined || Number.isNaN(Number(props.value))) return String(props.value ?? '–')
  return current.value.toFixed(props.decimals) + props.suffix
})

onBeforeUnmount(() => cancelAnimationFrame(raf))
</script>
