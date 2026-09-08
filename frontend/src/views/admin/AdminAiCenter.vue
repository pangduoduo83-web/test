<template>
  <div>
    <el-tabs v-model="tab" @tab-change="onTab">
      <!-- SKILL 管理 -->
      <el-tab-pane label="SKILL 管理" name="skills">
        <p class="hint">内置 SKILL 随版本发布不可改;本站共享 SKILL 对全站用户可见;个人 SKILL 可由管理员提升为本站共享。编辑请到「AI 助手」页选中该 SKILL 后操作。</p>
        <el-table :data="skills" stripe>
          <el-table-column label="SKILL" min-width="220">
            <template #default="{ row }"><span class="icon">{{ row.icon }}</span> <b>{{ row.name }}</b><div class="sub">{{ row.key }}</div></template>
          </el-table-column>
          <el-table-column prop="description" label="简介" min-width="240" show-overflow-tooltip />
          <el-table-column label="范围" width="100">
            <template #default="{ row }"><el-tag size="small" :type="row.scope === 'TENANT' ? 'success' : 'info'">{{ scopeText(row.scope) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="ownerUserId" label="所有者" width="90" />
          <el-table-column label="工具" min-width="200">
            <template #default="{ row }"><el-tag v-for="t in row.tools" :key="t" size="small" effect="plain" class="tag">{{ t }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="70" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'danger'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button size="small" @click="toggleStatus(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
              <el-button v-if="row.scope === 'PERSONAL'" size="small" type="primary" @click="promote(row)">设为本站共享</el-button>
              <el-button size="small" type="danger" text @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 工具策略 -->
      <el-tab-pane label="工具策略" name="tools">
        <p class="hint">工具是模型可以调用的平台能力。写操作类工具(如提交借阅申请)默认必须经用户确认;可按角色限制、关闭或设置每人每日次数。</p>
        <el-table :data="tools" stripe>
          <el-table-column prop="name" label="工具" width="180" />
          <el-table-column prop="description" label="说明" min-width="260" show-overflow-tooltip />
          <el-table-column label="类型" width="90">
            <template #default="{ row }"><el-tag size="small" :type="row.readOnly ? 'info' : 'warning'">{{ row.readOnly ? '只读' : '写操作' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="启用" width="80">
            <template #default="{ row }"><el-switch v-model="row.enabled" @change="saveTool(row, { enabled: row.enabled })" /></template>
          </el-table-column>
          <el-table-column label="最低角色" width="150">
            <template #default="{ row }">
              <el-select :model-value="row.minRole || 'STUDENT'" size="small" @change="(v) => saveTool(row, { minRole: v })">
                <el-option value="STUDENT" label="所有用户" /><el-option value="TEACHER" label="教师及以上" /><el-option value="ADMIN" label="仅管理员" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="需确认" width="90">
            <template #default="{ row }"><el-switch v-model="row.requiresConfirmation" :disabled="!row.readOnly" @change="saveTool(row, { requiresConfirmation: row.requiresConfirmation })" /></template>
          </el-table-column>
          <el-table-column label="每人每日上限" width="160">
            <template #default="{ row }"><el-input-number v-model="row.dailyLimit" :min="0" :max="1000" size="small" placeholder="不限" @change="saveTool(row, { dailyLimit: row.dailyLimit || null })" /></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 调用审计 -->
      <el-tab-pane label="调用审计" name="runs">
        <div class="toolbar">
          <el-input-number v-model="runUserId" placeholder="按用户 id 筛选" :min="1" :controls="false" style="width:160px" />
          <el-button @click="loadRuns">查询</el-button>
          <span class="count">共 {{ runsTotal }} 条</span>
        </div>
        <el-table :data="runs" stripe @row-click="openRun">
          <el-table-column prop="id" label="#" width="70" />
          <el-table-column prop="createdAt" label="时间" width="165" />
          <el-table-column label="用户" width="140"><template #default="{ row }">{{ row.userName }} (#{{ row.userId }})</template></el-table-column>
          <el-table-column prop="skillKey" label="SKILL" width="160" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }"><el-tag size="small" :type="{ SUCCESS: 'success', FAILED: 'danger', CONFIRM_REQUIRED: 'warning' }[row.status] || 'info'">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="input" label="输入" min-width="220" show-overflow-tooltip />
          <el-table-column prop="toolRounds" label="工具轮" width="80" />
          <el-table-column label="Token" width="120"><template #default="{ row }">{{ row.promptTokens }} / {{ row.completionTokens }}</template></el-table-column>
          <el-table-column prop="latencyMs" label="耗时ms" width="90" />
        </el-table>
        <el-pagination layout="prev, pager, next" :total="runsTotal" :page-size="20" :current-page="runPage + 1"
                       @current-change="(p) => { runPage = p - 1; loadRuns() }" class="pager" />
        <el-drawer v-model="runVisible" :title="`运行 #${runDetail?.id}`" size="560px">
          <div v-if="runDetail">
            <el-descriptions :column="1" size="small" border>
              <el-descriptions-item label="用户">{{ runDetail.userName }} (#{{ runDetail.userId }})</el-descriptions-item>
              <el-descriptions-item label="SKILL">{{ runDetail.skillKey }} v{{ runDetail.skillVersion }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ runDetail.status }} {{ runDetail.error ? '· ' + runDetail.error : '' }}</el-descriptions-item>
              <el-descriptions-item label="输入"><pre class="pre">{{ runDetail.input }}</pre></el-descriptions-item>
              <el-descriptions-item label="输出"><pre class="pre">{{ runDetail.output }}</pre></el-descriptions-item>
            </el-descriptions>
            <h4>工具调用</h4>
            <div v-for="t in runTools" :key="t.id" class="tool-row">
              <b>{{ t.toolName }}</b> <el-tag size="small" :type="t.ok ? 'success' : 'danger'">{{ t.ok ? '成功' : '失败' }}</el-tag>
              <el-tag v-if="t.confirmed" size="small" type="warning">已确认</el-tag> <span class="sub">{{ t.latencyMs }}ms</span>
              <pre class="pre">{{ t.arguments }}</pre>
              <pre class="pre">{{ t.resultSummary }}</pre>
            </div>
            <div v-if="runTools.length === 0" class="sub">本次运行没有调用工具</div>
          </div>
        </el-drawer>
      </el-tab-pane>

      <!-- 用量 -->
      <el-tab-pane label="用量统计" name="usage">
        <div v-if="usage" class="usage">
          <div class="stat card"><div class="stat-num">{{ usage.runs }}</div><div class="stat-label">近 {{ usage.days }} 天运行次数</div></div>
          <div class="stat card"><div class="stat-num">{{ usage.activeUsers }}</div><div class="stat-label">活跃用户</div></div>
          <div class="stat card"><div class="stat-num">{{ (usage.promptTokens + usage.completionTokens).toLocaleString() }}</div><div class="stat-label">Token 总量(输入 {{ usage.promptTokens.toLocaleString() }} / 输出 {{ usage.completionTokens.toLocaleString() }})</div></div>
          <div class="stat card"><div class="stat-num">{{ usage.dailyRunsPerUser }}</div><div class="stat-label">每人每日次数上限(IOEDU_AI_DAILY_RUNS)</div></div>
        </div>
        <el-table v-if="usage" :data="usage.series" size="small" stripe class="series">
          <el-table-column prop="day" label="日期" width="130" />
          <el-table-column prop="runs" label="运行次数" width="110" />
          <el-table-column prop="promptTokens" label="输入 Token" width="130" />
          <el-table-column prop="completionTokens" label="输出 Token" width="130" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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

const scopeText = (s) => ({ BUILTIN: '内置', TENANT: '本站共享', PERSONAL: '个人' }[s] || s)

const loadSkills = async () => { skills.value = await adminAiSkills() }
const loadTools = async () => { tools.value = await adminAiTools() }
const loadRuns = async () => {
  const r = await adminAiRuns({ page: runPage.value, size: 20, userId: runUserId.value || undefined })
  runs.value = r.items
  runsTotal.value = r.total
}
const loadUsage = async () => { usage.value = await adminAiUsage(14) }

const onTab = (name) => {
  if (name === 'skills') loadSkills()
  if (name === 'tools') loadTools()
  if (name === 'runs') loadRuns()
  if (name === 'usage') loadUsage()
}

const toggleStatus = async (row) => {
  await aiUpdateSkill(row.id, { status: row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })
  await loadSkills()
}
const promote = async (row) => {
  await adminAiPromoteSkill(row.id)
  ElMessage.success('已设为本站共享')
  await loadSkills()
}
const remove = async (row) => {
  try { await ElMessageBox.confirm(`删除 SKILL「${row.name}」?`, '删除', { type: 'warning' }) } catch (e) { return }
  await aiDeleteSkill(row.id)
  await loadSkills()
}
const saveTool = async (row, patch) => {
  const updated = await adminAiUpdateTool(row.name, patch)
  Object.assign(row, updated)
  ElMessage.success('策略已更新')
}
const openRun = async (row) => {
  runDetail.value = row
  runTools.value = await adminAiRunTools(row.id)
  runVisible.value = true
}

onMounted(loadSkills)
</script>

<style scoped>
.hint { font-size: 13px; color: #6b7280; margin: 0 0 12px; }
.icon { font-size: 18px; margin-right: 4px; }
.sub { font-size: 12px; color: #9ca3af; }
.tag { margin: 0 4px 4px 0; }
.toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 12px; }
.count { color: #9ca3af; font-size: 13px; margin-left: auto; }
.pager { margin-top: 12px; justify-content: flex-end; }
.pre { white-space: pre-wrap; word-break: break-all; background: #f9fafb; padding: 8px; border-radius: 6px; font-size: 12px; max-height: 220px; overflow: auto; margin: 4px 0; }
.tool-row { border: 1px solid var(--border); border-radius: 8px; padding: 8px 10px; margin-bottom: 8px; font-size: 13px; }
h4 { margin: 16px 0 8px; }
.usage { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 16px; }
.stat { padding: 18px; }
.stat-num { font-size: 26px; font-weight: 700; }
.stat-label { font-size: 12px; color: #6b7280; margin-top: 4px; }
</style>
