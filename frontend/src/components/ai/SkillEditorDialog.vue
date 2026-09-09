<template>
  <!-- 自定义 SKILL 编辑器(教师 / 管理员可建本站共享技能,任何人可建个人技能) -->
  <el-dialog :model-value="modelValue" :title="editor.id ? '编辑技能' : '新建技能'" width="760px" top="5vh" @update:model-value="(v) => $emit('update:modelValue', v)">
    <el-form label-width="110px">
      <div class="form-row">
        <el-form-item label="名称" class="grow"><el-input v-model="editor.name" maxlength="60" /></el-form-item>
        <el-form-item label="图标" style="width:140px"><el-input v-model="editor.icon" maxlength="4" /></el-form-item>
      </div>
      <el-form-item label="简介"><el-input v-model="editor.description" maxlength="300" placeholder="这个技能帮学生做什么,一句话" /></el-form-item>
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
        <el-input v-model="editor.systemPrompt" type="textarea" :autosize="{ minRows: 5, maxRows: 14 }" placeholder="定义角色、任务、规则与输出格式" />
      </el-form-item>
      <el-form-item label="可用工具">
        <el-checkbox-group v-model="editor.tools">
          <el-checkbox v-for="t in tools" :key="t.name" :value="t.name">{{ t.name }}<span class="tool-hint">{{ t.readOnly ? '' : '(写操作,需确认)' }}</span></el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="输入方式">
        <el-radio-group v-model="editor.inputMode">
          <el-radio value="chat">一段文字</el-radio>
          <el-radio value="form">结构化表单</el-radio>
        </el-radio-group>
      </el-form-item>
      <template v-if="editor.inputMode === 'form'">
        <el-form-item label="输入字段">
          <div class="fields">
            <div v-for="(f, i) in editor.fields" :key="i" class="field-row">
              <el-input v-model="f.name" placeholder="字段名(英文)" style="width:140px" />
              <el-input v-model="f.title" placeholder="显示名" style="width:120px" />
              <el-select v-model="f.type" style="width:100px"><el-option value="string" label="文本" /><el-option value="integer" label="整数" /></el-select>
              <el-input v-model="f.description" placeholder="说明 / 占位提示" class="grow" />
              <el-checkbox v-model="f.required">必填</el-checkbox>
              <el-button text type="danger" @click="editor.fields.splice(i, 1)">删</el-button>
            </div>
            <el-button size="small" @click="editor.fields.push({ name: '', title: '', type: 'string', description: '', required: false })">+ 字段</el-button>
          </div>
        </el-form-item>
        <el-form-item label="用户消息模板">
          <el-input v-model="editor.userPromptTemplate" type="textarea" :autosize="{ minRows: 2, maxRows: 6 }" placeholder="用 {{字段名}} 引用输入,例如:项目背景:{{context}}" />
        </el-form-item>
      </template>
      <div class="form-row">
        <el-form-item label="输出格式" class="grow">
          <el-radio-group v-model="editor.outputMode"><el-radio value="text">文本</el-radio><el-radio value="json">JSON</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="温度" class="grow"><el-slider v-model="editor.temperature" :min="0" :max="2" :step="0.1" show-input /></el-form-item>
      </div>
      <el-form-item label="输入提示"><el-input v-model="editor.greeting" maxlength="200" placeholder="显示在输入框里的提示语" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button v-if="editor.id" type="danger" text :loading="saving" @click="remove">删除技能</el-button>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiCreateSkill, aiDeleteSkill, aiSkillDetail, aiTools, aiUpdateSkill } from '../../api'
import { useAuthStore } from '../../stores/auth'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 传入已有技能(含 key)则为编辑,null 为新建 */
  skill: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved', 'deleted'])
const authStore = useAuthStore()
const isStaff = ['ADMIN', 'TEACHER'].includes(authStore.user?.role)
const tools = ref([])
const saving = ref(false)
const blank = () => ({
  id: null, key: null, name: '', icon: '✨', description: '', category: '', scope: isStaff ? 'TENANT' : 'PERSONAL',
  systemPrompt: '', tools: [], inputMode: 'chat', fields: [], userPromptTemplate: '', outputMode: 'text', temperature: 0.4, greeting: ''
})
const editor = reactive(blank())

const load = async () => {
  if (!tools.value.length) tools.value = await aiTools().catch(() => [])
  Object.assign(editor, blank())
  if (props.skill?.key) {
    const d = await aiSkillDetail(props.skill.key)
    const spec = d.spec || {}
    Object.assign(editor, {
      id: d.id, key: d.key, name: d.name, icon: d.icon, description: d.description || '', category: d.category || '', scope: d.scope,
      systemPrompt: spec.systemPrompt || '', tools: spec.tools || [], inputMode: spec.inputSchema?.properties ? 'form' : 'chat',
      fields: Object.entries(spec.inputSchema?.properties || {}).map(([name, p]) => ({ name, title: p.title || '', type: p.type || 'string', description: p.description || '', required: (spec.inputSchema?.required || []).includes(name) })),
      userPromptTemplate: spec.userPromptTemplate || '', outputMode: spec.outputMode || 'text', temperature: spec.model?.temperature ?? 0.4, greeting: spec.greeting || ''
    })
  }
}
watch(() => props.modelValue, (v) => { if (v) load() })

const buildSpec = () => {
  const spec = { systemPrompt: editor.systemPrompt, tools: editor.tools, outputMode: editor.outputMode, model: { temperature: editor.temperature, maxTokens: 1500 }, greeting: editor.greeting }
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
const save = async () => {
  if (!editor.name.trim() || !editor.systemPrompt.trim()) { ElMessage.warning('名称与系统提示词必填'); return }
  saving.value = true
  try {
    const body = { name: editor.name, icon: editor.icon, description: editor.description, category: editor.category, scope: editor.scope, spec: buildSpec() }
    const saved = editor.id ? await aiUpdateSkill(editor.id, body) : await aiCreateSkill(body)
    emit('update:modelValue', false)
    emit('saved', saved)
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}
const remove = async () => {
  try { await ElMessageBox.confirm(`删除技能「${editor.name}」?`, '删除', { type: 'warning' }) } catch (e) { return }
  saving.value = true
  try {
    await aiDeleteSkill(editor.id)
    emit('update:modelValue', false)
    emit('deleted', editor.key)
    ElMessage.success('已删除')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.form-row { display: flex; gap: 12px; }
.grow { flex: 1; min-width: 0; }
.tool-hint { font-size: 11px; color: #d97706; margin-left: 4px; }
.fields { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.field-row { display: flex; gap: 8px; align-items: center; }
</style>
