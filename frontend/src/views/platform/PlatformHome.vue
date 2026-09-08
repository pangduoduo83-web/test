<template>
  <div>
    <div class="pf-page-head">
      <div>
        <h2 class="pf-page-title">总览</h2>
        <p class="pf-page-desc">今天要处理的审核、已开通的客户站点，以及把项目从学校送到商店的完整路径。</p>
      </div>
      <div class="head-actions">
        <el-button @click="$router.push('/platform/sites')">管理客户站点</el-button>
        <el-button type="primary" @click="$router.push('/platform/items')">去审核条目</el-button>
      </div>
    </div>

    <div class="pf-stats">
      <div class="pf-card pf-stat click" @click="$router.push('/platform/items')">
        <span class="pf-stat-icon" style="background:#fffbeb;color:#d97706"><Clock3 :size="20" /></span>
        <div><div class="pf-stat-value">{{ stats.pending ?? '–' }}</div><div class="pf-stat-label">待审核条目</div></div>
      </div>
      <div class="pf-card pf-stat click" @click="$router.push('/platform/items')">
        <span class="pf-stat-icon" style="background:#ecfdf5;color:#059669"><CircleCheckBig :size="20" /></span>
        <div><div class="pf-stat-value">{{ stats.approved ?? '–' }}</div><div class="pf-stat-label">已上架</div></div>
      </div>
      <div class="pf-card pf-stat click" @click="$router.push('/platform/sites')">
        <span class="pf-stat-icon" style="background:#eef2ff;color:#4f46e5"><Building2 :size="20" /></span>
        <div><div class="pf-stat-value">{{ sites.length }}</div><div class="pf-stat-label">客户站点</div></div>
      </div>
      <div class="pf-card pf-stat">
        <span class="pf-stat-icon" style="background:#f0f9ff;color:#0284c7"><Download :size="20" /></span>
        <div><div class="pf-stat-value">{{ stats.installs ?? '–' }}</div><div class="pf-stat-label">累计安装次数</div></div>
      </div>
    </div>

    <div class="grid-2">
      <div class="pf-card">
        <div class="pf-card-head">
          <div class="pf-card-title">待你处理</div>
          <a class="pf-link" @click="$router.push('/platform/items')">全部审核 →</a>
        </div>
        <div v-if="pending.length === 0" class="pf-empty tight">
          <div class="pf-empty-title">没有待审核条目</div>
          <div>学校在「项目商店 → 本站发布」或教师工作台提交后会出现在这里</div>
        </div>
        <div v-else class="queue">
          <div v-for="it in pending.slice(0, 6)" :key="it.id" class="queue-item" @click="$router.push('/platform/items')">
            <img v-if="it.coverUrl" :src="it.coverUrl" class="pf-thumb" alt="" />
            <span v-else class="pf-thumb">📦</span>
            <div class="grow">
              <div class="t">{{ it.title }}</div>
              <div class="s">{{ it.publisherTenantName }} · 最新 v{{ it.latestVersionNo }} · {{ fmt(it.updatedAt) }}</div>
            </div>
            <span class="pf-pill warning">待审核</span>
          </div>
        </div>
      </div>

      <div class="pf-card">
        <div class="pf-card-head">
          <div class="pf-card-title">客户站点</div>
          <a class="pf-link" @click="$router.push('/platform/sites')">管理站点 →</a>
        </div>
        <div class="queue">
          <div v-for="s in sites.slice(0, 6)" :key="s.code" class="queue-item" @click="$router.push('/platform/sites')">
            <span class="site-mark" :class="{ off: s.status !== 'ACTIVE' }">{{ (s.name || s.code)[0] }}</span>
            <div class="grow">
              <div class="t">{{ s.name }}</div>
              <div class="s pf-mono">{{ s.siteUrl || s.code }}</div>
            </div>
            <span class="pf-pill" :class="s.status === 'ACTIVE' ? 'success' : 'danger'">{{ s.status === 'ACTIVE' ? '运行中' : '已停用' }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="pf-section-title">项目是怎么进商店的</div>
    <div class="flow">
      <div class="pf-card flow-step">
        <div class="n">1</div>
        <h3>学校发布</h3>
        <p>管理员在「项目商店 → 本站发布」、教师在「教师工作台」点发布。封面、教案图、资料会一并上传。</p>
      </div>
      <div class="pf-card flow-step">
        <div class="n">2</div>
        <h3>你来审核</h3>
        <p>在「条目审核与分享」通过、驳回或下架。通过后可设所有客户可见，或只给指定学校。</p>
      </div>
      <div class="pf-card flow-step">
        <div class="n">3</div>
        <h3>其他学校安装</h3>
        <p>对方在自己站点的商店里安装，会复制一份到本校（不影响你这边的教学数据）。</p>
      </div>
    </div>

    <div class="pf-note" style="margin-top:18px">
      <Globe :size="16" />
      <div>
        暂不配 HTTPS。客户访问地址是 <b>http://站点编码.labcloud.com.cn:8093</b>（默认站点仍是 www）。
        要让子域名能打开，只需在阿里云解析加一条主机记录 <code>*</code>、类型 A、值填本机公网 IP，不用每个客户单独申请域名。
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Building2, CircleCheckBig, Clock3, Download, Globe } from 'lucide-vue-next'
import { hubItems, hubSites, hubStats } from '../../api/hub'

const stats = ref({})
const pending = ref([])
const sites = ref([])

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

onMounted(async () => {
  const [s, p, list] = await Promise.all([
    hubStats(),
    hubItems('PENDING').catch(() => []),
    hubSites().catch(() => [])
  ])
  stats.value = s
  pending.value = p
  sites.value = list
})
</script>

<style scoped>
.head-actions { display: flex; gap: 8px; }
.click { cursor: pointer; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 8px; }
.queue { padding: 4px 0 8px; }
.queue-item { display: flex; align-items: center; gap: 12px; padding: 12px 22px; cursor: pointer; border-top: 1px solid var(--pf-border); }
.queue-item:first-child { border-top: none; }
.queue-item:hover { background: #f8f9fc; }
.grow { flex: 1; min-width: 0; }
.t { font-weight: 600; font-size: 14px; }
.s { font-size: 12px; color: var(--pf-text-3); margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pf-empty.tight { padding: 36px 16px; }
.site-mark { width: 36px; height: 36px; border-radius: 10px; display: grid; place-items: center; font-weight: 700; color: #fff; background: linear-gradient(135deg, #6366f1, #a855f7); flex-shrink: 0; }
.site-mark.off { background: #cbd5e1; }
.flow { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.flow-step { padding: 20px 22px; }
.flow-step .n { width: 28px; height: 28px; border-radius: 8px; background: var(--pf-primary-soft); color: var(--pf-primary); font-weight: 800; display: grid; place-items: center; margin-bottom: 12px; }
.flow-step h3 { margin: 0 0 8px; font-size: 15px; }
.flow-step p { margin: 0; font-size: 13px; color: var(--pf-text-2); line-height: 1.65; }
@media (max-width: 960px) {
  .grid-2, .flow { grid-template-columns: 1fr; }
}
</style>
