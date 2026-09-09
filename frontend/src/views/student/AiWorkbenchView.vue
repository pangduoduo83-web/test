<template>
  <div class="wb">
    <!-- 顶部:不是聊天,是任务 -->
    <header class="hero">
      <div>
        <h2 class="page-title">AI 助教</h2>
        <p class="page-subtitle">选一个任务、填两个条件,直接拿到可以行动的结果 —— 项目路线、设备清单、BOM 问题表、带要点的解答。</p>
      </div>
      <div class="hero-tip">
        <span class="hero-dot"></span>项目页里的「AI 导师」会按教学大纲带你一步步做;个人中心每天有「今日建议」
      </div>
    </header>

    <section class="tiles">
      <button v-for="t in TASKS" :key="t.key" class="tile" :class="{ on: active === t.key }" @click="select(t.key)">
        <span class="tile-icon" :style="{ background: t.bg }">{{ t.icon }}</span>
        <span class="tile-text"><b>{{ t.name }}</b><small>{{ t.desc }}</small></span>
      </button>
      <button v-if="customSkills.length || isStaff" class="tile" :class="{ on: active === 'custom' }" @click="select('custom')">
        <span class="tile-icon" style="background:linear-gradient(135deg,#f5f3ff,#ede9fe)">✨</span>
        <span class="tile-text"><b>本站技能</b><small>{{ customSkills.length ? `老师定制的 ${customSkills.length} 个 AI 技能` : '老师可在这里创建本站专属技能' }}</small></span>
      </button>
    </section>

    <!-- 项目推荐 -->
    <TaskPanel v-if="active === 'recommend'" ref="panel" skill-key="task-recommend" icon="🧭" title="项目推荐"
               intro="结合你的技能画像和目标,从本站项目库里排一条「基础 → 综合 → 挑战」的三阶段路线,每一步都能直接报名。"
               empty-title="告诉我你想学什么" empty-sub="AI 会读取你的技能画像、检索项目库,给出三阶段路线与第一步该做什么" :initial="initial" @done="onDone">
      <template #form="{ run, running }">
        <div class="f">
          <label>我想学 / 想做的</label>
          <el-input v-model="rec.goal" type="textarea" :rows="3" maxlength="200" placeholder="如:掌握 STM32 和传感器采集,能独立做一个小型物联网节点" />
        </div>
        <div class="f-row">
          <div class="f"><label>每周可投入</label><el-input-number v-model="rec.weeklyHours" :min="1" :max="60" style="width:100%" /><small>小时</small></div>
          <div class="f"><label>已有基础(选填)</label><el-input v-model="rec.note" maxlength="100" placeholder="学过 C 语言,没画过 PCB" /></div>
        </div>
        <div class="quick"><span v-for="q in recQuick" :key="q" class="q" @click="rec.goal = q">{{ q }}</span></div>
        <el-button type="primary" size="large" class="go" :loading="running" :disabled="!rec.goal.trim()" @click="run({ goal: rec.goal, weeklyHours: rec.weeklyHours, note: rec.note || '无' }, '路线 · ' + rec.goal)">生成学习路线</el-button>
      </template>
      <template #result="{ result, raw }">
        <RecommendResult v-if="result?.path" :data="result" />
        <AnswerResult v-else :raw="raw" />
      </template>
    </TaskPanel>

    <!-- 设备顾问 -->
    <TaskPanel v-else-if="active === 'equipment'" ref="panel" skill-key="task-equipment" icon="🔧" title="设备顾问"
               intro="说清你要做的实验,AI 在本实验室设备库里配一套能借到的设备,并给出参数设置与安全提醒。"
               empty-title="你要做什么实验?" empty-sub="AI 会检索本实验室的设备库存,只推荐现在能借到的" :initial="initial" @done="onDone">
      <template #form="{ run, running }">
        <div class="f">
          <label>要做的实验 / 任务</label>
          <el-input v-model="eq.need" type="textarea" :rows="3" maxlength="200" placeholder="如:测量 STM32 板子上 3.3V 电源的纹波,并调试 SPI 通信" />
        </div>
        <div class="f"><label>补充(选填)</label><el-input v-model="eq.note" maxlength="100" placeholder="信号频率、电压范围、预算等" /></div>
        <div class="quick"><span v-for="q in eqQuick" :key="q" class="q" @click="eq.need = q">{{ q }}</span></div>
        <el-button type="primary" size="large" class="go" :loading="running" :disabled="!eq.need.trim()" @click="run({ need: eq.need, note: eq.note || '无' }, '配设备 · ' + eq.need)">配设备</el-button>
      </template>
      <template #result="{ result, raw }">
        <EquipmentResult v-if="result?.items" :data="result" />
        <AnswerResult v-else :raw="raw" />
      </template>
    </TaskPanel>

    <!-- BOM 审查 -->
    <TaskPanel v-else-if="active === 'bom'" ref="panel" skill-key="task-bom" icon="📋" title="BOM 审查"
               intro="贴一份物料清单,或直接选你报名的项目,AI 按硬件工程师的标准找问题、补缺项、估成本。"
               empty-title="把 BOM 交给我" empty-sub="会给出评分、问题清单(按风险分级)、缺失项、替代料和下单前自查表" follow-up-field="none" :initial="initial" @done="onDone">
      <template #form="{ run, running }">
        <div class="f">
          <label>审查我报名项目的 BOM</label>
          <el-select v-model="bom.projectId" clearable placeholder="选一个项目(可选)" style="width:100%">
            <el-option v-for="p in myProjects" :key="p.projectId" :label="p.projectTitle" :value="p.projectId" />
          </el-select>
        </div>
        <div class="f">
          <label>或粘贴 BOM 文本</label>
          <el-input v-model="bom.text" type="textarea" :rows="7" maxlength="6000" placeholder="每行一个元件,如:&#10;C1 10uF 0805 x2&#10;U1 AMS1117-3.3 SOT-223&#10;R1 10k 0603 x4" />
        </div>
        <div class="f"><label>关注点(选填)</label><el-input v-model="bom.note" maxlength="100" placeholder="如:控制成本 / 全部用贴片 / 手焊友好" /></div>
        <el-button type="primary" size="large" class="go" :loading="running" :disabled="!bom.projectId && !bom.text.trim()"
                   @click="run({ projectId: bom.projectId || '无', bom: bom.text || '(见项目)', note: bom.note || '无' }, bom.projectId ? 'BOM 审查 · ' + (myProjects.find((p) => p.projectId === bom.projectId)?.projectTitle || '项目') : 'BOM 审查 · ' + bom.text.split('\n').filter(Boolean).length + ' 行物料')">开始审查</el-button>
      </template>
      <template #result="{ result, raw }">
        <BomResult v-if="result?.issues !== undefined" :data="result" />
        <AnswerResult v-else :raw="raw" />
      </template>
    </TaskPanel>

    <!-- 知识问答 -->
    <TaskPanel v-else-if="active === 'ask'" ref="panel" skill-key="task-ask" icon="💡" title="知识问答"
               intro="电子、嵌入式、项目学习上的问题都可以问。回答分结论、要点、步骤,并附上本站相关的项目和设备。"
               empty-title="有什么想弄明白的?" empty-sub="答案会以结构化卡片呈现,并给出你可能想继续问的方向" :initial="initial" @done="onDone">
      <template #form="{ run, running }">
        <div class="f">
          <label>问题</label>
          <el-input v-model="ask.q" type="textarea" :rows="4" maxlength="500" placeholder="如:I2C 总线上拉电阻怎么选?为什么我的传感器读数不稳定?" @keydown.ctrl.enter="ask.q.trim() && run(ask.q)" />
        </div>
        <div class="quick"><span v-for="q in askQuick" :key="q" class="q" @click="ask.q = q">{{ q }}</span></div>
        <el-button type="primary" size="large" class="go" :loading="running" :disabled="!ask.q.trim()" @click="run(ask.q)">解答</el-button>
        <small class="hint">Ctrl + Enter 也可以</small>
      </template>
      <template #result="{ result, raw }">
        <AnswerResult :data="result || {}" :raw="raw" />
      </template>
    </TaskPanel>

    <!-- 本站自定义技能 -->
    <TaskPanel v-else-if="active === 'custom' && customSkill" ref="panel" :key="customSkill.key" :skill-key="customSkill.key" :icon="customSkill.icon || '✨'" :title="customSkill.name"
               :intro="customSkill.description" :json="false" empty-title="按左侧说明填写" empty-sub="这是本站老师定制的技能,结果以文字形式呈现" :initial="initial" @done="onDone">
      <template #form="{ run, running }">
        <div class="f">
          <label>选择技能</label>
          <el-select v-model="customKey" style="width:100%">
            <el-option v-for="s in customSkills" :key="s.key" :label="`${s.icon || '✨'} ${s.name}`" :value="s.key" />
          </el-select>
        </div>
        <div class="f">
          <label>输入</label>
          <el-input v-model="custom.q" type="textarea" :rows="5" maxlength="2000" :placeholder="customSkill.greeting || '请输入'" />
        </div>
        <el-button type="primary" size="large" class="go" :loading="running" :disabled="!custom.q.trim()" @click="run(custom.q)">运行</el-button>
        <div v-if="isStaff" class="skill-admin">
          <el-button size="small" text @click="editSkill(customSkill)">编辑此技能</el-button>
          <el-button size="small" text type="primary" @click="editSkill(null)">+ 新建技能</el-button>
        </div>
      </template>
      <template #result="{ raw }">
        <AnswerResult :raw="raw" />
      </template>
    </TaskPanel>
    <div v-else-if="active === 'custom'" class="card empty-custom">
      <div class="ec-icon">✨</div>
      <b>本站还没有自定义技能</b>
      <p>老师可以把常用的辅导流程(如"实验报告批改""原理图检查清单")做成技能,学生在这里直接使用。</p>
      <el-button type="primary" @click="editSkill(null)">+ 新建技能</el-button>
    </div>

    <SkillEditorDialog v-model="editorVisible" :skill="editingSkill" @saved="onSkillSaved" @deleted="onSkillDeleted" />

    <!-- 最近任务 -->
    <section class="card history">
      <div class="h-head">
        <h3>最近任务</h3>
        <span class="muted">点击可重新打开结果</span>
      </div>
      <el-empty v-if="!history.length" description="还没有任务记录" :image-size="60" />
      <div v-else class="h-list">
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
const editorVisible = ref(false)
const editingSkill = ref(null)
const editSkill = (s) => { editingSkill.value = s; editorVisible.value = true }
const onSkillSaved = async (saved) => { skills.value = await aiSkills().catch(() => []); customKey.value = saved.key; active.value = 'custom' }
const onSkillDeleted = async () => { skills.value = await aiSkills().catch(() => []); customKey.value = customSkills.value[0]?.key || '' }
const TASKS = [
  { key: 'recommend', skill: 'task-recommend', icon: '🧭', name: '项目推荐', desc: '按我的技能画像排一条三阶段学习路线', bg: 'linear-gradient(135deg,#eef2ff,#e0e7ff)' },
  { key: 'equipment', skill: 'task-equipment', icon: '🔧', name: '设备顾问', desc: '说清实验,配一套能借到的设备', bg: 'linear-gradient(135deg,#ecfeff,#cffafe)' },
  { key: 'bom', skill: 'task-bom', icon: '📋', name: 'BOM 审查', desc: '找问题、补缺项、估成本、给替代料', bg: 'linear-gradient(135deg,#fff7ed,#ffedd5)' },
  { key: 'ask', skill: 'task-ask', icon: '💡', name: '知识问答', desc: '结论 + 要点 + 步骤 + 站内相关资源', bg: 'linear-gradient(135deg,#fefce8,#fef9c3)' }
]
const active = ref('recommend')
const initial = ref(null)
const panel = ref(null)
const rec = reactive({ goal: '', weeklyHours: 6, note: '' })
const eq = reactive({ need: '', note: '' })
const bom = reactive({ projectId: null, text: '', note: '' })
const ask = reactive({ q: '' })
const custom = reactive({ q: '' })
const recQuick = ['入门嵌入式,做一个能联网的传感器节点', '补强 PCB 设计,做一块自己的开发板', '往机器人方向走,学电机控制']
const eqQuick = ['测量 3.3V 电源纹波并调试 SPI 通信', '焊接一块 0603 贴片元件的 PCB', '分析 UART 通信波形排查乱码']
const askQuick = ['I2C 上拉电阻怎么选?', '为什么 ADC 读数抖动很大?', 'STM32 的 HAL 和 LL 库有什么区别?', 'PCB 上电源走线多宽合适?']

const skills = ref([])
const customSkills = computed(() => skills.value.filter((s) => !s.key.startsWith('task-') && !['project-tutor', 'study-assistant', 'project-recommender', 'equipment-advisor', 'bom-reviewer'].includes(s.key) && !s.hidden))
const customKey = ref('')
const customSkill = computed(() => customSkills.value.find((s) => s.key === customKey.value) || customSkills.value[0])
watch(customSkills, (list) => { if (!customKey.value && list.length) customKey.value = list[0].key })

const myProjects = ref([])
const history = ref([])
const TASK_KEYS = TASKS.map((t) => t.skill)
const nameOf = (k) => TASKS.find((t) => t.skill === k)?.name || skills.value.find((s) => s.key === k)?.name || k
const iconOf = (k) => TASKS.find((t) => t.skill === k)?.icon || skills.value.find((s) => s.key === k)?.icon || '✨'
const fmt = (t) => (t ? String(t).replace('T', ' ').slice(5, 16) : '')

const select = (key) => { active.value = key; initial.value = null }
const loadHistory = async () => {
  const list = await aiConversations()
  const keys = new Set([...TASK_KEYS, ...customSkills.value.map((s) => s.key)])
  history.value = list.filter((c) => keys.has(c.skillKey)).slice(0, 12)
}
const onDone = () => loadHistory()
const openHistory = async (c) => {
  const msgs = await aiConversationMessages(c.id)
  const lastAi = [...msgs].reverse().find((m) => m.role === 'assistant' && m.content && !m.toolCalls)
  const lastUser = msgs.find((m) => m.role === 'user')
  let result = null
  try { result = parseJson(lastAi?.content || '') } catch (e) { result = null }
  const task = TASKS.find((t) => t.skill === c.skillKey)
  if (task) {
    active.value = task.key
  } else {
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
  // 老链接 ?skill=project-tutor&projectId=xx → 项目页内嵌的导师面板
  if (route.query.skill === 'project-tutor' && route.query.projectId) {
    router.replace({ path: `/app/projects/${route.query.projectId}`, query: { tutor: 1 } })
    return
  }
  if (route.query.task && (TASKS.some((t) => t.key === route.query.task) || route.query.task === 'custom')) active.value = route.query.task
  skills.value = await aiSkills().catch(() => [])
  if (route.query.skill && customSkills.value.some((s) => s.key === route.query.skill)) { customKey.value = route.query.skill; active.value = 'custom' }
  await loadHistory()
  fetchDashboard().then((d) => { myProjects.value = d.ongoingProjects || [] }).catch(() => {})
})
</script>

<style scoped>
.wb { display: flex; flex-direction: column; gap: 16px; }
.hero { display: flex; justify-content: space-between; align-items: flex-end; gap: 20px; }
.hero-tip { font-size: 12.5px; color: #4338ca; background: #eef2ff; border-radius: 999px; padding: 8px 14px; display: flex; align-items: center; gap: 8px; white-space: nowrap; }
.hero-dot { width: 8px; height: 8px; border-radius: 50%; background: #6366f1; box-shadow: 0 0 0 4px #e0e7ff; }
.tiles { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
@media (max-width: 1200px) { .tiles { grid-template-columns: repeat(3, 1fr); } }
.tile { display: flex; align-items: center; gap: 12px; text-align: left; padding: 14px 16px; border-radius: 14px; border: 1px solid var(--border); background: #fff; cursor: pointer; transition: all .15s; }
.tile:hover { box-shadow: var(--shadow-card); transform: translateY(-1px); }
.tile.on { border-color: #6366f1; background: linear-gradient(180deg, #fff, #f5f3ff); box-shadow: 0 0 0 3px #e0e7ff; }
.tile-icon { width: 46px; height: 46px; border-radius: 12px; display: grid; place-items: center; font-size: 22px; flex-shrink: 0; }
.tile-text { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.tile-text b { font-size: 14.5px; }
.tile-text small { font-size: 12px; color: var(--text-secondary); line-height: 1.5; }
.f { display: flex; flex-direction: column; gap: 6px; margin-bottom: 12px; position: relative; }
.f label { font-size: 13px; font-weight: 600; color: #374151; }
.f small { position: absolute; right: 40px; bottom: 8px; font-size: 12px; color: #9ca3af; }
.f-row { display: grid; grid-template-columns: 120px 1fr; gap: 10px; }
.quick { display: flex; flex-wrap: wrap; gap: 6px; margin: 2px 0 12px; }
.q { font-size: 12px; background: #f3f4f6; color: #374151; padding: 4px 10px; border-radius: 999px; cursor: pointer; }
.q:hover { background: #e0e7ff; color: #3730a3; }
.go { width: 100%; }
.hint { display: block; margin-top: 6px; font-size: 12px; color: #9ca3af; text-align: center; }
.grow { flex: 1; min-width: 0; }
.muted { font-size: 12px; color: var(--text-secondary); }
.h-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.h-head h3 { margin: 0; font-size: 15px; }
.h-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 8px; }
.h-item { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--border); border-radius: 12px; cursor: pointer; }
.h-item:hover { background: #f9fafb; }
.h-icon { width: 34px; height: 34px; border-radius: 10px; background: #f3f4f6; display: grid; place-items: center; font-size: 17px; }
.h-title { font-size: 13.5px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.h-meta { font-size: 11.5px; color: var(--text-secondary); margin-top: 2px; }
.skill-admin { display: flex; justify-content: space-between; margin-top: 10px; padding-top: 10px; border-top: 1px dashed var(--border); }
.empty-custom { text-align: center; padding: 48px 20px; }
.ec-icon { font-size: 42px; margin-bottom: 8px; }
.empty-custom p { color: var(--text-secondary); font-size: 13px; max-width: 460px; margin: 8px auto 16px; line-height: 1.7; }
</style>
