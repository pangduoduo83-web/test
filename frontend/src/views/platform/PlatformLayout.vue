<template>
  <div class="admin-layout">
    <aside class="admin-side">
      <div class="admin-brand">
        <span class="logo"><Store :size="22" color="#fff" /></span>
        <div>
          <div class="brand-name">项目商店</div>
          <div class="brand-sub">平台管理</div>
        </div>
      </div>
      <nav class="admin-menu">
        <router-link v-for="m in menus" :key="m.path" :to="m.path" class="admin-menu-item"
                     :class="{ active: $route.path.startsWith(m.path) }">
          <span class="ami-icon"><component :is="m.icon" :size="17" /></span>{{ m.title }}
          <el-badge v-if="m.path === '/platform/items' && stats.pending > 0" :value="stats.pending" class="menu-badge" />
        </router-link>
      </nav>
      <div class="admin-foot">
        <a class="admin-menu-item" @click="changePassword"><span class="ami-icon"><KeyRound :size="17" /></span>修改密码</a>
        <a class="admin-menu-item logout" @click="logout"><span class="ami-icon"><LogOut :size="17" /></span>退出登录</a>
      </div>
    </aside>
    <div class="admin-main">
      <header class="admin-top">
        <span class="admin-top-title">{{ currentTitle }}</span>
        <div class="stats">
          <span>待审核 <b>{{ stats.pending ?? '-' }}</b></span>
          <span>已上架 <b>{{ stats.approved ?? '-' }}</b></span>
          <span>接入客户 <b>{{ stats.tenants ?? '-' }}</b></span>
          <span>累计安装 <b>{{ stats.installs ?? '-' }}</b></span>
          <span class="who">{{ adminName }}</span>
        </div>
      </header>
      <main class="admin-content">
        <router-view @refresh-stats="loadStats" />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Building2, KeyRound, LogOut, PackageCheck, Store } from 'lucide-vue-next'
import { clearHubAuth, getHubAdminName, hubChangePassword, hubStats } from '../../api/hub'

const route = useRoute()
const router = useRouter()
const stats = ref({})
const adminName = getHubAdminName()

const menus = [
  { path: '/platform/items', icon: PackageCheck, title: '条目审核与分享' },
  { path: '/platform/tenants', icon: Building2, title: '客户接入' }
]
const currentTitle = computed(() => menus.find((m) => route.path.startsWith(m.path))?.title || '平台管理')

const loadStats = async () => {
  try { stats.value = await hubStats() } catch (e) { /* 已提示 */ }
}

const changePassword = async () => {
  try {
    const { value: oldPassword } = await ElMessageBox.prompt('请输入当前密码', '修改密码', { inputType: 'password' })
    const { value: newPassword } = await ElMessageBox.prompt('请输入新密码(不少于 8 位)', '修改密码', { inputType: 'password' })
    await hubChangePassword({ oldPassword, newPassword })
    ElMessage.success('密码已修改')
  } catch (e) { /* 取消或已提示 */ }
}

const logout = () => {
  clearHubAuth()
  router.push('/platform/login')
}

onMounted(loadStats)
</script>

<style scoped>
.admin-layout { display: flex; height: 100vh; background: #f9fafb; }
.admin-side { width: 232px; background: #fff; border-right: 1px solid var(--border); display: flex; flex-direction: column; padding: 20px 12px; flex-shrink: 0; }
.admin-brand { display: flex; gap: 10px; align-items: center; padding: 0 8px 20px; }
.logo { width: 40px; height: 40px; border-radius: 10px; background: var(--brand-gradient); display: flex; align-items: center; justify-content: center; box-shadow: var(--shadow-card); }
.brand-name { font-weight: 700; font-size: 14px; color: #111827; }
.brand-sub { font-size: 11px; color: #9ca3af; }
.admin-menu { display: flex; flex-direction: column; gap: 4px; }
.admin-menu-item { display: flex; align-items: center; gap: 10px; padding: 11px 14px; border-radius: 10px; font-size: 14px; color: #4b5563; cursor: pointer; transition: background .15s; }
.admin-menu-item:hover { background: #f3f4f6; }
.admin-menu-item.active { background: var(--brand-gradient); color: #fff; box-shadow: 0 8px 12px -3px rgba(79,70,229,.35); }
.ami-icon { display: flex; align-items: center; }
.menu-badge { margin-left: auto; }
.admin-foot { margin-top: auto; display: flex; flex-direction: column; gap: 4px; }
.admin-foot .logout { color: #dc2626; }
.admin-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.admin-top { height: 60px; background: #fff; border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center; padding: 0 24px; flex-shrink: 0; }
.admin-top-title { font-weight: 700; font-size: 16px; }
.stats { display: flex; gap: 18px; font-size: 13px; color: #6b7280; }
.stats b { color: #111827; }
.who { color: #4f46e5; font-weight: 600; }
.admin-content { flex: 1; overflow-y: auto; padding: 24px; }
</style>
