<template>
  <div class="pf-root login">
    <!-- 左侧品牌面板 -->
    <aside class="hero">
      <div class="hero-grid"></div>
      <div class="hero-glow a"></div>
      <div class="hero-glow b"></div>
      <div class="hero-inner">
        <div class="hero-brand">
          <span class="pf-brand-mark"><Store :size="22" color="#fff" /></span>
          <span>项目商店 · 平台控制台</span>
        </div>
        <h1>一处审核,<br />百所院校同步上架</h1>
        <p class="hero-lead">
          客户站点把教学项目发布到商店,你在这里审核上架、决定谁能看到、为新客户一键开通站点。
        </p>
        <ul class="hero-points">
          <li><span class="pt-icon"><PackageCheck :size="16" /></span><div><b>审核与版本</b><span>每次发布形成不可变版本,驳回不影响已上架内容</span></div></li>
          <li><span class="pt-icon"><Share2 :size="16" /></span><div><b>定向分享</b><span>公开给所有客户,或只给指定院校可见</span></div></li>
          <li><span class="pt-icon"><Building2 :size="16" /></span><div><b>客户站点开通</b><span>建库、初始化管理员、签发商店密钥,一次完成</span></div></li>
        </ul>
        <div class="hero-foot">IOEDU 多租户实践教学平台</div>
      </div>
    </aside>

    <!-- 右侧登录表单 -->
    <main class="panel">
      <div class="panel-inner">
        <div class="panel-head">
          <div class="eyebrow">PLATFORM CONSOLE</div>
          <h2>登录平台控制台</h2>
          <p>使用平台管理员账号登录。该账号独立于任何客户站点的用户体系。</p>
        </div>
        <el-form class="form" label-position="top" size="large" @submit.prevent="submit">
          <el-form-item label="账号">
            <el-input v-model="form.username" placeholder="平台管理员账号" autocomplete="username">
              <template #prefix><UserRound :size="16" /></template>
            </el-input>
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="密码" autocomplete="current-password"
                      @keyup.enter="submit">
              <template #prefix><KeyRound :size="16" /></template>
            </el-input>
          </el-form-item>
          <el-button type="primary" size="large" class="submit" :loading="loading" :disabled="!form.username || !form.password" @click="submit">
            登录
          </el-button>
        </el-form>
        <div class="panel-foot">
          <span>首个管理员由商店服务初始化时创建</span>
          <span class="dot">·</span>
          <span>密码在部署环境的 <code>HUB_ADMIN_PASSWORD</code> 或启动日志中</span>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Building2, KeyRound, PackageCheck, Share2, Store, UserRound } from 'lucide-vue-next'
import { hubLogin, setHubAuth } from '../../api/hub'
import '../../styles/platform.css'

const router = useRouter()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

const submit = async () => {
  if (!form.username || !form.password) return
  loading.value = true
  try {
    const r = await hubLogin({ username: form.username.trim(), password: form.password })
    setHubAuth(r.token, r.username)
    router.push('/platform/home')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login { min-height: 100vh; display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(420px, .9fr); background: #fff; }

.hero { position: relative; overflow: hidden; background: radial-gradient(1200px 600px at 20% 10%, #1e1b4b 0%, #0b1020 55%, #060913 100%); color: #e2e8f0; }
.hero-grid {
  position: absolute; inset: 0; opacity: .18;
  background-image: linear-gradient(rgba(148, 163, 184, .35) 1px, transparent 1px), linear-gradient(90deg, rgba(148, 163, 184, .35) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse at center, #000 30%, transparent 75%);
}
.hero-glow { position: absolute; border-radius: 50%; filter: blur(70px); opacity: .55; }
.hero-glow.a { width: 420px; height: 420px; background: #6366f1; top: -120px; right: -80px; }
.hero-glow.b { width: 360px; height: 360px; background: #a855f7; bottom: -140px; left: -60px; }
.hero-inner { position: relative; height: 100%; display: flex; flex-direction: column; padding: 48px 56px; max-width: 640px; }
.hero-brand { display: flex; align-items: center; gap: 12px; font-weight: 700; font-size: 15px; color: #c7d2fe; }
.hero h1 { font-size: 40px; line-height: 1.15; font-weight: 800; letter-spacing: -.5px; margin: 72px 0 18px; color: #fff; }
.hero-lead { font-size: 15px; line-height: 1.8; color: #a5b4fc; margin: 0 0 40px; max-width: 480px; }
.hero-points { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 16px; }
.hero-points li { display: flex; gap: 14px; align-items: flex-start; }
.hero-points li div { display: flex; flex-direction: column; gap: 2px; }
.hero-points b { color: #fff; font-size: 14px; }
.hero-points span:not(.pt-icon) { color: #94a3b8; font-size: 13px; }
.pt-icon { width: 34px; height: 34px; border-radius: 10px; display: grid; place-items: center; background: rgba(99, 102, 241, .18); color: #a5b4fc; border: 1px solid rgba(165, 180, 252, .25); flex-shrink: 0; }
.hero-foot { margin-top: auto; font-size: 12px; color: #475569; letter-spacing: .1em; }

.panel { display: flex; align-items: center; justify-content: center; padding: 48px 32px; background: #fff; }
.panel-inner { width: 100%; max-width: 400px; }
.eyebrow { font-size: 11px; letter-spacing: .18em; color: var(--pf-primary); font-weight: 700; margin-bottom: 12px; }
.panel-head h2 { font-size: 28px; font-weight: 800; margin: 0 0 8px; letter-spacing: -.3px; }
.panel-head p { color: var(--pf-text-2); font-size: 14px; line-height: 1.7; margin: 0 0 30px; }
.form :deep(.el-form-item) { margin-bottom: 20px; }
.form :deep(.el-input__wrapper) { padding: 4px 14px; }
.submit { width: 100%; height: 46px; font-size: 15px; font-weight: 600; margin-top: 4px; box-shadow: 0 10px 24px -10px rgba(79, 70, 229, .7); }
.panel-foot { margin-top: 26px; font-size: 12px; color: var(--pf-text-3); line-height: 1.8; }
.panel-foot .dot { margin: 0 6px; }
.panel-foot code { background: #f1f5f9; padding: 1px 6px; border-radius: 4px; color: var(--pf-text-2); }

@media (max-width: 900px) {
  .login { grid-template-columns: 1fr; }
  .hero { display: none; }
}
</style>
