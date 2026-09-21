<template>
  <section class="rubric-editor">
    <div class="rubric-head"><div><b>评分细则</b><p>按成果表现设置给分标准，教师评审时逐项核对。</p></div><span v-if="modelValue.length" class="badge" :class="total === 100 ? 'badge-green' : 'badge-yellow'">{{ total }} / 100 分</span></div>
    <div v-if="!modelValue.length" class="rubric-empty"><ListChecks :size="22" /><span>暂未单独设置，将使用{{ fallback }}。</span></div>
    <div v-for="(row, i) in modelValue" :key="i" class="rubric-row">
      <div class="rule-top"><span class="rule-number">{{ String(i + 1).padStart(2, '0') }}</span>
        <el-input :model-value="row.name" @update:model-value="value => update(i, 'name', value)" placeholder="评分项，如：报警功能" maxlength="80" :aria-label="`第${i + 1}项名称`" class="rule-name" />
        <div class="rule-points"><el-input-number :model-value="row.points" @update:model-value="value => update(i, 'points', value)" :min="1" :max="100" :precision="0" controls-position="right" :aria-label="`第${i + 1}项满分`" /><span>分</span></div>
        <el-button text type="danger" :aria-label="`删除第${i + 1}项`" @click="$emit('update:modelValue', modelValue.filter((_, index) => i !== index))"><Trash2 :size="16" /></el-button>
      </div>
      <el-input type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" :model-value="row.description" @update:model-value="value => update(i, 'description', value)" placeholder="写清给分标准，例如：触发后蜂鸣器响起，并在视频中展示触发和复位过程。" maxlength="1000" :aria-label="`第${i + 1}项给分标准`" />
    </div>
    <div class="rubric-footer"><el-button size="small" plain :disabled="modelValue.length >= 12" @click="$emit('update:modelValue', [...modelValue, { name: '', points: 10, description: '' }])"><Plus :size="14" />添加评分项</el-button><span>{{ modelValue.length }} / 12 项<span v-if="modelValue.length && total !== 100"> · 分值合计需为100分</span></span></div>
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { ListChecks, Plus, Trash2 } from 'lucide-vue-next'
const props = defineProps({ modelValue: { type: Array, default: () => [] }, fallback: { type: String, default: '通用评分标准' } })
const emit = defineEmits(['update:modelValue'])
const total = computed(() => props.modelValue.reduce((sum, row) => sum + (Number(row.points) || 0), 0))
const update = (i, field, value) => emit('update:modelValue', props.modelValue.map((row, index) => index === i ? { ...row, [field]: value } : row))
</script>
<style scoped>
.rubric-editor { width: 100%; border: 1px solid var(--border); border-radius: 14px; padding: 16px; margin: 12px 0; background: #f9fafb; }
.rubric-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 14px; }.rubric-head b { font-size: 14px; }.rubric-head p { font-size: 12px; color: var(--text-secondary); margin: 5px 0 0; line-height: 1.6; }.badge { white-space: nowrap; }
.rubric-empty { display: flex; justify-content: center; align-items: center; gap: 10px; padding: 22px 12px; color: var(--text-secondary); font-size: 13px; border: 1px dashed #d1d5db; border-radius: 10px; background: #fff; }
.rubric-row { padding: 12px; margin-bottom: 10px; background: #fff; border: 1px solid var(--border); border-radius: 12px; }.rule-top { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }.rule-number { color: var(--brand-blue); background: #eff6ff; border-radius: 8px; padding: 5px 7px; font-size: 12px; font-weight: 600; }.rule-name { flex: 1; min-width: 0; }.rule-points { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-secondary); }.rule-points :deep(.el-input-number) { width: 100px; }.rule-top>.el-button { padding: 8px; }
.rubric-footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 12px; }.rubric-footer>span { font-size: 12px; color: var(--text-secondary); }.rubric-footer .el-button :deep(span) { gap: 4px; }
@media(max-width:600px) { .rubric-editor { padding: 12px; }.rule-top { flex-wrap: wrap; gap: 8px; }.rule-name { flex-basis: calc(100% - 42px); }.rule-points { margin-left: 35px; }.rule-top>.el-button { margin-left: auto; }.rubric-head,.rubric-footer { flex-wrap: wrap; }.rubric-empty { align-items: flex-start; line-height: 1.6; } }
</style>
