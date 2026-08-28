<template>
  <view class="auth-page">
    <!-- 顶部渐变横幅 -->
    <view class="hero" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="brand">
        <image v-if="site.logoUrl" :src="fullUrl(site.logoUrl)" class="brand-logo-img" mode="aspectFill" />
        <view v-else class="brand-logo">AI</view>
        <view class="brand-text">
          <text class="brand-name">{{ site.title }}</text>
          <text class="brand-slogan">项目驱动教学实验平台</text>
        </view>
      </view>
      <view class="stats-row">
        <view class="stat-item">
          <text class="stat-num">{{ stats.equipmentCount }}</text>
          <text class="stat-label">实验设备</text>
        </view>
        <view class="stat-item">
          <text class="stat-num">{{ stats.studentCount }}</text>
          <text class="stat-label">注册学生</text>
        </view>
        <view class="stat-item">
          <text class="stat-num">{{ stats.projectCount }}</text>
          <text class="stat-label">实战项目</text>
        </view>
      </view>
    </view>

    <!-- 登录/注册卡片 -->
    <view class="form-card card">
      <view class="mode-tabs">
        <view class="mode-tab" :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</view>
        <view v-if="site.allowRegister" class="mode-tab" :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</view>
      </view>

      <template v-if="mode === 'register'">
        <view class="field">
          <text class="field-label">姓名</text>
          <input v-model="form.name" class="field-input" placeholder="请输入真实姓名" placeholder-class="ph" />
        </view>
        <view class="field">
          <text class="field-label">学号</text>
          <input v-model="form.studentNo" class="field-input" placeholder="请输入学号" placeholder-class="ph" />
        </view>
        <view class="field-row">
          <view class="field grow">
            <text class="field-label">专业</text>
            <picker mode="selector" :range="majors" @change="form.major = majors[$event.detail.value]">
              <view class="field-input picker" :class="{ placeholder: !form.major }">
                {{ form.major || '请选择专业' }}
              </view>
            </picker>
          </view>
          <view class="field grow">
            <text class="field-label">年级</text>
            <picker mode="selector" :range="grades" @change="form.grade = grades[$event.detail.value]">
              <view class="field-input picker" :class="{ placeholder: !form.grade }">
                {{ form.grade || '请选择年级' }}
              </view>
            </picker>
          </view>
        </view>
      </template>

      <view v-if="mode === 'register'" class="field">
        <text class="field-label">手机号(选填)</text>
        <input v-model="form.phone" class="field-input" type="number" placeholder="填写后可用手机号登录" placeholder-class="ph" />
      </view>
      <view class="field">
        <text class="field-label">{{ mode === 'login' ? '账号' : '邮箱' }}</text>
        <input v-model="form.email" class="field-input" :placeholder="mode === 'login' ? '请输入邮箱或手机号' : '请输入邮箱'" placeholder-class="ph" />
      </view>
      <view class="field">
        <text class="field-label">密码</text>
        <input v-model="form.password" class="field-input" password placeholder="请输入密码(6-32位)" placeholder-class="ph" />
      </view>

      <view class="agree-row" @click="agreed = !agreed">
        <view class="agree-box" :class="{ checked: agreed }">
          <text v-if="agreed">✓</text>
        </view>
        <text class="agree-text">我已阅读并同意</text>
        <text class="agree-link" @click.stop="goAgreement">《用户协议与隐私政策》</text>
      </view>

      <button class="btn-gradient submit-btn" :disabled="submitting" @click="submit">
        {{ submitting ? '请稍候...' : mode === 'login' ? '登 录' : '注册并登录' }}
      </button>

      <!-- #ifdef MP-WEIXIN -->
      <view class="divider">
        <view class="divider-line" />
        <text class="divider-text">或</text>
        <view class="divider-line" />
      </view>
      <!-- 未勾选协议时用普通按钮承接点击,避免先弹微信授权窗再提示 -->
      <button
        v-if="agreed"
        class="wx-btn"
        open-type="getPhoneNumber"
        :disabled="wxSubmitting"
        @getphonenumber="onWxPhone"
      >
        <uni-icons type="weixin" size="22" color="#fff" />
        <text class="wx-btn-text">{{ wxSubmitting ? '登录中...' : '微信手机号一键登录' }}</text>
      </button>
      <button v-else class="wx-btn" @click="needAgree">
        <uni-icons type="weixin" size="22" color="#fff" />
        <text class="wx-btn-text">微信手机号一键登录</text>
      </button>
      <!-- #endif -->

      <view v-if="mode === 'login'" class="demo-tip" @click="fillDemo">
        <text class="demo-link">使用演示账号(学生/教师/管理员)</text>
      </view>
      <view v-else class="demo-tip">
        <text class="muted">注册即成为学生账号,可浏览项目、借阅设备</text>
      </view>
    </view>

    <view class="skip-row" @click="skipLogin">
      <text class="skip-text">暂不登录,先去逛逛</text>
      <uni-icons type="right" size="13" color="#6b7280" />
    </view>

    <view class="footer-tip">忘记密码请联系管理员</view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { login, register, wechatPhoneLogin, fetchPublicStats, fetchSiteConfig } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { getToken } from '@/utils/auth'
import { fullUrl } from '@/config'

const authStore = useAuthStore()
const statusBarHeight = ref(20)
const mode = ref('login')
const submitting = ref(false)
const wxSubmitting = ref(false)
const agreed = ref(false)
const stats = reactive({ equipmentCount: 128, studentCount: 3200, projectCount: 56 })
const site = reactive({ title: 'AI未来实践中心', logoUrl: '', allowRegister: true })

const majors = ['电子信息工程', '通信工程', '自动化', '计算机科学', '物联网工程']
const grades = ['大一', '大二', '大三', '大四', '研究生']

const form = reactive({
  name: '',
  studentNo: '',
  major: '',
  grade: '',
  email: '',
  phone: '',
  password: ''
})

onLoad(() => {
  const info = uni.getSystemInfoSync()
  statusBarHeight.value = info.statusBarHeight || 20

  // 已登录直接进主页
  if (getToken()) {
    uni.switchTab({ url: '/pages/projects/index' })
    return
  }
  fetchPublicStats()
    .then((d) => Object.assign(stats, d))
    .catch(() => {})
  fetchSiteConfig()
    .then((d) => {
      Object.assign(site, d)
      if (!site.allowRegister && mode.value === 'register') mode.value = 'login'
    })
    .catch(() => {})
})

const switchMode = (m) => {
  mode.value = m
}

const demoAccounts = [
  { label: '学生 · 张同学', email: 'zhang@stu.ioedu.cn', password: '123456' },
  { label: '教师 · 陈老师', email: 'chen@ioedu.cn', password: '123456' },
  { label: '管理员', email: 'admin@ioedu.cn', password: 'admin123' }
]

const fillDemo = () => {
  uni.showActionSheet({
    itemList: demoAccounts.map((a) => a.label),
    success: (res) => {
      const acc = demoAccounts[res.tapIndex]
      form.email = acc.email
      form.password = acc.password
    }
  })
}

const validate = () => {
  if (mode.value === 'register') {
    if (!form.name.trim()) return '请输入姓名'
    if (!form.studentNo.trim()) return '请输入学号'
    if (!form.major) return '请选择专业'
    if (!form.grade) return '请选择年级'
    if (form.phone.trim() && !/^1\d{10}$/.test(form.phone.trim())) return '手机号格式不正确'
    if (!form.email.trim() || !form.email.includes('@')) return '请输入正确的邮箱'
  } else {
    // 登录账号可为邮箱或 11 位手机号
    const account = form.email.trim()
    if (!account || (!account.includes('@') && !/^1\d{10}$/.test(account))) return '请输入邮箱或11位手机号'
  }
  if (!form.password || form.password.length < 6 || form.password.length > 32) return '密码长度需为6-32位'
  return ''
}

const goAgreement = () => uni.navigateTo({ url: '/pages/agreement/index' })

const needAgree = () =>
  uni.showToast({ title: '请先阅读并勾选同意《用户协议与隐私政策》', icon: 'none' })

// 游客可不登录直接浏览项目与设备(小程序审核要求)
const skipLogin = () => uni.switchTab({ url: '/pages/projects/index' })

// 微信手机号一键登录:组件回调给动态令牌 code,后端向微信换取手机号后登录/自动建号
const onWxPhone = async (e) => {
  const detail = e.detail || {}
  if (!detail.code) {
    // 用户取消授权时静默;额度用尽(errno 1400001)等异常给出说明
    if (detail.errno === 1400001) {
      uni.showToast({ title: '一键登录额度已用完,请用账号密码登录', icon: 'none' })
    }
    return
  }
  wxSubmitting.value = true
  try {
    const data = await wechatPhoneLogin(detail.code)
    authStore.setAuth(data)
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => uni.switchTab({ url: '/pages/projects/index' }), 500)
  } catch (err) {
    // 错误提示已由请求层统一处理
  } finally {
    wxSubmitting.value = false
  }
}

const submit = async () => {
  if (!agreed.value) {
    needAgree()
    return
  }
  const err = validate()
  if (err) {
    uni.showToast({ title: err, icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const data =
      mode.value === 'login'
        ? await login({ email: form.email.trim(), password: form.password })
        : await register({
            name: form.name.trim(),
            studentNo: form.studentNo.trim(),
            major: form.major,
            grade: form.grade,
            email: form.email.trim(),
            phone: form.phone.trim(),
            password: form.password
          })
    authStore.setAuth(data)
    uni.showToast({ title: mode.value === 'login' ? '登录成功' : '注册成功', icon: 'success' })
    setTimeout(() => uni.switchTab({ url: '/pages/projects/index' }), 500)
  } catch (e) {
    // 错误提示已由请求层统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.auth-page {
  min-height: 100vh;
  background: $bg-page;
}

.hero {
  background: linear-gradient(135deg, #2563eb, #9333ea);
  padding: 40rpx 48rpx 140rpx;
}

.brand {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-top: 60rpx;
}

.brand-logo {
  width: 96rpx;
  height: 96rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  font-size: 40rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-logo-img {
  width: 96rpx;
  height: 96rpx;
  border-radius: 28rpx;
  background: #fff;
}

.brand-text {
  display: flex;
  flex-direction: column;
}

.brand-name {
  color: #fff;
  font-size: 44rpx;
  font-weight: 700;
}

.brand-slogan {
  color: rgba(255, 255, 255, 0.75);
  font-size: 26rpx;
  margin-top: 4rpx;
}

.stats-row {
  display: flex;
  margin-top: 48rpx;
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-num {
  color: #fff;
  font-size: 44rpx;
  font-weight: 700;
}

.stat-label {
  color: rgba(255, 255, 255, 0.75);
  font-size: 24rpx;
  margin-top: 4rpx;
}

.form-card {
  margin: -80rpx 32rpx 0;
  padding: 40rpx;
}

.mode-tabs {
  display: flex;
  background: $gray-bg;
  border-radius: 20rpx;
  padding: 6rpx;
  margin-bottom: 40rpx;
}

.mode-tab {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  font-size: 30rpx;
  color: $text-sub;
  border-radius: 16rpx;

  &.active {
    background: #fff;
    color: $brand-blue;
    font-weight: 600;
    box-shadow: 0 2rpx 8rpx rgba(17, 24, 39, 0.06);
  }
}

.field {
  margin-bottom: 28rpx;
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
  color: $text-main;
  font-weight: 500;
  margin-bottom: 12rpx;
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

.ph {
  color: $text-light;
}

.submit-btn {
  margin-top: 16rpx;
}

.agree-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin: 8rpx 0 24rpx;
}

.agree-box {
  width: 32rpx;
  height: 32rpx;
  border-radius: 8rpx;
  border: 2rpx solid $border-color;
  background: #fff;
  color: #fff;
  font-size: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &.checked {
    background: $brand-blue;
    border-color: $brand-blue;
  }
}

.agree-text {
  font-size: 24rpx;
  color: $text-sub;
}

.agree-link {
  font-size: 24rpx;
  color: $brand-blue;
}

.divider {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin: 30rpx 0 22rpx;
}

.divider-line {
  flex: 1;
  height: 2rpx;
  background: $border-color;
}

.divider-text {
  font-size: 22rpx;
  color: $text-light;
}

.wx-btn {
  background: #07c160;
  color: #fff;
  border-radius: 20rpx;
  font-size: 30rpx;
  height: 92rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;

  &::after {
    border: none;
  }

  &[disabled] {
    opacity: 0.6;
    background: #07c160;
    color: #fff;
  }
}

.wx-btn-text {
  font-size: 30rpx;
}

.skip-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
  margin-top: 36rpx;
}

.skip-text {
  font-size: 26rpx;
  color: $text-sub;
}

.demo-tip {
  margin-top: 28rpx;
  text-align: center;
  font-size: 24rpx;
}

.demo-link {
  color: $brand-blue;
  font-size: 24rpx;
}

.footer-tip {
  text-align: center;
  color: $text-light;
  font-size: 22rpx;
  padding: 32rpx 0 60rpx;
}
</style>
