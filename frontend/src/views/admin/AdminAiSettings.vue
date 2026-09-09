<template>
  <div class="ai-settings">
    <div class="layout">
      <!-- 主栏 -->
      <div class="main">
        <div class="card">
          <div class="card-head">
            <div>
              <h3><Plug :size="17" color="#9333ea" /> 模型接入</h3>
              <p class="sub">OpenAI 兼容协议。保存后「AI 助手」「AI 学习规划师」「AI 成果预评审」立即使用新配置,无需重启。</p>
            </div>
          </div>

          <div class="providers">
            <div v-for="p in providers" :key="p.key" class="provider" :class="{ active: providerKey === p.key }" @click="applyPreset(p.key)">
              <span class="pv-logo" :style="{ background: p.bg }">{{ p.short }}</span>
              <div class="pv-text">
                <div class="pv-name">{{ p.name }}</div>
                <div class="pv-desc">{{ p.desc }}</div>
              </div>
              <span v-if="providerKey === p.key" class="pv-check"><Check :size="14" /></span>
            </div>
          </div>

          <div class="form-grid">
            <div class="field span-2">
              <label>接口地址 <b>*</b></label>
              <el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com" />
              <div class="hint">不带 /chat/completions 后缀;通义千问填 https://dashscope.aliyuncs.com/compatible-mode</div>
            </div>
            <div class="field">
              <label>模型名称 <b>*</b></label>
              <el-input v-model="form.model" placeholder="deepseek-chat">
                <template #suffix>
                  <el-dropdown trigger="click" @command="(m) => (form.model = m)">
                    <span class="model-pick">常用 ▾</span>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item v-for="m in modelSuggestions" :key="m" :command="m">{{ m }}</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </template>
              </el-input>
            </div>
            <div class="field">
              <label>API Key</label>
              <el-input v-model="form.apiKey" type="password" show-password
                        :placeholder="settings.apiKeySet ? `已配置 ${settings.apiKeyMasked},留空保持不变` : '粘贴 sk- 开头的密钥'" />
              <div class="hint">只存在服务端并加密,前端永远只回显掩码</div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <div>
              <h3><SlidersHorizontal :size="17" color="#2563eb" /> 生成参数</h3>
              <p class="sub">影响回复长度、稳定性与费用。不确定就保持默认。</p>
            </div>
          </div>
          <div class="form-grid">
            <div class="field">
              <label>输出 Token 上限</label>
              <el-input-number v-model="form.maxTokens" :min="200" :max="8000" :step="100" style="width:100%" />
              <div class="hint">单次回复最大长度,建议 1500~2500</div>
            </div>
            <div class="field">
              <label>温度 <span class="val">{{ form.temperature }}</span></label>
              <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" :marks="{ 0: '稳定', 0.7: '推荐', 2: '发散' }" class="temp" />
              <div class="hint">越低越稳定、越高越有创造性,推荐 0.3~0.7</div>
            </div>
            <div class="field">
              <label>连接超时</label>
              <el-input-number v-model="form.connectTimeoutMs" :min="1000" :max="30000" :step="500" style="width:100%" />
              <div class="hint">毫秒;建立连接的最长等待</div>
            </div>
            <div class="field">
              <label>读取超时</label>
              <el-input-number v-model="form.readTimeoutMs" :min="3000" :max="120000" :step="1000" style="width:100%" />
              <div class="hint">毫秒;等待模型回复的最长时间,推理类模型可调大</div>
            </div>
            <div class="field span-2">
              <label>每人每日使用次数上限 <span class="val">{{ form.dailyRunsPerUser ? form.dailyRunsPerUser + ' 次' : '不限' }}</span></label>
              <el-input-number v-model="form.dailyRunsPerUser" :min="0" :max="10000" :step="10" style="width:100%" />
              <div class="hint">防止个别账号刷接口;Key 是本站自己的,平台不做额度限制。0 表示不限。本站的硬件设计助手也使用这里配置的模型与 Key</div>
            </div>
          </div>
        </div>

        <div class="actions card">
          <div class="actions-left">
            <el-button type="primary" size="large" :loading="saving" @click="save"><Save :size="15" style="margin-right:6px" />保存配置</el-button>
            <el-button size="large" :loading="testing" @click="test"><Zap :size="15" style="margin-right:6px" />测试连接</el-button>
          </div>
          <div v-if="testResult" class="test-result" :class="testResult.ok ? 'ok' : 'fail'">
            <CircleCheckBig v-if="testResult.ok" :size="16" /><CircleX v-else :size="16" />
            {{ testResult.ok ? `连接成功,往返 ${testResult.latencyMs} ms` : testResult.error }}
          </div>
          <span v-else class="muted">测试会用当前保存的配置向模型发一条极短的消息</span>
        </div>
      </div>

      <!-- 侧栏 -->
      <aside class="side">
        <div class="card status" :class="statusKind">
          <div class="status-head">
            <span class="status-dot"></span>
            <b>{{ statusTitle }}</b>
            <el-switch v-model="form.enabled" class="status-switch" inline-prompt active-text="开" inactive-text="关" @change="saveField('enabled')" />
          </div>
          <p class="status-desc">{{ statusDesc }}</p>
          <dl class="kv">
            <dt>API Key</dt><dd>{{ settings.apiKeySet ? settings.apiKeyMasked : '未配置' }}</dd>
            <dt>来源</dt><dd>{{ settings.apiKeySet ? (settings.apiKeySource === 'DB' ? '后台设置' : '环境变量') : '–' }}</dd>
            <dt>模型</dt><dd class="mono">{{ settings.model || '–' }}</dd>
            <dt>接口</dt><dd class="mono ellipsis" :title="settings.baseUrl">{{ settings.baseUrl || '–' }}</dd>
          </dl>
        </div>

        <div class="card">
          <h4>AI 用在哪里</h4>
          <div class="feature" v-for="f in features" :key="f.title">
            <span class="ft-icon" :style="{ background: f.bg }"><component :is="f.icon" :size="16" color="#fff" /></span>
            <div>
              <div class="ft-title">{{ f.title }}</div>
              <div class="ft-desc">{{ f.desc }}</div>
            </div>
          </div>
          <el-button text type="primary" class="link-btn" @click="$router.push('/admin/ai-center')">查看 SKILL、工具策略与用量 →</el-button>
        </div>

        <div class="card notes">
          <h4>说明</h4>
          <ul>
            <li>后台设置优先于环境变量 <code>IOEDU_AI_*</code>,保存即生效。</li>
            <li>关闭 AI 后,学习规划与预评审自动退回规则匹配,AI 助手不可用。</li>
            <li>上下文由系统自动裁剪,这里只需控制输出上限。</li>
          </ul>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Bot, Check, CircleCheckBig, CircleX, ClipboardCheck, Plug, Save, SlidersHorizontal, Sparkles, Zap
} from 'lucide-vue-next'
import { adminGetAiSettings, adminTestAiSettings, adminUpdateAiSettings } from '../../api'

const settings = ref({})
const saving = ref(false)
const testing = ref(false)
const testResult = ref(null)

const form = reactive({
  enabled: true, baseUrl: '', model: '', apiKey: '', maxTokens: 2000, temperature: 0.4, connectTimeoutMs: 3000, readTimeoutMs: 20000,
  dailyRunsPerUser: 50
})

const providers = [
  { key: 'deepseek', name: 'DeepSeek', short: 'DS', desc: '性价比高,默认推荐', bg: 'linear-gradient(135deg,#4f46e5,#7c3aed)', baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat', models: ['deepseek-chat', 'deepseek-reasoner'] },
  { key: 'qwen', name: '通义千问', short: '通', desc: '阿里云 DashScope 兼容模式', bg: 'linear-gradient(135deg,#f97316,#ef4444)', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode', model: 'qwen-plus', models: ['qwen-plus', 'qwen-turbo', 'qwen-max'] },
  { key: 'custom', name: '自定义', short: '⚙', desc: '任意 OpenAI 兼容服务', bg: 'linear-gradient(135deg,#64748b,#334155)', models: ['gpt-4o-mini', 'glm-4-flash', 'moonshot-v1-8k'] }
]
const providerKey = computed(() => providers.find((p) => p.baseUrl && form.baseUrl && form.baseUrl.replace(/\/$/, '') === p.baseUrl)?.key || (form.baseUrl ? 'custom' : ''))
const modelSuggestions = computed(() => providers.find((p) => p.key === providerKey.value)?.models || providers[2].models)

const features = [
  { icon: Bot, title: 'AI 助手 / SKILL', desc: '学生教师对话、调用平台工具', bg: 'linear-gradient(135deg,#c084fc,#9333ea)' },
  { icon: Sparkles, title: 'AI 学习规划师', desc: '按技能画像生成三阶段学习路线', bg: 'linear-gradient(135deg,#60a5fa,#2563eb)' },
  { icon: ClipboardCheck, title: 'AI 成果预评审', desc: '成果评审页一键生成评分建议', bg: 'linear-gradient(135deg,#4ade80,#16a34a)' }
]

const statusKind = computed(() => !form.enabled ? 'off' : settings.value.apiKeySet ? 'on' : 'warn')
const statusTitle = computed(() => !form.enabled ? 'AI 已关闭' : settings.value.apiKeySet ? 'AI 运行中' : '缺少 API Key')
const statusDesc = computed(() => !form.enabled
  ? '所有 AI 功能停用,学习规划与预评审退回规则匹配。'
  : settings.value.apiKeySet ? '模型接口已配置,各 AI 功能正常工作。' : '已启用但没有密钥,当前以规则匹配模式降级运行。')

const applyPreset = (key) => {
  const p = providers.find((x) => x.key === key)
  if (p?.baseUrl) {
    form.baseUrl = p.baseUrl
    form.model = p.model
  } else if (providerKey.value !== 'custom') {
    // 从预设切到自定义:清空地址让用户填自己的服务
    form.baseUrl = ''
  }
}

const load = async () => {
  const d = await adminGetAiSettings()
  settings.value = d
  Object.assign(form, {
    enabled: d.enabled, baseUrl: d.baseUrl, model: d.model, maxTokens: d.maxTokens, temperature: d.temperature,
    connectTimeoutMs: d.connectTimeoutMs, readTimeoutMs: d.readTimeoutMs, apiKey: '', dailyRunsPerUser: d.dailyRunsPerUser ?? 50
  })
}

const save = async () => {
  if (!form.baseUrl.trim() || !form.model.trim()) { ElMessage.warning('接口地址和模型名称必填'); return }
  saving.value = true
  try {
    settings.value = await adminUpdateAiSettings({
      enabled: form.enabled, baseUrl: form.baseUrl.trim(), model: form.model.trim(), apiKey: form.apiKey || undefined,
      maxTokens: form.maxTokens, temperature: form.temperature, connectTimeoutMs: form.connectTimeoutMs, readTimeoutMs: form.readTimeoutMs,
      dailyRunsPerUser: form.dailyRunsPerUser ?? 0
    })
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
    ElMessage.success(form.enabled ? 'AI 功能已启用' : 'AI 功能已停用,前台自动降级')
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
.layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 16px; align-items: start; }
@media (max-width: 1100px) { .layout { grid-template-columns: 1fr; } }
.main { display: flex; flex-direction: column; gap: 16px; }
.side { display: flex; flex-direction: column; gap: 16px; }
.muted { font-size: 12px; color: #9ca3af; }

.card-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 18px; }
.card-head h3 { margin: 0 0 4px; font-size: 16px; display: flex; align-items: center; gap: 8px; }
.sub { color: var(--text-secondary); font-size: 13px; margin: 0; line-height: 1.6; }

/* 服务商卡 */
.providers { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 20px; }
@media (max-width: 900px) { .providers { grid-template-columns: 1fr; } }
.provider {
  display: flex; align-items: center; gap: 12px; padding: 14px; border-radius: 14px; border: 1.5px solid var(--border); cursor: pointer;
  position: relative; transition: all .15s; background: #fff;
}
.provider:hover { border-color: #c7d2fe; }
.provider.active { border-color: var(--brand-blue); background: #eff6ff; box-shadow: 0 0 0 3px #dbeafe; }
.pv-logo { width: 40px; height: 40px; border-radius: 10px; color: #fff; font-weight: 800; display: grid; place-items: center; flex-shrink: 0; font-size: 14px; }
.pv-name { font-weight: 700; font-size: 14px; }
.pv-desc { font-size: 12px; color: #6b7280; margin-top: 2px; }
.pv-check { position: absolute; top: 10px; right: 10px; width: 20px; height: 20px; border-radius: 50%; background: var(--brand-blue); color: #fff; display: grid; place-items: center; }

/* 表单网格 */
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px 20px; }
@media (max-width: 900px) { .form-grid { grid-template-columns: 1fr; } }
.field { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.field.span-2 { grid-column: 1 / -1; }
.field label { font-size: 13px; font-weight: 600; color: #374151; display: flex; align-items: center; gap: 6px; }
.field label b { color: #dc2626; }
.field label .val { margin-left: auto; font-weight: 700; color: var(--brand-blue); font-variant-numeric: tabular-nums; }
.hint { font-size: 12px; color: #9ca3af; line-height: 1.5; }
.model-pick { font-size: 12px; color: var(--brand-blue); cursor: pointer; white-space: nowrap; }
.temp { padding: 0 8px 14px; }
:deep(.el-input-number .el-input__inner) { text-align: left; }

/* 操作条 */
.actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 16px 24px; flex-wrap: wrap; }
.actions-left { display: flex; gap: 10px; }
.test-result { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; }
.test-result.ok { color: #16a34a; }
.test-result.fail { color: #dc2626; }

/* 状态卡 */
.status { border-left: 4px solid #22c55e; }
.status.warn { border-left-color: #f59e0b; }
.status.off { border-left-color: #9ca3af; }
.status-head { display: flex; align-items: center; gap: 10px; font-size: 15px; }
.status-dot { width: 10px; height: 10px; border-radius: 50%; background: #22c55e; box-shadow: 0 0 0 4px #dcfce7; }
.status.warn .status-dot { background: #f59e0b; box-shadow: 0 0 0 4px #fef3c7; }
.status.off .status-dot { background: #9ca3af; box-shadow: 0 0 0 4px #f3f4f6; }
.status-switch { margin-left: auto; }
.status-desc { font-size: 13px; color: #6b7280; line-height: 1.6; margin: 10px 0 14px; }
.kv { display: grid; grid-template-columns: 64px 1fr; gap: 8px 10px; margin: 0; font-size: 13px; }
.kv dt { color: #9ca3af; }
.kv dd { margin: 0; color: #111827; min-width: 0; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12px; }
.ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.side h4 { margin: 0 0 12px; font-size: 14px; }
.feature { display: flex; gap: 10px; align-items: flex-start; padding: 8px 0; }
.ft-icon { width: 32px; height: 32px; border-radius: 9px; display: grid; place-items: center; flex-shrink: 0; }
.ft-title { font-size: 13px; font-weight: 600; }
.ft-desc { font-size: 12px; color: #6b7280; margin-top: 2px; }
.link-btn { padding-left: 0; margin-top: 6px; }
.notes ul { margin: 0; padding-left: 18px; font-size: 13px; color: #4b5563; line-height: 1.8; }
.notes code { background: #eef2ff; padding: 1px 6px; border-radius: 4px; font-size: 12px; }
</style>
