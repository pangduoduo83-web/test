<template>
  <div>
    <div class="pf-page-head">
      <div>
        <h2 class="pf-page-title">客户站点</h2>
        <p class="pf-page-desc">每个客户是一个独立站点(独立数据库、独立域名、自己的管理员)。在这里一键开通:建库、初始化管理员、在商店登记并把接入密钥写进新站点,客户拿到地址就能用。</p>
      </div>
      <el-button type="primary" size="large" :disabled="!config.configured" @click="openCreate">
        <Plus :size="16" style="margin-right:6px" />开通新客户
      </el-button>
    </div>

    <div v-if="config && !config.configured" class="pf-note warning" style="margin-bottom:16px">
      <TriangleAlert :size="16" />
      <div>商店服务尚未配置客户站点后端(<code>HUB_PLATFORM_API_URL</code> / <code>HUB_PLATFORM_TOKEN</code>),此页只能查看,不能开通。请在部署环境补齐后重启商店服务。</div>
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
        <el-table-column label="访问地址" min-width="220">
          <template #default="{ row }">
            <a v-if="row.siteUrl" class="pf-link pf-mono" :href="row.siteUrl" target="_blank">{{ row.siteUrl }}</a>
            <span v-else class="s2">{{ row.customDomain || '未配置站点地址模板' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="站点状态" width="110">
          <template #default="{ row }"><span class="pf-pill" :class="row.status === 'ACTIVE' ? 'success' : 'danger'">{{ row.status === 'ACTIVE' ? '运行中' : '已停用' }}</span></template>
        </el-table-column>
        <el-table-column label="商店接入" width="120">
          <template #default="{ row }">
            <span v-if="!row.storeRegistered" class="pf-pill info">未登记</span>
            <span v-else class="pf-pill" :class="row.storeStatus === 'ACTIVE' ? 'success' : 'warning'">{{ row.storeStatus === 'ACTIVE' ? '已接入' : '已停用' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="开通时间" width="150"><template #default="{ row }"><span class="s2">{{ fmt(row.createdAt) }}</span></template></el-table-column>
        <el-table-column label="操作" width="300" align="right">
          <template #default="{ row }">
            <el-button size="small" @click="rotate(row)">重签商店密钥</el-button>
            <el-button v-if="row.code !== 'default'" size="small" :type="row.status === 'ACTIVE' ? 'warning' : 'success'" plain @click="toggle(row)">
              {{ row.status === 'ACTIVE' ? '停用' : '恢复' }}
            </el-button>
            <el-button v-if="row.code !== 'default'" size="small" type="danger" text @click="openDelete(row)">注销</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="pf-empty"><div class="pf-empty-icon">🏫</div><div class="pf-empty-title">还没有客户站点</div><div>点击右上角「开通新客户」创建第一个</div></div>
        </template>
      </el-table>
    </div>

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
import { CircleCheckBig, Plus, RefreshCw, Search, TriangleAlert } from 'lucide-vue-next'
import { hubSiteDelete, hubSiteProvision, hubSiteRotateKey, hubSiteStatus, hubSites, hubSitesConfig } from '../../api/hub'

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

const load = async () => {
  config.value = await hubSitesConfig()
  if (config.value.configured) {
    sites.value = await hubSites()
  }
  emit('refresh-stats')
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
.fh { font-size: 12px; color: var(--pf-text-3); line-height: 1.5; margin-top: 4px; }
</style>
