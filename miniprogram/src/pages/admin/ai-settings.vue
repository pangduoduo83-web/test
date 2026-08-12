<template>
  <view class="page">
    <view class="card">
      <view class="head-row">
        <view class="head-text">
          <text class="title">AI 模型设置</text>
          <text class="sub muted">保存后立即生效,无需重启;未配置时前台自动降级为智能匹配</text>
        </view>
        <switch :checked="form.enabled" color="#2563eb" @change="toggleEnabled" />
      </view>

      <view class="status-tip" :class="settings.apiKeySet ? 'ok' : 'warn'">
        {{ settings.apiKeySet
          ? `已配置 API Key(${settings.apiKeyMasked},来源:${settings.apiKeySource === 'DB' ? '后台设置' : '环境变量'})`
          : '尚未配置 API Key,AI 功能当前降级运行' }}
      </view>

      <text class="field-label">服务商快捷选择</text>
      <view class="pill-row">
        <view class="pill" :class="{ active: preset === 'deepseek' }" @click="applyPreset('deepseek')">DeepSeek</view>
        <view class="pill" :class="{ active: preset === 'qwen' }" @click="applyPreset('qwen')">通义千问</view>
        <view class="pill" :class="{ active: preset === 'custom' }" @click="preset = 'custom'">自定义</view>
      </view>

      <text class="field-label">接口地址</text>
      <input v-model="form.baseUrl" class="field-input" placeholder="https://api.deepseek.com" placeholder-class="ph" />

      <text class="field-label">模型名称</text>
      <input v-model="form.model" class="field-input" placeholder="deepseek-chat" placeholder-class="ph" />

      <text class="field-label">API Key</text>
      <input
        v-model="form.apiKey"
        class="field-input"
        password
        :placeholder="settings.apiKeySet ? '已配置(留空保持不变)' : '粘贴 sk- 开头的密钥'"
        placeholder-class="ph"
      />

      <text class="field-label">输出 Token 上限:{{ form.maxTokens }}</text>
      <slider
        :value="form.maxTokens"
        :min="200"
        :max="8000"
        :step="100"
        activeColor="#2563eb"
        backgroundColor="#e5e7eb"
        block-size="22"
        @change="form.maxTokens = $event.detail.value"
      />
      <text class="hint muted">单次回复最大长度,影响详细程度与费用,建议 1500~2500</text>

      <text class="field-label">温度(随机性):{{ form.temperature }}</text>
      <slider
        :value="form.temperature * 10"
        :min="0"
        :max="20"
        :step="1"
        activeColor="#2563eb"
        backgroundColor="#e5e7eb"
        block-size="22"
        @change="form.temperature = $event.detail.value / 10"
      />
      <text class="hint muted">越低越稳定,推荐 0.3~0.7</text>

      <view class="field-row">
        <view class="grow">
          <text class="field-label">连接超时(ms)</text>
          <input v-model="form.connectTimeoutMs" type="number" class="field-input" placeholder="3000" placeholder-class="ph" />
        </view>
        <view class="grow">
          <text class="field-label">读取超时(ms)</text>
          <input v-model="form.readTimeoutMs" type="number" class="field-input" placeholder="20000" placeholder-class="ph" />
        </view>
      </view>

      <button class="btn-gradient save-btn" :disabled="saving" @click="save">
        {{ saving ? '保存中...' : '保存配置' }}
      </button>
      <button class="btn-plain" :disabled="testing" @click="test">
        {{ testing ? '测试中...' : '测试连接' }}
      </button>
      <view v-if="testResult" class="test-result" :class="testResult.ok ? 'ok' : 'fail'">
        {{ testResult.ok ? `✓ 连接成功,延迟 ${testResult.latencyMs}ms(${testResult.model})` : `✗ ${testResult.error}` }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { adminGetAiSettings, adminUpdateAiSettings, adminTestAiSettings } from '@/api'

const settings = ref({})
const preset = ref('custom')
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
  connectTimeoutMs: '3000',
  readTimeoutMs: '20000'
})

const presets = {
  deepseek: { baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat' },
  qwen: { baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode', model: 'qwen-plus' }
}

const applyPreset = (key) => {
  preset.value = key
  form.baseUrl = presets[key].baseUrl
  form.model = presets[key].model
}

const load = async () => {
  try {
    const d = await adminGetAiSettings()
    settings.value = d
    form.enabled = d.enabled
    form.baseUrl = d.baseUrl
    form.model = d.model
    form.maxTokens = d.maxTokens
    form.temperature = d.temperature
    form.connectTimeoutMs = String(d.connectTimeoutMs)
    form.readTimeoutMs = String(d.readTimeoutMs)
    form.apiKey = ''
  } catch (e) {
    // 已提示
  }
}

onLoad(load)

const toggleEnabled = async (e) => {
  form.enabled = e.detail.value
  try {
    settings.value = await adminUpdateAiSettings({ enabled: form.enabled })
    uni.showToast({ title: form.enabled ? 'AI 已启用' : 'AI 已停用,前台自动降级', icon: 'none' })
  } catch (err) {
    // 已提示
  }
}

const save = async () => {
  saving.value = true
  try {
    const d = await adminUpdateAiSettings({
      enabled: form.enabled,
      baseUrl: form.baseUrl.trim(),
      model: form.model.trim(),
      apiKey: form.apiKey.trim() || undefined,
      maxTokens: form.maxTokens,
      temperature: form.temperature,
      connectTimeoutMs: parseInt(form.connectTimeoutMs, 10) || 3000,
      readTimeoutMs: parseInt(form.readTimeoutMs, 10) || 20000
    })
    settings.value = d
    form.apiKey = ''
    testResult.value = null
    uni.showToast({ title: '已保存,立即生效', icon: 'success' })
  } catch (e) {
    // 已提示
  } finally {
    saving.value = false
  }
}

const test = async () => {
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await adminTestAiSettings()
  } catch (e) {
    // 已提示
  } finally {
    testing.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx 24rpx 60rpx;
}

.head-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20rpx;
}

.head-text {
  flex: 1;
}

.title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
}

.sub {
  display: block;
  font-size: 23rpx;
  margin-top: 8rpx;
}

.status-tip {
  margin-top: 24rpx;
  border-radius: 14rpx;
  padding: 16rpx 22rpx;
  font-size: 24rpx;

  &.ok {
    background: $green-bg;
    color: $green;
  }

  &.warn {
    background: $yellow-bg;
    color: $yellow;
  }
}

.field-label {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  margin: 28rpx 0 12rpx;
}

.pill-row {
  display: flex;
  gap: 14rpx;
}

.field-input {
  background: $gray-bg;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  font-size: 27rpx;
  height: 84rpx;
  box-sizing: border-box;
  width: 100%;
}

.ph {
  color: $text-light;
}

.hint {
  display: block;
  font-size: 22rpx;
  margin-top: 6rpx;
}

.field-row {
  display: flex;
  gap: 24rpx;

  .grow {
    flex: 1;
  }
}

.save-btn {
  margin: 36rpx 0 20rpx;
}

.test-result {
  margin-top: 20rpx;
  border-radius: 14rpx;
  padding: 16rpx 22rpx;
  font-size: 24rpx;

  &.ok {
    background: $green-bg;
    color: $green;
  }

  &.fail {
    background: $red-bg;
    color: $red;
  }
}
</style>
