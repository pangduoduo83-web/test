<template>
  <div class="wb">
    <!-- 顶部横幅 -->
    <header class="hero">
      <div class="hero-text">
        <div class="hero-kicker"><Sparkles :size="14" /> AI 助教 · 任务式</div>
        <h2>说清你要什么,直接拿结果</h2>
        <p>不是聊天机器人。选一张任务卡、填两个条件,AI 会去读你的技能画像、检索项目库和设备库,产出能直接行动的结果:学习路线、设备清单、BOM 问题表、带要点的解答。</p>
        <div class="hero-steps"><span><b>1</b> 选任务</span><i></i><span><b>2</b> 填条件</span><i></i><span><b>3</b> 拿结果,一键报名 / 借用 / 追问</span></div>
      </div>
      <div class="hero-side">
        <div class="hero-chip" @click="$router.push('/app/dashboard')">☀️ 今日建议在个人中心</div>
        <div class="hero-chip" @click="$router.push('/app/projects')">🧭 项目导师在每个项目页右侧</div>
        <div class="hero-chip" @click="select('skills')">✨ 技能库 · {{ allSkills.length }} 个</div>
      </div>
    </header>

    <!-- 任务卡 -->
    <section class="tiles">
      <button v-for="t in TASKS" :key="t.key" class="tile" :class="[t.key, { on: active === t.key }]" @click="select(t.key)">
        <span class="tile-icon">{{ t.icon }}</span>
        <span class="tile-name">{{ t.name }}</span>
        <span class="tile-desc">{{ t.desc }}</span>
        <span class="tile-go">{{ active === t.key ? '正在使用' : '开始 →' }}</span>
      </button>
      <button class="tile skills" :class="{ on: active === 'skills' || active === 'custom' }" @click="select('skills')">
        <span class="tile-icon">✨</span>
        <span class="tile-name">技能库</span>
        <span class="tile-desc">{{ customSkills.length ? `本站老师定制了 ${customSkills.length} 个技能` : '全部 AI 技能一览,老师可自建' }}</span>
        <span class="tile-go">查看 →</span>
      </button>
    </section>

    <!-- 项目推荐 -->
    <TaskPanel v-if="active === 'recommend'" ref="panel" skill-key="task-recommend" icon="🧭" title="项目推荐" accent="#6366f1"
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
        <button class="go" :disabled="running || !rec.goal.trim()" @click="run({ goal: rec.goal, weeklyHours: rec.weeklyHours, note: rec.note || '无' }, '路线 · ' + rec.goal)">{{ running ? '生成中…' : '生成学习路线' }}</button>
      </template>
      <template #result="{ result, raw }">
        <RecommendResult v-if="result?.path" :data="result" />
        <AnswerResult v-else :raw="raw" />
      </template>
    </TaskPanel>

    <!-- 设备顾问 -->
    <TaskPanel v-else-if="active === 'equipment'" ref="panel" skill-key="task-equipment" icon="🔧" title="设备顾问" accent="#0891b2"
               intro="说清你要做的实验,AI 在本实验室设备库里配一套能借到的设备,并给出参数设置与安全提醒。"
               empty-title="你要做什么实验?" empty-sub="AI 会检索本实验室的设备库存,只推荐现在能借到的" :initial="initial" @done="onDone">
      <template #form="{ run, running }">
        <div class="f">
          <label>要做的实验 / 任务</label>
          <el-input v-model="eq.need" type="textarea" :rows="3" maxlength="200" placeholder="如:测量 STM32 板子上 3.3V 电源的纹波,并调试 SPI 通信" />
        </div>
        <div class="f"><label>补充(选填)</label><el-input v-model="eq.note" maxlength="100" placeholder="信号频率、电压范围、预算等" /></div>
        <div class="quick"><span v-for="q in eqQuick" :key="q" class="q" @click="eq.need = q">{{ q }}</span></div>
        <button class="go" :disabled="running || !eq.need.trim()" @click="run({ need: eq.need, note: eq.note || '无' }, '配设备 · ' + eq.need)">{{ running ? '配置中…' : '配设备' }}</button>
      </template>
      <template #result="{ result, raw }">
        <EquipmentResult v-if="result?.items" :data="result" />
        <AnswerResult v-else :raw="raw" />
      </template>
    </TaskPanel>

    <!-- BOM 审查 -->
    <TaskPanel v-else-if="active === 'bom'" ref="panel" skill-key="task-bom" icon="📋" title="BOM 审查" accent="#ea580c"
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
        <button class="go" :disabled="running || (!bom.projectId && !bom.text.trim())"
                @click="run({ projectId: bom.projectId || '无', bom: bom.text || '(见项目)', note: bom.note || '无' }, bom.projectId ? 'BOM 审查 · ' + (myProjects.find((p) => p.projectId === bom.projectId)?.projectTitle || '项目') : 'BOM 审查 · ' + bom.text.split('\n').filter(Boolean).length + ' 行物料')">{{ running ? '审查中…' : '开始审查' }}</button>
      </template>
      <template #result="{ result, raw }">
        <BomResult v-if="result?.issues !== undefined" :data="result" />
        <AnswerResult v-else :raw="raw" />
      </template>
    </TaskPanel>

    <!-- 知识问答 -->
    <TaskPanel v-else-if="active === 'ask'" ref="panel" skill-key="task-ask" icon="💡" title="知识问答" accent="#ca8a04"
               intro="电子、嵌入式、项目学习上的问题都可以问。回答分结论、要点、步骤,并附上本站相关的项目和设备。"
               empty-title="有什么想弄明白的?" empty-sub="答案会以结构化卡片呈现,并给出你可能想继续问的方向" :initial="initial" @done="onDone">
      <template #form="{ run, running }">
        <div class="f">
          <label>问题</label>
          <el-input v-model="ask.q" type="textarea" :rows="4" maxlength="500" placeholder="如:I2C 总线上拉电阻怎么选?为什么我的传感器读数不稳定?" @keydown.ctrl.enter="ask.q.trim() && run(ask.q)" />
        </div>
        <div class="quick"><span v-for="q in askQuick" :key="q" class="q" @click="ask.q = q">{{ q }}</span></div>
        <button class="go" :disabled="running || !ask.q.trim()" @click="run(ask.q)">{{ running ? '思考中…' : '解答' }}</button>
        <small class="hint">Ctrl + Enter 也可以</small>
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
import { History, Plus, Sparkles } from 'lucide-vue-next'
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

const TASKS = [
  { key: 'recommend', skill: 'task-recommend', icon: '🧭', name: '项目推荐', desc: '按我的技能画像排一条三阶段学习路线' },
  { key: 'equipment', skill: 'task-equipment', icon: '🔧', name: '设备顾问', desc: '说清实验,配一套能借到的设备' },
  { key: 'bom', skill: 'task-bom', icon: '📋', name: 'BOM 审查', desc: '找问题、补缺项、估成本、给替代料' },
  { key: 'ask', skill: 'task-ask', icon: '💡', name: '知识问答', desc: '结论 + 要点 + 步骤 + 站内相关资源' }
]
const LEGACY = new Set(['study-assistant', 'project-recommender', 'equipment-advisor', 'bom-reviewer'])
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

// ---------- 技能 ----------
const skills = ref([])
const customSkills = computed(() => skills.value.filter((s) => !s.key.startsWith('task-') && !LEGACY.has(s.key) && s.key !== 'project-tutor' && !s.hidden))
const customKey = ref('')
const customSkill = computed(() => customSkills.value.find((s) => s.key === customKey.value) || customSkills.value[0])
watch(customSkills, (list) => { if (!customKey.value && list.length) customKey.value = list[0].key })
/** 技能库列表:四个任务 + 项目导师 + 本站/个人技能 */
const allSkills = computed(() => [
  ...TASKS.map((t) => ({ key: t.skill, icon: t.icon, name: t.name, description: t.desc + '。', category: 'AI 助教任务', builtin: true, scopeText: '内置', scopeClass: '', action: '开始任务', task: t.key })),
  { key: 'project-tutor', icon: '🎓', name: '项目导师', description: '在每个项目页右侧:按教学大纲分阶段带你做,生成行动清单,确认后帮你更新进度。', category: '学习', builtin: true, scopeText: '内置', scopeClass: '', action: '去项目中心', task: 'tutor' },
  ...customSkills.value.map((s) => ({ ...s, icon: s.icon || '✨', description: s.description || '老师定制的技能', builtin: false, scopeText: s.scope === 'TENANT' ? '本站共享' : '我的', scopeClass: s.scope === 'TENANT' ? 'grn' : 'pur', action: '使用', task: 'custom' }))
])
const useSkill = (s) => {
  if (s.task === 'tutor') return router.push('/app/projects')
  if (s.task === 'custom') { customKey.value = s.key; select('custom'); return }
  select(s.task)
}
const editorVisible = ref(false)
const editingSkill = ref(null)
const editSkill = (s) => { editingSkill.value = s; editorVisible.value = true }
const reloadSkills = async () => { skills.value = await aiSkills().catch(() => []) }
const onSkillSaved = async (saved) => { await reloadSkills(); customKey.value = saved.key; active.value = 'custom' }
const onSkillDeleted = async () => { await reloadSkills(); customKey.value = customSkills.value[0]?.key || ''; active.value = 'skills' }

// ---------- 历史 ----------
const myProjects = ref([])
const history = ref([])
const nameOf = (k) => TASKS.find((t) => t.skill === k)?.name || skills.value.find((s) => s.key === k)?.name || k
const iconOf = (k) => TASKS.find((t) => t.skill === k)?.icon || skills.value.find((s) => s.key === k)?.icon || '✨'
const fmt = (t) => (t ? String(t).replace('T', ' ').slice(5, 16) : '')
const select = (key) => { active.value = key; initial.value = null }
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
  try { result = parseJson(lastAi?.content || '') } catch (e) { result = null }
  const task = TASKS.find((t) => t.skill === c.skillKey)
  if (task) active.value = task.key
  else { active.value = 'custom'; customKey.value = c.skillKey }
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
  if (route.query.skill && customSkills.value.some((s) => s.key === route.query.skill)) { customKey.value = route.query.skill; active.value = 'custom' }
  await loadHistory()
  fetchDashboard().then((d) => { myProjects.value = d.ongoingProjects || [] }).catch(() => {})
})
</script>

<style scoped>
.wb { display: flex; flex-direction: column; gap: 18px; }
.grow { flex: 1; min-width: 0; }
.muted { font-size: 12.5px; color: var(--text-secondary); }

/* 横幅 */
.hero { display: grid; grid-template-columns: minmax(0, 1fr) 260px; gap: 24px; align-items: center; padding: 28px 32px; border-radius: 20px; color: #fff; background: linear-gradient(120deg, #4f46e5 0%, #7c3aed 55%, #2563eb 100%); position: relative; overflow: hidden; box-shadow: 0 12px 32px rgba(79, 70, 229, .25); }
.hero::after { content: ''; position: absolute; right: -80px; top: -80px; width: 320px; height: 320px; border-radius: 50%; background: rgba(255, 255, 255, .08); }
.hero::before { content: ''; position: absolute; right: 120px; bottom: -120px; width: 260px; height: 260px; border-radius: 50%; background: rgba(255, 255, 255, .06); }
.hero-kicker { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; letter-spacing: 1px; background: rgba(255, 255, 255, .18); padding: 4px 10px; border-radius: 999px; }
.hero h2 { margin: 12px 0 8px; font-size: 26px; font-weight: 800; letter-spacing: .5px; }
.hero p { margin: 0; font-size: 14px; line-height: 1.75; color: rgba(255, 255, 255, .88); max-width: 720px; }
.hero-steps { display: flex; align-items: center; gap: 10px; margin-top: 16px; font-size: 13px; color: rgba(255, 255, 255, .92); flex-wrap: wrap; }
.hero-steps span { display: inline-flex; align-items: center; gap: 6px; }
.hero-steps b { width: 20px; height: 20px; border-radius: 50%; background: #fff; color: #4f46e5; font-size: 11px; display: grid; place-items: center; }
.hero-steps i { width: 24px; height: 1px; background: rgba(255, 255, 255, .5); }
.hero-side { display: flex; flex-direction: column; gap: 8px; position: relative; z-index: 1; }
.hero-chip { font-size: 13px; background: rgba(255, 255, 255, .16); border: 1px solid rgba(255, 255, 255, .25); padding: 9px 12px; border-radius: 12px; cursor: pointer; transition: background .15s; }
.hero-chip:hover { background: rgba(255, 255, 255, .28); }

/* 任务卡 */
.tiles { display: grid; grid-template-columns: repeat(5, 1fr); gap: 14px; }
@media (max-width: 1200px) { .tiles { grid-template-columns: repeat(3, 1fr); } }
.tile { position: relative; display: flex; flex-direction: column; align-items: flex-start; gap: 6px; text-align: left; padding: 18px 18px 16px; border-radius: 18px; border: 1px solid transparent; cursor: pointer; transition: all .18s; color: #fff; min-height: 150px; overflow: hidden; }
.tile::after { content: ''; position: absolute; right: -30px; bottom: -40px; width: 120px; height: 120px; border-radius: 50%; background: rgba(255, 255, 255, .12); }
.tile:hover { transform: translateY(-3px); box-shadow: 0 14px 30px rgba(15, 23, 42, .16); }
.tile.on { box-shadow: 0 0 0 3px #fff, 0 0 0 6px currentColor, 0 14px 30px rgba(15, 23, 42, .16); }
.tile.recommend { background: linear-gradient(135deg, #6366f1, #4338ca); color: #6366f1; }
.tile.equipment { background: linear-gradient(135deg, #06b6d4, #0e7490); color: #06b6d4; }
.tile.bom { background: linear-gradient(135deg, #fb923c, #c2410c); color: #fb923c; }
.tile.ask { background: linear-gradient(135deg, #facc15, #ca8a04); color: #eab308; }
.tile.skills { background: linear-gradient(135deg, #a78bfa, #6d28d9); color: #a78bfa; }
.tile-icon { font-size: 30px; line-height: 1; filter: drop-shadow(0 4px 8px rgba(0, 0, 0, .2)); }
.tile-name { font-size: 17px; font-weight: 800; color: #fff; margin-top: 6px; }
.tile-desc { font-size: 12.5px; color: rgba(255, 255, 255, .9); line-height: 1.5; }
.tile-go { margin-top: auto; font-size: 12px; font-weight: 700; color: #fff; background: rgba(255, 255, 255, .22); padding: 4px 10px; border-radius: 999px; }

/* 表单 */
.f { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; position: relative; }
.f label { font-size: 13px; font-weight: 700; color: #374151; }
.f small { position: absolute; right: 40px; bottom: 8px; font-size: 12px; color: #9ca3af; }
.f-row { display: grid; grid-template-columns: 120px 1fr; gap: 10px; }
.quick { display: flex; flex-wrap: wrap; gap: 6px; margin: 0 0 14px; }
.q { font-size: 12px; background: #f3f4f6; color: #374151; padding: 5px 10px; border-radius: 999px; cursor: pointer; transition: all .15s; }
.q:hover { background: #e0e7ff; color: #3730a3; }
.go { width: 100%; border: 0; border-radius: 12px; padding: 13px 16px; font-size: 15px; font-weight: 800; color: #fff; cursor: pointer; background: linear-gradient(90deg, #4f46e5, #7c3aed); box-shadow: 0 8px 20px rgba(79, 70, 229, .3); transition: transform .15s, box-shadow .15s; }
.go:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 12px 26px rgba(79, 70, 229, .35); }
.go:disabled { opacity: .5; cursor: not-allowed; box-shadow: none; }
.hint { display: block; margin-top: 8px; font-size: 12px; color: #9ca3af; text-align: center; }
.skill-admin { display: flex; justify-content: space-between; margin-top: 12px; padding-top: 10px; border-top: 1px dashed var(--border); }

/* 技能库 */
.skills-lib { background: #fff; border-radius: 18px; padding: 22px 24px; border: 1px solid var(--border); }
.lib-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 16px; }
.lib-head h3 { margin: 0 0 4px; font-size: 17px; }
.lib-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 14px; }
.lib-card { border: 1px solid var(--border); border-radius: 16px; padding: 16px; display: flex; flex-direction: column; gap: 10px; background: #fff; transition: all .15s; }
.lib-card:hover { box-shadow: var(--shadow-card); transform: translateY(-2px); }
.lib-card.builtin { background: linear-gradient(180deg, #fff, #fafaff); }
.lib-top { display: flex; align-items: center; gap: 12px; }
.lib-icon { width: 44px; height: 44px; border-radius: 12px; background: #f3f4f6; display: grid; place-items: center; font-size: 22px; flex-shrink: 0; }
.lib-name { font-weight: 800; font-size: 15px; }
.lib-meta { display: flex; gap: 6px; margin-top: 4px; }
.tag { font-size: 11px; background: #f3f4f6; color: #4b5563; padding: 2px 8px; border-radius: 999px; }
.tag.grn { background: #ecfdf5; color: #047857; } .tag.pur { background: #f5f3ff; color: #6d28d9; }
.lib-desc { margin: 0; font-size: 13px; color: var(--text-secondary); line-height: 1.6; flex: 1; }
.lib-actions { display: flex; justify-content: space-between; align-items: center; }

/* 历史 */
.h-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.h-head h3 { margin: 0; font-size: 15px; display: flex; align-items: center; gap: 6px; }
.h-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 8px; }
.h-item { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--border); border-radius: 12px; cursor: pointer; transition: background .15s; }
.h-item:hover { background: #f9fafb; }
.h-icon { width: 34px; height: 34px; border-radius: 10px; background: #f3f4f6; display: grid; place-items: center; font-size: 17px; }
.h-title { font-size: 13.5px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.h-meta { font-size: 11.5px; color: var(--text-secondary); margin-top: 2px; }
</style>
