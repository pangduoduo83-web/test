<template>
  <view class="page">
    <!-- 步骤条 -->
    <view class="steps card">
      <view v-for="(s, i) in steps" :key="i" class="step-item">
        <view class="step-dot" :class="{ active: step >= i, done: step > i }">
          {{ step > i ? '✓' : i + 1 }}
        </view>
        <text class="step-label" :class="{ active: step >= i }">{{ s }}</text>
        <view v-if="i < steps.length - 1" class="step-line" :class="{ active: step > i }" />
      </view>
    </view>

    <!-- 设备信息卡 -->
    <view v-if="equip && step < 2" class="card equip-brief">
      <view class="eb-icon">{{ equip.icon || '🔧' }}</view>
      <view class="eb-info">
        <text class="eb-name">{{ equip.name }}</text>
        <text class="eb-model">{{ equip.model }} · 可借 {{ equip.availableCount }} 台 · {{ equip.location }}</text>
      </view>
    </view>

    <!-- 第一步:填写申请 -->
    <view v-if="step === 0" class="card form-card">
      <view class="field">
        <text class="field-label">借用数量 <text class="req">*</text></text>
        <view class="qty-row">
          <view class="qty-btn" :class="{ disabled: form.quantity <= 1 }" @click="changeQty(-1)">−</view>
          <text class="qty-num">{{ form.quantity }}</text>
          <view class="qty-btn" :class="{ disabled: form.quantity >= maxQty }" @click="changeQty(1)">+</view>
          <text class="qty-tip muted">最多 {{ maxQty }} 台</text>
        </view>
      </view>

      <view class="field">
        <text class="field-label">使用目的 <text class="req">*</text></text>
        <picker mode="selector" :range="purposes" @change="form.purpose = purposes[$event.detail.value]">
          <view class="field-input picker" :class="{ placeholder: !form.purpose }">
            {{ form.purpose || '请选择使用目的' }}
          </view>
        </picker>
      </view>

      <view class="field">
        <text class="field-label">关联项目(选填)</text>
        <input v-model="form.projectName" class="field-input" placeholder="如:STM32核心板设计" placeholder-class="ph" />
      </view>

      <view class="field-row">
        <view class="field grow">
          <text class="field-label">开始日期 <text class="req">*</text></text>
          <picker mode="date" :value="form.startDate" :start="todayStr" @change="form.startDate = $event.detail.value">
            <view class="field-input picker">{{ form.startDate }}</view>
          </picker>
        </view>
        <view class="field grow">
          <text class="field-label">借用时长</text>
          <picker
            mode="selector"
            :range="durationLabels"
            @change="form.durationDays = durations[$event.detail.value]"
          >
            <view class="field-input picker">{{ durationLabel }}</view>
          </picker>
        </view>
      </view>

      <view class="field">
        <text class="field-label">备注说明</text>
        <textarea
          v-model="form.remark"
          class="field-textarea"
          placeholder="请描述具体的使用计划和需求..."
          placeholder-class="ph"
          :maxlength="200"
        />
      </view>

      <view class="agree-row" @click="agreed = !agreed">
        <view class="checkbox" :class="{ checked: agreed }">{{ agreed ? '✓' : '' }}</view>
        <text class="agree-text">
          我已阅读并同意<text class="link" @click.stop="agreementVisible = true">《设备借阅协议》</text>,承诺妥善保管设备,按时归还,如有损坏照价赔偿。
        </text>
      </view>

      <button class="btn-gradient" @click="toConfirm">下一步</button>
    </view>

    <!-- 第二步:确认信息 -->
    <view v-else-if="step === 1" class="card form-card">
      <view class="cf-row"><text class="cf-label">设备名称</text><text class="cf-value">{{ equip?.name }}</text></view>
      <view class="cf-row"><text class="cf-label">借用数量</text><text class="cf-value">{{ form.quantity }} 台</text></view>
      <view class="cf-row"><text class="cf-label">使用目的</text><text class="cf-value">{{ form.purpose }}</text></view>
      <view class="cf-row"><text class="cf-label">关联项目</text><text class="cf-value">{{ form.projectName || '-' }}</text></view>
      <view class="cf-row"><text class="cf-label">借用时间</text><text class="cf-value">{{ form.startDate }} 起 {{ form.durationDays }} 天</text></view>
      <view class="cf-row"><text class="cf-label">备注</text><text class="cf-value">{{ form.remark || '-' }}</text></view>

      <view class="tips-box">
        <text class="tips-title">📌 温馨提示</text>
        <text class="tips-item">· 请在使用前检查设备完整性</text>
        <text class="tips-item">· 开发板类设备借用期限为2周</text>
        <text class="tips-item">· 精密仪器需在老师指导下使用</text>
        <text class="tips-item">· 逾期归还将影响信用评分</text>
      </view>

      <view class="btn-row">
        <button class="btn-plain half" @click="step = 0">上一步</button>
        <button class="btn-gradient half" :disabled="submitting" @click="submit">
          {{ submitting ? '提交中...' : '提交申请' }}
        </button>
      </view>
    </view>

    <!-- 第三步:提交成功 -->
    <view v-else class="card form-card success-card">
      <view class="success-icon">✓</view>
      <text class="success-title">申请提交成功</text>
      <text class="success-sub">申请编号</text>
      <text class="success-no">{{ result?.requestNo }}</text>
      <text class="success-tip muted">管理员将在 1 个工作日内审批,请留意站内通知</text>
      <view class="btn-row">
        <button class="btn-plain half" @click="backEquipment">返回设备馆</button>
        <button class="btn-gradient half" @click="goBorrows">查看借阅记录</button>
      </view>
    </view>

    <!-- 借阅协议弹层 -->
    <view v-if="agreementVisible" class="mask" @click="agreementVisible = false">
      <view class="modal" @click.stop>
        <text class="modal-title">设备借阅协议</text>
        <scroll-view scroll-y class="modal-body">
          <text class="clause">1. 借用人须为本平台注册学生,凭有效身份领取设备,设备仅限本人在校内学习科研使用,不得转借他人或挪作商用。</text>
          <text class="clause">2. 领取设备时应当场检查外观与功能,发现异常立即向管理员登记;未登记的损坏视为借用期内发生。</text>
          <text class="clause">3. 开发板类设备借用期限最长 2 周,仪器仪表类最长 1 周;到期前 3 天可申请续借一次,逾期未还将暂停借阅资格并影响信用评分。</text>
          <text class="clause">4. 借用期间妥善保管设备,防水防摔防静电;精密仪器须在指导老师监督下使用。</text>
          <text class="clause">5. 归还时须通过管理员功能验收;人为损坏或遗失的,按设备参考价值赔偿或承担维修费用。</text>
          <text class="clause">6. 本协议自勾选同意并提交申请时生效,最终解释权归电子信息创新实验室所有。</text>
        </scroll-view>
        <view class="btn-row">
          <button class="btn-plain half" @click="agreementVisible = false">关闭</button>
          <button class="btn-gradient half" @click="agreeAndClose">同意并勾选</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { applyBorrow, fetchEquipmentDetail } from '@/api'
import { today } from '@/utils/format'
import { getToken } from '@/utils/auth'

const steps = ['填写申请', '确认信息', '提交成功']
const purposes = ['课程实验', '竞赛准备', '科研研究', '毕业设计']
const durations = [3, 7, 14, 30]
const durationLabels = ['3天', '1周', '2周', '1个月']

const step = ref(0)
const equip = ref(null)
const agreed = ref(false)
const agreementVisible = ref(false)
const submitting = ref(false)
const result = ref(null)
const todayStr = today()

const form = reactive({
  quantity: 1,
  purpose: '',
  projectName: '',
  startDate: today(),
  durationDays: 14,
  remark: ''
})

const maxQty = computed(() => equip.value?.availableCount || 1)

const durationLabel = computed(() => durationLabels[durations.indexOf(form.durationDays)] || `${form.durationDays}天`)

onLoad(async (options) => {
  // 借阅申请是登录后功能,游客引导去登录
  if (!getToken()) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => uni.redirectTo({ url: '/pages/auth/index' }), 600)
    return
  }
  equip.value = await fetchEquipmentDetail(options.equipmentId)
})

const changeQty = (delta) => {
  const next = form.quantity + delta
  if (next >= 1 && next <= maxQty.value) form.quantity = next
}

const agreeAndClose = () => {
  agreed.value = true
  agreementVisible.value = false
}

const toConfirm = () => {
  if (!form.purpose || !form.startDate) {
    uni.showToast({ title: '请填写完整信息', icon: 'none' })
    return
  }
  if (!agreed.value) {
    uni.showToast({ title: '请先阅读并同意设备借阅协议', icon: 'none' })
    return
  }
  step.value = 1
}

const submit = async () => {
  submitting.value = true
  try {
    result.value = await applyBorrow({
      equipmentId: equip.value.id,
      quantity: form.quantity,
      purpose: form.purpose,
      projectName: form.projectName.trim() || undefined,
      startDate: form.startDate,
      durationDays: form.durationDays,
      remark: form.remark.trim() || undefined
    })
    step.value = 2
  } catch (e) {
    // 已提示
  } finally {
    submitting.value = false
  }
}

const goBorrows = () => {
  uni.switchTab({ url: '/pages/borrows/index' })
}

const backEquipment = () => {
  uni.switchTab({ url: '/pages/equipment/index' })
}
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx 24rpx 60rpx;
}

.steps {
  display: flex;
  padding: 32rpx 24rpx;
}

.step-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.step-dot {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: $gray-bg;
  color: $text-light;
  font-size: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;

  &.active {
    background: linear-gradient(135deg, #2563eb, #9333ea);
    color: #fff;
  }
}

.step-label {
  font-size: 24rpx;
  color: $text-light;
  margin-top: 12rpx;

  &.active {
    color: $brand-blue;
    font-weight: 600;
  }
}

.step-line {
  position: absolute;
  top: 28rpx;
  left: calc(50% + 40rpx);
  width: calc(100% - 80rpx);
  height: 4rpx;
  background: $border-color;

  &.active {
    background: $brand-blue;
  }
}

.equip-brief {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-top: 24rpx;
}

.eb-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 20rpx;
  background: linear-gradient(135deg, #60a5fa, #a78bfa);
  font-size: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.eb-info {
  flex: 1;
  overflow: hidden;
}

.eb-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
}

.eb-model {
  display: block;
  font-size: 24rpx;
  color: $text-sub;
  margin-top: 6rpx;
}

.form-card {
  margin-top: 24rpx;
}

.field {
  margin-bottom: 30rpx;
}

.field-row {
  display: flex;
  gap: 24rpx;

  .grow {
    flex: 1;
  }
}

.field-label {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  margin-bottom: 12rpx;
}

.req {
  color: $red;
}

.field-input {
  background: $gray-bg;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx;
  height: 88rpx;
  box-sizing: border-box;

  &.picker {
    display: flex;
    align-items: center;
  }

  &.placeholder {
    color: $text-light;
  }
}

.field-textarea {
  background: $gray-bg;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx;
  width: 100%;
  height: 160rpx;
  box-sizing: border-box;
}

.ph {
  color: $text-light;
}

.qty-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.qty-btn {
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  background: $gray-bg;
  font-size: 36rpx;
  color: $text-main;
  display: flex;
  align-items: center;
  justify-content: center;

  &.disabled {
    color: $text-light;
    opacity: 0.6;
  }
}

.qty-num {
  font-size: 34rpx;
  font-weight: 600;
  min-width: 60rpx;
  text-align: center;
}

.qty-tip {
  font-size: 22rpx;
}

.agree-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 32rpx;
  align-items: flex-start;
}

.checkbox {
  width: 36rpx;
  height: 36rpx;
  border-radius: 8rpx;
  border: 2rpx solid $border-color;
  background: #fff;
  color: #fff;
  font-size: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 6rpx;

  &.checked {
    background: $brand-blue;
    border-color: $brand-blue;
  }
}

.agree-text {
  font-size: 24rpx;
  color: $text-sub;
  flex: 1;
}

.link {
  color: $brand-blue;
}

.cf-row {
  display: flex;
  justify-content: space-between;
  padding: 22rpx 0;
  border-bottom: 2rpx solid $border-color;
}

.cf-label {
  color: $text-sub;
  font-size: 27rpx;
}

.cf-value {
  font-size: 27rpx;
  font-weight: 600;
  max-width: 65%;
  text-align: right;
}

.tips-box {
  background: $yellow-bg;
  border-radius: 16rpx;
  padding: 24rpx;
  margin: 28rpx 0 32rpx;
}

.tips-title {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: $yellow;
  margin-bottom: 12rpx;
}

.tips-item {
  display: block;
  font-size: 24rpx;
  color: #854d0e;
  line-height: 1.9;
}

.btn-row {
  display: flex;
  gap: 24rpx;

  .half {
    flex: 1;
  }
}

.success-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60rpx 40rpx;
}

.success-icon {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: $green-bg;
  color: $green;
  font-size: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.success-title {
  font-size: 36rpx;
  font-weight: 700;
  margin-top: 28rpx;
}

.success-sub {
  font-size: 24rpx;
  color: $text-sub;
  margin-top: 24rpx;
}

.success-no {
  font-size: 40rpx;
  font-weight: 700;
  color: $brand-blue;
  margin-top: 8rpx;
  letter-spacing: 2rpx;
}

.success-tip {
  margin: 24rpx 0 40rpx;
  text-align: center;
}

.mask {
  position: fixed;
  inset: 0;
  background: rgba(17, 24, 39, 0.5);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal {
  width: 640rpx;
  background: #fff;
  border-radius: 28rpx;
  padding: 40rpx 36rpx;
  max-height: 75vh;
  display: flex;
  flex-direction: column;
}

.modal-title {
  font-size: 32rpx;
  font-weight: 700;
  text-align: center;
  margin-bottom: 24rpx;
}

.modal-body {
  flex: 1;
  max-height: 50vh;
  margin-bottom: 28rpx;
}

.clause {
  display: block;
  font-size: 26rpx;
  color: $text-sub;
  line-height: 1.8;
  margin-bottom: 18rpx;
}
</style>
