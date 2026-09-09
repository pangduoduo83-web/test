<template>
  <div>
    <div class="top">
      <div class="score" :class="scoreClass">
        <b>{{ data.score ?? '–' }}</b><span>完整性评分</span>
      </div>
      <div class="grow">
        <p class="lead">{{ data.summary }}</p>
        <div class="stats">
          <span class="chip">{{ data.stats?.items ?? '–' }} 条物料</span>
          <span class="chip">{{ data.stats?.estimatedCost || '成本未估' }}</span>
          <span class="chip" :class="'risk-' + (data.stats?.riskLevel || '低')">风险 {{ data.stats?.riskLevel || '–' }}</span>
          <span class="chip">{{ (data.issues || []).filter((i) => i.level === 'high').length }} 个高风险问题</span>
        </div>
      </div>
    </div>

    <div v-if="data.issues?.length" class="sec">
      <div class="sec-title">问题清单</div>
      <table class="tbl">
        <thead><tr><th style="width:70px">级别</th><th style="width:70px">位号</th><th>元件</th><th>问题</th><th>建议</th></tr></thead>
        <tbody>
          <tr v-for="(it, i) in data.issues" :key="i">
            <td><span class="lvl" :class="it.level">{{ { high: '高', medium: '中', low: '低' }[it.level] || it.level }}</span></td>
            <td class="mono">{{ it.ref || '–' }}</td>
            <td><b>{{ it.item }}</b></td>
            <td>{{ it.problem }}</td>
            <td class="sug">{{ it.suggestion }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else class="ok">✅ 没有发现明显问题</div>

    <div class="two">
      <div v-if="data.missing?.length" class="sec">
        <div class="sec-title">可能缺失</div>
        <ul><li v-for="m in data.missing" :key="m">{{ m }}</li></ul>
      </div>
      <div v-if="data.alternatives?.length" class="sec">
        <div class="sec-title">替代料</div>
        <ul><li v-for="(a, i) in data.alternatives" :key="i"><b>{{ a.item }}</b> → {{ a.alt }}<span class="muted"> · {{ a.why }}</span></li></ul>
      </div>
    </div>
    <div v-if="data.checklist?.length" class="check">
      <div class="sec-title">下单前自查</div>
      <label v-for="(c, i) in data.checklist" :key="i" class="ck"><el-checkbox v-model="checked[i]" />{{ c }}</label>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'

const props = defineProps({ data: { type: Object, required: true } })
const checked = reactive({})
const scoreClass = computed(() => (props.data.score >= 85 ? 'good' : props.data.score >= 60 ? 'mid' : 'bad'))
</script>

<style scoped>
.top { display: flex; gap: 16px; align-items: flex-start; margin-bottom: 16px; }
.grow { flex: 1; min-width: 0; }
.score { width: 96px; height: 96px; border-radius: 18px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #fff; flex-shrink: 0; }
.score b { font-size: 34px; line-height: 1; }
.score span { font-size: 11px; opacity: .85; margin-top: 4px; }
.score.good { background: linear-gradient(135deg, #22c55e, #15803d); } .score.mid { background: linear-gradient(135deg, #f59e0b, #b45309); } .score.bad { background: linear-gradient(135deg, #ef4444, #991b1b); }
.lead { margin: 0 0 10px; font-size: 14.5px; line-height: 1.7; color: #111827; }
.stats { display: flex; gap: 8px; flex-wrap: wrap; }
.chip { font-size: 12px; background: #f3f4f6; color: #374151; padding: 3px 10px; border-radius: 999px; }
.chip.risk-高 { background: #fef2f2; color: #b91c1c; } .chip.risk-中 { background: #fffbeb; color: #b45309; } .chip.risk-低 { background: #ecfdf5; color: #047857; }
.sec { margin-top: 14px; }
.sec-title { font-size: 13px; font-weight: 700; color: #374151; margin-bottom: 8px; }
.tbl { width: 100%; border-collapse: collapse; font-size: 13px; }
.tbl th { text-align: left; font-size: 12px; color: #6b7280; padding: 8px 10px; border-bottom: 1px solid var(--border); }
.tbl td { padding: 9px 10px; border-bottom: 1px solid #f3f4f6; vertical-align: top; line-height: 1.5; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; }
.sug { color: #065f46; }
.lvl { font-size: 11px; padding: 2px 8px; border-radius: 999px; font-weight: 700; }
.lvl.high { background: #fef2f2; color: #b91c1c; } .lvl.medium { background: #fffbeb; color: #b45309; } .lvl.low { background: #f3f4f6; color: #4b5563; }
.ok { margin-top: 12px; padding: 12px; background: #ecfdf5; color: #065f46; border-radius: 10px; font-size: 14px; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.two ul { margin: 0; padding-left: 18px; font-size: 13px; color: #4b5563; line-height: 1.8; }
.muted { color: #9ca3af; }
.check { margin-top: 14px; padding: 12px 14px; background: #f9fafb; border-radius: 12px; }
.ck { display: flex; align-items: center; gap: 8px; font-size: 13px; padding: 4px 0; cursor: pointer; }
</style>
