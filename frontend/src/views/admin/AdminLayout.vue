<template>
  <div class="admin-layout">
    <aside class="admin-side">
      <div class="admin-brand">
        <span class="logo"><ShieldCheck :size="22" color="#fff" /></span>
        <div>
          <div class="brand-name">项目驱动式教学平台</div>
          <div class="brand-sub">管理员控制台</div>
        </div>
      </div>
      <nav class="admin-menu">
        <router-link v-for="m in menus" :key="m.path" :to="m.path" class="admin-menu-item"
                     :class="{ active: $route.path.startsWith(m.path) }">
          <span class="ami-icon"><component :is="m.icon" :size="17" /></span>{{ m.title }}
          <el-badge v-if="m.path === '/admin/borrows' && pendingCount > 0"
                    :value="pendingCount" class="menu-badge" />
        </router-link>
      </nav>
      <div class="admin-foot">
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
        <span class="admin-top-title">{{ currentTitle }}</span>
        <div class="admin-user">
          <span class="avatar">{{ (authStore.user?.name || '管')[0] }}</span>
          <div class="admin-user-text">
            <span class="au-name">{{ authStore.user?.name }}</span>
            <span class="au-role">系统管理员</span>
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
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell, ClipboardCheck, ClipboardList, GraduationCap, LayoutDashboard, LogOut,
  MessageSquareText, Rocket, ShieldCheck, UserRoundCheck, Users, Wrench
} from 'lucide-vue-next'
import { adminStats } from '../../api'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const pendingCount = ref(0)

const menus = [
  { path: '/admin/dashboard', icon: LayoutDashboard, title: '数据看板' },
  { path: '/admin/equipment', icon: Wrench, title: '设备管理' },
  { path: '/admin/borrows', icon: ClipboardList, title: '借阅审批' },
  { path: '/admin/projects', icon: Rocket, title: '项目管理' },
  { path: '/admin/enrollments', icon: UserRoundCheck, title: '报名进度' },
  { path: '/admin/submissions', icon: ClipboardCheck, title: '成果评审' },
  { path: '/admin/notifications', icon: Bell, title: '通知管理' },
  { path: '/admin/discussions', icon: MessageSquareText, title: '讨论管理' },
  { path: '/admin/users', icon: Users, title: '用户管理' }
]

const currentTitle = computed(() =>
  menus.find((m) => route.path.startsWith(m.path))?.title || '管理后台')

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

onMounted(loadPending)
</script>

<style scoped>
.admin-layout { display: flex; height: 100vh; background: #f9fafb; }

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
  width: 40px; height: 40px; border-radius: 10px;
  background: var(--brand-gradient);
  display: flex; align-items: center; justify-content: center; font-size: 20px;
  box-shadow: var(--shadow-card);
}
.brand-name { font-weight: 700; font-size: 14px; color: #111827; line-height: 1.3; }
.brand-sub { font-size: 11px; color: #9ca3af; }

.admin-menu { display: flex; flex-direction: column; gap: 4px; }
.admin-menu-item {
  display: flex; align-items: center; gap: 10px;
  padding: 11px 14px; border-radius: 10px;
  font-size: 14px; color: #4b5563; cursor: pointer;
  transition: background .15s;
}
.admin-menu-item:hover { background: #f3f4f6; }
.admin-menu-item.active {
  background: var(--brand-gradient);
  color: #fff;
  box-shadow: 0 8px 12px -3px rgba(79,70,229,.35);
}
.ami-icon { display: flex; align-items: center; }
.menu-badge { margin-left: auto; }

.admin-foot { margin-top: auto; display: flex; flex-direction: column; gap: 4px; }
.admin-foot .logout { color: #dc2626; }
.admin-foot .logout:hover { background: #fef2f2; }

.admin-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
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
.admin-content { flex: 1; overflow-y: auto; padding: 24px; }
</style>
