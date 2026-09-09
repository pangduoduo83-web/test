<template>
  <div>
    <div class="pf-page-head">
      <div>
        <h2 class="pf-page-title">客户站点</h2>
        <p class="pf-page-desc">每个客户独立数据库、独立子域名、自己的管理员。开通时一次完成：建库、初始化管理员、登记商店并写入接入密钥。</p>
      </div>
      <el-button type="primary" size="large" :disabled="!config.configured" @click="openCreate">
        <Plus :size="16" style="margin-right:6px" />开通新客户
      </el-button>
    </div>

    <div v-if="config && !config.configured" class="pf-note warning" style="margin-bottom:16px">
      <TriangleAlert :size="16" />
      <div>商店服务尚未配置客户站点后端(<code>HUB_PLATFORM_API_URL</code> / <code>HUB_PLATFORM_TOKEN</code>),此页只能查看,不能开通。请在部署环境补齐后重启商店服务。</div>
    </div>

    <div class="pf-stats">
      <div class="pf-card pf-stat">
        <span class="pf-stat-icon" style="background:#ecfdf5;color:#059669"><Building2 :size="20" /></span>
        <div><div class="pf-stat-value">{{ running }}</div><div class="pf-stat-label">运行中</div></div>
      </div>
      <div class="pf-card pf-stat">
        <span class="pf-stat-icon" style="background:#eef2ff;color:#4f46e5"><Store :size="20" /></span>
        <div><div class="pf-stat-value">{{ storeOn }}</div><div class="pf-stat-label">已接入商店</div></div>
      </div>
      <div class="pf-card pf-stat">
        <span class="pf-stat-icon" style="background:#fef2f2;color:#dc2626"><Pause :size="20" /></span>
        <div><div class="pf-stat-value">{{ stopped }}</div><div class="pf-stat-label">已停用</div></div>
      </div>
      <div class="pf-card pf-stat">
        <span class="pf-stat-icon" style="background:#f8fafc;color:#475569"><Database :size="20" /></span>
        <div><div class="pf-stat-value">{{ sites.length }}</div><div class="pf-stat-label">站点总数</div></div>
      </div>
    </div>

    <div class="flow">
      <div class="pf-card flow-step"><div class="n">1</div><h3>填编码和名称</h3><p>编码会变成子域名，例如 test-001.labcloud.com.cn，开通后不能改。</p></div>
      <div class="pf-card flow-step"><div class="n">2</div><h3>系统自动建站</h3><p>独立数据库、初始管理员、商店密钥一次写好，不必再 curl。</p></div>
      <div class="pf-card flow-step"><div class="n">3</div><h3>把地址交给客户</h3><p>暂用 HTTP：http://编码.labcloud.com.cn:8093 。阿里云只需一条 * 解析。</p></div>
    </div>

    <div class="pf-note" style="margin-bottom:16px">
      <Globe :size="16" />
      <div>
        现在不配 HTTPS。列表里的地址是 <b>http://子域名:8093</b>。请在阿里云 DNS 增加主机记录 <code>*</code>、A 记录、值填本机公网 IP，之后开通的站点不用再去申请二级域名。
      </div>
    </div>

    <div class="pf-card">
      <div class="pf-card-head">
        <div class="pf-card-title">全部站点 <span class="count">{{ sites.length }}</span></div>
        <div class="pf-toolbar">
          <el-input v-model="keyword" placeholder="搜索编码 / 名称" clearable style="width:220px"><template #prefix><Search :size="14" /></template></el-input>
          <el-button @click="load"><RefreshCw :size="14" style="margin-right:6px" />刷新</el-button>
        </div>
      </div>
      <el-table :data="filtered">
        <el-table-column label="站点" min-width="260">
          <template #default="{ row }">
            <div class="pf-title-cell">
              <span class="site-mark" :class="{ off: row.status !== 'ACTIVE' }">{{ (row.name || row.code)[0] }}</span>
              <div>
                <div class="t">{{ row.name }} <span v-if="row.code === 'default'" class="pf-pill primary" style="margin-left:6px">默认站点</span></div>
                <div class="s">编码 <span class="pf-mono">{{ row.code }}</span> · 库 <span class="pf-mono">{{ row.dbName }}</span></div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="访问地址" min-width="180">
          <template #default="{ row }">
            <a v-if="row.siteUrl" class="pf-link pf-mono" :href="row.siteUrl" target="_blank">{{ row.siteUrl }}</a>
            <span v-else class="s2">{{ row.customDomain || '未配置站点地址模板' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <span v-if="row.expired" class="pf-pill danger">已到期</span>
            <span v-else class="pf-pill" :class="row.status === 'ACTIVE' ? 'success' : 'danger'">{{ row.status === 'ACTIVE' ? '运行中' : '已停用' }}</span>
            <div v-if="row.expiresAt && !row.expired" class="s2" style="margin-top:3px">到期 {{ row.expiresAt }}</div>
          </template>
        </el-table-column>
        <el-table-column label="用量 / 配额" min-width="230">
          <template #default="{ row }">
            <div v-if="usageOf(row.code)" class="usage">
              <div class="u-row"><span>用户</span><b>{{ usageOf(row.code).users }}</b><small v-if="row.maxUsers">/ {{ row.maxUsers }}</small><small class="dim">· 7 天活跃 {{ usageOf(row.code).activeUsers7d }}</small></div>
              <div class="u-row"><span>存储</span><b>{{ usageOf(row.code).storageMb }} MB</b><small v-if="row.storageLimitMb">/ {{ row.storageLimitMb }} MB</small></div>
              <div class="u-row"><span>AI 本月</span><b>{{ fmtK(usageOf(row.code).aiTokensMonth) }}</b><small class="dim">· {{ usageOf(row.code).aiRunsMonth }} 次 · 用站点自己的 Key</small></div>
            </div>
            <span v-else class="s2">加载中…</span>
          </template>
        </el-table-column>
        <el-table-column label="商店接入" width="100">
          <template #default="{ row }">
            <span v-if="!row.storeRegistered" class="pf-pill info">未登记</span>
            <span v-else class="pf-pill" :class="row.storeStatus === 'ACTIVE' ? 'success' : 'warning'">{{ row.storeStatus === 'ACTIVE' ? '已接入' : '已停用' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" align="right" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openQuota(row)">配额</el-button>
            <el-button size="small" @click="rotate(row)">重签密钥</el-button>
            <el-dropdown v-if="row.code !== 'default'" trigger="click" @command="(c) => (c === 'toggle' ? toggle(row) : openDelete(row))">
              <el-button size="small" text>更多 ▾</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="toggle">{{ row.status === 'ACTIVE' ? '停用站点' : '恢复站点' }}</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>注销站点</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <template #empty>
          <div class="pf-empty"><div class="pf-empty-icon">🏫</div><div class="pf-empty-title">还没有客户站点</div><div>点击右上角「开通新客户」创建第一个</div></div>
        </template>
      </el-table>
    </div>

    <!-- 配额 -->
    <el-dialog v-model="quotaVisible" :title="`配额与套餐 · ${quotaRow?.name || ''}`" width="520px" class="pf-root">
      <el-form label-position="top">
        <el-form-item label="套餐名称(仅展示)"><el-input v-model="quotaForm.plan" placeholder="如 标准版 / 试用" maxlength="30" /></el-form-item>
        <div class="two">
          <el-form-item label="用户数上限"><el-input-number v-model="quotaForm.maxUsers" :min="0" :step="50" style="width:100%" /><div class="fh">0 表示不限。达到后不能再注册或新建账号</div></el-form-item>
          <el-form-item label="存储上限(MB)"><el-input-number v-model="quotaForm.storageLimitMb" :min="0" :step="1024" style="width:100%" /><div class="fh">0 表示不限。包含图片、教学资料</div></el-form-item>
          <el-form-item label="到期日"><el-date-picker v-model="quotaForm.expiresAt" type="date" value-format="YYYY-MM-DD" style="width:100%" placeholder="留空为永久" /><div class="fh">到期后站点所有请求返回 403,续期即恢复</div></el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="quotaVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="saveQuota">保存</el-button>
      </template>
    </el-dialog>

    <!-- 开通 -->
    <el-dialog v-model="createVisible" title="开通新客户站点" width="560px" class="pf-root" :close-on-click-modal="false">
      <el-form label-position="top">
        <div class="two">
          <el-form-item label="站点编码" required>
            <el-input v-model="form.code" placeholder="如 c001、shanghai-lab" @input="form.code = form.code.toLowerCase()" />
            <div class="fh">小写字母开头,字母/数字/短横线;将成为子域名前缀与数据库名后缀,开通后不可改</div>
          </el-form-item>
          <el-form-item label="客户名称" required>
            <el-input v-model="form.name" placeholder="某某学院 · 电子信息实践中心" />
          </el-form-item>
        </div>
        <div class="two">
          <el-form-item label="管理员邮箱">
            <el-input v-model="form.adminEmail" placeholder="admin@ioedu.cn" />
          </el-form-item>
          <el-form-item label="管理员初始密码">
            <el-input v-model="form.adminPassword" placeholder="留空则随机生成" show-password />
          </el-form-item>
        </div>
        <el-form-item label="自有域名(可选)">
          <el-input v-model="form.customDomain" placeholder="客户自己的域名,如 lab.xxx.edu.cn;留空使用子域名" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.seedDemo">写入演示数据(演示师生账号、10 个项目、12 台设备,仅用于演示站)</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" :disabled="!form.code || !form.name" @click="create">开通</el-button>
      </template>
    </el-dialog>

    <!-- 开通结果 -->
    <el-dialog v-model="resultVisible" title="站点已开通" width="560px" class="pf-root" :close-on-click-modal="false">
      <div v-if="result">
        <div class="pf-note" style="margin-bottom:16px"><CircleCheckBig :size="16" />请把下面的信息交给客户管理员,初始密码只显示这一次。</div>
        <dl class="pf-kv" style="margin-bottom:14px">
          <dt>站点</dt><dd>{{ result.name }}(<span class="pf-mono">{{ result.code }}</span>)</dd>
          <dt>访问地址</dt><dd><a v-if="result.siteUrl" class="pf-link" :href="result.siteUrl" target="_blank">{{ result.siteUrl }}</a><span v-else>{{ result.siteHost || '按站点编码子域名访问' }}</span></dd>
          <dt>管理员账号</dt><dd class="pf-mono">{{ result.adminEmail }}</dd>
          <dt>商店接入</dt><dd>{{ result.storeKeyConfigured ? '已自动写入接入密钥,商店功能可直接使用' : '密钥未能自动写入,请手动填入下方 Key' }}</dd>
        </dl>
        <div v-if="result.initialAdminPassword" class="pf-section-title">管理员初始密码</div>
        <div v-if="result.initialAdminPassword" class="pf-secret"><code>{{ result.initialAdminPassword }}</code><el-button size="small" @click="copy(result.initialAdminPassword)">复制</el-button></div>
        <div v-if="result.storeApiKey" class="pf-section-title">商店接入密钥(填入该站点「项目商店 → 接入设置」)</div>
        <div v-if="result.storeApiKey" class="pf-secret"><code>{{ result.storeApiKey }}</code><el-button size="small" @click="copy(result.storeApiKey)">复制</el-button></div>
      </div>
      <template #footer><el-button type="primary" @click="resultVisible = false">我已保存</el-button></template>
    </el-dialog>

    <!-- 重签密钥结果 -->
    <el-dialog v-model="keyVisible" title="商店接入密钥已重签" width="520px" class="pf-root">
      <div v-if="keyResult">
        <div v-if="keyResult.pushed" class="pf-note"><CircleCheckBig :size="16" />新密钥已自动写入站点「{{ keyResult.code }}」,旧密钥立即失效,无需客户操作。</div>
        <template v-else>
          <div class="pf-note warning" style="margin-bottom:12px"><TriangleAlert :size="16" />未能自动写入站点,请把新密钥交给该站点管理员填入「项目商店 → 接入设置」。</div>
          <div class="pf-secret"><code>{{ keyResult.apiKey }}</code><el-button size="small" @click="copy(keyResult.apiKey)">复制</el-button></div>
        </template>
      </div>
      <template #footer><el-button type="primary" @click="keyVisible = false">知道了</el-button></template>
    </el-dialog>

    <!-- 注销 -->
    <el-dialog v-model="deleteVisible" title="注销客户站点" width="520px" class="pf-root">
      <div v-if="deleting">
        <div class="pf-note danger" style="margin-bottom:14px"><TriangleAlert :size="16" />将注销站点「{{ deleting.name }}」。默认只从平台移除、保留数据库与文件;勾选下方选项会连同数据一起删除,<b>不可恢复</b>。</div>
        <el-checkbox v-model="dropData">同时删除该站点的数据库与上传文件</el-checkbox>
        <el-form-item :label="`请输入站点编码 ${deleting.code} 以确认`" label-position="top" style="margin-top:14px">
          <el-input v-model="confirmCode" :placeholder="deleting.code" />
        </el-form-item>
      </div>
      <template #footer>
        <el-button @click="deleteVisible = false">取消</el-button>
        <el-button type="danger" :loading="acting" :disabled="confirmCode !== deleting?.code" @click="doDelete">确认注销</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Building2, CircleCheckBig, Database, Globe, Pause, Plus, RefreshCw, Search, Store, TriangleAlert } from 'lucide-vue-next'
import { hubSiteDelete, hubSiteProvision, hubSiteQuota, hubSiteRotateKey, hubSiteStatus, hubSites, hubSitesConfig, hubSitesUsage } from '../../api/hub'

const emit = defineEmits(['refresh-stats'])
const config = ref({ configured: true })
const sites = ref([])
const keyword = ref('')
const createVisible = ref(false)
const creating = ref(false)
const form = reactive({ code: '', name: '', adminEmail: '', adminPassword: '', customDomain: '', seedDemo: false })
const resultVisible = ref(false)
const result = ref(null)
const keyVisible = ref(false)
const keyResult = ref(null)
const deleteVisible = ref(false)
const deleting = ref(null)
const dropData = ref(false)
const confirmCode = ref('')
const acting = ref(false)

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  return sites.value.filter((s) => !k || (s.code + s.name).toLowerCase().includes(k))
})
const running = computed(() => sites.value.filter((s) => s.status === 'ACTIVE').length)
const stopped = computed(() => sites.value.filter((s) => s.status !== 'ACTIVE').length)
const storeOn = computed(() => sites.value.filter((s) => s.storeRegistered && s.storeStatus === 'ACTIVE').length)

const usage = ref([])
const usageOf = (code) => usage.value.find((u) => u.code === code)
const fmtK = (n) => (n == null ? '–' : n >= 1000000 ? (n / 1000000).toFixed(1) + 'M' : n >= 1000 ? (n / 1000).toFixed(0) + 'k' : String(n))
const quotaVisible = ref(false)
const quotaRow = ref(null)
const quotaForm = reactive({ plan: '', maxUsers: 0, storageLimitMb: 0, expiresAt: '' })

const load = async () => {
  config.value = await hubSitesConfig()
  if (config.value.configured) {
    sites.value = await hubSites()
    hubSitesUsage().then((u) => { usage.value = u }).catch(() => {})
  }
  emit('refresh-stats')
}

const openQuota = (row) => {
  quotaRow.value = row
  Object.assign(quotaForm, { plan: row.plan || '', maxUsers: row.maxUsers || 0, storageLimitMb: row.storageLimitMb || 0, expiresAt: row.expiresAt || '' })
  quotaVisible.value = true
}
const saveQuota = async () => {
  acting.value = true
  try {
    await hubSiteQuota(quotaRow.value.code, {
      plan: quotaForm.plan || null, maxUsers: quotaForm.maxUsers || null, storageLimitMb: quotaForm.storageLimitMb || null,
      aiMonthlyTokens: null, expiresAt: quotaForm.expiresAt || null
    })
    ElMessage.success('配额已更新,立即生效')
    quotaVisible.value = false
    await load()
  } finally {
    acting.value = false
  }
}

const openCreate = () => {
  Object.assign(form, { code: '', name: '', adminEmail: '', adminPassword: '', customDomain: '', seedDemo: false })
  createVisible.value = true
}

const create = async () => {
  creating.value = true
  try {
    result.value = await hubSiteProvision({ ...form })
    createVisible.value = false
    resultVisible.value = true
    await load()
  } finally {
    creating.value = false
  }
}

const rotate = async (row) => {
  try {
    await ElMessageBox.confirm(`重签后「${row.name}」原来的商店密钥立即失效;新密钥会自动写入该站点。是否继续?`, '重签商店密钥', { type: 'warning', confirmButtonText: '重签', cancelButtonText: '取消' })
  } catch (e) { return }
  keyResult.value = await hubSiteRotateKey(row.code)
  keyVisible.value = true
  await load()
}

const toggle = async (row) => {
  const next = row.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'
  if (next === 'SUSPENDED') {
    try { await ElMessageBox.confirm(`停用后「${row.name}」的所有用户都无法访问,可随时恢复。`, '停用站点', { type: 'warning', confirmButtonText: '停用', cancelButtonText: '取消' }) } catch (e) { return }
  }
  await hubSiteStatus(row.code, next)
  ElMessage.success(next === 'ACTIVE' ? '站点已恢复' : '站点已停用')
  await load()
}

const openDelete = (row) => {
  deleting.value = row
  dropData.value = false
  confirmCode.value = ''
  deleteVisible.value = true
}

const doDelete = async () => {
  acting.value = true
  try {
    await hubSiteDelete(deleting.value.code, dropData.value)
    deleteVisible.value = false
    ElMessage.success(dropData.value ? '站点已注销并删除数据' : '站点已注销(数据保留)')
    await load()
  } finally {
    acting.value = false
  }
}

const copy = async (text) => {
  try { await navigator.clipboard.writeText(text); ElMessage.success('已复制') } catch (e) { ElMessage.warning('复制失败,请手动选择') }
}
const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '–')

onMounted(load)
</script>

<style scoped>
.count { font-size: 12px; color: var(--pf-text-3); font-weight: 500; margin-left: 4px; }
.s2 { font-size: 12px; color: var(--pf-text-3); }
.site-mark { width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center; font-weight: 700; color: #fff; background: linear-gradient(135deg, #6366f1, #a855f7); flex-shrink: 0; }
.site-mark.off { background: #cbd5e1; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.usage { display: flex; flex-direction: column; gap: 3px; font-size: 12px; }
.u-row { display: flex; align-items: baseline; gap: 6px; }
.u-row span { color: var(--pf-text-3); width: 46px; }
.u-row b { color: var(--pf-text); }
.u-row small { color: var(--pf-text-3); }
.u-row .dim { margin-left: 2px; }
.fh { font-size: 12px; color: var(--pf-text-3); line-height: 1.5; margin-top: 4px; }
.flow { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 16px; }
.flow-step { padding: 18px 20px; }
.flow-step .n { width: 26px; height: 26px; border-radius: 8px; background: var(--pf-primary-soft); color: var(--pf-primary); font-weight: 800; display: grid; place-items: center; margin-bottom: 10px; font-size: 13px; }
.flow-step h3 { margin: 0 0 6px; font-size: 14px; }
.flow-step p { margin: 0; font-size: 13px; color: var(--pf-text-2); line-height: 1.6; }
@media (max-width: 960px) { .flow { grid-template-columns: 1fr; } }
</style>
