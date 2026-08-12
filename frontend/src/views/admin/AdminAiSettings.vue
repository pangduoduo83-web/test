<template>
  <div class="card ai-settings">
    <div class="head">
      <div>
        <h3>AI 模型设置</h3>
        <p class="sub">配置大模型接口后,「AI 学习规划师」「AI 成果预评审」即刻生效,无需重启服务;未配置时自动降级为智能匹配。</p>
      </div>
      <el-switch v-model="form.enabled" active-text="启用 AI" @change="saveField('enabled')" />
    </div>

    <el-alert v-if="!settings.apiKeySet" type="warning" :closable="false" class="tip"
              title="尚未配置 API Key,AI 功能当前以智能匹配模式降级运行" />
    <el-alert v-else type="success" :closable="false" class="tip"
              :title="`已配置 API Key(${settings.apiKeyMasked},来源:${settings.apiKeySource === 'DB' ? '后台设置' : '环境变量'})`" />

    <el-form label-width="130px" class="form">
      <el-form-item label="服务商快捷选择">
        <el-radio-group @change="applyPreset">
          <el-radio-button value="deepseek">DeepSeek</el-radio-button>
          <el-radio-button value="qwen">通义千问</el-radio-button>
          <el-radio-button value="custom">自定义</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="接口地址" required>
        <el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com" />
        <div class="hint">OpenAI 兼容协议地址,不带 /chat/completions 后缀</div>
      </el-form-item>

      <el-form-item label="模型名称" required>
        <el-input v-model="form.model" placeholder="deepseek-chat" />
      </el-form-item>

      <el-form-item label="API Key">
        <el-input v-model="form.apiKey" type="password" show-password
                  :placeholder="settings.apiKeySet ? '已配置(留空保持不变)' : '粘贴 sk- 开头的密钥'" />
        <div class="hint">密钥仅存储在服务端数据库,前端只回显掩码</div>
      </el-form-item>

      <el-form-item label="输出 Token 上限">
        <el-input-number v-model="form.maxTokens" :min="200" :max="8000" :step="100" />
        <span class="inline-hint">单次回复的最大长度,影响生成内容详细程度与费用(建议 1500~2500)</span>
      </el-form-item>

      <el-form-item label="温度(随机性)">
        <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input
                   style="max-width:420px" />
        <div class="hint">越低越稳定、越高越有创造性,推荐 0.3~0.7</div>
      </el-form-item>

      <el-form-item label="连接超时(毫秒)">
        <el-input-number v-model="form.connectTimeoutMs" :min="1000" :max="30000" :step="500" />
      </el-form-item>

      <el-form-item label="读取超时(毫秒)">
        <el-input-number v-model="form.readTimeoutMs" :min="3000" :max="120000" :step="1000" />
        <span class="inline-hint">等待模型回复的最长时间,推理类模型可调大</span>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
        <el-button :loading="testing" @click="test">测试连接</el-button>
        <span v-if="testResult" class="test-result" :class="testResult.ok ? 'ok' : 'fail'">
          {{ testResult.ok ? `✓ 连接成功,延迟 ${testResult.latencyMs}ms` : `✗ ${testResult.error}` }}
        </span>
      </el-form-item>
    </el-form>

    <div class="notes">
      <b>说明</b>
      <ul>
        <li>后台设置的优先级高于环境变量(IOEDU_AI_*);保存后立即生效。</li>
        <li>上下文输入由系统自动裁剪(技能画像+候选项目摘要),此处只需控制输出上限。</li>
        <li>DeepSeek 模型填 <code>deepseek-chat</code>;通义千问填 <code>qwen-plus</code> 或 <code>qwen-turbo</code>。</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminGetAiSettings, adminTestAiSettings, adminUpdateAiSettings } from '../../api'

const settings = ref({})
const saving = ref(false)
const testing = ref(false)
const testResult = ref(null)

const form = reactive({
  enabled: true,
  baseUrl: '',
  model: '',
  apiKey: '',
  maxTokens: 2000,
  temperature: 0.4,
  connectTimeoutMs: 3000,
  readTimeoutMs: 20000
})

const presets = {
  deepseek: { baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat' },
  qwen: { baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode', model: 'qwen-plus' }
}

const applyPreset = (key) => {
  const p = presets[key]
  if (p) {
    form.baseUrl = p.baseUrl
    form.model = p.model
  }
}

const load = async () => {
  const d = await adminGetAiSettings()
  settings.value = d
  form.enabled = d.enabled
  form.baseUrl = d.baseUrl
  form.model = d.model
  form.maxTokens = d.maxTokens
  form.temperature = d.temperature
  form.connectTimeoutMs = d.connectTimeoutMs
  form.readTimeoutMs = d.readTimeoutMs
  form.apiKey = ''
}

const save = async () => {
  saving.value = true
  try {
    const d = await adminUpdateAiSettings({
      enabled: form.enabled,
      baseUrl: form.baseUrl,
      model: form.model,
      apiKey: form.apiKey || undefined,
      maxTokens: form.maxTokens,
      temperature: form.temperature,
      connectTimeoutMs: form.connectTimeoutMs,
      readTimeoutMs: form.readTimeoutMs
    })
    settings.value = d
    form.apiKey = ''
    testResult.value = null
    ElMessage.success('配置已保存,立即生效')
  } finally {
    saving.value = false
  }
}

const saveField = async (field) => {
  try {
    settings.value = await adminUpdateAiSettings({ [field]: form[field] })
    ElMessage.success(form.enabled ? 'AI 功能已启用' : 'AI 功能已停用(前台自动降级)')
  } catch (e) { /* 已提示 */ }
}

const test = async () => {
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await adminTestAiSettings()
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.ai-settings { max-width: 860px; }
.head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.head h3 { margin: 0 0 4px; }
.sub { color: var(--text-secondary); font-size: 13px; margin: 0; }
.tip { margin: 14px 0; }
.form { margin-top: 8px; }
.hint { font-size: 12px; color: #9ca3af; line-height: 1.6; width: 100%; }
.inline-hint { font-size: 12px; color: #9ca3af; margin-left: 12px; }
.test-result { margin-left: 14px; font-size: 13px; }
.test-result.ok { color: #16a34a; }
.test-result.fail { color: #dc2626; }
.notes {
  background: #f9fafb; border: 1px solid var(--border); border-radius: 12px;
  padding: 14px 18px; font-size: 13px; color: #374151;
}
.notes ul { margin: 8px 0 0; padding-left: 18px; }
.notes li { margin-bottom: 4px; }
.notes code { background: #eef2ff; padding: 1px 6px; border-radius: 4px; }
</style>
