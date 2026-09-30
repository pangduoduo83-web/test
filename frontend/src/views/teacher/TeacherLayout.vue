<template>
  <div class="teacher-layout">
    <aside class="teacher-side">
      <div class="teacher-brand">
        <span class="logo">
          <img v-if="site.logoUrl" :src="site.logoUrl" class="logo-img" alt="LOGO" />
          <BookOpen v-else :size="22" color="#fff" />
        </span>
        <div class="brand-copy">
          <div class="brand-name">{{ site.title || 'AI未来项目实践中心' }}</div>
          <div class="brand-sub">教师工作台</div>
        </div>
      </div>

      <nav class="teacher-menu" aria-label="教师导航">
        <router-link to="/teacher/workbench" class="teacher-menu-item" aria-label="今日工作台"
                     :class="{ active: isWorkbench }">
          <span class="tmi-icon"><House :size="18" /></span>
          <span>今日工作台</span>
        </router-link>
        <router-link to="/teacher/projects" class="teacher-menu-item" aria-label="我的项目" :class="{ active: isProjects }">
          <span class="tmi-icon"><FolderOpen :size="18" /></span>
          <span>我的项目</span>
        </router-link>
        <router-link to="/teacher/classes" class="teacher-menu-item" aria-label="我的班级"
                     :class="{ active: route.path.startsWith('/teacher/classes') }">
          <span class="tmi-icon"><UsersRound :size="18" /></span>
          <span>我的班级</span>
        </router-link>
        <router-link to="/teacher/submissions" class="teacher-menu-item" aria-label="成果评审"
                     :class="{ active: route.path.startsWith('/teacher/submissions') }">
          <span class="tmi-icon"><ClipboardCheck :size="18" /></span>
          <span>成果评审</span>
          <span v-if="pending > 0" class="menu-badge">{{ pending }}</span>
        </router-link>
        <router-link to="/app/feedback" class="teacher-menu-item" aria-label="问题反馈">
          <span class="tmi-icon"><MessageSquarePlus :size="18" /></span>
          <span>问题反馈</span>
        </router-link>
      </nav>

      <div class="teacher-foot">
        <a class="teacher-menu-item" @click="router.push('/app/dashboard')">
          <span class="tmi-icon"><GraduationCap :size="18" /></span>
          <span>学生端视图</span>
        </a>
        <a class="teacher-menu-item logout" @click="logout">
          <span class="tmi-icon"><LogOut :size="18" /></span>
          <span>退出登录</span>
        </a>
      </div>
    </aside>

    <div class="teacher-main">
      <header class="teacher-top">
        <div class="teacher-top-title-block">
          <h1>{{ title }}</h1>
          <p>{{ greeting }}</p>
        </div>
        <div class="top-actions">
          <label v-if="isProjects" class="top-search">
            <Search :size="18" />
            <input v-model="searchTerm" type="search" placeholder="搜索项目、学生或关键词..." aria-label="搜索项目、学生或关键词" />
          </label>
          <button class="icon-action" type="button" title="成果评审" @click="router.push('/teacher/submissions')">
            <Bell :size="20" />
            <span v-if="pending > 0" class="notification-dot" />
          </button>
          <div class="teacher-user">
            <span class="avatar">{{ (authStore.user?.name || '师')[0] }}</span>
            <div class="teacher-user-text">
              <span class="tu-name">{{ authStore.user?.name || '指导教师' }}</span>
              <span class="tu-role">{{ authStore.user?.role === 'ADMIN' ? '管理员' : '指导教师' }}</span>
            </div>
            <ChevronDown :size="16" class="user-chevron" />
          </div>
          <button class="new-project-btn" type="button" aria-label="新建项目" @click="createProject">
            <Plus :size="18" />
            <span>新建项目</span>
          </button>
        </div>
      </header>

      <main ref="contentElement" class="teacher-content">
        <router-view v-slot="{ Component }">
          <component :is="Component" v-bind="isProjects ? { searchTerm } : {}" @refresh-pending="loadPending" @workbench-stats="pending = $event.pendingSubmissions || 0" />
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell, BookOpen, ChevronDown, ClipboardCheck, FolderOpen, GraduationCap, House, LogOut, MessageSquarePlus, Plus, Search, UsersRound
} from 'lucide-vue-next'
import { useAuthStore } from '../../stores/auth'
import { teacherStats } from '../../api'
import { loadSiteConfig, siteConfig as site } from '../../utils/siteConfig'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const pending = ref(0)
const searchTerm = ref('')
const contentElement = ref(null)

const isWorkbench = computed(() => route.path.startsWith('/teacher/workbench'))
const isProjects = computed(() => route.path.startsWith('/teacher/projects'))
const title = computed(() => route.path.startsWith('/teacher/submissions') ? '成果评审'
  : route.path.startsWith('/teacher/classes') ? '我的班级' : isProjects.value ? '我的项目' : '今日工作台')
const greeting = computed(() => {
  if (isProjects.value) return '管理项目内容、学生进度、学习资源与公告。'
  if (route.path.startsWith('/teacher/classes')) return '管理班级成员、授课教师和教学安排。'
  if (route.path.startsWith('/teacher/submissions')) return '查看学生提交的成果，完成评审与反馈。'
  const hour = new Date().getHours()
  const period = hour < 6 ? '晚上好' : hour < 12 ? '上午好' : hour < 18 ? '下午好' : '晚上好'
  return `${authStore.user?.name || '老师'}，${period}！今天优先处理待办任务即可。`
})

const loadPending = async () => {
  try { pending.value = (await teacherStats()).pendingSubmissions || 0 } catch (e) { /* 忽略 */ }
}

const createProject = () => router.push({ path: '/teacher/projects', query: { new: '1' } })
watch(isProjects, value => { if (!value) searchTerm.value = '' })
watch(() => route.path, async () => {
  await nextTick()
  contentElement.value?.scrollTo({ top: 0 })
  window.scrollTo({ top: 0 })
})
const logout = () => {
  authStore.logout()
  router.push('/auth')
}

onMounted(() => {
  loadSiteConfig()
  loadPending()
})
</script>

<style scoped>
.teacher-layout {
  display: flex;
  min-height: 100vh;
  height: 100vh;
  overflow: hidden;
  background: #f7f9fd;
  color: #172554;
}

.teacher-side {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 21px 12px 18px;
  background: rgba(255, 255, 255, .94);
  border-right: 1px solid #e9eef8;
}

.teacher-brand {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 58px;
  padding: 0 10px 21px;
  border-bottom: 1px solid #edf1f8;
}

.logo {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 11px;
  background: linear-gradient(135deg, #2f76f4, #6038f5);
  box-shadow: 0 8px 16px rgba(65, 92, 230, .24);
}

.logo-img { width: 100%; height: 100%; object-fit: cover; }
.brand-copy { min-width: 0; }
.brand-name { overflow: hidden; color: #152554; font-size: 14px; font-weight: 800; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.brand-sub { margin-top: 3px; color: #8b9abb; font-size: 11px; }

.teacher-menu { display: flex; flex-direction: column; gap: 7px; padding: 18px 2px; }
.teacher-menu-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 47px;
  padding: 0 14px;
  border-radius: 11px;
  color: #536487;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background .18s, color .18s, box-shadow .18s, transform .18s;
}
.teacher-menu-item:hover { color: #315ee8; background: #f1f5ff; }
.teacher-menu-item.active {
  color: #fff;
  background: linear-gradient(101deg, #3b82f6, #7042f4);
  box-shadow: 0 10px 18px rgba(72, 87, 222, .23);
}
.tmi-icon { display: inline-flex; align-items: center; justify-content: center; width: 19px; }
.menu-badge {
  min-width: 21px;
  height: 21px;
  margin-left: auto;
  padding: 0 6px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: #fff;
  color: #e83b55;
  font-size: 11px;
  font-weight: 800;
}
.teacher-menu-item:not(.active) .menu-badge { background: #fff1f2; color: #e83b55; }

.teacher-foot { display: flex; flex-direction: column; gap: 7px; margin-top: auto; padding: 0 2px; }
.teacher-foot .logout { color: #ef4444; }
.teacher-foot .logout:hover { color: #dc2626; background: #fff1f2; }

.teacher-main { min-width: 0; flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.teacher-top {
  min-height: 91px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 14px 30px 13px 36px;
  background: rgba(255, 255, 255, .88);
  border-bottom: 1px solid #edf1f8;
}
.teacher-top-title-block h1 { margin: 0; color: #172554; font-size: 26px; font-weight: 800; letter-spacing: -.02em; line-height: 1.2; }
.teacher-top-title-block p { margin: 6px 0 0; color: #8190ad; font-size: 13px; }
.top-actions { display: flex; align-items: center; justify-content: flex-end; gap: 18px; min-width: 0; }
.top-search {
  width: min(273px, 24vw);
  height: 44px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border: 1px solid #dbe3f1;
  border-radius: 10px;
  background: #fff;
  color: #7890b9;
  transition: border-color .18s, box-shadow .18s;
}
.top-search:focus-within { border-color: #6f7ff5; box-shadow: 0 0 0 3px rgba(93, 103, 235, .1); }
.top-search input { width: 100%; min-width: 0; border: 0; outline: 0; color: #273965; background: transparent; font: inherit; font-size: 13px; }
.top-search input::placeholder { color: #a1adc1; }
.icon-action { position: relative; width: 40px; height: 40px; display: inline-grid; place-items: center; border: 0; border-radius: 50%; color: #22345e; background: transparent; cursor: pointer; }
.icon-action:hover { background: #eff3ff; color: #4663e9; }
.notification-dot { position: absolute; top: 4px; right: 3px; width: 9px; height: 9px; border: 2px solid #fff; border-radius: 50%; background: #ef4444; }
.teacher-user { display: flex; align-items: center; gap: 9px; white-space: nowrap; }
.avatar { width: 40px; height: 40px; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: linear-gradient(135deg, #4e50ed, #5530df); color: #fff; font-size: 15px; font-weight: 700; box-shadow: 0 6px 12px rgba(76, 65, 215, .2); }
.teacher-user-text { display: flex; flex-direction: column; gap: 2px; line-height: 1.2; }
.tu-name { color: #1d2d55; font-size: 13px; font-weight: 700; }
.tu-role { color: #9aa7bd; font-size: 11px; }
.user-chevron { color: #8292ae; margin-left: 3px; }
.new-project-btn { height: 44px; display: inline-flex; align-items: center; gap: 7px; padding: 0 18px; border: 0; border-radius: 11px; color: #fff; background: linear-gradient(101deg, #3d76f4, #643bf0); box-shadow: 0 9px 18px rgba(72, 78, 218, .24); font: inherit; font-size: 13px; font-weight: 700; cursor: pointer; transition: transform .18s, box-shadow .18s; }
.new-project-btn:hover { transform: translateY(-1px); box-shadow: 0 12px 21px rgba(72, 78, 218, .31); }
.teacher-content { flex: 1; overflow-y: auto; padding: 28px 34px 42px 36px; }

@media (max-width: 1150px) {
  .teacher-side { width: 210px; }
  .teacher-top { padding-left: 24px; padding-right: 22px; }
  .teacher-content { padding-left: 24px; padding-right: 24px; }
  .top-actions { gap: 10px; }
  .top-search { width: 220px; }
  .new-project-btn { padding: 0 13px; }
}

@media (max-width: 860px) {
  .teacher-layout { height: auto; min-height: 100vh; overflow: visible; }
  .teacher-side { width: 74px; padding: 14px 10px; }
  .teacher-brand { justify-content: center; padding: 0 0 17px; }
  .brand-copy, .teacher-menu-item > span:not(.tmi-icon), .teacher-foot .teacher-menu-item > span:not(.tmi-icon) { display: none; }
  .teacher-menu-item { justify-content: center; padding: 0; }
  .menu-badge { position: absolute; top: 2px; right: 2px; min-width: 17px; height: 17px; padding: 0 4px; font-size: 10px; }
  .teacher-top { align-items: flex-start; flex-direction: column; gap: 14px; }
  .top-actions { width: 100%; justify-content: flex-start; flex-wrap: wrap; }
  .top-search { flex: 1; width: auto; min-width: 180px; }
  .teacher-content { padding: 22px 16px 32px; }
}

@media (max-width: 520px) {
  .teacher-user-text, .user-chevron { display: none; }
  .new-project-btn span { display: none; }
  .new-project-btn { width: 42px; padding: 0; justify-content: center; }
}
</style>
