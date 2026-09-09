<template>
  <div class="ai-page">
    <!-- 左侧:SKILL 库 + 历史会话 -->
    <aside class="side card">
      <div class="side-head">
        <span>AI 技能(SKILL)</span>
        <el-button size="small" text type="primary" @click="openEditor()">+ 自定义</el-button>
      </div>
      <el-input v-model="skillFilter" size="small" placeholder="搜索技能" clearable class="side-search" />
      <div class="skill-list">
        <div v-for="s in filteredSkills" :key="s.key" class="skill-item" :class="{ active: current?.key === s.key }"
             @click="selectSkill(s)">
          <span class="skill-icon">{{ s.icon || '✨' }}</span>
          <div class="skill-text">
            <div class="skill-name">{{ s.name }}
              <el-tag size="small" effect="plain" class="scope-tag">{{ scopeText(s.scope) }}</el-tag>
            </div>
            <div class="skill-desc">{{ s.description }}</div>
          </div>
        </div>
      </div>
      <div class="side-head history-head">
        <span>最近会话</span>
        <el-button size="small" text @click="newConversation">新会话</el-button>
      </div>
      <div class="history">
        <div v-for="c in conversations" :key="c.id" class="history-item" :class="{ active: conversationId === c.id }"
             @click="openConversation(c)">
          <span class="history-title">{{ c.title || '未命名会话' }}</span>
          <el-button size="small" text class="history-del" @click.stop="removeConversation(c)">×</el-button>
        </div>
        <div v-if="conversations.length === 0" class="empty-hint">还没有会话</div>
      </div>
    </aside>

    <!-- 中间:对话 -->
    <section class="chat card">
      <header class="chat-head" v-if="current">
        <span class="skill-icon big">{{ current.icon || '✨' }}</span>
        <div class="chat-head-text">
          <div class="chat-title">{{ current.name }}</div>
          <div class="chat-sub">{{ current.description }}</div>
        </div>
        <div class="chat-tools">
          <el-tag v-for="t in current.tools" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
        </div>
        <el-dropdown @command="onSkillCommand">
          <el-button size="small" text>操作 ▾</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="duplicate">另存为我的 SKILL</el-dropdown-item>
              <el-dropdown-item v-if="current.editable" command="edit">编辑</el-dropdown-item>
              <el-dropdown-item v-if="current.editable" command="delete" divided>删除</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <!-- 项目上下文条:从项目页「AI 导师」进入时带上,新会话会把项目、进度、成果情况交给模型 -->
      <div v-if="contextProject" class="context-bar" :class="{ muted: !!conversationId }">
        <span class="ctx-icon">🧭</span>
        <div class="ctx-text">
          <b>{{ conversationId ? '本会话关联项目' : '正在辅导' }}:《{{ contextProject.title }}》</b>
          <span class="ctx-sub">
            <template v-if="contextProject.enrollment">进度 {{ contextProject.enrollment.progress }}% · 当前任务:{{ contextProject.enrollment.currentTask || '未设置' }}</template>
            <template v-else>尚未报名 · AI 会先帮你判断是否适合</template>
          </span>
        </div>
        <el-button size="small" text @click="$router.push(`/app/projects/${contextProject.id}`)">打开项目页</el-button>
        <el-button size="small" text @click="clearContext">解除关联</el-button>
      </div>

      <div ref="scroller" class="messages">
        <div v-if="messages.length === 0 && current" class="greeting">
          <div class="greeting-text">{{ current.greeting || '有什么可以帮你?' }}</div>
          <div v-if="current.examples?.length" class="examples">
            <el-button v-for="ex in current.examples" :key="ex" size="small" round @click="send(ex)">{{ ex }}</el-button>
          </div>
        </div>
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div v-if="m.role === 'user'" class="bubble user">{{ m.content }}</div>
          <div v-else class="bubble assistant">
            <div v-for="(t, j) in m.tools" :key="j" class="tool-card" :class="{ fail: t.ok === false }">
              <span class="tool-name">🔧 {{ t.name }}</span>
              <span class="tool-args">{{ shortJson(t.args) }}</span>
              <span v-if="t.ok !== undefined" class="tool-result">→ {{ t.ok ? '成功' : '失败' }}{{ t.summary ? ':' + t.summary.slice(0, 120) : '' }}</span>
              <span v-else class="tool-result running">执行中…</span>
            </div>
            <div v-if="m.confirm" class="confirm-card">
              <div class="confirm-title">需要你确认:{{ m.confirm.description }}</div>
              <pre class="confirm-args">{{ JSON.stringify(m.confirm.args, null, 2) }}</pre>
              <div v-if="!m.confirm.resolved" class="confirm-actions">
                <el-button type="primary" size="small" :loading="busy" @click="confirmTool(m)">确认执行</el-button>
                <el-button size="small" :disabled="busy" @click="m.confirm.resolved = 'cancelled'">取消</el-button>
              </div>
              <div v-else class="confirm-resolved">{{ m.confirm.resolved === 'confirmed' ? '已确认执行' : '已取消' }}</div>
            </div>
            <div class="content" v-html="renderMarkdown(m.content)"></div>
            <span v-if="m.streaming" class="cursor">▍</span>
          </div>
        </div>
      </div>

      <footer class="composer">
        <!-- 结构化输入 -->
        <div v-if="current?.structuredInput && !conversationId" class="form-input">
          <el-form label-position="top" size="small">
            <el-form-item v-for="(prop, name) in current.inputSchema.properties" :key="name"
                          :label="(prop.title || name) + (isRequired(name) ? ' *' : '')">
              <el-input-number v-if="prop.type === 'integer' || prop.type === 'number'" v-model="formInput[name]" />
              <el-input v-else v-model="formInput[name]" :type="(prop.description || '').length > 30 || name === 'bom' ? 'textarea' : 'text'"
                        :autosize="{ minRows: 1, maxRows: 6 }" :placeholder="prop.description" />
            </el-form-item>
          </el-form>
          <el-button type="primary" :loading="busy" @click="sendForm">运行</el-button>
        </div>
        <div v-else class="text-input">
          <el-input v-model="draft" type="textarea" :autosize="{ minRows: 1, maxRows: 5 }"
                    placeholder="输入问题,Enter 发送,Shift+Enter 换行" :disabled="busy"
                    @keydown.enter.exact.prevent="send()" />
          <el-button v-if="!busy" type="primary" :disabled="!draft.trim()" @click="send()">发送</el-button>
          <el-button v-else type="danger" plain @click="stop">停止</el-button>
        </div>
      </footer>
    </section>

    <!-- SKILL 编辑器 -->
    <el-dialog v-model="editorVisible" :title="editor.id ? '编辑 SKILL' : '自定义 SKILL'" width="760px" top="5vh">
      <el-form label-width="110px">
        <div class="form-row">
          <el-form-item label="名称" class="grow"><el-input v-model="editor.name" maxlength="60" /></el-form-item>
          <el-form-item label="图标" style="width:140px"><el-input v-model="editor.icon" maxlength="4" /></el-form-item>
        </div>
        <el-form-item label="简介"><el-input v-model="editor.description" maxlength="300" /></el-form-item>
        <div class="form-row">
          <el-form-item label="分类" class="grow"><el-input v-model="editor.category" maxlength="30" /></el-form-item>
          <el-form-item label="共享范围" class="grow">
            <el-radio-group v-model="editor.scope">
              <el-radio value="PERSONAL">仅自己</el-radio>
              <el-radio value="TENANT" :disabled="!isStaff">本站共享</el-radio>
            </el-radio-group>
          </el-form-item>
        </div>
        <el-form-item label="系统提示词">
          <el-input v-model="editor.systemPrompt" type="textarea" :autosize="{ minRows: 5, maxRows: 14 }"
                    placeholder="定义角色、任务、规则与输出格式" />
        </el-form-item>
        <el-form-item label="可用工具">
          <el-checkbox-group v-model="editor.tools">
            <el-checkbox v-for="t in tools" :key="t.name" :value="t.name">
              {{ t.name }}<span class="tool-hint">{{ t.readOnly ? '' : '(写操作,需确认)' }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="输入方式">
          <el-radio-group v-model="editor.inputMode">
            <el-radio value="chat">自由对话</el-radio>
            <el-radio value="form">结构化表单</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="editor.inputMode === 'form'">
          <el-form-item label="输入字段">
            <div class="fields">
              <div v-for="(f, i) in editor.fields" :key="i" class="field-row">
                <el-input v-model="f.name" placeholder="字段名(英文)" style="width:140px" />
                <el-input v-model="f.title" placeholder="显示名" style="width:120px" />
                <el-select v-model="f.type" style="width:100px">
                  <el-option value="string" label="文本" /><el-option value="integer" label="整数" />
                </el-select>
                <el-input v-model="f.description" placeholder="说明 / 占位提示" class="grow" />
                <el-checkbox v-model="f.required">必填</el-checkbox>
                <el-button text type="danger" @click="editor.fields.splice(i, 1)">删</el-button>
              </div>
              <el-button size="small" @click="editor.fields.push({ name: '', title: '', type: 'string', description: '', required: false })">+ 字段</el-button>
            </div>
          </el-form-item>
          <el-form-item label="用户消息模板">
            <el-input v-model="editor.userPromptTemplate" type="textarea" :autosize="{ minRows: 2, maxRows: 6 }"
                      placeholder="用 {{字段名}} 引用输入,例如:项目背景:{{context}}" />
          </el-form-item>
        </template>
        <div class="form-row">
          <el-form-item label="输出格式" class="grow">
            <el-radio-group v-model="editor.outputMode">
              <el-radio value="text">文本</el-radio><el-radio value="json">JSON</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="温度" class="grow"><el-slider v-model="editor.temperature" :min="0" :max="2" :step="0.1" show-input /></el-form-item>
        </div>
        <el-form-item label="开场白"><el-input v-model="editor.greeting" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSkill">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  aiConversationMessages, aiConversations, aiCreateSkill, aiDeleteConversation, aiDeleteSkill,
  aiDuplicateSkill, aiSkillDetail, aiSkills, aiTools, aiUpdateSkill, fetchProjectDetail
} from '../../api'
import { streamChat } from '../../api/aiStream'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const authStore = useAuthStore()
const isStaff = computed(() => ['ADMIN', 'TEACHER'].includes(authStore.user?.role))

const skills = ref([])
const skillFilter = ref('')
const current = ref(null)
const conversations = ref([])
const conversationId = ref(null)
const messages = ref([])
const draft = ref('')
const formInput = reactive({})
const busy = ref(false)
const scroller = ref(null)
const tools = ref([])
let controller = null

const filteredSkills = computed(() => {
  const k = skillFilter.value.trim().toLowerCase()
  return skills.value.filter((s) => !k || (s.name + s.description + (s.category || '')).toLowerCase().includes(k))
})
const scopeText = (s) => ({ BUILTIN: '内置', TENANT: '本站', PERSONAL: '我的' }[s] || s)
const isRequired = (name) => (current.value?.inputSchema?.required || []).includes(name)
const shortJson = (o) => { const s = JSON.stringify(o || {}); return s.length > 80 ? s.slice(0, 80) + '…' : s }

const renderMarkdown = (text) => {
  if (!text) return ''
  const esc = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return esc
    .replace(/```([\s\S]*?)```/g, (m, code) => `<pre>${code.replace(/^\w*\n/, '')}</pre>`)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>')
    .replace(/^### (.*)$/gm, '<h4>$1</h4>').replace(/^## (.*)$/gm, '<h3>$1</h3>')
    .replace(/^[-*] (.*)$/gm, '<li>$1</li>').replace(/(<li>.*<\/li>\n?)+/g, (m) => `<ul>${m}</ul>`)
    .replace(/\n/g, '<br/>')
}

const scrollToBottom = () => nextTick(() => { if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight })

const loadSkills = async () => {
  skills.value = await aiSkills()
  if (!current.value && skills.value.length) {
    // 项目页「AI 导师」、管理端「试用」等入口通过 ?skill=key 直接定位到某个 SKILL
    const wanted = skills.value.find((s) => s.key === route.query.skill)
    current.value = wanted || skills.value.find((s) => s.key === 'study-assistant') || skills.value[0]
  }
}
const loadConversations = async () => { conversations.value = await aiConversations() }

// ---------- 项目上下文(?projectId=) ----------
const contextProject = ref(null)
const contextBody = computed(() => (contextProject.value ? { projectId: contextProject.value.id } : undefined))
const loadContext = async (projectId) => {
  const id = Number(projectId ?? route.query.projectId)
  if (!id) return
  try {
    const d = await fetchProjectDetail(id)
    contextProject.value = { id: d.project.id, title: d.project.title, enrollment: d.enrollment }
  } catch (e) { /* 项目不存在则忽略 */ }
}
const clearContext = () => { contextProject.value = null }

const selectSkill = (s) => {
  current.value = s
  conversationId.value = null
  messages.value = []
  Object.keys(formInput).forEach((k) => delete formInput[k])
}
const newConversation = () => { conversationId.value = null; messages.value = [] }

const openConversation = async (c) => {
  conversationId.value = c.id
  const skill = skills.value.find((s) => s.key === c.skillKey)
  if (skill) current.value = skill
  // 历史会话自带上下文(服务端已存),这里只用于展示关联条
  if (c.context) {
    try {
      const pid = JSON.parse(c.context).projectId
      if (pid && contextProject.value?.id !== pid) await loadContext(pid)
    } catch (e) { /* 忽略 */ }
  } else {
    contextProject.value = null
  }
  const list = await aiConversationMessages(c.id)
  messages.value = list.filter((m) => m.role === 'user' || (m.role === 'assistant' && m.content && !m.toolCalls))
    .map((m) => ({ role: m.role, content: m.content, tools: [] }))
  scrollToBottom()
}

const removeConversation = async (c) => {
  await aiDeleteConversation(c.id)
  if (conversationId.value === c.id) newConversation()
  await loadConversations()
}

const runStream = (body) => {
  busy.value = true
  const assistant = reactive({ role: 'assistant', content: '', tools: [], confirm: null, streaming: true })
  messages.value.push(assistant)
  scrollToBottom()
  controller = streamChat(body, {
    onDelta: (t) => { assistant.content += t; scrollToBottom() },
    onToolCall: (t) => { assistant.tools.push({ name: t.name, args: t.args }); scrollToBottom() },
    onToolResult: (r) => {
      const t = [...assistant.tools].reverse().find((x) => x.name === r.name && x.ok === undefined)
      if (t) { t.ok = r.ok; t.summary = r.summary }
    },
    onConfirmRequired: (c) => { assistant.confirm = { ...c, resolved: null } },
    onDone: async (d) => {
      assistant.streaming = false
      busy.value = false
      if (d.conversationId) conversationId.value = d.conversationId
      if (!assistant.content && !assistant.confirm) assistant.content = d.content || ''
      await loadConversations()
      // AI 可能刚更新了进度,刷新上下文条里的进度信息
      if (contextProject.value) loadContext(contextProject.value.id)
      scrollToBottom()
    },
    onError: (msg) => {
      assistant.streaming = false
      assistant.content = assistant.content || `⚠️ ${msg}`
      busy.value = false
      ElMessage.error(msg)
    }
  })
}

const send = (text) => {
  const content = (text ?? draft.value).trim()
  if (!content || busy.value || !current.value) return
  draft.value = ''
  messages.value.push({ role: 'user', content, tools: [] })
  runStream({ skillKey: current.value.key, conversationId: conversationId.value, input: content, context: conversationId.value ? undefined : contextBody.value })
}

const sendForm = () => {
  const required = current.value?.inputSchema?.required || []
  for (const r of required) {
    if (formInput[r] === undefined || formInput[r] === '' || formInput[r] === null) {
      ElMessage.warning(`请填写 ${current.value.inputSchema.properties[r]?.title || r}`)
      return
    }
  }
  const summary = Object.entries(formInput).filter(([, v]) => v !== '' && v != null).map(([k, v]) => `${current.value.inputSchema.properties[k]?.title || k}:${v}`).join('\n')
  messages.value.push({ role: 'user', content: summary, tools: [] })
  runStream({ skillKey: current.value.key, conversationId: conversationId.value, input: { ...formInput }, context: conversationId.value ? undefined : contextBody.value })
}

const confirmTool = (m) => {
  m.confirm.resolved = 'confirmed'
  runStream({ skillKey: current.value.key, conversationId: conversationId.value, confirm: true })
}

const stop = () => { controller?.abort(); busy.value = false; const last = messages.value[messages.value.length - 1]; if (last) last.streaming = false }

// ---------- SKILL 编辑 ----------
const editorVisible = ref(false)
const saving = ref(false)
const editor = reactive({
  id: null, key: null, name: '', icon: '✨', description: '', category: '', scope: 'PERSONAL',
  systemPrompt: '', tools: [], inputMode: 'chat', fields: [], userPromptTemplate: '', outputMode: 'text',
  temperature: 0.4, greeting: ''
})

const openEditor = async (skill) => {
  if (tools.value.length === 0) tools.value = await aiTools()
  Object.assign(editor, {
    id: null, key: null, name: '', icon: '✨', description: '', category: '', scope: 'PERSONAL',
    systemPrompt: '', tools: [], inputMode: 'chat', fields: [], userPromptTemplate: '', outputMode: 'text',
    temperature: 0.4, greeting: ''
  })
  if (skill) {
    const d = await aiSkillDetail(skill.key)
    const spec = d.spec || {}
    Object.assign(editor, {
      id: d.id, key: d.key, name: d.name, icon: d.icon, description: d.description || '', category: d.category || '',
      scope: d.scope, systemPrompt: spec.systemPrompt || '', tools: spec.tools || [],
      inputMode: spec.inputSchema?.properties ? 'form' : 'chat',
      fields: Object.entries(spec.inputSchema?.properties || {}).map(([name, p]) => ({
        name, title: p.title || '', type: p.type || 'string', description: p.description || '',
        required: (spec.inputSchema?.required || []).includes(name)
      })),
      userPromptTemplate: spec.userPromptTemplate || '', outputMode: spec.outputMode || 'text',
      temperature: spec.model?.temperature ?? 0.4, greeting: spec.greeting || ''
    })
  }
  editorVisible.value = true
}

const buildSpec = () => {
  const spec = {
    systemPrompt: editor.systemPrompt, tools: editor.tools, outputMode: editor.outputMode,
    model: { temperature: editor.temperature, maxTokens: 1500 }, greeting: editor.greeting
  }
  if (editor.inputMode === 'form') {
    const properties = {}
    const required = []
    for (const f of editor.fields) {
      if (!f.name) continue
      properties[f.name] = { type: f.type, title: f.title || f.name, description: f.description }
      if (f.required) required.push(f.name)
    }
    spec.inputSchema = { type: 'object', properties, required }
    spec.userPromptTemplate = editor.userPromptTemplate
  }
  return spec
}

const saveSkill = async () => {
  if (!editor.name.trim() || !editor.systemPrompt.trim()) { ElMessage.warning('名称与系统提示词必填'); return }
  saving.value = true
  try {
    const body = { name: editor.name, icon: editor.icon, description: editor.description, category: editor.category, scope: editor.scope, spec: buildSpec() }
    const saved = editor.id ? await aiUpdateSkill(editor.id, body) : await aiCreateSkill(body)
    editorVisible.value = false
    await loadSkills()
    current.value = skills.value.find((s) => s.key === saved.key) || current.value
    newConversation()
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

const onSkillCommand = async (cmd) => {
  if (cmd === 'edit') return openEditor(current.value)
  if (cmd === 'duplicate') {
    const saved = await aiDuplicateSkill(current.value.key, {})
    await loadSkills()
    current.value = skills.value.find((s) => s.key === saved.key) || current.value
    newConversation()
    ElMessage.success('已另存为我的 SKILL,可在「操作 → 编辑」中修改')
    return
  }
  if (cmd === 'delete') {
    try { await ElMessageBox.confirm(`删除 SKILL「${current.value.name}」?`, '删除', { type: 'warning' }) } catch (e) { return }
    await aiDeleteSkill(current.value.id)
    await loadSkills()
    current.value = skills.value[0] || null
    newConversation()
  }
}

onMounted(async () => {
  await Promise.all([loadSkills(), loadConversations(), loadContext()])
})
</script>

<style scoped>
.ai-page { display: grid; grid-template-columns: 300px 1fr; gap: 16px; height: calc(100vh - 140px); min-height: 560px; }
.side { display: flex; flex-direction: column; overflow: hidden; padding: 14px; }
.side-head { display: flex; justify-content: space-between; align-items: center; font-weight: 700; font-size: 14px; margin-bottom: 8px; }
.history-head { margin-top: 12px; border-top: 1px solid var(--border); padding-top: 12px; }
.side-search { margin-bottom: 8px; }
.skill-list { overflow-y: auto; flex: 1; min-height: 160px; }
.skill-item { display: flex; gap: 10px; padding: 10px; border-radius: 10px; cursor: pointer; }
.skill-item:hover { background: #f3f4f6; }
.skill-item.active { background: #eef2ff; }
.skill-icon { font-size: 22px; line-height: 1; }
.skill-icon.big { font-size: 28px; }
.skill-name { font-weight: 600; font-size: 14px; display: flex; gap: 6px; align-items: center; }
.scope-tag { transform: scale(.85); }
.skill-desc { font-size: 12px; color: #6b7280; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.history { overflow-y: auto; max-height: 200px; }
.history-item { display: flex; align-items: center; justify-content: space-between; padding: 6px 10px; border-radius: 8px; cursor: pointer; font-size: 13px; color: #374151; }
.history-item:hover, .history-item.active { background: #f3f4f6; }
.history-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.history-del { opacity: .5; }
.empty-hint { font-size: 12px; color: #9ca3af; padding: 6px 10px; }

.chat { display: flex; flex-direction: column; overflow: hidden; padding: 0; }
.chat-head { display: flex; align-items: center; gap: 12px; padding: 14px 18px; border-bottom: 1px solid var(--border); }
.context-bar {
  display: flex; align-items: center; gap: 10px; padding: 10px 18px; font-size: 13px;
  background: linear-gradient(to right, #faf5ff, #eff6ff); border-bottom: 1px solid #e9d5ff;
}
.context-bar.muted { background: #f9fafb; border-bottom-color: var(--border); }
.ctx-icon { font-size: 18px; }
.ctx-text { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.ctx-text b { font-size: 13px; color: #4c1d95; }
.ctx-sub { font-size: 12px; color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-head-text { flex: 1; min-width: 0; }
.chat-title { font-weight: 700; }
.chat-sub { font-size: 12px; color: #6b7280; }
.chat-tools { display: flex; gap: 6px; flex-wrap: wrap; max-width: 40%; }
.messages { flex: 1; overflow-y: auto; padding: 18px; display: flex; flex-direction: column; gap: 14px; }
.greeting { margin: auto; text-align: center; max-width: 560px; color: #4b5563; }
.greeting-text { font-size: 15px; margin-bottom: 14px; }
.examples { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; }
.msg { display: flex; }
.msg.user { justify-content: flex-end; }
.bubble { max-width: 78%; padding: 12px 16px; border-radius: 14px; font-size: 14px; line-height: 1.7; word-break: break-word; }
.bubble.user { background: var(--brand-gradient); color: #fff; white-space: pre-wrap; }
.bubble.assistant { background: #f3f4f6; color: #111827; }
.bubble.assistant :deep(pre) { background: #0f172a; color: #e2e8f0; padding: 10px 12px; border-radius: 8px; overflow: auto; font-size: 12px; }
.bubble.assistant :deep(code) { background: #e5e7eb; padding: 1px 5px; border-radius: 4px; }
.bubble.assistant :deep(ul) { margin: 4px 0; padding-left: 20px; }
.tool-card { display: flex; gap: 8px; flex-wrap: wrap; font-size: 12px; background: #fff; border: 1px dashed #c7d2fe; border-radius: 8px; padding: 6px 10px; margin-bottom: 6px; }
.tool-card.fail { border-color: #fca5a5; }
.tool-name { font-weight: 600; color: #4f46e5; }
.tool-args { color: #6b7280; font-family: monospace; }
.tool-result { color: #16a34a; }
.tool-card.fail .tool-result { color: #dc2626; }
.tool-result.running { color: #9ca3af; }
.confirm-card { background: #fffbeb; border: 1px solid #fcd34d; border-radius: 10px; padding: 10px 12px; margin-bottom: 8px; }
.confirm-title { font-weight: 600; color: #92400e; margin-bottom: 6px; }
.confirm-args { font-size: 12px; background: #fff; border-radius: 6px; padding: 8px; margin: 0 0 8px; max-height: 160px; overflow: auto; }
.confirm-actions { display: flex; gap: 8px; }
.confirm-resolved { font-size: 12px; color: #6b7280; }
.cursor { animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0; } }
.composer { border-top: 1px solid var(--border); padding: 12px 16px; }
.text-input { display: flex; gap: 10px; align-items: flex-end; }
.text-input .el-textarea { flex: 1; }
.form-input { display: flex; flex-direction: column; gap: 8px; }
.form-row { display: flex; gap: 12px; }
.grow { flex: 1; }
.fields { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.field-row { display: flex; gap: 8px; align-items: center; }
.tool-hint { color: #d97706; font-size: 12px; margin-left: 4px; }
</style>
