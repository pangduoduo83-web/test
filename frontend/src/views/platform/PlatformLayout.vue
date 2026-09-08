<template>
  <div class="pf-root pf-shell">
    <aside class="pf-side">
      <div class="pf-brand">
        <span class="pf-brand-mark"><Store :size="20" color="#fff" /></span>
        <div>
          <div class="pf-brand-name">项目商店</div>
          <div class="pf-brand-sub">平台控制台</div>
        </div>
      </div>

      <nav class="pf-nav">
        <div class="pf-nav-group">商店运营</div>
        <router-link to="/platform/items" class="pf-nav-item" :class="{ active: $route.path.startsWith('/platform/items') }">
          <PackageCheck :size="17" />条目审核与分享
          <span v-if="stats.pending > 0" class="pf-nav-badge">{{ stats.pending }}</span>
        </router-link>
        <div class="pf-nav-group">客户</div>
        <router-link to="/platform/sites" class="pf-nav-item" :class="{ active: $route.path.startsWith('/platform/sites') }">
          <Building2 :size="17" />客户站点
        </router-link>
      </nav>

      <div class="pf-side-foot">
        <div class="pf-user">
          <span class="pf-avatar">{{ (adminName || 'P')[0].toUpperCase() }}</span>
          <div>
            <div class="pf-user-name">{{ adminName }}</div>
            <div class="pf-user-role">平台管理员</div>
          </div>
        </div>
        <a class="pf-side-link" @click="changePassword"><KeyRound :size="15" />修改密码</a>
        <a class="pf-side-link danger" @click="logout"><LogOut :size="15" />退出登录</a>
      </div>
    </aside>

    <div class="pf-main">
      <header class="pf-topbar">
        <div>
          <div class="pf-topbar-title">{{ current.title }}</div>
          <div class="pf-topbar-sub">{{ current.desc }}</div>
        </div>
        <div class="pf-topbar-right">
          <span class="pf-kpi"><span class="dot" style="background:#f59e0b"></span>待审核 <b>{{ stats.pending ?? '–' }}</b></span>
          <span class="pf-kpi"><span class="dot" style="background:#10b981"></span>已上架 <b>{{ stats.approved ?? '–' }}</b></span>
          <span class="pf-kpi"><span class="dot" style="background:#6366f1"></span>接入客户 <b>{{ stats.tenants ?? '–' }}</b></span>
          <span class="pf-kpi"><span class="dot" style="background:#0ea5e9"></span>累计安装 <b>{{ stats.installs ?? '–' }}</b></span>
        </div>
      </header>
      <main class="pf-content">
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
import '../../styles/platform.css'

const route = useRoute()
const router = useRouter()
const stats = ref({})
const adminName = getHubAdminName()

const pages = {
  '/platform/items': { title: '条目审核与分享', desc: '审核客户发布的项目、控制可见范围与定向分享' },
  '/platform/sites': { title: '客户站点', desc: '开通新客户站点、启停与注销、维护商店接入密钥' }
}
const current = computed(() => Object.entries(pages).find(([p]) => route.path.startsWith(p))?.[1] || { title: '平台控制台', desc: '' })

const loadStats = async () => {
  try { stats.value = await hubStats() } catch (e) { /* 已提示 */ }
}

const changePassword = async () => {
  try {
    const { value: oldPassword } = await ElMessageBox.prompt('请输入当前密码', '修改密码', { inputType: 'password', confirmButtonText: '下一步', cancelButtonText: '取消' })
    const { value: newPassword } = await ElMessageBox.prompt('请输入新密码(不少于 8 位)', '修改密码', { inputType: 'password', confirmButtonText: '确认修改', cancelButtonText: '取消' })
    await hubChangePassword({ oldPassword, newPassword })
    ElMessage.success('密码已修改,下次登录请使用新密码')
  } catch (e) { /* 取消或已提示 */ }
}

const logout = () => {
  clearHubAuth()
  router.push('/platform/login')
}

onMounted(loadStats)
</script>
