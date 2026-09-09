<template>
  <div class="ai-center">
    <!-- 概览统计 -->
    <div class="stat-grid">
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#c084fc,#9333ea)"><Sparkles :size="22" color="#fff" /></div>
        <div>
          <div class="ref-stat-value">{{ skills.length }}</div>
          <div class="ref-stat-label">SKILL 总数 · 启用 {{ skills.filter((s) => s.status === 'ACTIVE').length }}</div>
        </div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#60a5fa,#2563eb)"><Wrench :size="22" color="#fff" /></div>
        <div>
          <div class="ref-stat-value">{{ tools.filter((t) => t.enabled).length }}<span class="of">/ {{ tools.length }}</span></div>
          <div class="ref-stat-label">已启用工具</div>
        </div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#4ade80,#16a34a)"><Activity :size="22" color="#fff" /></div>
        <div>
          <div class="ref-stat-value">{{ usage?.runs ?? '–' }}</div>
          <div class="ref-stat-label">近 {{ usageDays }} 天运行次数</div>
        </div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#facc15,#f59e0b)"><Users :size="22" color="#fff" /></div>
        <div>
          <div class="ref-stat-value">{{ usage ? fmtK(usage.monthTokens) : '–' }}</div>
          <div class="ref-stat-label">
            本月 Token{{ usage?.monthlyTokenBudget ? ` · 预算 ${fmtK(usage.monthlyTokenBudget)},已用 ${Math.min(100, Math.round(usage.monthTokens * 100 / usage.monthlyTokenBudget))}%` : ' · 未设预算' }}
            · {{ usage?.activeUsers ?? '–' }} 活跃用户
          </div>
        </div>
      </div>
    </div>

    <!-- 分段切换 -->
    <div class="seg-row">
      <div class="seg">
        <button v-for="t in tabs" :key="t.value" :class="{ active: tab === t.value }" @click="switchTab(t.value)">
          <component :is="t.icon" :size="15" />{{ t.label }}
          <span v-if="t.count !== undefined" class="seg-count">{{ t.count }}</span>
        </button>
      </div>
      <div class="seg-right">
        <template v-if="tab === 'skills'">
          <div class="pill-row">
            <span v-for="f in scopeFilters" :key="f.value" class="pill small" :class="{ active: scopeFilter === f.value }" @click="scopeFilter = f.value">{{ f.label }}</span>
          </div>
          <el-input v-model="skillKeyword" placeholder="搜索名称 / key / 简介" clearable style="width:220px">
            <template #prefix><Search :size="14" /></template>
          </el-input>
          <el-button type="primary" plain @click="$router.push('/app/ai')"><Bot :size="14" style="margin-right:6px" />去 AI 助手创建 / 编辑</el-button>
        </template>
        <template v-else-if="tab === 'tools'">
          <span class="muted">{{ tools.filter((t) => !t.readOnly).length }} 个写操作工具默认必须经用户确认</span>
        </template>
        <template v-else-if="tab === 'runs'">
          <el-input-number v-model="runUserId" placeholder="按用户 id" :min="1" :controls="false" style="width:130px" />
          <el-button @click="runPage = 0; loadRuns()">查询</el-button>
          <span class="muted">共 {{ runsTotal }} 条</span>
        </template>
        <template v-else>
          <div class="pill-row">
            <span v-for="d in [7, 14, 30]" :key="d" class="pill small" :class="{ active: usageDays === d }" @click="usageDays = d; loadUsage()">近 {{ d }} 天</span>
          </div>
        </template>
      </div>
    </div>

    <!-- ========== SKILL 卡片 ========== -->
    <template v-if="tab === 'skills'">
      <div v-if="filteredSkills.length === 0" class="card empty">
        <div class="empty-icon">✨</div>
        <div class="empty-title">{{ skills.length === 0 ? '没有可用的 SKILL' : '没有匹配的 SKILL' }}</div>
        <div class="muted">内置 SKILL 随版本发布;学生、教师在「AI 助手」里自定义的 SKILL 会出现在这里,你可以把好用的设为本站共享。</div>
      </div>
      <div v-else class="skill-grid">
        <div v-for="s in filteredSkills" :key="s.key" class="skill-card" :class="{ off: s.status !== 'ACTIVE' }">
          <div class="sk-top">
            <span class="sk-icon" :style="{ background: scopeBg(s.scope) }">{{ s.icon || '✨' }}</span>
            <div class="sk-title-box">
              <div class="sk-name">{{ s.name }}</div>
              <div class="sk-key">{{ s.key }} · v{{ s.version }}</div>
            </div>
            <span class="badge" :class="scopeBadge(s.scope)">{{ scopeText(s.scope) }}</span>
          </div>
          <div class="sk-desc">{{ s.description || '暂无简介' }}</div>
          <div class="sk-tools">
            <span v-if="!s.tools?.length" class="muted">不调用工具</span>
            <span v-for="t in (s.tools || []).slice(0, 4)" :key="t" class="chip">{{ t }}</span>
            <span v-if="(s.tools || []).length > 4" class="chip">+{{ s.tools.length - 4 }}</span>
          </div>
          <div class="sk-meta">
            <span><Layers :size="12" /> {{ s.outputMode === 'JSON' ? '结构化输出' : '对话输出' }}</span>
            <span v-if="s.structuredInput"><ClipboardList :size="12" /> 表单输入</span>
            <span v-if="s.ownerUserId"><User :size="12" /> 用户 #{{ s.ownerUserId }}</span>
            <span v-if="s.forkedFrom"><GitFork :size="12" /> 派生自 #{{ s.forkedFrom }}</span>
          </div>
          <div class="sk-foot">
            <span v-if="s.scope === 'BUILTIN'" class="muted">随版本发布 · 始终启用</span>
            <el-switch v-else :model-value="s.status === 'ACTIVE'" size="small" inline-prompt active-text="启用" inactive-text="停用"
                       :loading="acting === s.id" @change="toggleStatus(s)" />
            <span class="spacer"></span>
            <el-button v-if="s.scope === 'PERSONAL'" size="small" type="primary" plain @click="promote(s)">设为本站共享</el-button>
            <el-button size="small" text @click="$router.push({ path: '/app/ai', query: { skill: s.key } })">试用</el-button>
            <el-button v-if="s.scope !== 'BUILTIN'" size="small" text type="danger" @click="remove(s)">删除</el-button>
          </div>
        </div>
      </div>
    </template>

    <!-- ========== 工具策略卡片 ========== -->
    <template v-else-if="tab === 'tools'">
      <div class="tool-grid">
        <div v-for="t in tools" :key="t.name" class="tool-card" :class="{ off: !t.enabled }">
          <div class="tl-top">
            <span class="tl-icon" :class="t.readOnly ? 'ro' : 'rw'">
              <Eye v-if="t.readOnly" :size="18" /><PencilLine v-else :size="18" />
            </span>
            <div class="tl-title-box">
              <div class="tl-name">{{ t.name }}</div>
              <div class="tl-type">
                <span class="badge" :class="t.readOnly ? 'badge-blue' : 'badge-yellow'">{{ t.readOnly ? '只读' : '写操作' }}</span>
                <span class="muted">默认角色 {{ roleText(t.defaultRole) }}</span>
              </div>
            </div>
            <el-switch v-model="t.enabled" :loading="acting === t.name" @change="saveTool(t, { enabled: t.enabled })" />
          </div>
          <div class="tl-desc">{{ t.description }}</div>
          <div class="tl-controls">
            <div class="ctl">
              <span class="ctl-label">最低角色</span>
              <el-select :model-value="t.minRole || 'STUDENT'" size="small" style="width:120px" @change="(v) => saveTool(t, { minRole: v })">
                <el-option value="STUDENT" label="所有用户" />
                <el-option value="TEACHER" label="教师及以上" />
                <el-option value="ADMIN" label="仅管理员" />
              </el-select>
            </div>
            <div class="ctl">
              <span class="ctl-label">执行前确认</span>
              <el-switch v-model="t.requiresConfirmation" size="small" :disabled="!t.readOnly"
                         @change="saveTool(t, { requiresConfirmation: t.requiresConfirmation })" />
              <span v-if="!t.readOnly" class="muted">写操作固定需确认</span>
            </div>
            <div class="ctl">
              <span class="ctl-label">每人每日上限</span>
              <el-input-number v-model="t.dailyLimit" :min="0" :max="1000" size="small" style="width:120px" placeholder="不限"
                               @change="saveTool(t, { dailyLimit: t.dailyLimit || null })" />
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ========== 调用审计 ========== -->
    <template v-else-if="tab === 'runs'">
      <div class="card table-card">
        <el-table :data="runs" stripe @row-click="openRun" row-class-name="clickable">
          <el-table-column prop="id" label="#" width="70" />
          <el-table-column label="时间" width="150"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
          <el-table-column label="用户" width="150"><template #default="{ row }">{{ row.userName }} <span class="muted">#{{ row.userId }}</span></template></el-table-column>
          <el-table-column prop="skillKey" label="SKILL" width="170" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }"><span class="badge" :class="statusBadge(row.status)">{{ statusText(row.status) }}</span></template>
          </el-table-column>
          <el-table-column prop="input" label="输入" min-width="240" show-overflow-tooltip />
          <el-table-column prop="toolRounds" label="工具轮" width="80" />
          <el-table-column label="Token 入/出" width="130"><template #default="{ row }">{{ row.promptTokens }} / {{ row.completionTokens }}</template></el-table-column>
          <el-table-column label="耗时" width="90"><template #default="{ row }">{{ row.latencyMs }} ms</template></el-table-column>
          <template #empty>
            <div class="empty in-table"><div class="empty-icon">🧾</div><div class="empty-title">还没有调用记录</div><div class="muted">学生或教师在 AI 助手里发起对话后会记录在这里</div></div>
          </template>
        </el-table>
        <el-pagination v-if="runsTotal > 20" layout="prev, pager, next" :total="runsTotal" :page-size="20" :current-page="runPage + 1"
                       @current-change="(p) => { runPage = p - 1; loadRuns() }" class="pager" />
      </div>
      <el-drawer v-model="runVisible" :title="`运行 #${runDetail?.id}`" size="600px">
        <div v-if="runDetail">
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="用户">{{ runDetail.userName }} (#{{ runDetail.userId }})</el-descriptions-item>
            <el-descriptions-item label="SKILL">{{ runDetail.skillKey }} v{{ runDetail.skillVersion }}</el-descriptions-item>
            <el-descriptions-item label="状态"><span class="badge" :class="statusBadge(runDetail.status)">{{ statusText(runDetail.status) }}</span> {{ runDetail.error ? '· ' + runDetail.error : '' }}</el-descriptions-item>
            <el-descriptions-item label="Token">输入 {{ runDetail.promptTokens }} / 输出 {{ runDetail.completionTokens }} · {{ runDetail.latencyMs }} ms</el-descriptions-item>
            <el-descriptions-item label="输入"><pre class="pre">{{ runDetail.input }}</pre></el-descriptions-item>
            <el-descriptions-item label="输出"><pre class="pre">{{ runDetail.output }}</pre></el-descriptions-item>
          </el-descriptions>
          <h4>工具调用</h4>
          <div v-for="t in runTools" :key="t.id" class="tool-row">
            <b>{{ t.toolName }}</b> <el-tag size="small" :type="t.ok ? 'success' : 'danger'">{{ t.ok ? '成功' : '失败' }}</el-tag>
            <el-tag v-if="t.confirmed" size="small" type="warning">已确认</el-tag> <span class="muted">{{ t.latencyMs }}ms</span>
            <pre class="pre">{{ t.arguments }}</pre>
            <pre class="pre">{{ t.resultSummary }}</pre>
          </div>
          <div v-if="runTools.length === 0" class="muted">本次运行没有调用工具</div>
        </div>
      </el-drawer>
    </template>

    <!-- ========== 用量统计 ========== -->
    <template v-else>
      <div class="usage-grid">
        <div class="card">
          <div class="card-head">
            <h3>每日运行次数与 Token</h3>
            <span class="muted">输入 {{ (usage?.promptTokens || 0).toLocaleString() }} · 输出 {{ (usage?.completionTokens || 0).toLocaleString() }} Token</span>
          </div>
          <div ref="chartRef" class="chart"></div>
        </div>
        <div class="card">
          <div class="card-head"><h3>按日明细</h3></div>
          <el-table :data="[...(usage?.series || [])].reverse()" size="small" max-height="300">
            <el-table-column label="日期" width="100"><template #default="{ row }">{{ row.day.slice(5) }}</template></el-table-column>
            <el-table-column prop="runs" label="次数" width="70" />
            <el-table-column label="Token 入/出"><template #default="{ row }">{{ row.promptTokens.toLocaleString() }} / {{ row.completionTokens.toLocaleString() }}</template></el-table-column>
          </el-table>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Activity, BarChart3, Bot, ClipboardList, Eye, GitFork, Layers, PencilLine, ScrollText, Search, Sparkles, User, Users, Wrench
} from 'lucide-vue-next'
import {
  adminAiPromoteSkill, adminAiRunTools, adminAiRuns, adminAiSkills, adminAiTools, adminAiUpdateTool, adminAiUsage,
  aiDeleteSkill, aiUpdateSkill
} from '../../api'

const tab = ref('skills')
const skills = ref([])
const tools = ref([])
const runs = ref([])
const runsTotal = ref(0)
const runPage = ref(0)
const runUserId = ref(null)
const runVisible = ref(false)
const runDetail = ref(null)
const runTools = ref([])
const usage = ref(null)
const usageDays = ref(14)
const acting = ref(null)
const scopeFilter = ref('ALL')
const skillKeyword = ref('')
const chartRef = ref(null)
let chart = null

const tabs = computed(() => [
  { value: 'skills', label: 'SKILL 管理', icon: Sparkles, count: skills.value.length },
  { value: 'tools', label: '工具策略', icon: Wrench, count: tools.value.length },
  { value: 'runs', label: '调用审计', icon: ScrollText },
  { value: 'usage', label: '用量统计', icon: BarChart3 }
])
const scopeFilters = [
  { value: 'ALL', label: '全部' }, { value: 'BUILTIN', label: '内置' }, { value: 'TENANT', label: '本站共享' }, { value: 'PERSONAL', label: '个人' }
]
const filteredSkills = computed(() => {
  const k = skillKeyword.value.trim().toLowerCase()
  return skills.value.filter((s) => {
    if (scopeFilter.value !== 'ALL' && s.scope !== scopeFilter.value) return false
    return !k || (s.name + ' ' + s.key + ' ' + (s.description || '')).toLowerCase().includes(k)
  })
})

const scopeText = (s) => ({ BUILTIN: '内置', TENANT: '本站共享', PERSONAL: '个人' }[s] || s)
const scopeBadge = (s) => ({ BUILTIN: 'badge-purple', TENANT: 'badge-green', PERSONAL: 'badge-blue' }[s] || 'badge-gray')
const scopeBg = (s) => ({
  BUILTIN: 'linear-gradient(135deg,#ede9fe,#f5f3ff)', TENANT: 'linear-gradient(135deg,#dcfce7,#f0fdf4)', PERSONAL: 'linear-gradient(135deg,#dbeafe,#eff6ff)'
}[s] || '#f3f4f6')
const roleText = (r) => ({ STUDENT: '所有用户', TEACHER: '教师及以上', ADMIN: '仅管理员' }[r] || r || '所有用户')
const statusText = (s) => ({ SUCCESS: '成功', FAILED: '失败', CONFIRM_REQUIRED: '待确认', RUNNING: '运行中' }[s] || s)
const statusBadge = (s) => ({ SUCCESS: 'badge-green', FAILED: 'badge-red', CONFIRM_REQUIRED: 'badge-yellow' }[s] || 'badge-gray')
const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '–')
const fmtK = (n) => (n >= 1000000 ? (n / 1000000).toFixed(2) + 'M' : n >= 1000 ? (n / 1000).toFixed(n >= 100000 ? 0 : 1) + 'k' : String(n))

const loadSkills = async () => { skills.value = await adminAiSkills() }
const loadTools = async () => { tools.value = await adminAiTools() }
const loadRuns = async () => {
  const r = await adminAiRuns({ page: runPage.value, size: 20, userId: runUserId.value || undefined })
  runs.value = r.items
  runsTotal.value = r.total
}
const loadUsage = async () => {
  usage.value = await adminAiUsage(usageDays.value)
  if (tab.value === 'usage') renderChart()
}

const renderChart = async () => {
  await nextTick()
  if (!chartRef.value || !usage.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const s = usage.value.series
  chart.setOption({
    grid: { left: 44, right: 56, top: 40, bottom: 28 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['运行次数', '输入 Token', '输出 Token'], top: 0, left: 'center', itemWidth: 14, textStyle: { color: '#6b7280', fontSize: 12 } },
    xAxis: { type: 'category', data: s.map((x) => x.day.slice(5)), axisLabel: { color: '#6b7280' }, axisLine: { lineStyle: { color: '#e5e7eb' } } },
    yAxis: [
      { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#f3f4f6' } }, axisLabel: { color: '#6b7280', formatter: '{value} 次' } },
      { type: 'value', splitLine: { show: false }, axisLabel: { color: '#6b7280', formatter: (v) => (v >= 1000 ? (v / 1000).toFixed(v >= 10000 ? 0 : 1) + 'k' : v) } }
    ],
    series: [
      { name: '运行次数', type: 'bar', data: s.map((x) => x.runs), barMaxWidth: 26, itemStyle: { color: '#8b5cf6', borderRadius: [6, 6, 0, 0] } },
      { name: '输入 Token', type: 'line', yAxisIndex: 1, smooth: true, data: s.map((x) => x.promptTokens), lineStyle: { color: '#3b82f6', width: 2 }, itemStyle: { color: '#3b82f6' }, symbolSize: 5 },
      { name: '输出 Token', type: 'line', yAxisIndex: 1, smooth: true, data: s.map((x) => x.completionTokens), lineStyle: { color: '#22c55e', width: 2 }, itemStyle: { color: '#22c55e' }, symbolSize: 5 }
    ]
  }, true)
}

const switchTab = (name) => {
  tab.value = name
  if (name === 'runs') loadRuns()
  if (name === 'usage') { chart = null; renderChart() }
}
watch(tab, (t) => { if (t === 'usage') { chart = null; renderChart() } })

const toggleStatus = async (row) => {
  acting.value = row.id
  try {
    await aiUpdateSkill(row.id, { status: row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })
    row.status = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
    ElMessage.success(row.status === 'ACTIVE' ? '已启用' : '已停用')
  } finally {
    acting.value = null
  }
}
const promote = async (row) => {
  await adminAiPromoteSkill(row.id)
  ElMessage.success('已设为本站共享,全站用户可见')
  await loadSkills()
}
const remove = async (row) => {
  try { await ElMessageBox.confirm(`删除 SKILL「${row.name}」?使用它的会话历史保留,但不能再发起新对话。`, '删除 SKILL', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch (e) { return }
  await aiDeleteSkill(row.id)
  ElMessage.success('已删除')
  await loadSkills()
}
const saveTool = async (row, patch) => {
  acting.value = row.name
  try {
    const updated = await adminAiUpdateTool(row.name, patch)
    Object.assign(row, updated)
    ElMessage.success('策略已更新')
  } finally {
    acting.value = null
  }
}
const openRun = async (row) => {
  runDetail.value = row
  runTools.value = await adminAiRunTools(row.id)
  runVisible.value = true
}

const onResize = () => chart && chart.resize()
onMounted(async () => {
  await Promise.all([loadSkills(), loadTools(), loadUsage()])
  window.addEventListener('resize', onResize)
})
onUnmounted(() => window.removeEventListener('resize', onResize))
</script>

<style scoped>
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 18px; }
@media (max-width: 1200px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }
.of { font-size: 14px; color: #9ca3af; font-weight: 500; margin-left: 4px; }
.muted { font-size: 12px; color: #9ca3af; }
.spacer { flex: 1; }
:deep(.clickable) { cursor: pointer; }

/* 分段 */
.seg-row { display: flex; justify-content: space-between; align-items: center; gap: 14px; margin-bottom: 16px; flex-wrap: wrap; }
.seg { display: inline-flex; background: #e9ebf3; border-radius: 12px; padding: 4px; gap: 2px; }
.seg button {
  border: none; background: transparent; padding: 8px 16px; border-radius: 9px; font-size: 13px; color: #4b5563; cursor: pointer;
  display: inline-flex; align-items: center; gap: 7px; transition: all .15s;
}
.seg button.active { background: #fff; color: #111827; font-weight: 600; box-shadow: 0 1px 3px rgba(15, 23, 42, .12); }
.seg-count { font-size: 11px; padding: 0 6px; border-radius: 999px; background: #dfe3ee; color: #6b7280; }
.seg button.active .seg-count { background: #eef2ff; color: #4f46e5; }
.seg-right { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.pill-row { display: flex; gap: 6px; }
.pill.small { padding: 5px 12px; font-size: 12px; }

/* 空状态 */
.empty { text-align: center; padding: 56px 20px; color: #6b7280; }
.empty.in-table { padding: 36px 20px; }
.empty-icon { font-size: 40px; margin-bottom: 10px; }
.empty-title { font-weight: 600; color: #374151; margin-bottom: 4px; }

/* SKILL 卡片 */
.skill-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
.skill-card {
  background: #fff; border-radius: 16px; box-shadow: var(--shadow-card); padding: 18px 18px 14px;
  display: flex; flex-direction: column; gap: 10px; border: 1px solid transparent; transition: box-shadow .15s, transform .15s;
}
.skill-card:hover { box-shadow: var(--shadow-lg); transform: translateY(-2px); }
.skill-card.off { opacity: .72; background: #fafafa; }
.sk-top { display: flex; align-items: flex-start; gap: 12px; }
.sk-icon { width: 46px; height: 46px; border-radius: 12px; display: grid; place-items: center; font-size: 22px; flex-shrink: 0; }
.sk-title-box { flex: 1; min-width: 0; }
.sk-name { font-weight: 700; font-size: 15px; color: #111827; }
.sk-key { font-size: 12px; color: #9ca3af; font-family: ui-monospace, Menlo, Consolas, monospace; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sk-desc { font-size: 13px; color: #6b7280; line-height: 1.6; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; min-height: 41px; }
.sk-tools { display: flex; flex-wrap: wrap; gap: 0; min-height: 22px; }
.sk-tools .chip { margin: 0 6px 4px 0; }
.sk-meta { display: flex; gap: 12px; flex-wrap: wrap; font-size: 12px; color: #9ca3af; }
.sk-meta span { display: inline-flex; align-items: center; gap: 4px; }
.sk-foot { display: flex; align-items: center; gap: 6px; padding-top: 10px; border-top: 1px solid var(--border); margin-top: auto; }

/* 工具卡片 */
.tool-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 16px; }
.tool-card { background: #fff; border-radius: 16px; box-shadow: var(--shadow-card); padding: 18px; display: flex; flex-direction: column; gap: 12px; }
.tool-card.off { opacity: .7; background: #fafafa; }
.tl-top { display: flex; align-items: center; gap: 12px; }
.tl-icon { width: 42px; height: 42px; border-radius: 12px; display: grid; place-items: center; flex-shrink: 0; }
.tl-icon.ro { background: #eff6ff; color: #2563eb; }
.tl-icon.rw { background: #fefce8; color: #ca8a04; }
.tl-title-box { flex: 1; min-width: 0; }
.tl-name { font-weight: 700; font-size: 14px; font-family: ui-monospace, Menlo, Consolas, monospace; color: #111827; }
.tl-type { display: flex; align-items: center; gap: 8px; margin-top: 4px; }
.tl-desc { font-size: 13px; color: #6b7280; line-height: 1.6; }
.tl-controls { display: flex; flex-direction: column; gap: 8px; background: #f9fafb; border-radius: 12px; padding: 12px 14px; }
.ctl { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.ctl-label { width: 92px; color: #4b5563; flex-shrink: 0; }

/* 审计 */
.table-card { padding: 8px 8px 12px; }
.pager { margin: 12px 8px 0; justify-content: flex-end; }
.pre { white-space: pre-wrap; word-break: break-all; background: #f9fafb; padding: 8px; border-radius: 6px; font-size: 12px; max-height: 220px; overflow: auto; margin: 4px 0; }
.tool-row { border: 1px solid var(--border); border-radius: 8px; padding: 8px 10px; margin-bottom: 8px; font-size: 13px; }
h4 { margin: 16px 0 8px; }

/* 用量 */
.usage-grid { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; }
@media (max-width: 1100px) { .usage-grid { grid-template-columns: 1fr; } }
.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.card-head h3 { margin: 0; font-size: 15px; }
.chart { height: 300px; }
</style>
