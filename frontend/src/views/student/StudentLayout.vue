<template>
  <div class="layout">
    <!-- 顶部导航 -->
    <header class="topbar">
      <div class="top-brand" @click="$router.push('/app/dashboard')">
        <span class="logo">
          <img v-if="site.logoUrl" :src="site.logoUrl" class="logo-img" alt="LOGO" />
          <GraduationCap v-else :size="22" color="#fff" />
        </span>
        <div>
          <div class="brand-name">{{ site.title }}</div>
          <div class="brand-sub">项目驱动教学实验平台</div>
        </div>
      </div>

      <div class="top-actions">
        <div class="top-search">
          <el-input v-model="keyword" placeholder="搜索项目、设备或教程..." clearable
                    @keyup.enter="doSearch">
            <template #prefix><Search :size="15" /></template>
          </el-input>
        </div>

        <!-- 通知 -->
        <el-popover placement="bottom-end" :width="360" trigger="click">
          <template #reference>
            <el-badge :is-dot="unread > 0" class="bell-badge">
              <span class="icon-btn"><Bell :size="20" /></span>
            </el-badge>
          </template>
          <div class="notice-head">
            <span>通知消息</span>
            <a class="notice-all" @click="readAll">全部已读</a>
          </div>
          <div v-if="notifications.length === 0" class="notice-empty">暂无通知</div>
          <div v-for="n in notifications" :key="n.id" class="notice-item"
               :class="{ unread: !n.isRead }" @click="readOne(n)">
            <div class="notice-icon" :class="'nt-' + (n.type || 'system')">
              <Package v-if="n.type === 'borrow'" :size="15" />
              <Rocket v-else-if="n.type === 'project'" :size="15" />
              <Megaphone v-else :size="15" />
            </div>
            <div class="notice-body">
              <div class="notice-title">{{ n.title }}</div>
              <div class="notice-content">{{ n.content }}</div>
              <div class="notice-time">{{ formatTime(n.createdAt) }}</div>
            </div>
          </div>
        </el-popover>

        <!-- 用户菜单 -->
        <el-dropdown @command="onUserCommand">
          <div class="user-chip">
            <img v-if="authStore.user?.avatarUrl" :src="authStore.user.avatarUrl" class="avatar avatar-img" alt="头像" />
            <span v-else class="avatar">{{ userInitial }}</span>
            <div class="user-text">
              <span class="user-name">{{ authStore.user?.name }}</span>
              <span class="user-major">{{ authStore.user?.major || '同学' }}</span>
            </div>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人资料</el-dropdown-item>
              <el-dropdown-item v-if="authStore.user?.role === 'TEACHER'" command="teacher">教师工作台</el-dropdown-item>
              <el-dropdown-item v-if="authStore.isAdmin" command="admin">管理后台</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 个人资料弹窗 -->
    <el-dialog v-model="profileVisible" title="个人资料" width="480px">
      <el-tabs v-model="profileTab">
        <el-tab-pane label="基本资料" name="basic">
          <el-form :model="profileForm" label-position="top">
            <el-form-item label="头像">
              <ImageUploader v-model="profileForm.avatarUrl" />
            </el-form-item>
            <el-form-item label="姓名">
              <el-input v-model="profileForm.name" />
            </el-form-item>
            <div class="form-row">
              <el-form-item label="专业" class="grow">
                <el-select v-model="profileForm.major">
                  <el-option v-for="m in majors" :key="m" :label="m" :value="m" />
                </el-select>
              </el-form-item>
              <el-form-item label="年级" class="grow">
                <el-select v-model="profileForm.grade">
                  <el-option v-for="g in grades" :key="g" :label="g" :value="g" />
                </el-select>
              </el-form-item>
            </div>
          </el-form>
          <el-button type="primary" style="width:100%" :loading="profileSaving" @click="saveProfile">
            保存资料
          </el-button>
        </el-tab-pane>
        <el-tab-pane label="修改密码" name="password">
          <el-form :model="pwdForm" label-position="top">
            <el-form-item label="原密码">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码(6-32位)">
              <el-input v-model="pwdForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="pwdForm.confirm" type="password" show-password />
            </el-form-item>
          </el-form>
          <el-button type="primary" style="width:100%" :loading="pwdSaving" @click="savePassword">
            修改密码
          </el-button>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <div class="body">
      <!-- 侧边栏 -->
      <aside class="sidebar">
        <!-- 实践等级卡(参考站置顶) -->
        <div class="level-card">
          <div class="level-head">
            <span>实践等级</span>
            <span class="level-bookmark"><Bookmark :size="14" color="#fff" /></span>
          </div>
          <div class="level-num">Lv.{{ level }}</div>
          <div class="level-name">{{ authStore.user?.name || '同学' }}</div>
          <div class="level-bar">
            <div class="level-bar-inner" :style="{ width: levelProgress + '%' }"></div>
          </div>
          <div class="level-tip">距离升级还需 {{ 100 - levelProgress }}% 经验</div>
        </div>

        <nav class="menu">
          <router-link v-for="m in menus" :key="m.path" :to="m.path" class="menu-item"
                       :class="{ active: isActive(m.path) }">
            <span class="menu-icon"><component :is="m.icon" :size="19" /></span>
            <span class="menu-text">
              <span class="menu-title">{{ m.title }}</span>
              <span class="menu-desc">{{ m.desc }}</span>
            </span>
          </router-link>
        </nav>

        <!-- 本周学习统计 -->
        <div class="week-stats">
          <div class="ws-label">本周学习</div>
          <div class="ws-row">
            <span>学习时长</span>
            <b class="ws-blue">{{ weekStats.hours }}h</b>
          </div>
          <div class="ws-row">
            <span>完成项目</span>
            <b>{{ weekStats.completed }}个</b>
          </div>
          <div class="ws-row">
            <span>技能掌握</span>
            <b class="ws-green">{{ weekStats.skillAvg }}%</b>
          </div>
        </div>

        <div class="side-foot">
          <a class="foot-item" @click="guideVisible = true"><BookOpen :size="16" /> 使用指南</a>
          <a class="foot-item" @click="helpVisible = true"><HelpCircle :size="16" /> 帮助支持</a>
          <a class="foot-item" @click="onUserCommand('profile')"><Settings :size="16" /> 系统设置</a>
          <div class="foot-divider"></div>
          <a class="foot-item logout" @click="onUserCommand('logout')"><LogOut :size="16" /> 退出登录</a>
        </div>
      </aside>

      <!-- 内容区 -->
      <main class="content">
        <router-view />
        <div v-if="site.footerText" class="site-footer">{{ site.footerText }}</div>
      </main>
    </div>

    <!-- 使用指南 -->
    <el-dialog v-model="guideVisible" title="使用指南" width="520px">
      <div class="guide-step" v-for="(g, i) in guideSteps" :key="i">
        <span class="guide-num">{{ i + 1 }}</span>
        <div>
          <div class="guide-title">{{ g.title }}</div>
          <div class="guide-desc">{{ g.desc }}</div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="guideVisible = false">开始探索</el-button>
      </template>
    </el-dialog>

    <!-- 帮助支持 -->
    <el-dialog v-model="helpVisible" title="帮助支持" width="520px">
      <el-collapse>
        <el-collapse-item v-for="f in faqs" :key="f.q" :title="f.q">
          <div class="faq-answer">{{ f.a }}</div>
        </el-collapse-item>
      </el-collapse>
      <div class="help-contact">
        仍未解决? 联系实验室管理员: <b>admin@ioedu.cn</b> · 值班时间 工作日 9:00-17:30
      </div>
      <template #footer>
        <el-button type="primary" @click="helpVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  BarChart3, Bell, BookOpen, Bookmark, ClipboardList, GraduationCap,
  HelpCircle, LogOut, Megaphone, Package, Rocket, Search, Settings, User, Wrench
} from 'lucide-vue-next'
import { useAuthStore } from '../../stores/auth'
import {
  changePassword, fetchDashboard, fetchMe, fetchNotifications,
  markAllNotificationsRead, markNotificationRead, updateProfile
} from '../../api'
import ImageUploader from '../../components/ImageUploader.vue'
import { loadSiteConfig, siteConfig as site } from '../../utils/siteConfig'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const keyword = ref('')
const notifications = ref([])
const unread = ref(0)
const weekStats = reactive({ hours: 0, completed: 0, skillAvg: 0 })
let pollTimer = null

const majors = ['电子信息工程', '通信工程', '自动化', '计算机科学', '物联网工程']
const grades = ['大一', '大二', '大三', '大四', '研究生']

const profileVisible = ref(false)
const profileTab = ref('basic')
const profileSaving = ref(false)
const pwdSaving = ref(false)
const profileForm = reactive({ name: '', major: '', grade: '', avatarUrl: '' })
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const guideVisible = ref(false)
const helpVisible = ref(false)

const guideSteps = [
  { title: '完成能力测评', desc: '在「技能评估」自评各项技能,获得个性化项目推荐与学习建议。' },
  { title: '挑选并报名项目', desc: '在「项目中心」按分类/标签筛选,查看详情后点击「立即报名」。' },
  { title: '借用实验设备', desc: '在「设备图书馆」提交借阅申请,等待管理员审批通过后到实验室领取。' },
  { title: '推进项目进度', desc: '在「个人中心」更新项目进度,完成后获得经验值与成就徽章。' },
  { title: '按时归还设备', desc: '在「借阅管理」发起归还申请,通过功能验收后完成归还。' }
]

const faqs = [
  { q: '设备可以借多久?', a: '开发板类最长 2 周,仪器仪表类最长 1 周;到期前 3 天可申请续借一次。' },
  { q: '借阅申请多久审批?', a: '管理员在 1 个工作日内完成审批,结果会通过站内通知提醒你。' },
  { q: '设备损坏了怎么办?', a: '第一时间联系实验室管理员登记;人为损坏需按价赔偿或维修。' },
  { q: '忘记密码怎么办?', a: '发邮件到 admin@ioedu.cn 或到实验室管理处,由管理员协助重置。' },
  { q: '项目完成后如何结项?', a: '把进度推进到 100% 即自动结项,系统会发放经验值并更新成就。' }
]

const menus = [
  { path: '/app/projects', icon: Rocket, title: '项目中心', desc: '浏览所有创新项目' },
  { path: '/app/equipment', icon: Wrench, title: '设备图书馆', desc: '借用开发工具仪表' },
  { path: '/app/borrowing', icon: ClipboardList, title: '借阅管理', desc: '申请审批追踪' },
  { path: '/app/skills', icon: BarChart3, title: '技能评估', desc: '能力测评与提升' },
  { path: '/app/dashboard', icon: User, title: '个人中心', desc: '我的项目进度' }
]

const isActive = (path) => route.path.startsWith(path)
const userInitial = computed(() => (authStore.user?.name || '同')[0])
const level = computed(() => Math.floor((authStore.user?.exp || 0) / 100) + 1)
const levelProgress = computed(() => (authStore.user?.exp || 0) % 100)

const doSearch = () => {
  if (keyword.value.trim()) {
    router.push({ path: '/app/projects', query: { keyword: keyword.value.trim() } })
  }
}

const loadNotifications = async () => {
  try {
    const data = await fetchNotifications()
    notifications.value = data.items
    unread.value = data.unread
  } catch (e) { /* 忽略,登录态失效时拦截器已处理 */ }
}

const loadWeekStats = async () => {
  try {
    const d = await fetchDashboard()
    weekStats.hours = d.weeklyHours
    weekStats.completed = d.completedProjects
    weekStats.skillAvg = d.skillAvg
  } catch (e) { /* 侧栏统计失败不影响主内容 */ }
}

const readOne = async (n) => {
  if (!n.isRead) {
    await markNotificationRead(n.id)
    await loadNotifications()
  }
}

const readAll = async () => {
  await markAllNotificationsRead()
  await loadNotifications()
}

const formatTime = (t) => (t || '').replace('T', ' ').slice(0, 16)

const onUserCommand = (cmd) => {
  if (cmd === 'logout') {
    authStore.logout()
    router.push('/auth')
  } else if (cmd === 'admin') {
    router.push('/admin')
  } else if (cmd === 'teacher') {
    router.push('/teacher')
  } else if (cmd === 'profile') {
    const u = authStore.user || {}
    Object.assign(profileForm, {
      name: u.name || '', major: u.major || '', grade: u.grade || '', avatarUrl: u.avatarUrl || ''
    })
    Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirm: '' })
    profileTab.value = 'basic'
    profileVisible.value = true
  }
}

const saveProfile = async () => {
  if (!profileForm.name.trim()) {
    ElMessage.warning('姓名不能为空')
    return
  }
  profileSaving.value = true
  try {
    const user = await updateProfile({ ...profileForm })
    authStore.updateUser(user)
    ElMessage.success('资料已更新')
    profileVisible.value = false
  } catch (e) { /* 已提示 */ } finally {
    profileSaving.value = false
  }
}

const savePassword = async () => {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写完整')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  pwdSaving.value = true
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码修改成功,下次登录请使用新密码')
    profileVisible.value = false
  } catch (e) { /* 已提示 */ } finally {
    pwdSaving.value = false
  }
}

onMounted(async () => {
  loadSiteConfig()
  loadNotifications()
  loadWeekStats()
  // 准实时:每 60 秒轮询一次通知
  pollTimer = setInterval(loadNotifications, 60000)
  try {
    const me = await fetchMe()
    authStore.updateUser(me)
  } catch (e) { /* token 失效时由拦截器跳转 */ }
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.layout { height: 100vh; display: flex; flex-direction: column; }

.topbar {
  height: 64px;
  background: #fff;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 0 24px;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  z-index: 50;
}
.top-brand { display: flex; align-items: center; gap: 12px; cursor: pointer; }
.logo-img { width: 100%; height: 100%; object-fit: cover; }
.site-footer {
  text-align: center; padding: 22px 0 6px; font-size: 12px; color: #9ca3af; white-space: pre-wrap;
}
.logo {
  width: 40px; height: 40px; border-radius: 10px; overflow: hidden;
  background: linear-gradient(135deg, #3b82f6, #9333ea);
  display: flex; align-items: center; justify-content: center; font-size: 20px;
  box-shadow: var(--shadow-card);
}
.brand-name { font-weight: 700; font-size: 16px; line-height: 1.2; color: #111827; }
.brand-sub { font-size: 11px; color: #6b7280; }

.top-actions { display: flex; align-items: center; gap: 22px; }
.top-search { width: 320px; }
.top-search :deep(.el-input__wrapper) { border-radius: 10px; background: #f9fafb; }
.icon-btn {
  cursor: pointer; color: #4b5563;
  display: flex; align-items: center; justify-content: center;
  width: 36px; height: 36px; border-radius: 10px;
  transition: background .15s;
}
.icon-btn:hover { background: #f3f4f6; }
.user-chip { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.avatar {
  width: 36px; height: 36px; border-radius: 50%;
  background: linear-gradient(135deg, #60a5fa, #a855f7); color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 700;
}
.avatar-img { object-fit: cover; background: none; }
.user-text { display: flex; flex-direction: column; line-height: 1.3; }
.user-name { font-size: 14px; color: #111827; font-weight: 600; }
.user-major { font-size: 11px; color: #9ca3af; }
.form-row { display: flex; gap: 14px; }
.grow { flex: 1; }
:deep(.el-select) { width: 100%; }

.body { flex: 1; display: flex; overflow: hidden; }

.sidebar {
  width: 256px;
  background: #fff;
  border-right: 1px solid var(--border);
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  flex-shrink: 0;
}

/* 实践等级卡:蓝紫渐变,置顶 */
.level-card {
  background: linear-gradient(135deg, #6366f1, #9333ea);
  border-radius: 14px;
  padding: 16px;
  color: #fff;
  flex-shrink: 0;
}
.level-head { display: flex; justify-content: space-between; font-size: 12px; opacity: .9; }
.level-bookmark { font-size: 13px; }
.level-num { font-size: 28px; font-weight: 800; margin-top: 4px; }
.level-name { font-size: 13px; opacity: .9; margin: 2px 0 10px; }
.level-bar {
  width: 100%; height: 7px; border-radius: 999px;
  background: rgba(255,255,255,.25);
  overflow: hidden;
}
.level-bar-inner { height: 100%; border-radius: 999px; background: #fff; transition: width .3s; }
.level-tip { font-size: 11px; opacity: .85; margin-top: 8px; }

.menu { display: flex; flex-direction: column; gap: 4px; }
.menu-item {
  display: flex; gap: 12px; align-items: center;
  padding: 10px 14px; border-radius: 10px;
  transition: background .15s;
}
.menu-item:hover { background: #f9fafb; }
.menu-item.active { background: #eff6ff; }
.menu-item.active .menu-title { color: #1d4ed8; }
.menu-item.active .menu-desc { color: #3b82f6; }
.menu-icon { color: #6b7280; display: flex; align-items: center; }
.menu-item.active .menu-icon { color: #2563eb; }
.menu-text { display: flex; flex-direction: column; }
.menu-title { font-size: 14px; font-weight: 600; color: #374151; }
.menu-desc { font-size: 11px; color: #9ca3af; }

/* 本周学习统计 */
.week-stats {
  border-top: 1px solid var(--border);
  padding: 14px 14px 0;
  display: flex; flex-direction: column; gap: 10px;
}
.ws-label { font-size: 12px; color: #9ca3af; }
.ws-row {
  display: flex; justify-content: space-between; align-items: center;
  font-size: 13px; color: var(--text-secondary);
}
.ws-row b { color: #111827; font-weight: 600; }
.ws-row .ws-blue { color: var(--brand-blue); }
.ws-row .ws-green { color: #16a34a; }

.side-foot { margin-top: auto; display: flex; flex-direction: column; gap: 2px; }
.foot-item {
  padding: 9px 14px; font-size: 13px; color: var(--text-secondary);
  border-radius: 8px; cursor: pointer;
  display: flex; align-items: center; gap: 8px;
}
.foot-item:hover { background: #f9fafb; }
.foot-divider { border-top: 1px solid var(--border); margin: 8px 0; }
.foot-item.logout { color: #dc2626; }
.foot-item.logout:hover { background: #fef2f2; }

.content { flex: 1; overflow-y: auto; padding: 24px; background: #f9fafb; }

/* 移动端:侧边栏收窄为图标 */
@media (max-width: 768px) {
  .sidebar { width: 64px; padding: 12px 8px; }
  .menu-text, .level-card, .week-stats, .side-foot .foot-item:not(.logout) { display: none; }
  .menu-item { justify-content: center; padding: 12px 0; }
  .top-search, .brand-sub, .user-text { display: none; }
  .content { padding: 14px; }
}

/* 通知样式 */
.notice-head { display: flex; justify-content: space-between; font-weight: 600; margin-bottom: 8px; }
.notice-all { color: var(--brand-blue); font-size: 12px; cursor: pointer; font-weight: 400; }
.notice-empty { text-align: center; color: var(--text-secondary); padding: 24px 0; font-size: 13px; }
.notice-item {
  display: flex; gap: 10px; padding: 10px 8px;
  border-radius: 10px; cursor: pointer;
}
.notice-item:hover { background: #f9fafb; }
.notice-item.unread { background: #eff6ff; }
.notice-icon {
  width: 30px; height: 30px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.notice-icon.nt-borrow { background: #dcfce7; color: #16a34a; }
.notice-icon.nt-project { background: #eff6ff; color: #2563eb; }
.notice-icon.nt-system { background: #faf5ff; color: #9333ea; }

/* 使用指南 / 帮助支持 */
.guide-step { display: flex; gap: 12px; padding: 10px 0; }
.guide-num {
  width: 26px; height: 26px; border-radius: 50%; flex-shrink: 0;
  background: var(--brand-gradient-br); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 700;
}
.guide-title { font-size: 14px; font-weight: 600; color: #111827; }
.guide-desc { font-size: 13px; color: var(--text-secondary); margin-top: 3px; line-height: 1.6; }
.faq-answer { font-size: 13px; color: var(--text-secondary); line-height: 1.7; }
.help-contact {
  margin-top: 16px; background: #eff6ff; border-radius: 10px;
  padding: 12px 14px; font-size: 13px; color: #1e40af;
}
.notice-title { font-size: 13px; font-weight: 600; }
.notice-content { font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
.notice-time { font-size: 11px; color: #9ca3af; margin-top: 4px; }
</style>
