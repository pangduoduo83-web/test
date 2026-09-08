<template>
  <div class="platform-login">
    <div class="card box">
      <div class="brand">
        <span class="logo"><Store :size="22" color="#fff" /></span>
        <div>
          <div class="brand-name">项目商店 · 平台管理</div>
          <div class="brand-sub">审核上架、定向分享、客户接入</div>
        </div>
      </div>
      <el-form @submit.prevent="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="平台管理员账号" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码" size="large" @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" size="large" class="btn" :loading="loading" @click="submit">登录</el-button>
      </el-form>
      <p class="hint">此账号由项目商店服务初始化时创建(HUB_ADMIN_USERNAME / HUB_ADMIN_PASSWORD),与各客户站点的账号无关。</p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Store } from 'lucide-vue-next'
import { hubLogin, setHubAuth } from '../../api/hub'

const router = useRouter()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

const submit = async () => {
  if (!form.username || !form.password) return
  loading.value = true
  try {
    const r = await hubLogin({ username: form.username, password: form.password })
    setHubAuth(r.token, r.username)
    router.push('/platform/items')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.platform-login { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f3f4f6; }
.box { width: 400px; padding: 32px; }
.brand { display: flex; gap: 12px; align-items: center; margin-bottom: 24px; }
.logo { width: 42px; height: 42px; border-radius: 10px; background: var(--brand-gradient); display: flex; align-items: center; justify-content: center; }
.brand-name { font-weight: 700; font-size: 16px; }
.brand-sub { font-size: 12px; color: #9ca3af; }
.btn { width: 100%; }
.hint { font-size: 12px; color: #9ca3af; margin: 16px 0 0; line-height: 1.6; }
</style>
