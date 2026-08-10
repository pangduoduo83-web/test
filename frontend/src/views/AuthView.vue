<template>
  <div class="auth-page">
    <!-- 左侧品牌区:实景照片 + 蓝紫渐变叠加 -->
    <div class="brand-panel">
      <img v-if="!bgFailed" class="brand-bg"
           src="https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?auto=format&fit=crop&w=1400&q=70"
           alt="" @error="bgFailed = true" />
      <div class="brand-overlay"></div>

      <div class="brand-inner">
        <div class="brand-head">
          <div class="brand-logo">
            <BookOpen :size="30" color="#4f46e5" :stroke-width="2.2" />
          </div>
          <div>
            <h1 class="brand-title">设备图书馆</h1>
            <div class="brand-sub">项目驱动教学实验平台</div>
          </div>
        </div>
        <p class="brand-tagline">融合理论与实践，通过项目驱动学习，培养创新型电子信息人才</p>

        <div class="feature-grid">
          <div v-for="f in features" :key="f.title" class="feature-item">
            <div class="feature-icon" :style="{ background: f.bg }">
              <component :is="f.icon" :size="22" color="#fff" :stroke-width="2" />
            </div>
            <div>
              <div class="feature-title">{{ f.title }}</div>
              <div class="feature-desc">{{ f.desc }}</div>
            </div>
          </div>
        </div>

        <div class="stats-row">
          <div v-for="s in statItems" :key="s.label" class="stat-chip">
            <div class="stat-num">{{ s.value }}+</div>
            <div class="stat-label">{{ s.label }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="form-panel">
      <div class="form-box">
        <h2>{{ isLogin ? '欢迎回来' : '创建账号' }}</h2>
        <p class="form-sub">{{ isLogin ? '登录以继续你的创新之旅' : '加入我们的项目驱动学习平台' }}</p>

        <el-form :model="form" label-position="top" size="large" @submit.prevent>
          <template v-if="!isLogin">
            <el-form-item label="姓名">
              <el-input v-model="form.name" placeholder="请输入姓名">
                <template #prefix><User :size="16" /></template>
              </el-input>
            </el-form-item>
            <el-form-item label="学号">
              <el-input v-model="form.studentNo" placeholder="请输入学号">
                <template #prefix><Hash :size="16" /></template>
              </el-input>
            </el-form-item>
            <div class="form-row">
              <el-form-item label="专业" class="grow">
                <el-select v-model="form.major" placeholder="请选择专业">
                  <el-option v-for="m in majors" :key="m" :label="m" :value="m" />
                </el-select>
              </el-form-item>
              <el-form-item label="年级" class="grow">
                <el-select v-model="form.grade" placeholder="请选择年级">
                  <el-option v-for="g in grades" :key="g" :label="g" :value="g" />
                </el-select>
              </el-form-item>
            </div>
          </template>

          <el-form-item label="邮箱">
            <el-input v-model="form.email" placeholder="请输入学校邮箱">
              <template #prefix><Mail :size="16" /></template>
            </el-input>
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码"
                      @keyup.enter="submit">
              <template #prefix><Lock :size="16" /></template>
            </el-input>
          </el-form-item>

          <div v-if="isLogin" class="login-extra">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            <a class="forgot" @click.prevent="onForgot">忘记密码？</a>
          </div>

          <button class="submit-btn" :disabled="loading" @click="submit">
            {{ loading ? '提交中...' : isLogin ? '立即登录' : '立即注册' }}
          </button>
        </el-form>

        <div class="switch-line">
          {{ isLogin ? '还没有账号？' : '已有账号？' }}
          <a @click="isLogin = !isLogin">{{ isLogin ? '立即注册' : '立即登录' }}</a>
        </div>

        <div class="divider"><span>其他登录方式</span></div>
        <div class="third-party">
          <span class="tp-btn tp-wechat" title="微信登录" @click="onThirdParty('微信')">微</span>
          <span class="tp-btn tp-qq" title="QQ登录" @click="onThirdParty('QQ')">Q</span>
          <span class="tp-btn tp-school" title="学校统一认证" @click="onThirdParty('学校统一认证')">学</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Award, BookOpen, Hash, Lock, Mail, Monitor, User, Users, Wrench } from 'lucide-vue-next'
import { fetchPublicStats, login, register } from '../api'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isLogin = ref(true)
const loading = ref(false)
const rememberMe = ref(true)
const bgFailed = ref(false)

const majors = ['电子信息工程', '通信工程', '自动化', '计算机科学', '物联网工程']
const grades = ['大一', '大二', '大三', '大四', '研究生']

const features = [
  { icon: Wrench, title: '智能设备管理', desc: '实验器材智能化借还', bg: 'linear-gradient(135deg,#34d399,#059669)' },
  { icon: Users, title: '协作学习', desc: '团队项目协同开发', bg: 'linear-gradient(135deg,#60a5fa,#2563eb)' },
  { icon: Award, title: '技能评估', desc: '个性化能力测评', bg: 'linear-gradient(135deg,#fbbf24,#f59e0b)' },
  { icon: Monitor, title: '虚拟仿真', desc: '数字孪生实验环境', bg: 'linear-gradient(135deg,#22d3ee,#0891b2)' }
]

const stats = reactive({ equipmentCount: 500, studentCount: 1000, projectCount: 50 })
const statItems = computed(() => [
  { label: '实验设备', value: stats.equipmentCount },
  { label: '注册学生', value: stats.studentCount },
  { label: '创新项目', value: stats.projectCount }
])

const form = reactive({ name: '', studentNo: '', major: '', grade: '', email: '', password: '' })

onMounted(async () => {
  if (route.query.mode === 'register') isLogin.value = false
  // 已登录直接进入对应端
  if (authStore.isLoggedIn) {
    const role = authStore.user?.role
    router.replace(role === 'ADMIN' ? '/admin' : role === 'TEACHER' ? '/teacher' : '/app/dashboard')
    return
  }
  try {
    Object.assign(stats, await fetchPublicStats())
  } catch (e) { /* 后端未启动时展示默认数字 */ }
})

const onForgot = () => {
  ElMessageBox.alert(
    '平台暂未开放自助重置。请携带学生证到实验室管理处,或发邮件到 admin@ioedu.cn 联系管理员重置密码。',
    '忘记密码',
    { confirmButtonText: '知道了' }
  )
}

const onThirdParty = (name) => {
  ElMessage.info(`${name}登录暂未开通,请使用邮箱账号登录`)
}

const submit = async () => {
  if (!form.email || !form.password) {
    ElMessage.warning('请填写邮箱和密码')
    return
  }
  if (!isLogin.value && (!form.name || !form.studentNo || !form.major || !form.grade)) {
    ElMessage.warning('请填写完整的注册信息')
    return
  }
  loading.value = true
  try {
    const data = isLogin.value
      ? await login({ email: form.email, password: form.password })
      : await register({ ...form })
    // 登录时按"记住我"决定凭据保存位置;注册默认记住
    authStore.setAuth(data, isLogin.value ? rememberMe.value : true)
    ElMessage.success(isLogin.value ? '登录成功' : '注册成功,欢迎加入!')
    const role = data.user.role
    router.push(role === 'ADMIN' ? '/admin' : role === 'TEACHER' ? '/teacher' : '/app/dashboard')
  } catch (e) { /* 错误提示已由拦截器处理 */ } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { display: flex; min-height: 100vh; }

/* ---------- 左侧品牌区 ---------- */
.brand-panel {
  flex: 1.2;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #2563eb 0%, #7c3aed 60%, #9333ea 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.brand-bg {
  position: absolute; inset: 0;
  width: 100%; height: 100%;
  object-fit: cover;
}
.brand-overlay {
  position: absolute; inset: 0;
  background: linear-gradient(135deg, rgba(37,99,235,.82) 0%, rgba(109,40,217,.78) 60%, rgba(147,51,234,.82) 100%);
}
.brand-inner { max-width: 520px; padding: 48px; position: relative; z-index: 1; }

.brand-head { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; }
.brand-logo {
  width: 58px; height: 58px; border-radius: 14px;
  background: #fff;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 20px 25px -5px rgba(0,0,0,.25);
  flex-shrink: 0;
}
.brand-title {
  font-size: 32px; font-weight: 700; margin: 0; line-height: 1.2;
  text-shadow: 0 2px 8px rgba(0,0,0,.2);
}
.brand-sub { font-size: 16px; font-weight: 300; color: #e0e7ff; text-shadow: 0 1px 4px rgba(0,0,0,.15); margin-top: 2px; }
.brand-tagline {
  font-size: 15px; font-weight: 300; color: #e0e7ff;
  line-height: 1.8; margin: 0 0 34px; max-width: 440px;
}

.feature-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 30px;
}
.feature-item {
  display: flex; align-items: center; gap: 12px;
  padding: 14px;
  background: rgba(255,255,255,.14);
  border: 1px solid rgba(255,255,255,.22);
  border-radius: 14px;
  backdrop-filter: blur(12px);
  box-shadow: 0 10px 15px -3px rgba(0,0,0,.12);
}
.feature-icon {
  width: 42px; height: 42px; border-radius: 11px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 8px 12px -3px rgba(0,0,0,.2);
}
.feature-title { font-weight: 600; font-size: 14px; }
.feature-desc { color: #e0e7ff; font-size: 12px; margin-top: 2px; }

.stats-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.stat-chip {
  text-align: center; padding: 14px;
  background: rgba(255,255,255,.12);
  border: 1px solid rgba(255,255,255,.18);
  border-radius: 12px;
  backdrop-filter: blur(8px);
}
.stat-num { font-size: 24px; font-weight: 700; }
.stat-label { color: #e0e7ff; font-size: 12px; margin-top: 2px; }

/* ---------- 右侧表单区 ---------- */
.form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  padding: 32px;
}
.form-box { width: 100%; max-width: 360px; }
.form-box h2 { margin: 0 0 6px; font-size: 26px; color: #111827; text-align: center; }
.form-sub { color: #6b7280; margin: 0 0 28px; font-size: 13px; text-align: center; }

.form-box :deep(.el-form-item__label) { font-size: 13px; color: #374151; padding-bottom: 4px; }
.form-box :deep(.el-input__wrapper) { border-radius: 10px; }

.form-row { display: flex; gap: 14px; }
.grow { flex: 1; }
:deep(.el-select) { width: 100%; }

.login-extra {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 18px;
}
.forgot { color: var(--brand-blue); font-size: 13px; cursor: pointer; }

.submit-btn {
  width: 100%;
  padding: 12px 0;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(to right, #2563eb, #9333ea);
  border: none;
  border-radius: 10px;
  cursor: pointer;
  box-shadow: 0 10px 15px -3px rgba(37,99,235,.3);
  transition: all .2s;
}
.submit-btn:hover { transform: scale(1.02); box-shadow: 0 20px 25px -5px rgba(37,99,235,.35); }
.submit-btn:disabled { opacity: .5; transform: none; cursor: not-allowed; }

.switch-line { text-align: center; margin-top: 18px; color: var(--text-secondary); font-size: 13px; }
.switch-line a { color: var(--brand-blue); cursor: pointer; margin-left: 4px; font-weight: 500; }

.divider {
  display: flex; align-items: center; gap: 12px;
  color: #9ca3af; font-size: 12px; margin: 24px 0 16px;
}
.divider::before, .divider::after { content: ''; flex: 1; height: 1px; background: var(--border); }

.third-party { display: flex; justify-content: center; gap: 16px; }
.tp-btn {
  width: 42px; height: 42px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; font-size: 14px; font-weight: 700; color: #fff;
  box-shadow: 0 10px 15px -3px rgba(0,0,0,.15);
  transition: transform .15s;
}
.tp-btn:hover { transform: scale(1.08); }
.tp-wechat { background: #22c55e; }
.tp-qq { background: #3b82f6; }
.tp-school { background: #ef4444; }

@media (max-width: 960px) {
  .brand-panel { display: none; }
}
</style>
