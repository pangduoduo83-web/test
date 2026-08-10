<template>
  <div class="teacher-layout">
    <aside class="teacher-side">
      <div class="teacher-brand">
        <span class="logo"><BookOpen :size="22" color="#fff" /></span>
        <div>
          <div class="brand-name">项目驱动式教学平台</div>
          <div class="brand-sub">教师工作台</div>
        </div>
      </div>
      <nav class="teacher-menu">
        <router-link to="/teacher/workbench" class="teacher-menu-item"
                     :class="{ active: $route.path.startsWith('/teacher/workbench') }">
          <span class="tmi-icon"><LayoutDashboard :size="17" /></span>教学工作台
        </router-link>
      </nav>
      <div class="teacher-foot">
        <a class="teacher-menu-item" @click="$router.push('/app/dashboard')">
          <span class="tmi-icon"><GraduationCap :size="17" /></span>学生端视图
        </a>
        <a class="teacher-menu-item logout" @click="logout">
          <span class="tmi-icon"><LogOut :size="17" /></span>退出登录
        </a>
      </div>
    </aside>

    <div class="teacher-main">
      <header class="teacher-top">
        <span class="teacher-top-title">教学工作台</span>
        <div class="teacher-user">
          <span class="avatar">{{ (authStore.user?.name || '师')[0] }}</span>
          <div class="teacher-user-text">
            <span class="tu-name">{{ authStore.user?.name }}</span>
            <span class="tu-role">认证讲师</span>
          </div>
        </div>
      </header>
      <main class="teacher-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { BookOpen, GraduationCap, LayoutDashboard, LogOut } from 'lucide-vue-next'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const logout = () => {
  authStore.logout()
  router.push('/auth')
}
</script>

<style scoped>
.teacher-layout { display: flex; height: 100vh; background: #f9fafb; }

.teacher-side {
  width: 232px;
  background: #fff;
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  padding: 20px 12px;
  flex-shrink: 0;
}
.teacher-brand { display: flex; gap: 10px; align-items: center; padding: 0 8px 20px; }
.logo {
  width: 40px; height: 40px; border-radius: 10px;
  background: var(--brand-gradient);
  display: flex; align-items: center; justify-content: center;
  box-shadow: var(--shadow-card);
}
.brand-name { font-weight: 700; font-size: 14px; color: #111827; line-height: 1.3; }
.brand-sub { font-size: 11px; color: #9ca3af; }

.teacher-menu { display: flex; flex-direction: column; gap: 4px; }
.teacher-menu-item {
  display: flex; align-items: center; gap: 10px;
  padding: 11px 14px; border-radius: 10px;
  font-size: 14px; color: #4b5563; cursor: pointer;
  transition: background .15s;
}
.teacher-menu-item:hover { background: #f3f4f6; }
.teacher-menu-item.active {
  background: var(--brand-gradient);
  color: #fff;
  box-shadow: 0 8px 12px -3px rgba(79,70,229,.35);
}
.tmi-icon { display: flex; align-items: center; }

.teacher-foot { margin-top: auto; display: flex; flex-direction: column; gap: 4px; }
.teacher-foot .logout { color: #dc2626; }
.teacher-foot .logout:hover { background: #fef2f2; }

.teacher-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.teacher-top {
  height: 60px; background: #fff; border-bottom: 1px solid var(--border);
  display: flex; justify-content: space-between; align-items: center;
  padding: 0 24px; flex-shrink: 0;
}
.teacher-top-title { font-weight: 700; font-size: 16px; }
.teacher-user { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.avatar {
  width: 34px; height: 34px; border-radius: 50%;
  background: var(--brand-gradient); color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 13px;
}
.teacher-user-text { display: flex; flex-direction: column; line-height: 1.3; }
.tu-name { font-size: 13px; font-weight: 600; color: #111827; }
.tu-role { font-size: 11px; color: #9ca3af; }
.teacher-content { flex: 1; overflow-y: auto; padding: 24px; }
</style>
