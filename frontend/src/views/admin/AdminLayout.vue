<template>
  <div class="admin-layout" @keydown.esc="menuOpen = false">
    <button v-if="menuOpen" type="button" class="menu-backdrop" aria-label="关闭导航菜单" @click="menuOpen = false"></button>
    <aside id="admin-navigation" class="admin-side" :class="{ 'is-open': menuOpen }">
      <div class="admin-brand">
        <span class="logo">
          <img v-if="site.logoUrl" :src="site.logoUrl" class="logo-img" alt="LOGO" />
          <ShieldCheck v-else :size="22" color="#fff" />
        </span>
        <div>
          <div class="brand-name">{{ site.title }}</div>
          <div class="brand-sub">管理员控制台</div>
        </div>
      </div>
      <nav class="admin-menu" aria-label="后台导航">
        <section v-for="group in menuGroups" :key="group.key" class="menu-group">
          <div class="menu-group-label">{{ group.title }}</div>
          <router-link v-for="m in group.items" :key="m.path" :to="m.path" class="admin-menu-item"
                       :class="{ active: $route.path.startsWith(m.path) }">
            <span class="ami-icon"><component :is="m.icon" :size="17" /></span>
            <span class="ami-title">{{ m.title }}</span>
            <el-badge v-if="m.path === '/admin/borrows' && pendingCount > 0"
                      :value="pendingCount" class="menu-badge" />
          </router-link>
        </section>
      </nav>
      <div class="admin-foot">
        <a v-if="authStore.isAdmin" class="admin-menu-item screen-link" href="/admin/screen" target="_blank">
          <span class="ami-icon"><MonitorPlay :size="17" /></span>数据大屏 <ArrowUpRight :size="13" class="external-icon" />
        </a>
        <a v-if="authStore.isAdmin" class="admin-menu-item kicad-link" href="/hw/admin" target="_blank" title="硬件设计助手的用量与用户管理(本站)">
          <span class="ami-icon"><CircuitBoard :size="17" /></span>硬件助手管理 <ArrowUpRight :size="13" class="external-icon" />
        </a>
        <a class="admin-menu-item" @click="$router.push('/app/feedback')">
          <span class="ami-icon"><MessageSquarePlus :size="17" /></span>提交问题反馈
        </a>
        <a class="admin-menu-item" @click="$router.push('/app/dashboard')">
          <span class="ami-icon"><GraduationCap :size="17" /></span>学生端视图
        </a>
        <a class="admin-menu-item logout" @click="logout">
          <span class="ami-icon"><LogOut :size="17" /></span>退出登录
        </a>
      </div>
    </aside>

    <div class="admin-main">
      <header class="admin-top">
        <div class="admin-top-heading">
          <button type="button" class="menu-toggle" :aria-expanded="menuOpen" aria-controls="admin-navigation" :aria-label="menuOpen ? '关闭导航菜单' : '打开导航菜单'" @click="menuOpen = !menuOpen"><X v-if="menuOpen" :size="20" /><Menu v-else :size="20" /></button>
          <span class="admin-top-title">{{ currentTitle }}</span>
        </div>
        <div class="admin-user">
          <span class="avatar">{{ (authStore.user?.name || '管')[0] }}</span>
          <div class="admin-user-text">
            <span class="au-name">{{ authStore.user?.name }}</span>
            <span class="au-role">{{ authStore.user?.role === 'LAB_ADMIN' ? '实验室管理员' : '系统管理员' }}</span>
          </div>
        </div>
      </header>
      <main class="admin-content">
        <router-view @refresh-pending="loadPending" />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowUpRight, Bell, Bot, CircuitBoard, ClipboardCheck, ClipboardList, GraduationCap, LayoutDashboard, LogOut, Menu,
  MessageSquarePlus, MessageSquareText, MonitorPlay, Radar, Rocket, School, ScrollText, Settings, ShieldCheck, Sparkles, Store, UserRoundCheck, Users, Wrench, X
} from 'lucide-vue-next'
import { adminStats } from '../../api'
import { useAuthStore } from '../../stores/auth'
import { loadSiteConfig, siteConfig as site } from '../../utils/siteConfig'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const pendingCount = ref(0)
const menuOpen = ref(false)
watch(() => route.path, () => { menuOpen.value = false })

const allMenus = [
  { path: '/admin/dashboard', icon: LayoutDashboard, title: '数据看板', group: 'workspace', lab: true },
  { path: '/admin/equipment', icon: Wrench, title: '设备管理', group: 'operations', lab: true },
  { path: '/admin/borrows', icon: ClipboardList, title: '借阅审批', group: 'operations', lab: true },
  { path: '/admin/projects', icon: Rocket, title: '项目管理', group: 'operations' },
  { path: '/admin/classes', icon: School, title: '班级管理', group: 'operations' },
  { path: '/admin/enrollments', icon: UserRoundCheck, title: '报名进度', group: 'operations' },
  { path: '/admin/submissions', icon: ClipboardCheck, title: '成果评审', group: 'operations' },
  { path: '/admin/skill-dimensions', icon: Radar, title: '技能维度', group: 'operations' },
  { path: '/admin/notifications', icon: Bell, title: '通知管理', group: 'operations' },
  { path: '/admin/discussions', icon: MessageSquareText, title: '讨论管理', group: 'operations' },
  { path: '/admin/feedbacks', icon: MessageSquarePlus, title: '问题反馈', group: 'operations' },
  { path: '/admin/users', icon: Users, title: '用户管理', group: 'operations' },
  { path: '/admin/store', icon: Store, title: '项目商店', group: 'operations' },
  { path: '/admin/ai-center', icon: Bot, title: 'AI 中心', group: 'configuration' },
  { path: '/admin/ai-settings', icon: Sparkles, title: 'AI 配置', group: 'configuration' },
  { path: '/admin/site-settings', icon: Settings, title: '站点设置', group: 'configuration' },
  { path: '/admin/audit-logs', icon: ScrollText, title: '操作日志', group: 'configuration' }
]
// 实验室管理员只看设备与借阅相关入口
const menus = computed(() => (authStore.user?.role === 'LAB_ADMIN' ? allMenus.filter((m) => m.lab) : allMenus))
const menuGroups = computed(() => {
  const groups = [
    { key: 'workspace', title: '工作台' },
    { key: 'operations', title: '业务管理' },
    { key: 'configuration', title: '配置中心' }
  ]
  return groups.map((group) => ({ ...group, items: menus.value.filter((m) => m.group === group.key) }))
    .filter((group) => group.items.length)
})

const currentTitle = computed(() =>
  allMenus.find((m) => route.path.startsWith(m.path))?.title || '管理后台')

const loadPending = async () => {
  try {
    const s = await adminStats()
    pendingCount.value = s.pendingBorrows
  } catch (e) { /* 忽略 */ }
}

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
.admin-layout { display: flex; height: 100vh; height: 100dvh; overflow: hidden; background: #f7f8fb; }

.admin-side {
  width: 232px;
  background: #fff;
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  padding: 20px 12px;
  flex-shrink: 0;
}
.admin-brand { display: flex; gap: 10px; align-items: center; padding: 0 8px 20px; }
.logo {
  width: 40px; height: 40px; border-radius: 10px; overflow: hidden; flex-shrink: 0;
  background: var(--brand-gradient);
  display: flex; align-items: center; justify-content: center; font-size: 20px;
  box-shadow: var(--shadow-card);
}
.logo-img { width: 100%; height: 100%; object-fit: cover; }
.brand-name { font-weight: 700; font-size: 14px; color: #111827; line-height: 1.3; }
.brand-sub { font-size: 11px; color: #9ca3af; }

.admin-menu { display: flex; flex: 1; min-height: 0; overflow-y: auto; flex-direction: column; gap: 4px; padding-bottom: 16px; scrollbar-width: thin; }
.menu-group { display: flex; flex-direction: column; gap: 3px; }
.menu-group + .menu-group { margin-top: 13px; }
.menu-group-label { padding: 0 14px 6px; color: #a1a1aa; font-size: 10px; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; }
.admin-menu-item {
  display: flex; align-items: center; gap: 10px;
  padding: 9px 14px; border-radius: 9px;
  font-size: 14px; color: #4b5563; cursor: pointer;
  transition: background .15s;
}
.ami-title { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.admin-menu-item:hover { background: #f3f4f6; }
.admin-menu-item.active { background: #edf2ff; color: #365bd5; font-weight: 600; box-shadow: inset 3px 0 #5272e9; }
.menu-group { flex-shrink: 0; }
.admin-menu-item:focus-visible { outline: 2px solid #5272e9; outline-offset: -2px; }
.ami-icon { display: flex; align-items: center; }
.menu-badge { margin-left: auto; }

.admin-foot { flex-shrink: 0; display: flex; flex-direction: column; gap: 2px; padding-top: 10px; border-top: 1px solid #eef0f5; }
.admin-foot .admin-menu-item { padding: 8px 14px; font-size: 12px; }
.external-icon { margin-left: auto; opacity: .6; }
.admin-foot .screen-link { color: #0e7490; background: linear-gradient(90deg, #ecfeff, #f0f9ff); text-decoration: none; }
.admin-foot .screen-link:hover { background: #cffafe; }
.admin-foot .kicad-link { color: #047857; text-decoration: none; }
.admin-foot .kicad-link:hover { background: #ecfdf5; }
.admin-foot .logout { color: #dc2626; }
.admin-foot .logout:hover { background: #fef2f2; }

.admin-main { flex: 1; min-width: 0; display: flex; flex-direction: column; overflow: hidden; }
.admin-top-heading { display: flex; align-items: center; gap: 12px; }
.menu-toggle, .menu-backdrop { display: none; }
.admin-top {
  height: 60px; background: #fff; border-bottom: 1px solid var(--border);
  display: flex; justify-content: space-between; align-items: center;
  padding: 0 24px; flex-shrink: 0;
}
.admin-top-title { font-weight: 700; font-size: 16px; }
.admin-user { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.avatar {
  width: 34px; height: 34px; border-radius: 50%;
  background: var(--brand-gradient); color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 13px;
}
.admin-user-text { display: flex; flex-direction: column; line-height: 1.3; }
.au-name { font-size: 13px; font-weight: 600; color: #111827; }
.au-role { font-size: 11px; color: #9ca3af; }
.admin-content { flex: 1; min-height: 0; overflow-y: auto; padding: 28px; }
@media (max-width: 1200px) {
  .admin-side { width: 208px; padding: 18px 10px; }
  .admin-content { padding: 22px; }
}
@media (max-width: 760px) {
  .admin-side { position: fixed; inset: 60px auto 0 0; z-index: 30; width: 232px; transform: translateX(-100%); transition: transform .18s ease; visibility: hidden; }
  .admin-side.is-open { transform: translateX(0); visibility: visible; }
  .menu-backdrop { display: block; position: fixed; inset: 60px 0 0; z-index: 29; border: 0; background: rgba(15,23,42,.3); }
  .menu-toggle { display: grid; place-items: center; width: 32px; height: 32px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #475569; cursor: pointer; }
  .menu-toggle:focus-visible { outline: 2px solid #5272e9; outline-offset: 2px; }
  .admin-content { padding: 18px 14px; }
  .admin-top { padding: 0 14px; }
}
@media (prefers-reduced-motion: reduce) { .admin-side { transition: none; } }
</style>
