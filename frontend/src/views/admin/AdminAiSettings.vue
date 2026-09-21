<template>
  <div class="settings-page ai-settings">
    <header class="settings-hero">
      <div class="hero-copy">
        <div class="eyebrow"><span class="eyebrow-icon"><Bot :size="14" /></span><span>系统配置</span><ChevronRight :size="13" /><b>AI 配置</b></div>
        <h1>AI 配置</h1>
        <p>管理主模型、生成策略和多媒体服务。保存后立即生效，无需重启服务。</p>
      </div>
      <div class="hero-state">
        <span class="status-pill" :class="statusKind"><span class="status-pill-dot"></span>{{ statusTitle }}</span>
        <span class="hero-state-label">{{ settings.apiKeySet ? `密钥 ${settings.apiKeyMasked}` : '尚未配置密钥' }}</span>
      </div>
    </header>

    <div class="summary-grid">
      <div class="summary-item summary-primary">
        <span class="summary-icon"><Cpu :size="18" /></span>
        <div><span class="summary-label">当前模型</span><strong>{{ form.model || '未配置' }}</strong><small>{{ providerKey === 'custom' ? '自定义服务' : (providers.find(p => p.key === providerKey)?.name || '等待选择服务商') }}</small></div>
      </div>
      <div class="summary-item">
        <span class="summary-icon purple"><Sparkles :size="18" /></span>
        <div><span class="summary-label">已启用能力</span><strong>{{ activeFeatureCount }} 项</strong><small>主模型与媒体服务</small></div>
      </div>
      <div class="summary-item">
        <span class="summary-icon green"><FileText :size="18" /></span>
        <div><span class="summary-label">文档解析</span><strong>{{ form.mineruMode === 'cloud' ? 'MinerU 云端' : '自部署服务' }}</strong><small>{{ parserEnabled ? '已启用' : '待配置' }}</small></div>
      </div>
      <div class="summary-item">
        <span class="summary-icon amber"><Gauge :size="18" /></span>
        <div><span class="summary-label">每日用量</span><strong>{{ form.dailyRunsPerUser ? `${form.dailyRunsPerUser} 次` : '不限' }}</strong><small>单个账号上限</small></div>
      </div>
    </div>

    <div class="settings-shell">
      <aside class="settings-nav">
        <div class="nav-caption">配置目录</div>
        <button v-for="item in sectionItems" :key="item.key" type="button" class="settings-nav-item" :class="{ active: activeSection === item.key }" @click="activeSection = item.key">
          <span class="nav-item-icon" :class="item.tone"><component :is="item.icon" :size="17" /></span>
          <span class="nav-item-copy"><b>{{ item.title }}</b><small>{{ item.desc }}</small></span>
          <ChevronRight :size="15" class="nav-chevron" />
        </button>
        <div class="nav-help"><span class="nav-help-icon"><Info :size="15" /></span><div><b>配置提示</b><p>后台设置优先于环境变量，修改后可用“测试连接”确认。</p></div></div>
      </aside>

      <main class="settings-content">
        <template v-if="activeSection === 'model'">
          <div class="section-heading"><div><span class="section-kicker">01 / 主模型</span><h2>连接模型服务</h2><p>选择服务商或填写任意 OpenAI 兼容接口，供 AI 助手、学习规划和成果预评审使用。</p></div><el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="停用" @change="saveField('enabled')" /></div>
          <div class="card block-card">
            <div class="block-head"><div><h3>服务商预设</h3><p class="sub">点击预设会自动填入接口地址和推荐模型。</p></div><span class="soft-badge"><Plug :size="13" /> OpenAI Compatible</span></div>
            <div class="providers">
              <button v-for="p in providers" :key="p.key" type="button" class="provider" :class="{ active: providerKey === p.key }" @click="applyPreset(p.key)">
                <span class="pv-logo" :style="{ background: p.bg }"><component :is="p.icon" :size="18" /></span>
                <span class="pv-text"><b class="pv-name">{{ p.name }}</b><small class="pv-desc">{{ p.desc }}</small></span>
                <span v-if="providerKey === p.key" class="pv-check"><Check :size="13" /></span>
              </button>
            </div>
            <div class="form-grid">
              <div class="field span-2"><label>接口地址 <b>*</b></label><el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com" /><div class="hint">填写服务基础地址，不要包含 /chat/completions；通义千问使用 compatible-mode 地址。</div></div>
              <div class="field"><label>模型名称 <b>*</b></label><el-input v-model="form.model" placeholder="deepseek-chat"><template #suffix><el-dropdown trigger="click" @command="(m) => (form.model = m)"><span class="model-pick">常用模型 <ChevronDown :size="13" /></span><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="m in modelSuggestions" :key="m" :command="m">{{ m }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-input></div>
              <div class="field"><label>API Key</label><el-input v-model="form.apiKey" type="password" show-password :placeholder="settings.apiKeySet ? `已配置 ${settings.apiKeyMasked}，留空保持不变` : '粘贴服务商密钥'" /><div class="hint">密钥只保存在服务端，前端只显示掩码。</div></div>
            </div>
          </div>
        </template>

        <template v-else-if="activeSection === 'generation'">
          <div class="section-heading"><div><span class="section-kicker">02 / 生成策略</span><h2>控制回复表现</h2><p>影响回复长度、稳定性、响应速度与每日使用成本，不确定时保持推荐值即可。</p></div><span class="section-side-note"><SlidersHorizontal :size="15" /> 当前模型 {{ form.model || '未选择' }}</span></div>
          <div class="card block-card">
            <div class="block-head"><div><h3>输出与稳定性</h3><p class="sub">为不同模型设置统一的输出边界。</p></div><span class="soft-badge blue">推荐设置</span></div>
            <div class="form-grid">
              <div class="field"><label>输出 Token 上限 <span class="field-value">{{ form.maxTokens }}</span></label><el-input-number v-model="form.maxTokens" :min="200" :max="8000" :step="100" style="width:100%" /><div class="hint">单次回复最大长度，多项成果评审建议 6000–8000。</div></div>
              <div class="field"><label>温度 <span class="field-value">{{ form.temperature }}</span></label><el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" :marks="{ 0: '稳定', 0.7: '推荐', 2: '发散' }" class="temp" /><div class="hint">越低越稳定，越高越有创造性，推荐 0.3–0.7。</div></div>
              <div class="field"><label>连接超时 <span class="field-unit">毫秒</span></label><el-input-number v-model="form.connectTimeoutMs" :min="1000" :max="30000" :step="500" style="width:100%" /><div class="hint">建立连接的最长等待时间。</div></div>
              <div class="field"><label>读取超时 <span class="field-unit">毫秒</span></label><el-input-number v-model="form.readTimeoutMs" :min="3000" :max="120000" :step="1000" style="width:100%" /><div class="hint">等待模型回复的最长时间，推理模型可调大。</div></div>
              <div class="field span-2 limit-field"><label>每人每日使用次数上限 <span class="field-value">{{ form.dailyRunsPerUser ? `${form.dailyRunsPerUser} 次` : '不限' }}</span></label><el-input-number v-model="form.dailyRunsPerUser" :min="0" :max="10000" :step="10" style="width:100%" /><div class="hint">0 表示不限。该限制只针对本站账号，硬件设计助手也使用这里的额度。</div></div>
            </div>
          </div>
          <div class="callout"><span class="callout-icon"><ShieldCheck :size="16" /></span><div><b>安全提示</b><p>上下文会由系统自动裁剪。你只需要关注输出上限和每日额度，避免因为过高参数造成不必要的调用成本。</p></div></div>
        </template>

        <template v-else-if="activeSection === 'media'">
          <div class="section-heading"><div><span class="section-kicker">03 / 多媒体服务</span><h2>扩展识别能力</h2><p>为成果图片识别和视频语音转文字分别配置服务，主模型不可用时不会自动切换。</p></div><span class="section-side-note"><Images :size="15" /> {{ enabledMediaCount }} 项已启用</span></div>
          <div v-for="service in mediaServicesOnly" :key="service.key" class="card service-card">
            <div class="service-head"><div class="service-title"><span class="service-icon" :class="service.tone"><component :is="service.icon" :size="18" /></span><div><h3>{{ service.label }}</h3><p>{{ service.description }}</p></div></div><el-switch v-model="form[service.key + 'Enabled']" inline-prompt active-text="启用" inactive-text="停用" /></div>
            <div class="service-fields form-grid"><div class="field span-2"><label>服务地址</label><el-input v-model="form[service.key + 'BaseUrl']" :placeholder="service.placeholder" /></div><div class="field"><label>模型名称</label><el-input v-model="form[service.key + 'Model']" placeholder="填写服务商提供的模型名称" /></div><div class="field"><label>API Key</label><el-input v-model="form[service.key + 'ApiKey']" type="password" show-password :placeholder="settings[service.key + 'ApiKeySet'] ? '已配置，留空保持不变' : '填写密钥'" /></div></div>
          </div>
          <div class="callout muted-callout"><span class="callout-icon"><Info :size="16" /></span><div><b>独立配置</b><p>图片和语音服务的地址、密钥分别保存。只在启用并完成配置后，相关能力才会出现在成果评审流程中。</p></div></div>
        </template>

        <template v-else>
          <div class="section-heading"><div><span class="section-kicker">04 / 文档解析</span><h2>选择报告解析方式</h2><p>PDF、Word 等学习报告会使用这里选择的解析服务，切换后立即生效。</p></div><span class="section-side-note"><FileText :size="15" /> 当前商户</span></div>
          <div class="card block-card">
            <div class="block-head"><div><h3>解析服务</h3><p class="sub">两套服务独立保存配置，系统只会调用当前选择的一套。</p></div><span class="soft-badge green"><CircleCheckBig :size="13" /> {{ parserEnabled ? '已配置' : '待配置' }}</span></div>
            <div class="parser-options"><button type="button" :class="{ selected: form.mineruMode === 'cloud' }" :aria-pressed="form.mineruMode === 'cloud'" @click="form.mineruMode = 'cloud'"><span class="choice-icon cloud"><Cloud :size="18" /></span><span><b>MinerU 官方 API</b><small>使用官方账号与 Token</small></span><Check v-if="form.mineruMode === 'cloud'" :size="16" class="choice-check" /></button><button type="button" :class="{ selected: form.mineruMode === 'selfhost' }" :aria-pressed="form.mineruMode === 'selfhost'" @click="form.mineruMode = 'selfhost'"><span class="choice-icon server"><Server :size="18" /></span><span><b>自部署 MinerU</b><small>连接自己的解析服务</small></span><Check v-if="form.mineruMode === 'selfhost'" :size="16" class="choice-check" /></button></div>
            <p class="hint parser-hint">服务不可用时不会自动发送到另一服务，请先在下方完成当前模式的地址和密钥配置。</p>
          </div>
          <div v-for="service in parserServices" :key="service.key" class="card service-card">
            <div class="service-head"><div class="service-title"><span class="service-icon" :class="service.tone"><component :is="service.icon" :size="18" /></span><div><h3>{{ service.label }}</h3><p>{{ service.description }}</p></div></div><el-switch v-model="form[service.key + 'Enabled']" inline-prompt active-text="启用" inactive-text="停用" /></div>
            <div class="service-fields form-grid"><div class="field span-2"><label>服务地址</label><el-input v-model="form[service.key + 'BaseUrl']" :placeholder="service.placeholder" /></div><div class="field"><label>{{ service.key === 'mineruLocal' ? '解析层级' : '解析模型版本' }}</label><el-input v-model="form[service.key + 'Model']" :placeholder="service.key === 'mineruLocal' ? 'standard' : 'vlm'" /></div><div class="field"><label>Token</label><el-input v-model="form[service.key + 'ApiKey']" type="password" show-password :placeholder="settings[service.key + 'ApiKeySet'] ? '已配置，留空保持不变' : '填写 Token'" /></div></div>
          </div>
        </template>

        <div class="action-dock">
          <div class="action-dock-copy"><span class="action-dot"></span><div><b>配置修改后立即生效</b><small>保存后可在模型服务商处查看调用状态</small></div></div>
          <div class="actions-left"><el-button size="large" :loading="testing" @click="test"><Zap :size="15" />测试连接</el-button><el-button size="large" type="primary" :loading="saving" @click="save"><Save :size="15" />保存配置</el-button></div>
        </div>
        <div v-if="testResult" class="test-result" :class="testResult.ok ? 'ok' : 'fail'"><CircleCheckBig v-if="testResult.ok" :size="16" /><CircleX v-else :size="16" />{{ testResult.ok ? `连接成功，往返 ${testResult.latencyMs} ms` : testResult.error }}</div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Bot, Check, ChevronDown, ChevronRight, CircleCheckBig, CircleX, Cloud, Cpu, FileText, Gauge, Images, Mic2,
  Info, Plug, Save, Server, ShieldCheck, SlidersHorizontal, Sparkles, Zap
} from 'lucide-vue-next'
import { adminGetAiSettings, adminTestAiSettings, adminUpdateAiSettings } from '../../api'

const settings = ref({})
const saving = ref(false)
const testing = ref(false)
const testResult = ref(null)
const activeSection = ref('model')

const sectionItems = [
  { key: 'model', title: '主模型接入', desc: '服务商、模型与密钥', icon: Plug, tone: 'blue' },
  { key: 'generation', title: '生成策略', desc: '长度、稳定性与额度', icon: SlidersHorizontal, tone: 'purple' },
  { key: 'media', title: '多媒体服务', desc: '图片识别与语音转写', icon: Images, tone: 'green' },
  { key: 'parser', title: '文档解析', desc: 'PDF 与 Word 报告', icon: FileText, tone: 'amber' }
]

const mediaServices = [
  { key: 'vision', label: '成果图片识别', description: '分析实物图片和视频抽样画面，使用支持图片输入的 OpenAI 兼容接口。', placeholder: 'https://服务商地址/v1', icon: Images, tone: 'purple' },
  { key: 'speech', label: '视频语音转文字', description: '使用兼容 /audio/transcriptions 的服务返回文字，与图片识别独立配置。', placeholder: 'https://服务商地址/v1', icon: Mic2, tone: 'blue' },
  { key: 'mineruCloud', label: 'MinerU 官方 API', description: '解析 PDF、Word 报告，支持表格与公式提取，版本填写 vlm 或 pipeline。', placeholder: 'https://mineru.net', icon: Cloud, tone: 'green' },
  { key: 'mineruLocal', label: '自部署 MinerU', description: '连接 MinerU 4.x V1 HTTP API，匿名内网服务可留空密钥。', placeholder: 'http://你的MinerU服务器:8000', icon: Server, tone: 'amber' }
]
const form = reactive({
  enabled: true, baseUrl: '', model: '', apiKey: '', maxTokens: 2000, temperature: 0.4, connectTimeoutMs: 3000, readTimeoutMs: 20000,
  dailyRunsPerUser: 50, mineruMode: 'cloud',
  ...Object.fromEntries(mediaServices.flatMap(s => [[s.key + 'Enabled', false], [s.key + 'BaseUrl', s.key === 'mineruCloud' ? 'https://mineru.net' : ''], [s.key + 'Model', s.key === 'mineruCloud' ? 'vlm' : s.key === 'mineruLocal' ? 'standard' : ''], [s.key + 'ApiKey', '']]))
})
const mediaServicesOnly = computed(() => mediaServices.filter((service) => !service.key.startsWith('mineru')))
const parserServices = computed(() => mediaServices.filter((service) => service.key === (form.mineruMode === 'cloud' ? 'mineruCloud' : 'mineruLocal')))

const providers = [
  { key: 'deepseek', name: 'DeepSeek', desc: '性价比高，默认推荐', icon: Cpu, bg: 'linear-gradient(135deg,#4f46e5,#7c3aed)', baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat', models: ['deepseek-chat', 'deepseek-reasoner'] },
  { key: 'qwen', name: '通义千问', desc: '阿里云 DashScope 兼容模式', icon: Cloud, bg: 'linear-gradient(135deg,#f97316,#ef4444)', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode', model: 'qwen-plus', models: ['qwen-plus', 'qwen-turbo', 'qwen-max'] },
  { key: 'custom', name: '自定义', desc: '任意 OpenAI 兼容服务', icon: SlidersHorizontal, bg: 'linear-gradient(135deg,#64748b,#334155)', models: ['gpt-4o-mini', 'glm-4-flash', 'moonshot-v1-8k'] }
]
const providerKey = computed(() => providers.find((p) => p.baseUrl && form.baseUrl && form.baseUrl.replace(/\/$/, '') === p.baseUrl)?.key || (form.baseUrl ? 'custom' : ''))
const modelSuggestions = computed(() => providers.find((p) => p.key === providerKey.value)?.models || providers[2].models)

const statusKind = computed(() => !form.enabled ? 'off' : settings.value.apiKeySet ? 'on' : 'warn')
const statusTitle = computed(() => !form.enabled ? 'AI 已关闭' : settings.value.apiKeySet ? 'AI 运行中' : '缺少 API Key')
const statusDesc = computed(() => !form.enabled
  ? '所有 AI 功能停用,学习规划与预评审退回规则匹配。'
  : settings.value.apiKeySet ? '模型接口已配置,各 AI 功能正常工作。' : '已启用但没有密钥,当前以规则匹配模式降级运行。')
const activeFeatureCount = computed(() => [form.enabled, form.visionEnabled, form.speechEnabled, form.mineruCloudEnabled, form.mineruLocalEnabled].filter(Boolean).length)
const enabledMediaCount = computed(() => [form.visionEnabled, form.speechEnabled].filter(Boolean).length)
const parserEnabled = computed(() => form[form.mineruMode === 'cloud' ? 'mineruCloudEnabled' : 'mineruLocalEnabled'])

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
  form.mineruMode = d.mineruMode || 'cloud'
  for (const service of mediaServices) for (const field of ['Enabled', 'BaseUrl', 'Model']) {
    if (d[service.key + field] != null) form[service.key + field] = d[service.key + field]
  }
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
      dailyRunsPerUser: form.dailyRunsPerUser ?? 0, mineruMode: form.mineruMode,
      ...Object.fromEntries(mediaServices.flatMap(s => ['Enabled', 'BaseUrl', 'Model', 'ApiKey'].map(field => [s.key + field, form[s.key + field]])))
    })
    form.apiKey = ''
    mediaServices.forEach(s => { form[s.key + 'ApiKey'] = '' })
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
.parser-options { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 12px; }.parser-options button { text-align: left; font: inherit; border: 1.5px solid var(--border); border-radius: 12px; padding: 16px; background: #fff; cursor: pointer; transition: background .15s, border-color .15s; }.parser-options button.selected { background: #eff6ff; border-color: var(--brand-blue); box-shadow: 0 0 0 3px #dbeafe; }.parser-options b { display: block; font-size: 14px; color: var(--text-main); }.parser-options span { display: block; margin-top: 6px; font-size: 12px; color: var(--text-secondary); }.media-card .card-head { gap: 16px; }.media-card .el-switch { flex-shrink: 0; }.parser-card .hint { margin-bottom: 0; }.actions { position: sticky; bottom: 12px; z-index: 5; border: 1px solid var(--border); box-shadow: var(--shadow-lg); }
@media(max-width:600px) { .parser-options { grid-template-columns: 1fr; }.media-card .card-head { flex-wrap: wrap; }.actions { position: static; }.actions-left { flex-wrap: wrap; } }
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

/* 分区式设置页 */
.settings-page { max-width: 1320px; margin: 0 auto; color: #111827; }
.settings-hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 4px 2px 22px; }
.hero-copy { min-width: 0; }
.eyebrow { display: flex; align-items: center; gap: 7px; color: #7c3aed; font-size: 12px; font-weight: 700; letter-spacing: .03em; }
.eyebrow-icon { display: grid; place-items: center; width: 24px; height: 24px; border-radius: 8px; color: #fff; background: linear-gradient(135deg, #6366f1, #a855f7); }
.eyebrow b { color: #374151; }
.settings-hero h1 { margin: 10px 0 5px; font-size: 28px; letter-spacing: -.03em; }
.settings-hero p { margin: 0; color: #6b7280; font-size: 13px; line-height: 1.6; }
.hero-state { display: flex; align-items: center; gap: 12px; flex-shrink: 0; padding-bottom: 2px; }
.status-pill { display: inline-flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 999px; background: #ecfdf5; color: #047857; font-size: 12px; font-weight: 700; }
.status-pill.warn { background: #fffbeb; color: #b45309; }
.status-pill.off { background: #f3f4f6; color: #6b7280; }
.status-pill-dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; box-shadow: 0 0 0 4px color-mix(in srgb, currentColor 14%, transparent); }
.hero-state-label { color: #9ca3af; font-size: 12px; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 18px; }
.summary-item { display: flex; align-items: center; gap: 11px; min-width: 0; padding: 14px 15px; border: 1px solid #e8eaf0; border-radius: 14px; background: rgba(255,255,255,.84); box-shadow: 0 5px 16px rgba(15,23,42,.035); }
.summary-icon { display: grid; place-items: center; width: 36px; height: 36px; flex-shrink: 0; border-radius: 11px; color: #2563eb; background: #eff6ff; }
.summary-icon.purple { color: #7c3aed; background: #f5f3ff; }.summary-icon.green { color: #059669; background: #ecfdf5; }.summary-icon.amber { color: #d97706; background: #fffbeb; }
.summary-item div { min-width: 0; }.summary-label, .summary-item small { display: block; color: #9ca3af; font-size: 11px; }.summary-item strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin: 2px 0; font-size: 15px; }.summary-item small { color: #6b7280; font-size: 11px; }
.settings-shell { display: grid; grid-template-columns: 224px minmax(0, 1fr); gap: 18px; align-items: start; }
.settings-nav { position: sticky; top: 0; display: flex; flex-direction: column; gap: 5px; padding: 12px 8px 10px; border: 1px solid #e8eaf0; border-radius: 17px; background: rgba(255,255,255,.76); box-shadow: 0 5px 18px rgba(15,23,42,.035); }
.nav-caption { padding: 2px 10px 8px; color: #9ca3af; font-size: 10px; font-weight: 700; letter-spacing: .14em; }
.settings-nav-item { display: flex; align-items: center; width: 100%; gap: 10px; padding: 10px; border: 0; border-radius: 12px; background: transparent; color: #4b5563; text-align: left; cursor: pointer; transition: background .16s, color .16s, transform .16s; }
.settings-nav-item:hover { background: #f8fafc; transform: translateX(2px); }.settings-nav-item.active { color: #1e40af; background: #eff6ff; box-shadow: inset 3px 0 #2563eb; }
.nav-item-icon { display: grid; place-items: center; width: 32px; height: 32px; flex-shrink: 0; border-radius: 10px; color: #2563eb; background: #dbeafe; }.nav-item-icon.purple { color: #7c3aed; background: #ede9fe; }.nav-item-icon.green { color: #059669; background: #d1fae5; }.nav-item-icon.amber { color: #d97706; background: #fef3c7; }
.nav-item-copy { display: grid; gap: 2px; min-width: 0; flex: 1; }.nav-item-copy b { font-size: 13px; }.nav-item-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #9ca3af; font-size: 11px; }.settings-nav-item.active .nav-item-copy small { color: #60a5fa; }.nav-chevron { color: #cbd5e1; }.settings-nav-item.active .nav-chevron { color: #60a5fa; }
.nav-help { display: flex; gap: 8px; margin: 14px 4px 0; padding: 11px 10px; border-top: 1px solid #eef0f4; color: #6b7280; }.nav-help-icon { display: grid; place-items: center; color: #2563eb; }.nav-help b { color: #374151; font-size: 11px; }.nav-help p { margin: 4px 0 0; font-size: 11px; line-height: 1.5; }
.settings-content { min-width: 0; }.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; min-height: 67px; margin: 2px 2px 13px; }.section-kicker { display: block; margin-bottom: 5px; color: #2563eb; font-size: 11px; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; }.section-heading h2 { margin: 0 0 5px; font-size: 19px; letter-spacing: -.02em; }.section-heading p { margin: 0; color: #6b7280; font-size: 13px; line-height: 1.55; }.section-heading > .el-switch { margin-top: 9px; }.section-side-note { display: inline-flex; align-items: center; gap: 6px; padding: 7px 10px; border-radius: 999px; background: #f8fafc; color: #64748b; font-size: 11px; white-space: nowrap; }
.block-card, .service-card { border: 1px solid #e8eaf0; box-shadow: 0 5px 18px rgba(15,23,42,.035); }.block-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 14px; margin-bottom: 18px; }.block-head h3 { margin: 0 0 4px; font-size: 15px; }.soft-badge { display: inline-flex; align-items: center; gap: 5px; padding: 5px 9px; border-radius: 999px; color: #7c3aed; background: #f5f3ff; font-size: 11px; font-weight: 700; white-space: nowrap; }.soft-badge.blue { color: #2563eb; background: #eff6ff; }.soft-badge.green { color: #059669; background: #ecfdf5; }
.providers { margin-bottom: 22px; }.provider { min-height: 68px; border-color: #e5e7eb; text-align: left; }.provider:hover { border-color: #a5b4fc; background: #fafaff; }.provider.active { border-color: #6366f1; background: #f5f3ff; box-shadow: 0 0 0 3px #ede9fe; }.pv-logo { width: 38px; height: 38px; }.pv-text { min-width: 0; }.pv-name { display: block; }.pv-desc { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.pv-check { width: 19px; height: 19px; top: 9px; right: 9px; }
.form-grid { gap: 20px 18px; }.field label { color: #374151; }.field-value { margin-left: auto; color: #2563eb; font-variant-numeric: tabular-nums; }.field-unit { margin-left: auto; color: #9ca3af; font-size: 11px; font-weight: 400; }.hint { color: #9ca3af; }.model-pick { display: inline-flex; align-items: center; gap: 2px; }.temp { padding: 0 8px 14px; }
.callout { display: flex; gap: 10px; align-items: flex-start; margin-top: 12px; padding: 12px 14px; border: 1px solid #dbeafe; border-radius: 13px; background: #f8fbff; color: #475569; }.callout-icon { display: grid; place-items: center; width: 26px; height: 26px; flex-shrink: 0; border-radius: 8px; color: #2563eb; background: #dbeafe; }.callout b { color: #1e3a8a; font-size: 12px; }.callout p { margin: 3px 0 0; font-size: 12px; line-height: 1.6; }.muted-callout { border-color: #e5e7eb; background: #fafafa; }.muted-callout .callout-icon { color: #64748b; background: #f1f5f9; }.muted-callout b { color: #475569; }
.service-card { margin-bottom: 12px; padding: 20px 22px; }.service-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; margin-bottom: 18px; }.service-title { display: flex; align-items: flex-start; gap: 11px; min-width: 0; }.service-icon { display: grid; place-items: center; width: 36px; height: 36px; flex-shrink: 0; border-radius: 11px; color: #2563eb; background: #dbeafe; }.service-icon.purple { color: #7c3aed; background: #ede9fe; }.service-icon.green { color: #059669; background: #d1fae5; }.service-icon.amber { color: #d97706; background: #fef3c7; }.service-title h3 { margin: 1px 0 4px; font-size: 15px; }.service-title p { margin: 0; color: #6b7280; font-size: 12px; line-height: 1.55; }.service-head .el-switch { flex-shrink: 0; }
.parser-options { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 10px; }.parser-options button { display: flex; align-items: center; gap: 11px; position: relative; min-height: 74px; padding: 13px 15px; border: 1.5px solid #e5e7eb; border-radius: 13px; background: #fff; text-align: left; cursor: pointer; transition: border-color .15s, background .15s, box-shadow .15s; }.parser-options button:hover { border-color: #93c5fd; }.parser-options button.selected { border-color: #2563eb; background: #eff6ff; box-shadow: 0 0 0 3px #dbeafe; }.choice-icon { display: grid; place-items: center; width: 34px; height: 34px; flex-shrink: 0; border-radius: 10px; color: #2563eb; background: #dbeafe; }.choice-icon.server { color: #d97706; background: #fef3c7; }.parser-options b { display: block; color: #111827; font-size: 13px; }.parser-options small { display: block; margin-top: 4px; color: #6b7280; font-size: 11px; }.choice-check { position: absolute; top: 11px; right: 11px; color: #2563eb; }.parser-hint { margin: 0; }
.action-dock { position: sticky; bottom: 10px; z-index: 5; display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 18px; padding: 12px 14px; border: 1px solid #dbe3f0; border-radius: 15px; background: rgba(255,255,255,.94); box-shadow: 0 12px 28px rgba(15,23,42,.10); backdrop-filter: blur(12px); }.action-dock-copy { display: flex; align-items: center; gap: 9px; }.action-dot { width: 8px; height: 8px; border-radius: 50%; background: #22c55e; box-shadow: 0 0 0 4px #dcfce7; }.action-dock-copy b, .action-dock-copy small { display: block; }.action-dock-copy b { font-size: 12px; }.action-dock-copy small { margin-top: 2px; color: #9ca3af; font-size: 11px; }.actions-left { display: flex; gap: 8px; }.actions-left .el-button { display: inline-flex; align-items: center; gap: 6px; }.test-result { display: flex; align-items: center; justify-content: flex-end; gap: 6px; margin: 9px 2px 0; font-size: 12px; font-weight: 600; }.test-result.ok { color: #16a34a; }.test-result.fail { color: #dc2626; }
@media (max-width: 1050px) { .summary-grid { grid-template-columns: repeat(2, 1fr); }.settings-shell { grid-template-columns: 190px minmax(0, 1fr); } }
@media (max-width: 760px) { .settings-hero { align-items: flex-start; flex-direction: column; }.hero-state { padding: 0; }.summary-grid { grid-template-columns: 1fr 1fr; }.settings-shell { grid-template-columns: 1fr; }.settings-nav { position: static; display: grid; grid-template-columns: 1fr 1fr; padding: 10px; }.nav-caption, .nav-help { grid-column: 1 / -1; }.nav-help { margin-top: 6px; }.section-heading { min-height: 0; }.action-dock { align-items: flex-start; flex-direction: column; }.actions-left { width: 100%; }.actions-left .el-button { flex: 1; }.parser-options { grid-template-columns: 1fr; } }
@media (max-width: 520px) { .summary-grid { grid-template-columns: 1fr; }.settings-nav { grid-template-columns: 1fr; }.nav-caption, .nav-help { grid-column: auto; }.form-grid { grid-template-columns: 1fr; }.field.span-2 { grid-column: auto; }.service-card { padding: 16px; }.service-head { flex-direction: column; }.service-head .el-switch { align-self: flex-end; } }
</style>
