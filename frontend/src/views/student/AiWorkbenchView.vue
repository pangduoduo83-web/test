<template>
  <div class="wb">
    <!-- 顶部横幅 -->
    <header class="hero">
      <div class="hero-bg-overlay"></div>
      <div class="hero-left">
        <div class="hero-tag">
          <Sparkles :size="13" />
          <span>AI 助教 · 任务引导</span>
        </div>
        <h2 class="hero-title">欢迎来到 AI 未来项目实践中心</h2>
        <p class="hero-desc">
          这里为你提供硬件设计、项目实践、设备资源等全方位的学习支持。无论你是想学习基础知识，还是进行项目实战，AI 助教都能为你提供个性化的建议和帮助。
        </p>
        <div class="hero-steps">
          <span class="step-item"><span class="step-num">1</span> 选择需求</span>
          <ArrowRight :size="13" class="step-sep" />
          <span class="step-item"><span class="step-num">2</span> AI 分析</span>
          <ArrowRight :size="13" class="step-sep" />
          <span class="step-item"><span class="step-num">3</span> 生成推荐</span>
          <ArrowRight :size="13" class="step-sep" />
          <span class="step-item"><span class="step-num">4</span> 开始学习</span>
        </div>
      </div>

      <div class="hero-stats">
        <div class="stat-card" @click="$router.push('/app/dashboard')">
          <CheckSquare :size="16" class="stat-ico stat-ico-blue" />
          <span class="stat-label">今日已完成 <b>{{ stats.completedTasks || 0 }}</b> 个任务</span>
        </div>
        <div class="stat-card" @click="$router.push('/app/projects')">
          <Boxes :size="16" class="stat-ico stat-ico-indigo" />
          <span class="stat-label">项目可领取 <b>{{ stats.availableProjects || 5 }}</b> 个</span>
        </div>
        <div class="stat-card" @click="select('skills')">
          <TrendingUp :size="16" class="stat-ico stat-ico-purple" />
          <span class="stat-label">技能提升 <b>{{ stats.skillsCount || allSkills.length || 3 }}</b> 个</span>
        </div>
      </div>
    </header>

    <!-- 5 个快捷任务卡片 -->
    <section class="tiles">
      <div v-for="t in ALL_TILES" :key="t.key" class="tile"
           :class="[t.key, { on: active === t.key || (t.key === 'skills' && active === 'custom') }]"
           :style="{ '--tile-bg': t.bg, '--tile-accent': t.accent, '--tile-border': t.border }"
           @click="select(t.key)">
        <div class="tile-icon-box" :style="{ background: t.accent }">
          <component :is="t.iconComp" :size="18" color="#fff" />
        </div>
        <div class="tile-text">
          <div class="tile-name">{{ t.name }}</div>
          <div class="tile-desc">{{ t.desc }}</div>
        </div>
        <ArrowRight :size="15" class="tile-arrow" :style="{ color: t.accent }" />
      </div>
    </section>

    <!-- 1. 项目推荐 -->
    <TaskPanel v-if="active === 'recommend'" ref="panel" skill-key="task-recommend" title="项目推荐"
               result-title="AI 为你推荐的项目" accent="#2563eb" accent-bg="#eff6ff"
               empty-title="还没有推荐结果" empty-sub="请填写左侧的需求信息，AI 将为你推荐最合适的学习项目"
               :initial="initial" @done="onDone" @refresh="onRecRefresh">
      <template #header-icon>
        <FolderCode :size="16" color="#2563eb" />
      </template>
      <template #form="{ run, running }">
        <div class="f">
          <label>我想学 / 想做的</label>
          <el-input v-model="rec.goal" type="textarea" :rows="3" maxlength="200"
                    placeholder="例如: 基于 STM32 的智能温湿度采集系统, 或一个小型的物联网项目" />
        </div>
        <div class="f">
          <label>每周可投入</label>
          <div class="stepper-row">
            <el-input-number v-model="rec.weeklyHours" :min="1" :max="60" class="stepper-input" />
            <span class="stepper-unit">小时</span>
          </div>
        </div>
        <div class="f">
          <label>已有基础 (选填)</label>
          <el-checkbox-group v-model="rec.bases" class="bases-group">
            <el-checkbox label="单片机 (C 语言)">单片机 (C 语言)</el-checkbox>
            <el-checkbox label="PCB 设计">PCB 设计</el-checkbox>
            <el-checkbox label="传感器应用">传感器应用</el-checkbox>
            <el-checkbox label="嵌入式开发">嵌入式开发</el-checkbox>
          </el-checkbox-group>
          <el-input v-model="rec.note" maxlength="100" placeholder="学过 C 语言, 没画过 PCB (补充备注)" style="margin-top: 6px;" />
        </div>
        <div class="f">
          <label>推荐方向</label>
          <el-select v-model="rec.direction" placeholder="选择推荐方向" style="width:100%">
            <el-option label="人工智能 / 物联网方向" value="人工智能 / 物联网方向" />
            <el-option label="嵌入式系统 / 单片机方向" value="嵌入式系统 / 单片机方向" />
            <el-option label="硬件电路 / PCB 研发方向" value="硬件电路 / PCB 研发方向" />
            <el-option label="机器人 / 自动化控制方向" value="机器人 / 自动化控制方向" />
          </el-select>
        </div>
        <button class="go-btn" :disabled="running || !rec.goal.trim()" @click="submitRecommend(run)">
          <Sparkles :size="16" />
          <span>{{ running ? 'AI 正在分析生成中…' : '生成项目推荐' }}</span>
        </button>
      </template>
      <template #result="{ result, raw }">
        <RecommendResult v-if="result?.path" :data="result" />
        <AnswerResult v-else :raw="raw" />
      </template>
    </TaskPanel>

    <!-- 2. 设备顾问 -->
    <TaskPanel v-else-if="active === 'equipment'" ref="panel" skill-key="task-equipment" title="设备顾问"
               result-title="AI 为你配置的设备方案" accent="#059669" accent-bg="#f0fdf4"
               empty-title="还没有设备配置结果" empty-sub="请填写左侧的实验需求，AI 将为你配置本实验室最合适的设备"
               :initial="initial" @done="onDone" @refresh="onEqRefresh">
      <template #header-icon>
        <Cpu :size="16" color="#059669" />
      </template>
      <template #form="{ run, running }">
        <div class="f">
          <label>要做的实验 / 任务</label>
          <el-input v-model="eq.need" type="textarea" :rows="3" maxlength="200"
                    placeholder="例如: 测量 STM32 板子上 3.3V 电源的纹波, 并调试 SPI 通信" />
        </div>
        <div class="f">
          <label>补充要求 (选填)</label>
          <el-input v-model="eq.note" maxlength="100" placeholder="信号频率、电压范围、台数要求等" />
        </div>
        <div class="quick">
          <span v-for="q in eqQuick" :key="q" class="q" @click="eq.need = q">{{ q }}</span>
        </div>
        <button class="go-btn go-btn-green" :disabled="running || !eq.need.trim()"
                @click="run({ need: eq.need, note: eq.note || '无' }, '配设备 · ' + eq.need)">
          <Cpu :size="16" />
          <span>{{ running ? '正在配置设备中…' : '配置设备方案' }}</span>
        </button>
      </template>
      <template #result="{ result, raw }">
        <EquipmentResult v-if="result?.items" :data="result" />
        <AnswerResult v-else :raw="raw" />
      </template>
    </TaskPanel>

    <!-- 3. BOM 审查 -->
    <TaskPanel v-else-if="active === 'bom'" ref="panel" skill-key="task-bom" title="BOM 审查"
               result-title="BOM 审查与成本评估" accent="#ea580c" accent-bg="#fff7ed"
               empty-title="还没有审查结果" empty-sub="请在左侧选择报名项目或粘贴物料清单，AI 将按工程师标准进行审查"
               follow-up-field="none" :initial="initial" @done="onDone" :show-refresh="false">
      <template #header-icon>
        <FileSpreadsheet :size="16" color="#ea580c" />
      </template>
      <template #form="{ run, running }">
        <div class="f">
          <label>审查我报名项目的 BOM</label>
          <el-select v-model="bom.projectId" clearable placeholder="选一个我报名的项目 (可选)" style="width:100%">
            <el-option v-for="p in myProjects" :key="p.projectId" :label="p.projectTitle" :value="p.projectId" />
          </el-select>
        </div>
        <div class="f">
          <label>或直接粘贴 BOM 文本</label>
          <el-input v-model="bom.text" type="textarea" :rows="6" maxlength="6000"
                    placeholder="每行一个元件，如:&#10;C1 10uF 0805 x2&#10;U1 AMS1117-3.3 SOT-223&#10;R1 10k 0603 x4" />
        </div>
        <div class="f">
          <label>关注点 (选填)</label>
          <el-input v-model="bom.note" maxlength="100" placeholder="例如: 控制成本 / 全部用贴片 / 手焊友好" />
        </div>
        <button class="go-btn go-btn-orange" :disabled="running || (!bom.projectId && !bom.text.trim())"
                @click="run({ projectId: bom.projectId || '无', bom: bom.text || '(见项目)', note: bom.note || '无' }, bom.projectId ? 'BOM 审查 · ' + (myProjects.find((p) => p.projectId === bom.projectId)?.projectTitle || '项目') : 'BOM 审查 · ' + bom.text.split('\n').filter(Boolean).length + ' 行物料')">
          <FileSpreadsheet :size="16" />
          <span>{{ running ? '正在审查分析中…' : '开始审查 BOM' }}</span>
        </button>
      </template>
      <template #result="{ result, raw }">
        <BomResult v-if="result?.issues !== undefined" :data="result" />
        <AnswerResult v-else :raw="raw" />
      </template>
    </TaskPanel>

    <!-- 4. 知识问答 -->
    <TaskPanel v-else-if="active === 'ask'" ref="panel" skill-key="task-ask" title="知识问答"
               result-title="AI 专业解答与知识指导" accent="#9333ea" accent-bg="#faf5ff"
               empty-title="有什么技术问题想弄明白?" empty-sub="输入你的硬件疑问，AI 将以结构化要点与步骤为你解答"
               :initial="initial" @done="onDone" @refresh="onAskRefresh">
      <template #header-icon>
        <MessageSquareText :size="16" color="#9333ea" />
      </template>
      <template #form="{ run, running }">
        <div class="f">
          <label>技术问题 / 疑惑</label>
          <el-input v-model="ask.q" type="textarea" :rows="4" maxlength="500"
                    placeholder="例如: I2C 总线上拉电阻怎么选? 为什么我的传感器读数不稳定?"
                    @keydown.ctrl.enter="ask.q.trim() && run(ask.q)" />
        </div>
        <div class="quick">
          <span v-for="q in askQuick" :key="q" class="q" @click="ask.q = q">{{ q }}</span>
        </div>
        <button class="go-btn go-btn-purple" :disabled="running || !ask.q.trim()" @click="run(ask.q)">
          <MessageSquareText :size="16" />
          <span>{{ running ? 'AI 深度思考中…' : '解答技术问题' }}</span>
        </button>
        <small class="hint">也可以直接按 Ctrl + Enter 发送</small>
      </template>
      <template #result="{ result, raw }">
        <AnswerResult :data="result || {}" :raw="raw" />
      </template>
    </TaskPanel>

    <!-- 技能库 -->
    <section v-else-if="active === 'skills'" class="skills-lib">
      <div class="lib-head">
        <div>
          <h3>技能库</h3>
          <p class="muted">平台内置的 AI 能力,以及本站老师定制的技能。老师可以把常用辅导流程做成技能给学生用。</p>
        </div>
        <el-button v-if="isStaff" type="primary" @click="editSkill(null)"><Plus :size="14" style="margin-right:4px" />新建技能</el-button>
      </div>
      <div class="lib-grid">
        <div v-for="s in allSkills" :key="s.key" class="lib-card" :class="{ builtin: s.builtin }">
          <div class="lib-top">
            <span class="lib-icon">{{ s.icon }}</span>
            <div class="grow">
              <div class="lib-name">{{ s.name }}</div>
              <div class="lib-meta"><span class="tag" :class="s.scopeClass">{{ s.scopeText }}</span><span v-if="s.category" class="tag">{{ s.category }}</span></div>
            </div>
          </div>
          <p class="lib-desc">{{ s.description }}</p>
          <div class="lib-actions">
            <el-button size="small" type="primary" plain @click="useSkill(s)">{{ s.action }}</el-button>
            <el-button v-if="isStaff && !s.builtin" size="small" text @click="editSkill(s)">编辑</el-button>
          </div>
        </div>
      </div>
    </section>

    <!-- 本站自定义技能运行 -->
    <TaskPanel v-else-if="active === 'custom' && customSkill" ref="panel" :key="customSkill.key" :skill-key="customSkill.key" :icon="customSkill.icon || '✨'" :title="customSkill.name" accent="#7c3aed"
               :intro="customSkill.description" :json="false" empty-title="按左侧说明填写" empty-sub="这是本站老师定制的技能,结果以文字形式呈现" :initial="initial" @done="onDone">
      <template #form="{ run, running }">
        <div class="f">
          <label>技能</label>
          <el-select v-model="customKey" style="width:100%">
            <el-option v-for="s in customSkills" :key="s.key" :label="`${s.icon || '✨'} ${s.name}`" :value="s.key" />
          </el-select>
        </div>
        <div class="f">
          <label>输入</label>
          <el-input v-model="custom.q" type="textarea" :rows="5" maxlength="2000" :placeholder="customSkill.greeting || '请输入'" />
        </div>
        <button class="go" :disabled="running || !custom.q.trim()" @click="run(custom.q)">{{ running ? '运行中…' : '运行' }}</button>
        <div class="skill-admin">
          <el-button size="small" text @click="select('skills')">← 技能库</el-button>
          <el-button v-if="isStaff" size="small" text type="primary" @click="editSkill(customSkill)">编辑此技能</el-button>
        </div>
      </template>
      <template #result="{ raw }">
        <AnswerResult :raw="raw" />
      </template>
    </TaskPanel>

    <SkillEditorDialog v-model="editorVisible" :skill="editingSkill" @saved="onSkillSaved" @deleted="onSkillDeleted" />

    <!-- 最近任务 -->
    <section class="card history" v-if="history.length">
      <div class="h-head">
        <h3><History :size="16" /> 最近任务</h3>
        <span class="muted">点击重新打开结果</span>
      </div>
      <div class="h-list">
        <div v-for="c in history" :key="c.id" class="h-item" @click="openHistory(c)">
          <span class="h-icon">{{ iconOf(c.skillKey) }}</span>
          <div class="grow">
            <div class="h-title">{{ c.title || nameOf(c.skillKey) }}</div>
            <div class="h-meta">{{ nameOf(c.skillKey) }} · {{ fmt(c.updatedAt || c.createdAt) }}</div>
          </div>
          <el-button size="small" text type="danger" @click.stop="removeHistory(c)">删除</el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Sparkles,
  ArrowRight,
  CheckSquare,
  Boxes,
  TrendingUp,
  FolderCode,
  Cpu,
  FileSpreadsheet,
  MessageSquareText,
  Layers,
  History,
  Plus
} from 'lucide-vue-next'
import { aiConversationMessages, aiConversations, aiDeleteConversation, aiSkills, fetchDashboard } from '../../api'
import { parseJson } from '../../api/aiJson'
import TaskPanel from '../../components/ai/TaskPanel.vue'
import RecommendResult from '../../components/ai/RecommendResult.vue'
import EquipmentResult from '../../components/ai/EquipmentResult.vue'
import BomResult from '../../components/ai/BomResult.vue'
import AnswerResult from '../../components/ai/AnswerResult.vue'
import SkillEditorDialog from '../../components/ai/SkillEditorDialog.vue'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isStaff = ['ADMIN', 'TEACHER'].includes(authStore.user?.role)

// 任务配置体系(对齐图2清新质感)
const TASKS = [
  {
    key: 'recommend',
    skill: 'task-recommend',
    iconComp: FolderCode,
    name: '项目推荐',
    desc: '根据你的需求智能推荐合适的学习项目',
    accent: '#2563eb',
    bg: '#f0f7ff',
    border: '#dbeafe'
  },
  {
    key: 'equipment',
    skill: 'task-equipment',
    iconComp: Cpu,
    name: '设备顾问',
    desc: '快速查询设备信息、配置方案与使用建议',
    accent: '#059669',
    bg: '#f0fdf4',
    border: '#dcfce7'
  },
  {
    key: 'bom',
    skill: 'task-bom',
    iconComp: FileSpreadsheet,
    name: 'BOM 审查',
    desc: '检查元件兼容性、成本估算、替换建议',
    accent: '#ea580c',
    bg: '#fff7ed',
    border: '#ffedd5'
  },
  {
    key: 'ask',
    skill: 'task-ask',
    iconComp: MessageSquareText,
    name: '知识问答',
    desc: '专业知识解答、技术问题咨询',
    accent: '#9333ea',
    bg: '#faf5ff',
    border: '#f3e8ff'
  }
]

const skillsTile = {
  key: 'skills',
  iconComp: Layers,
  name: '技能库',
  desc: '系统化学习资源与技能路径',
  accent: '#0891b2',
  bg: '#ecfeff',
  border: '#cffafe'
}

const ALL_TILES = [...TASKS, skillsTile]

const LEGACY = new Set(['study-assistant', 'project-recommender', 'equipment-advisor', 'bom-reviewer'])
const active = ref('recommend')
const initial = ref(null)
const panel = ref(null)

// 响应式表单数据
const rec = reactive({
  goal: '',
  weeklyHours: 6,
  bases: ['单片机 (C 语言)'],
  direction: '人工智能 / 物联网方向',
  note: ''
})
const eq = reactive({ need: '', note: '' })
const bom = reactive({ projectId: null, text: '', note: '' })
const ask = reactive({ q: '' })
const custom = reactive({ q: '' })

// 统计数据
const stats = reactive({
  completedTasks: 0,
  availableProjects: 5,
  skillsCount: 3
})

// 快捷选项与换一批预设
const recPresets = [
  { goal: '基于 STM32 的智能温湿度采集系统, 或一个小型的物联网项目', direction: '人工智能 / 物联网方向', bases: ['单片机 (C 语言)'] },
  { goal: '入门嵌入式, 做一个能联网的温湿度传感器节点', direction: '嵌入式系统 / 单片机方向', bases: ['单片机 (C 语言)', '传感器应用'] },
  { goal: '补强 PCB 设计, 独立完成一块双层电源开发板', direction: '硬件电路 / PCB 研发方向', bases: ['PCB 设计'] },
  { goal: '往智能硬件与机器人方向走, 学习电机控制与通信协议', direction: '机器人 / 自动化控制方向', bases: ['单片机 (C 语言)', '嵌入式开发'] }
]
const presetIdx = ref(0)
const onRecRefresh = () => {
  presetIdx.value = (presetIdx.value + 1) % recPresets.length
  const p = recPresets[presetIdx.value]
  rec.goal = p.goal
  rec.direction = p.direction
  rec.bases = [...p.bases]
  ElMessage.info('已为你切换一组推荐需求示例')
}

const submitRecommend = (run) => {
  const baseSummary = [...rec.bases, rec.note].filter(Boolean).join('；')
  run(
    {
      goal: rec.goal,
      weeklyHours: rec.weeklyHours,
      direction: rec.direction,
      note: baseSummary || '无'
    },
    '路线 · ' + rec.goal
  )
}

const eqQuick = ['测量 3.3V 电源纹波并调试 SPI 通信', '焊接一块 0603 贴片元件的 PCB', '分析 UART 通信波形排查乱码']
const onEqRefresh = () => {
  const idx = Math.floor(Math.random() * eqQuick.length)
  eq.need = eqQuick[idx]
  ElMessage.info('已为你切换设备实验场景')
}

const askQuick = ['I2C 上拉电阻怎么选?', '为什么 ADC 读数抖动很大?', 'STM32 的 HAL 和 LL 库有什么区别?', 'PCB 上电源走线多宽合适?']
const onAskRefresh = () => {
  const idx = Math.floor(Math.random() * askQuick.length)
  ask.q = askQuick[idx]
  ElMessage.info('已为你切换热门技术问题')
}

// ---------- 技能管理 ----------
const skills = ref([])
const customSkills = computed(() =>
  skills.value.filter((s) => !s.key.startsWith('task-') && !LEGACY.has(s.key) && s.key !== 'project-tutor' && !s.hidden)
)
const customKey = ref('')
const customSkill = computed(() => customSkills.value.find((s) => s.key === customKey.value) || customSkills.value[0])
watch(customSkills, (list) => {
  if (!customKey.value && list.length) customKey.value = list[0].key
})

const allSkills = computed(() => [
  ...TASKS.map((t) => ({
    key: t.skill,
    icon: '🧭',
    name: t.name,
    description: t.desc + '。',
    category: 'AI 专项任务',
    builtin: true,
    scopeText: '内置',
    scopeClass: '',
    action: '开始任务',
    task: t.key
  })),
  {
    key: 'project-tutor',
    icon: '🎓',
    name: '项目导师',
    description: '在每个项目页右侧: 按教学大纲分阶段带你做, 生成行动清单, 确认后帮你更新进度。',
    category: '学习',
    builtin: true,
    scopeText: '内置',
    scopeClass: '',
    action: '去项目中心',
    task: 'tutor'
  },
  ...customSkills.value.map((s) => ({
    ...s,
    icon: s.icon || '✨',
    description: s.description || '定制辅导技能',
    builtin: false,
    scopeText: s.scope === 'TENANT' ? '本站共享' : '我的',
    scopeClass: s.scope === 'TENANT' ? 'grn' : 'pur',
    action: '使用',
    task: 'custom'
  }))
])

const useSkill = (s) => {
  if (s.task === 'tutor') return router.push('/app/projects')
  if (s.task === 'custom') {
    customKey.value = s.key
    select('custom')
    return
  }
  select(s.task)
}

const editorVisible = ref(false)
const editingSkill = ref(null)
const editSkill = (s) => {
  editingSkill.value = s
  editorVisible.value = true
}

const reloadSkills = async () => {
  skills.value = await aiSkills().catch(() => [])
}
const onSkillSaved = async (saved) => {
  await reloadSkills()
  customKey.value = saved.key
  active.value = 'custom'
}
const onSkillDeleted = async () => {
  await reloadSkills()
  customKey.value = customSkills.value[0]?.key || ''
  active.value = 'skills'
}

// ---------- 历史记录 ----------
const myProjects = ref([])
const history = ref([])
const nameOf = (k) => TASKS.find((t) => t.skill === k)?.name || skills.value.find((s) => s.key === k)?.name || k
const iconOf = (k) => TASKS.find((t) => t.skill === k)?.name?.slice(0, 1) || '✨'
const fmt = (t) => (t ? String(t).replace('T', ' ').slice(5, 16) : '')
const select = (key) => {
  active.value = key
  initial.value = null
}
const loadHistory = async () => {
  const list = await aiConversations()
  const keys = new Set([...TASKS.map((t) => t.skill), ...customSkills.value.map((s) => s.key)])
  history.value = list.filter((c) => keys.has(c.skillKey)).slice(0, 12)
}
const onDone = () => loadHistory()
const openHistory = async (c) => {
  const msgs = await aiConversationMessages(c.id)
  const lastAi = [...msgs].reverse().find((m) => m.role === 'assistant' && m.content && !m.toolCalls)
  const lastUser = msgs.find((m) => m.role === 'user')
  let result = null
  try {
    result = parseJson(lastAi?.content || '')
  } catch (e) {
    result = null
  }
  const task = TASKS.find((t) => t.skill === c.skillKey)
  if (task) active.value = task.key
  else {
    active.value = 'custom'
    customKey.value = c.skillKey
  }
  await nextTick()
  initial.value = { conversationId: c.id, result, raw: lastAi?.content || '', input: lastUser?.content || '' }
}
const removeHistory = async (c) => {
  await aiDeleteConversation(c.id)
  history.value = history.value.filter((x) => x.id !== c.id)
  ElMessage.success('已删除')
}

onMounted(async () => {
  if (route.query.skill === 'project-tutor' && route.query.projectId) {
    router.replace({ path: `/app/projects/${route.query.projectId}`, query: { tutor: 1 } })
    return
  }
  const t = route.query.task
  if (t && (TASKS.some((x) => x.key === t) || t === 'custom' || t === 'skills')) active.value = t
  await reloadSkills()
  if (route.query.skill && customSkills.value.some((s) => s.key === route.query.skill)) {
    customKey.value = route.query.skill
    active.value = 'custom'
  }
  await loadHistory()
  fetchDashboard()
    .then((d) => {
      myProjects.value = d.ongoingProjects || []
      if (d.todaySubmissionsCount != null) stats.completedTasks = d.todaySubmissionsCount
      if (d.projectsCount != null) stats.availableProjects = d.projectsCount
      if (d.skillsCount != null) stats.skillsCount = d.skillsCount
    })
    .catch(() => {})
})
</script>

<style scoped>
.wb {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.grow { flex: 1; min-width: 0; }
.muted { font-size: 12.5px; color: var(--text-secondary); }

/* 顶部横幅:极简高雅清爽浅色微渐变 + 硬件工作台实拍融入 */
.hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 24px;
  align-items: center;
  padding: 24px 30px;
  border-radius: 16px;
  background: linear-gradient(135deg, #f8fafc 0%, #f0f7ff 50%, #eff6ff 100%);
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.03);
  overflow: hidden;
}

.hero-bg-overlay {
  position: absolute;
  right: 210px;
  top: 0;
  bottom: 0;
  width: 500px;
  background-image: url('../../assets/ai-hero-bg.png');
  background-repeat: no-repeat;
  background-position: right center;
  background-size: cover;
  mask-image: linear-gradient(to right, transparent 0%, rgba(0, 0, 0, 0.4) 20%, rgba(0, 0, 0, 0.95) 70%, transparent 100%);
  -webkit-mask-image: linear-gradient(to right, transparent 0%, rgba(0, 0, 0, 0.4) 20%, rgba(0, 0, 0, 0.95) 70%, transparent 100%);
  pointer-events: none;
  opacity: 0.88;
}

.hero-left {
  position: relative;
  z-index: 2;
}

.hero-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.15);
  padding: 3px 10px;
  border-radius: 999px;
  margin-bottom: 10px;
}

.hero-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.01em;
}

.hero-desc {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.65;
  color: #475569;
  max-width: 680px;
}

.hero-steps {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  font-size: 13px;
  color: #334155;
  flex-wrap: wrap;
}

.step-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.step-num {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #2563eb;
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  display: grid;
  place-items: center;
}

.step-sep {
  color: #94a3b8;
}

/* 顶部右侧数据微卡片 */
.hero-stats {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(226, 232, 240, 0.85);
  padding: 10px 14px;
  border-radius: 12px;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.02);
  transition: all 0.18s ease;
}
.stat-card:hover {
  background: #ffffff;
  transform: translateX(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

.stat-ico {
  flex-shrink: 0;
}
.stat-ico-blue { color: #2563eb; }
.stat-ico-indigo { color: #4f46e5; }
.stat-ico-purple { color: #9333ea; }

.stat-label {
  font-size: 13px;
  color: #334155;
}
.stat-label b {
  color: #0f172a;
  font-weight: 700;
}

/* 5 个快捷任务卡片(Tiles) */
.tiles {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
}
@media (max-width: 1200px) {
  .tiles { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 768px) {
  .tiles { grid-template-columns: 1fr; }
}

.tile {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 16px 16px 14px;
  border-radius: 14px;
  background: var(--tile-bg, #f8fafc);
  border: 1px solid var(--tile-border, #e2e8f0);
  cursor: pointer;
  transition: all 0.18s ease;
  min-height: 84px;
}

.tile:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.06);
  border-color: var(--tile-accent);
}

.tile.on {
  border-color: var(--tile-accent);
  border-width: 1.5px;
  box-shadow: 0 0 0 1px var(--tile-accent), 0 6px 16px rgba(37, 99, 235, 0.08);
}

.tile-icon-box {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
}

.tile-text {
  flex: 1;
  min-width: 0;
}

.tile-name {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 2px;
}

.tile-desc {
  font-size: 12px;
  color: #64748b;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.tile-arrow {
  flex-shrink: 0;
  transition: transform 0.15s ease;
}
.tile:hover .tile-arrow {
  transform: translateX(3px);
}

/* 表单细节 */
.f {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 15px;
}
.f label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.stepper-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.stepper-input {
  width: 140px;
}
.stepper-unit {
  font-size: 13px;
  color: #64748b;
}

.bases-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-bottom: 2px;
}
.bases-group :deep(.el-checkbox) {
  margin-right: 0;
}
.bases-group :deep(.el-checkbox__label) {
  font-size: 13px;
  color: #475569;
}

.quick {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0 0 14px;
}
.q {
  font-size: 12px;
  background: #f1f5f9;
  color: #475569;
  padding: 5px 11px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.15s;
}
.q:hover {
  background: #e0e7ff;
  color: #2563eb;
}

/* 统一蓝紫主色按钮 */
.go-btn {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 0;
  border-radius: 12px;
  padding: 12px 18px;
  font-size: 14.5px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.25);
  transition: all 0.18s ease;
}
.go-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(37, 99, 235, 0.32);
}
.go-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}

.go-btn-green {
  background: linear-gradient(135deg, #059669, #10b981);
  box-shadow: 0 4px 14px rgba(5, 150, 105, 0.25);
}
.go-btn-green:hover:not(:disabled) {
  box-shadow: 0 6px 18px rgba(5, 150, 105, 0.32);
}

.go-btn-orange {
  background: linear-gradient(135deg, #ea580c, #f97316);
  box-shadow: 0 4px 14px rgba(234, 88, 12, 0.25);
}
.go-btn-orange:hover:not(:disabled) {
  box-shadow: 0 6px 18px rgba(234, 88, 12, 0.32);
}

.go-btn-purple {
  background: linear-gradient(135deg, #7c3aed, #9333ea);
  box-shadow: 0 4px 14px rgba(124, 58, 237, 0.25);
}
.go-btn-purple:hover:not(:disabled) {
  box-shadow: 0 6px 18px rgba(124, 58, 237, 0.32);
}

.hint {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
}
.skill-admin {
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px dashed var(--border);
}

/* 技能库 */
.skills-lib {
  background: #fff;
  border-radius: 16px;
  padding: 22px 24px;
  border: 1px solid var(--border);
}
.lib-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}
.lib-head h3 {
  margin: 0 0 4px;
  font-size: 17px;
  color: #0f172a;
}
.lib-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}
.lib-card {
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #fff;
  transition: all 0.15s;
}
.lib-card:hover {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.05);
  transform: translateY(-2px);
}
.lib-card.builtin {
  background: linear-gradient(180deg, #fff, #f8fafc);
}
.lib-top {
  display: flex;
  align-items: center;
  gap: 12px;
}
.lib-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: #eff6ff;
  display: grid;
  place-items: center;
  font-size: 20px;
  flex-shrink: 0;
}
.lib-name {
  font-weight: 700;
  font-size: 15px;
  color: #1e293b;
}
.lib-meta {
  display: flex;
  gap: 6px;
  margin-top: 4px;
}
.tag {
  font-size: 11px;
  background: #f1f5f9;
  color: #475569;
  padding: 2px 8px;
  border-radius: 999px;
}
.tag.grn {
  background: #ecfdf5;
  color: #047857;
}
.tag.pur {
  background: #f5f3ff;
  color: #6d28d9;
}
.lib-desc {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
  flex: 1;
}
.lib-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 历史卡片 */
.h-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.h-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 6px;
}
.h-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 10px;
}
.h-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  border: 1px solid var(--border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.15s;
  background: #fff;
}
.h-item:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
}
.h-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #f1f5f9;
  display: grid;
  place-items: center;
  font-size: 15px;
}
.h-title {
  font-size: 13.5px;
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.h-meta {
  font-size: 11.5px;
  color: #94a3b8;
  margin-top: 2px;
}
</style>
