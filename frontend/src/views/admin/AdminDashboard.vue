<template>
  <div v-if="stats">
    <div class="stat-grid">
      <div v-for="s in cards" :key="s.label" class="ref-stat-card">
        <div class="ref-stat-icon" :style="{ background: s.bg }">
          <component :is="s.icon" :size="22" color="#fff" />
        </div>
        <div>
          <div class="ref-stat-value">{{ s.value }}</div>
          <div class="ref-stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <div class="two-col">
      <!-- 近30天借阅趋势 -->
      <div class="card">
        <div class="card-head"><h3>近30天借阅趋势</h3></div>
        <div ref="trendRef" class="chart"></div>
      </div>

      <!-- 设备利用率排行 -->
      <div class="card">
        <div class="card-head"><h3>设备利用率排行</h3></div>
        <div v-if="utilization.length === 0" class="util-empty">暂无设备数据</div>
        <div v-for="u in utilization" :key="u.name" class="util-row">
          <div class="util-head">
            <span class="util-name">{{ u.name }}</span>
            <span class="util-nums">在用 {{ u.inUse }}/{{ u.total }} · 累计 {{ u.borrowCount }} 次</span>
          </div>
          <div class="util-bar">
            <div class="util-inner" :style="{ width: u.inUseRate + '%' }"></div>
          </div>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <h3>待审批的借阅申请</h3>
        <el-button size="small" type="primary" plain @click="$router.push('/admin/borrows')">
          前往借阅审批 →
        </el-button>
      </div>
      <el-empty v-if="!stats.recentPending?.length" description="太棒了,没有待处理的申请" />
      <el-table v-else :data="stats.recentPending" stripe>
        <el-table-column prop="requestNo" label="申请编号" width="150" />
        <el-table-column prop="userName" label="申请人" width="110" />
        <el-table-column prop="equipmentName" label="设备" />
        <el-table-column prop="quantity" label="数量" width="70" />
        <el-table-column prop="purpose" label="用途" width="110" />
        <el-table-column label="申请时间" width="160">
          <template #default="{ row }">{{ (row.appliedAt || '').replace('T', ' ').slice(0, 16) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { Clock, Package, Rocket, RotateCcw, Users, Wrench } from 'lucide-vue-next'
import { adminStats, adminTrends } from '../../api'

const stats = ref(null)
const utilization = ref([])
const trendRef = ref(null)
let chart = null

const cards = computed(() => stats.value ? [
  { icon: Users, label: '注册学生', value: stats.value.studentCount, bg: 'linear-gradient(135deg,#60a5fa,#2563eb)' },
  { icon: Wrench, label: '设备总数', value: stats.value.equipmentCount, bg: 'linear-gradient(135deg,#4ade80,#16a34a)' },
  { icon: Rocket, label: '项目总数', value: stats.value.projectCount, bg: 'linear-gradient(135deg,#c084fc,#9333ea)' },
  { icon: Clock, label: '待审批申请', value: stats.value.pendingBorrows, bg: 'linear-gradient(135deg,#facc15,#f59e0b)' },
  { icon: Package, label: '借用中', value: stats.value.activeBorrows, bg: 'linear-gradient(135deg,#fb923c,#ea580c)' },
  { icon: RotateCcw, label: '待归还验收', value: stats.value.returnRequests, bg: 'linear-gradient(135deg,#f87171,#dc2626)' }
] : [])

const renderTrend = (t) => {
  if (!trendRef.value) return
  if (!chart) chart = echarts.init(trendRef.value)
  chart.setOption({
    grid: { left: 32, right: 16, top: 34, bottom: 26 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['申请', '归还'], top: 0, right: 0, itemWidth: 14, textStyle: { color: '#6b7280', fontSize: 12 } },
    xAxis: { type: 'category', data: t.days, axisLabel: { color: '#6b7280', interval: 4 } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#f3f4f6' } } },
    series: [
      {
        name: '申请', type: 'line', data: t.applied, smooth: true, symbolSize: 5,
        lineStyle: { width: 3, color: '#3b82f6' }, itemStyle: { color: '#3b82f6' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(59,130,246,.18)' },
            { offset: 1, color: 'rgba(59,130,246,0)' }
          ])
        }
      },
      {
        name: '归还', type: 'line', data: t.returned, smooth: true, symbolSize: 5,
        lineStyle: { width: 3, color: '#22c55e' }, itemStyle: { color: '#22c55e' }
      }
    ]
  })
}

onMounted(async () => {
  stats.value = await adminStats()
  try {
    const t = await adminTrends()
    utilization.value = t.utilization || []
    await nextTick()
    renderTrend(t)
  } catch (e) { /* 报表加载失败不阻塞看板 */ }
})
window.addEventListener('resize', () => chart && chart.resize())
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.two-col { display: grid; grid-template-columns: 3fr 2fr; gap: 16px; margin-bottom: 20px; }
@media (max-width: 1100px) { .two-col { grid-template-columns: 1fr; } }

.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.card-head h3 { margin: 0; font-size: 16px; }

.chart { height: 280px; }

.util-empty { color: var(--text-secondary); font-size: 13px; text-align: center; padding: 30px 0; }
.util-row { margin-bottom: 14px; }
.util-head { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 5px; }
.util-name { color: #374151; font-weight: 500; }
.util-nums { color: #9ca3af; font-size: 12px; }
.util-bar { height: 7px; border-radius: 999px; background: #f3f4f6; overflow: hidden; }
.util-inner {
  height: 100%; border-radius: 999px;
  background: linear-gradient(to right, #3b82f6, #9333ea);
  transition: width .3s;
}
</style>
