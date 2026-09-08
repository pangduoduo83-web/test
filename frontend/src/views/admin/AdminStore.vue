<template>
  <div class="store">
    <el-alert v-if="status && !status.configured" type="warning" :closable="false" class="tip"
              title="尚未接入项目商店:请在下方「接入设置」填写商店地址与平台管理员签发的 API Key" />
    <el-alert v-else-if="status && !status.connected" type="error" :closable="false" class="tip"
              :title="`无法连接项目商店:${status.error || '请检查地址与 API Key'}`" />
    <el-alert v-else-if="status" type="success" :closable="false" class="tip"
              :title="`已接入项目商店(本站在商店中的名称:${status.tenantName || status.tenantCode})`" />

    <el-tabs v-model="tab" @tab-change="onTab">
      <!-- 浏览商店 -->
      <el-tab-pane label="浏览商店" name="browse">
        <div class="toolbar">
          <el-input v-model="query" placeholder="搜索标题 / 简介 / 标签" clearable style="width:280px" @keyup.enter="loadItems" />
          <el-input v-model="category" placeholder="分类" clearable style="width:160px" @keyup.enter="loadItems" />
          <el-button type="primary" @click="loadItems">搜索</el-button>
          <span class="count">{{ items.length }} 个可用项目</span>
        </div>
        <el-empty v-if="!loading && items.length === 0" description="商店里还没有对本站可见的项目" />
        <div class="grid">
          <div v-for="it in items" :key="it.id" class="card item">
            <div class="cover" @click="openDetail(it)">
              <img v-if="it.coverUrl" :src="storeAssetUrl(it.coverUrl)" alt="" />
              <div v-else class="cover-placeholder">📦</div>
              <el-tag v-if="it.localProjectId" size="small" type="success" class="badge">已安装 v{{ it.localVersionNo }}</el-tag>
              <el-tag v-if="it.updateAvailable" size="small" type="warning" class="badge badge-2">有新版本 v{{ it.currentVersionNo }}</el-tag>
            </div>
            <div class="body">
              <div class="title" @click="openDetail(it)">{{ it.title }}</div>
              <div class="summary">{{ it.summary || '暂无简介' }}</div>
              <div class="meta">
                <span>{{ it.category || '未分类' }}</span>
                <span>来自 {{ it.publisherTenantName || '未知' }}</span>
                <span>安装 {{ it.installCount }} 次</span>
              </div>
              <div class="tags">
                <el-tag v-for="t in (it.tags || []).slice(0, 4)" :key="t" size="small" effect="plain">{{ t }}</el-tag>
              </div>
              <div class="actions">
                <el-button v-if="!it.localProjectId" type="primary" size="small" :loading="installing === it.id"
                           @click="install(it)">安装到本站</el-button>
                <el-button v-else-if="it.updateAvailable" type="warning" size="small" :loading="installing === it.id"
                           @click="install(it, it.localProjectId)">更新到 v{{ it.currentVersionNo }}</el-button>
                <el-button v-else size="small" @click="$router.push('/admin/projects')">查看本地项目</el-button>
                <el-button size="small" text @click="openDetail(it)">详情</el-button>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 我的发布 -->
      <el-tab-pane label="本站发布" name="mine">
        <div class="toolbar">
          <el-select v-model="publishProjectId" filterable placeholder="选择要发布到商店的本地项目" style="width:360px">
            <el-option v-for="p in localProjects" :key="p.id" :value="p.id"
                       :label="p.title + (p.hubItemId ? `(已发布,条目 #${p.hubItemId})` : '')" />
          </el-select>
          <el-input v-model="changelog" placeholder="版本说明(可选)" style="width:260px" />
          <el-button type="primary" :disabled="!publishProjectId" :loading="publishing" @click="publish">发布 / 更新到商店</el-button>
        </div>
        <p class="hint">发布后需平台管理员审核通过才会在商店上架;再次发布同一项目会追加新版本并重新审核。项目里的封面、富文本图片与教学资料会一并上传到商店。</p>
        <el-table :data="mine" stripe>
          <el-table-column prop="id" label="条目" width="70" />
          <el-table-column prop="title" label="标题" min-width="200" />
          <el-table-column label="审核状态" width="130">
            <template #default="{ row }">
              <el-tag :type="statusType(row.reviewStatus)" size="small">{{ statusText(row.reviewStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="版本" width="140">
            <template #default="{ row }">上架 v{{ row.currentVersionNo ?? '-' }} / 最新 v{{ row.latestVersionNo }}</template>
          </el-table-column>
          <el-table-column prop="visibility" label="可见范围" width="110">
            <template #default="{ row }">{{ row.visibility === 'PUBLIC' ? '公开' : '定向分享' }}</template>
          </el-table-column>
          <el-table-column prop="installCount" label="安装次数" width="100" />
          <el-table-column prop="reviewComment" label="审核意见" min-width="180" show-overflow-tooltip />
          <el-table-column prop="updatedAt" label="更新时间" width="170" />
        </el-table>
      </el-tab-pane>

      <!-- 接入设置 -->
      <el-tab-pane label="接入设置" name="settings">
        <div class="card settings">
          <el-alert v-if="settings && !settings.cryptoReady" type="warning" :closable="false" class="tip"
                    title="服务端未配置 IOEDU_MASTER_KEY,无法加密保存 API Key,请先在部署环境中设置" />
          <el-form label-width="120px">
            <el-form-item label="商店地址">
              <el-input v-model="settingsForm.baseUrl" :placeholder="settings?.baseUrlSource === 'ENV' ? '已由平台环境变量提供,留空即用默认' : 'https://hub.example.com'" />
              <div class="hint">当前生效:{{ settings?.baseUrl || '未配置' }}(来源:{{ sourceText(settings?.baseUrlSource) }})</div>
            </el-form-item>
            <el-form-item label="API Key">
              <el-input v-model="settingsForm.apiKey" type="password" show-password
                        :placeholder="settings?.apiKeySet ? `已配置(${settings.apiKeyMasked}),留空保持不变` : '粘贴平台管理员签发的 hk_ 开头密钥'" />
              <div class="hint">由项目商店的平台管理员为本站签发,只在服务端加密存储</div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveSettings">保存</el-button>
              <el-button :loading="testing" @click="testSettings">测试连接</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detail?.title" size="520px">
      <div v-if="detail" class="detail">
        <img v-if="detail.coverUrl" :src="storeAssetUrl(detail.coverUrl)" class="detail-cover" alt="" />
        <p class="summary">{{ detail.summary }}</p>
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="分类">{{ detail.category || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发布方">{{ detail.publisherTenantName }} / {{ detail.publisherUserName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前版本">v{{ detail.currentVersionNo }}</el-descriptions-item>
          <el-descriptions-item label="安装次数">{{ detail.installCount }}</el-descriptions-item>
          <el-descriptions-item label="本站状态">
            <span v-if="detail.localProjectId">已安装 v{{ detail.localVersionNo }}(本地项目 #{{ detail.localProjectId }})</span>
            <span v-else>未安装</span>
          </el-descriptions-item>
        </el-descriptions>
        <h4>版本记录</h4>
        <el-timeline>
          <el-timeline-item v-for="v in detail.versions" :key="v.versionNo" :timestamp="v.createdAt">
            v{{ v.versionNo }} {{ v.changelog || '' }}
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminGetStoreSettings, adminListProjects, adminTestStoreSettings, adminUpdateStoreSettings,
  storeAssetUrl, storeInstall, storeItem, storeItems, storeMine, storePublish, storeStatus
} from '../../api'

const tab = ref('browse')
const status = ref(null)
const items = ref([])
const loading = ref(false)
const query = ref('')
const category = ref('')
const installing = ref(null)
const detailVisible = ref(false)
const detail = ref(null)

const mine = ref([])
const localProjects = ref([])
const publishProjectId = ref(null)
const changelog = ref('')
const publishing = ref(false)

const settings = ref(null)
const settingsForm = reactive({ baseUrl: '', apiKey: '' })
const saving = ref(false)
const testing = ref(false)

const loadStatus = async () => {
  try { status.value = await storeStatus() } catch (e) { /* 已提示 */ }
}

const loadItems = async () => {
  if (!status.value?.connected) { items.value = []; return }
  loading.value = true
  try {
    items.value = await storeItems({ q: query.value || undefined, category: category.value || undefined })
  } catch (e) { items.value = [] } finally { loading.value = false }
}

const loadMine = async () => {
  if (!status.value?.connected) return
  try {
    const [m, p] = await Promise.all([storeMine(), adminListProjects()])
    mine.value = m
    localProjects.value = p
  } catch (e) { /* 已提示 */ }
}

const loadSettings = async () => {
  settings.value = await adminGetStoreSettings()
  settingsForm.baseUrl = settings.value.baseUrlSource === 'DB' ? settings.value.baseUrl : ''
  settingsForm.apiKey = ''
}

const onTab = (name) => {
  if (name === 'mine') loadMine()
  if (name === 'settings') loadSettings()
  if (name === 'browse') loadItems()
}

const install = async (it, overwriteProjectId) => {
  let asDraft = false
  if (overwriteProjectId) {
    try {
      await ElMessageBox.confirm(
        `将用商店 v${it.currentVersionNo} 覆盖本地项目「${it.localProjectTitle}」的教学内容(讲师、报名与成果数据保留),是否继续?`,
        '更新项目', { confirmButtonText: '更新', cancelButtonText: '取消' })
    } catch (e) { return }
  } else {
    const choice = await ElMessageBox.confirm(`将「${it.title}」安装为本站项目,选择安装后的状态:`, '安装项目', {
      confirmButtonText: '直接上架', cancelButtonText: '存为草稿', distinguishCancelAndClose: true
    }).then(() => 'publish').catch((action) => action)
    if (choice === 'close') return
    asDraft = choice === 'cancel'
  }
  installing.value = it.id
  try {
    const p = await storeInstall(it.id, { status: asDraft ? 'DRAFT' : 'PUBLISHED', overwriteProjectId })
    ElMessage.success(overwriteProjectId ? `已更新本地项目「${p.title}」` : `已安装为本地项目「${p.title}」(${asDraft ? '草稿' : '已上架'})`)
    await loadItems()
  } finally {
    installing.value = null
  }
}

const openDetail = async (it) => {
  detail.value = await storeItem(it.id)
  detailVisible.value = true
}

const publish = async () => {
  publishing.value = true
  try {
    const item = await storePublish(publishProjectId.value, { changelog: changelog.value })
    ElMessage.success(item.newItem ? `已提交到商店(条目 #${item.id}),等待平台审核` : `已追加新版本 v${item.latestVersionNo},等待平台审核`)
    changelog.value = ''
    await loadMine()
  } finally {
    publishing.value = false
  }
}

const saveSettings = async () => {
  saving.value = true
  try {
    settings.value = await adminUpdateStoreSettings({
      baseUrl: settingsForm.baseUrl,
      apiKey: settingsForm.apiKey || undefined
    })
    settingsForm.apiKey = ''
    ElMessage.success('已保存')
    await loadStatus()
  } finally {
    saving.value = false
  }
}

const testSettings = async () => {
  testing.value = true
  try {
    const s = await adminTestStoreSettings()
    status.value = s
    if (s.connected) ElMessage.success(`连接成功:${s.tenantName}`)
    else ElMessage.error(s.error || '连接失败')
  } finally {
    testing.value = false
  }
}

const statusText = (s) => ({ PENDING: '待审核', APPROVED: '已上架', REJECTED: '已驳回', OFFLINE: '已下架' }[s] || s)
const statusType = (s) => ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', OFFLINE: 'info' }[s] || '')
const sourceText = (s) => ({ DB: '本站设置', ENV: '平台默认', NONE: '未配置' }[s] || '-')

onMounted(async () => {
  await loadStatus()
  if (status.value?.connected) {
    await loadItems()
  } else {
    tab.value = 'settings'
    await loadSettings()
  }
})
</script>

<style scoped>
.tip { margin-bottom: 14px; }
.toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 14px; flex-wrap: wrap; }
.count { color: #9ca3af; font-size: 13px; margin-left: auto; }
.hint { font-size: 12px; color: #9ca3af; line-height: 1.6; margin: 0 0 12px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.item { padding: 0; overflow: hidden; display: flex; flex-direction: column; }
.cover { position: relative; height: 150px; background: #f3f4f6; cursor: pointer; }
.cover img { width: 100%; height: 100%; object-fit: cover; display: block; }
.cover-placeholder { height: 100%; display: flex; align-items: center; justify-content: center; font-size: 42px; }
.badge { position: absolute; top: 10px; left: 10px; }
.badge-2 { left: auto; right: 10px; }
.body { padding: 14px 16px 16px; display: flex; flex-direction: column; gap: 8px; flex: 1; }
.title { font-weight: 700; font-size: 15px; cursor: pointer; }
.summary { font-size: 13px; color: #6b7280; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; min-height: 39px; }
.meta { display: flex; gap: 12px; font-size: 12px; color: #9ca3af; flex-wrap: wrap; }
.tags { display: flex; gap: 6px; flex-wrap: wrap; }
.actions { display: flex; gap: 8px; margin-top: auto; padding-top: 6px; }
.settings { max-width: 760px; }
.detail-cover { width: 100%; border-radius: 10px; margin-bottom: 12px; }
.detail h4 { margin: 18px 0 10px; }
</style>
