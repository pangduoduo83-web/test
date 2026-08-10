<template>
  <div>
    <!-- 页头 -->
    <div class="head-row">
      <div>
        <h2 class="page-title">设备图书馆</h2>
        <p class="page-subtitle">浏览和借阅实验所需的开发板、仪表和工具</p>
      </div>
      <div class="head-right">
        <div class="avail-count">
          <b>{{ availableTotal }}</b>
          <span>可借设备</span>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="card filter-bar">
      <el-input v-model="filters.keyword" class="search" placeholder="搜索设备名称、型号、标签..."
                clearable @keyup.enter="load" @clear="load">
        <template #prefix><Search :size="15" /></template>
      </el-input>
      <el-button :type="advancedOpen ? 'primary' : 'default'" @click="advancedOpen = !advancedOpen">
        <SlidersHorizontal :size="14" style="margin-right:5px" /> 筛选
      </el-button>
    </div>

    <!-- 高级筛选 -->
    <div v-if="advancedOpen" class="card advanced-bar">
      <el-select v-model="filters.status" placeholder="设备状态" class="sel" @change="load">
        <el-option label="全部状态" value="ALL" />
        <el-option label="可借阅" value="AVAILABLE" />
        <el-option label="已借完" value="BORROWED_OUT" />
        <el-option label="维护中" value="MAINTENANCE" />
      </el-select>
      <el-select v-model="filters.location" placeholder="存放位置" class="sel" @change="load">
        <el-option label="全部位置" value="ALL" />
        <el-option v-for="l in locations" :key="l" :label="l" :value="l" />
      </el-select>
      <el-select v-model="filters.minRating" placeholder="设备评分" class="sel" @change="load">
        <el-option label="全部评分" :value="0" />
        <el-option label="4.5星以上" :value="4.5" />
        <el-option label="4.0星以上" :value="4.0" />
        <el-option label="3.5星以上" :value="3.5" />
      </el-select>
    </div>

    <!-- 分类 tab -->
    <div class="pills-row">
      <span class="pill" :class="{ active: activeCategory === '' }" @click="activeCategory = ''">
        <LayoutGrid :size="14" /> 全部设备 <span class="pill-count">{{ items.length }}</span>
      </span>
      <span v-for="c in categories" :key="c.name" class="pill"
            :class="{ active: activeCategory === c.name }"
            @click="activeCategory = activeCategory === c.name ? '' : c.name">
        <component :is="c.icon" :size="14" /> {{ c.name }} <span class="pill-count">{{ c.count }}</span>
      </span>
    </div>

    <!-- 设备卡片 -->
    <el-empty v-if="filtered.length === 0" description="没有匹配的设备" />
    <div v-else class="equip-grid">
      <div v-for="e in pageItems" :key="e.id" class="equip-card">
        <!-- 封面 -->
        <div class="ec-cover">
          <img v-if="e.imageUrl && !failedImages.has(e.id)" :src="e.imageUrl" :alt="e.name"
               @error="failedImages.add(e.id)" />
          <div v-else class="ec-cover-fallback"><Wrench :size="46" color="rgba(255,255,255,.85)" /></div>
          <span class="cover-badge" :class="statusColor(e)" style="top:10px;left:10px">
            {{ statusText(e) }}
          </span>
          <button class="ec-heart" @click.stop="toggleWish(e)">
            <Heart :size="15" :fill="wishlist.has(e.id) ? '#ef4444' : 'none'"
                   :color="wishlist.has(e.id) ? '#ef4444' : '#6b7280'" />
          </button>
          <button class="ec-eye" @click.stop="openDetail(e)"><Eye :size="14" color="#fff" /></button>
        </div>

        <div class="ec-body">
          <h3 class="ec-title">{{ e.name }}</h3>
          <div class="ec-model">{{ e.model }} · {{ e.manufacturer }}</div>
          <p class="ec-desc">{{ e.description }}</p>
          <div class="ec-specs">
            <span v-for="s in arr(e.specs).slice(0, 3)" :key="s" class="chip">{{ s }}</span>
            <span v-if="arr(e.specs).length > 3" class="chip">+{{ arr(e.specs).length - 3 }}</span>
          </div>
          <div class="ec-meta">
            <span><MapPin :size="13" /> {{ e.location }}</span>
            <span><Star :size="13" fill="#facc15" color="#facc15" /> {{ e.rating }}</span>
            <span>已借 {{ e.borrowCount }} 次</span>
            <span v-if="e.price" class="ec-price">¥{{ e.price }}</span>
          </div>

          <div class="ec-btns">
            <button v-if="e.status === 'AVAILABLE' && e.availableCount > 0"
                    class="ec-borrow-btn" @click="openBorrow(e)">借阅</button>
            <button v-else-if="e.status === 'AVAILABLE'" class="ec-borrow-btn disabled" disabled>预约排队</button>
            <button v-else class="ec-borrow-btn disabled" disabled>暂停借阅</button>
            <el-button class="ec-detail-btn" @click="openDetail(e)">详情</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="filtered.length > pageSize" class="pager">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length"
                     layout="prev, pager, next" background />
    </div>

    <!-- 设备详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="current?.name" width="620px">
      <template v-if="current">
        <div v-if="current.imageUrl && !failedImages.has(current.id)" class="dd-cover">
          <img :src="current.imageUrl" :alt="current.name" />
        </div>
        <div class="dd-head">
          <span v-if="!current.imageUrl || failedImages.has(current.id)" class="ec-icon big">
            <Wrench :size="28" color="#fff" />
          </span>
          <div>
            <div class="dd-model">{{ current.model }} · 制造商: {{ current.manufacturer }}</div>
            <div class="dd-price">参考价值: ¥{{ current.price }}</div>
            <div class="dd-loc">设备位置: {{ current.location }} · 已借出 {{ current.borrowCount }} 次</div>
          </div>
        </div>
        <h4>设备描述</h4>
        <p class="dd-desc">{{ current.description }}</p>
        <h4>技术规格</h4>
        <div><span v-for="s in arr(current.specs)" :key="s" class="chip">{{ s }}</span></div>
        <h4>适用项目</h4>
        <div><span v-for="p in arr(current.suitableProjects)" :key="p" class="chip">{{ p }}</span></div>
        <h4>参考文档</h4>
        <div class="dd-docs">
          <div v-for="d in arr(current.docs)" :key="d" class="dd-doc-row">
            <FileText :size="14" /> {{ d }}
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="current && current.availableCount > 0 && current.status === 'AVAILABLE'"
                   type="primary" @click="detailVisible = false; openBorrow(current)">申请借阅</el-button>
      </template>
    </el-dialog>

    <!-- 借阅申请:三步流程 -->
    <el-dialog v-model="borrowVisible" title="设备借阅申请" width="560px" :close-on-click-modal="false">
      <el-steps :active="step" align-center finish-status="success" class="steps">
        <el-step title="填写申请" />
        <el-step title="确认信息" />
        <el-step title="提交成功" />
      </el-steps>

      <!-- 第一步:填写 -->
      <div v-if="step === 0">
        <el-form :model="borrowForm" label-position="top">
          <el-form-item label="申请设备">
            <el-input :model-value="`${current?.name}(可借 ${current?.availableCount} 件)`" disabled />
          </el-form-item>
          <div class="form-row">
            <el-form-item label="使用目的" class="grow" required>
              <el-select v-model="borrowForm.purpose" placeholder="请选择使用目的">
                <el-option v-for="p in ['课程实验', '竞赛准备', '科研研究', '毕业设计']" :key="p" :label="p" :value="p" />
              </el-select>
            </el-form-item>
            <el-form-item label="借用数量" class="grow">
              <el-input-number v-model="borrowForm.quantity" :min="1" :max="current?.availableCount || 1" />
            </el-form-item>
          </div>
          <el-form-item label="关联项目名称">
            <el-input v-model="borrowForm.projectName" placeholder="如:智能家居项目开发" />
          </el-form-item>
          <div class="form-row">
            <el-form-item label="开始日期" class="grow" required>
              <el-date-picker v-model="borrowForm.startDate" type="date" value-format="YYYY-MM-DD"
                              placeholder="选择日期" style="width:100%" />
            </el-form-item>
            <el-form-item label="借用时长" class="grow">
              <el-select v-model="borrowForm.durationDays">
                <el-option label="3天" :value="3" />
                <el-option label="1周" :value="7" />
                <el-option label="2周" :value="14" />
                <el-option label="1个月" :value="30" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="备注说明">
            <el-input v-model="borrowForm.remark" type="textarea" :rows="3"
                      placeholder="请描述具体的使用计划和需求..." />
          </el-form-item>
          <el-checkbox v-model="agreed">
            我已阅读并同意<a class="link" @click.prevent="agreementVisible = true">《设备借阅协议》</a>，承诺妥善保管设备，按时归还，如有损坏照价赔偿。
          </el-checkbox>
        </el-form>
      </div>

      <!-- 第二步:确认 -->
      <div v-else-if="step === 1" class="confirm-box">
        <h4>确认申请信息</h4>
        <div class="cf-row"><span>申请人</span><b>{{ authStore.user?.name }}</b></div>
        <div class="cf-row"><span>申请设备</span><b>{{ current?.name }} × {{ borrowForm.quantity }}</b></div>
        <div class="cf-row"><span>使用目的</span><b>{{ borrowForm.purpose }}</b></div>
        <div class="cf-row"><span>项目名称</span><b>{{ borrowForm.projectName || '-' }}</b></div>
        <div class="cf-row"><span>借用时间</span><b>{{ borrowForm.startDate }} 起 {{ borrowForm.durationDays }} 天</b></div>
        <div class="cf-row"><span>备注</span><b>{{ borrowForm.remark || '-' }}</b></div>
        <div class="borrow-tips">
          <div class="notice-title">借阅须知</div>
          <ul>
            <li>请在使用前检查设备完整性</li>
            <li>开发板类设备借用期限为2周</li>
            <li>精密仪器需在老师指导下使用</li>
            <li>逾期归还将影响信用评分</li>
          </ul>
        </div>
      </div>

      <!-- 第三步:成功 -->
      <div v-else class="success-box">
        <div class="success-icon"><CheckCircle :size="52" color="#22c55e" /></div>
        <h3>申请提交成功！</h3>
        <p>您的申请已发送，请等待管理员审批</p>
        <div class="req-no">申请编号: <b>{{ submittedNo }}</b></div>
      </div>

      <template #footer>
        <template v-if="step === 0">
          <el-button @click="borrowVisible = false">取消</el-button>
          <el-button type="primary" @click="toConfirm">下一步</el-button>
        </template>
        <template v-else-if="step === 1">
          <el-button @click="step = 0">返回修改</el-button>
          <el-button type="primary" :loading="submitting" @click="doSubmit">确认申请</el-button>
        </template>
        <template v-else>
          <el-button @click="borrowVisible = false">关闭</el-button>
          <el-button type="primary" @click="$router.push('/app/borrowing')">查看借阅管理</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 设备借阅协议 -->
    <el-dialog v-model="agreementVisible" title="设备借阅协议" width="560px">
      <div class="agreement">
        <p>1. 借用人须为本平台注册学生,凭有效身份领取设备,设备仅限本人在校内学习科研使用,不得转借他人或挪作商用。</p>
        <p>2. 领取设备时应当场检查外观与功能,发现异常立即向管理员登记;未登记的损坏视为借用期内发生。</p>
        <p>3. 开发板类设备借用期限最长 2 周,仪器仪表类最长 1 周;到期前 3 天可申请续借一次,逾期未还将暂停借阅资格并影响信用评分。</p>
        <p>4. 借用期间妥善保管设备,防水防摔防静电;精密仪器须在指导老师监督下使用。</p>
        <p>5. 归还时须通过管理员功能验收;人为损坏或遗失的,按设备参考价值赔偿或承担维修费用。</p>
        <p>6. 本协议自勾选同意并提交申请时生效,最终解释权归电子信息创新实验室所有。</p>
      </div>
      <template #footer>
        <el-button @click="agreementVisible = false">关闭</el-button>
        <el-button type="primary" @click="agreed = true; agreementVisible = false">同意并勾选</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  CheckCircle, CircuitBoard, Eye, FileText, Folder, Gauge, Heart, LayoutGrid,
  MapPin, Radio, Search, SlidersHorizontal, Star, Thermometer, Wrench
} from 'lucide-vue-next'
import { applyBorrow, fetchEquipment, fetchLocations } from '../../api'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const authStore = useAuthStore()

const items = ref([])
const locations = ref([])
const filters = reactive({ keyword: route.query.keyword || '', status: 'ALL', location: 'ALL', minRating: 0 })
const advancedOpen = ref(false)
const activeCategory = ref('')
const failedImages = ref(new Set())
const agreementVisible = ref(false)
const page = ref(1)
const pageSize = 9

// 心愿单:仅本地保存的收藏标记
const wishlist = ref(new Set(JSON.parse(localStorage.getItem('ioedu_wishlist') || '[]')))

const detailVisible = ref(false)
const borrowVisible = ref(false)
const current = ref(null)
const step = ref(0)
const agreed = ref(false)
const submitting = ref(false)
const submittedNo = ref('')

const borrowForm = reactive({
  quantity: 1, purpose: '', projectName: '', startDate: '', durationDays: 14, remark: ''
})

const categoryIcons = {
  '开发板': CircuitBoard, '测试仪表': Gauge, '通信模块': Radio, '传感器': Thermometer, '工具': Wrench
}

const arr = (v) => Array.isArray(v) ? v : []

const statusText = (e) => e.status === 'MAINTENANCE' ? '维护中'
  : e.availableCount > 0 ? `可借阅 (${e.availableCount}件)` : '已借完'
const statusColor = (e) => e.status === 'MAINTENANCE' ? 'yellow' : e.availableCount > 0 ? 'green' : 'red'

const availableTotal = computed(() =>
  items.value.filter((e) => e.status === 'AVAILABLE' && e.availableCount > 0).length)

const categories = computed(() => {
  const map = {}
  items.value.forEach((e) => {
    if (!e.category) return
    map[e.category] = (map[e.category] || 0) + 1
  })
  return Object.keys(map).map((name) => ({
    name, count: map[name], icon: categoryIcons[name] || Folder
  }))
})

const filtered = computed(() => items.value.filter((e) =>
  !activeCategory.value || e.category === activeCategory.value))

const pageItems = computed(() =>
  filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))

watch([activeCategory, items], () => { page.value = 1 })

const toggleWish = (e) => {
  if (wishlist.value.has(e.id)) wishlist.value.delete(e.id)
  else wishlist.value.add(e.id)
  localStorage.setItem('ioedu_wishlist', JSON.stringify([...wishlist.value]))
}

const load = async () => {
  items.value = await fetchEquipment({
    keyword: filters.keyword || undefined,
    status: filters.status,
    location: filters.location,
    minRating: filters.minRating || undefined
  })
}

const openDetail = (e) => { current.value = e; detailVisible.value = true }

const openBorrow = (e) => {
  current.value = e
  step.value = 0
  agreed.value = false
  Object.assign(borrowForm, {
    quantity: 1, purpose: '', projectName: '',
    startDate: new Date().toISOString().slice(0, 10),
    durationDays: 14, remark: ''
  })
  borrowVisible.value = true
}

const toConfirm = () => {
  if (!borrowForm.purpose || !borrowForm.startDate) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (!agreed.value) {
    ElMessage.warning('请先阅读并同意设备借阅协议')
    return
  }
  step.value = 1
}

const doSubmit = async () => {
  submitting.value = true
  try {
    const data = await applyBorrow({ equipmentId: current.value.id, ...borrowForm })
    submittedNo.value = data.requestNo
    step.value = 2
    await load()
  } catch (e) { /* 错误已提示 */ } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await load()
  locations.value = await fetchLocations()
})
</script>

<style scoped>
.head-row { display: flex; justify-content: space-between; align-items: flex-start; }
.avail-count { text-align: right; }
.avail-count b { display: block; font-size: 26px; color: var(--brand-blue); line-height: 1.1; }
.avail-count span { font-size: 12px; color: var(--text-secondary); }

.filter-bar { display: flex; gap: 12px; padding: 14px 16px; margin-bottom: 12px; }
.search { flex: 1; }
.search :deep(.el-input__wrapper) { border-radius: 10px; }

.advanced-bar { display: flex; gap: 12px; padding: 12px 16px; margin-bottom: 12px; flex-wrap: wrap; }
.sel { width: 140px; }

.pills-row { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; }

.equip-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
.equip-card {
  background: #fff; border-radius: 16px; box-shadow: var(--shadow-card);
  display: flex; flex-direction: column; overflow: hidden;
  transition: transform .15s, box-shadow .15s;
}
.equip-card:hover { transform: translateY(-3px); box-shadow: 0 20px 25px -5px rgba(0,0,0,.1); }

.ec-cover { position: relative; height: 150px; }
.ec-cover img { width: 100%; height: 100%; object-fit: cover; display: block; }
.ec-cover-fallback {
  width: 100%; height: 100%;
  background: linear-gradient(135deg, #60a5fa, #06b6d4);
  display: flex; align-items: center; justify-content: center;
}
.ec-cover-fallback span { font-size: 48px; }

.ec-heart {
  position: absolute; top: 10px; right: 10px;
  width: 30px; height: 30px; border-radius: 50%;
  border: none; background: rgba(255,255,255,.9);
  cursor: pointer; font-size: 14px;
  display: flex; align-items: center; justify-content: center;
}
.ec-eye {
  position: absolute; bottom: 10px; right: 10px;
  width: 30px; height: 30px; border-radius: 50%;
  border: none; background: var(--brand-blue); color: #fff;
  cursor: pointer; font-size: 13px;
  display: flex; align-items: center; justify-content: center;
}

.ec-body { padding: 14px 18px 18px; display: flex; flex-direction: column; flex: 1; }
.ec-title { margin: 0 0 2px; font-size: 16px; }
.ec-model { font-size: 12px; color: var(--text-secondary); margin-bottom: 8px; }
.ec-desc {
  font-size: 13px; color: var(--text-secondary); margin: 0 0 10px;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
  min-height: 36px;
}
.ec-meta {
  display: flex; gap: 12px; align-items: center; flex-wrap: wrap;
  font-size: 12px; color: var(--text-secondary); margin: 8px 0 12px;
}
.ec-meta span { display: inline-flex; align-items: center; gap: 4px; }
.ec-price { margin-left: auto; font-size: 15px; font-weight: 700; color: var(--brand-blue); }

.ec-btns { margin-top: auto; display: flex; gap: 8px; }
.ec-borrow-btn {
  flex: 1; padding: 9px 0;
  background: var(--brand-blue); color: #fff;
  border: none; border-radius: 10px;
  font-size: 14px; font-weight: 500; cursor: pointer;
  transition: background .15s;
}
.ec-borrow-btn:hover { background: #1d4ed8; }
.ec-borrow-btn.disabled { background: #f3f4f6; color: #9ca3af; cursor: not-allowed; }
.ec-detail-btn { border-radius: 10px; }

.ec-icon {
  width: 48px; height: 48px; font-size: 24px;
  background: linear-gradient(135deg, #60a5fa, #06b6d4); border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
}
.ec-icon.big { width: 64px; height: 64px; font-size: 32px; }

.dd-cover { border-radius: 12px; overflow: hidden; margin-bottom: 14px; }
.dd-cover img { width: 100%; max-height: 240px; object-fit: cover; display: block; }
.dd-head { display: flex; gap: 16px; margin-bottom: 8px; }
.dd-model { font-size: 14px; font-weight: 600; }
.dd-price, .dd-loc { font-size: 13px; color: var(--text-secondary); margin-top: 4px; }
.dd-desc { font-size: 13px; color: #374151; }
h4 { margin: 14px 0 8px; font-size: 14px; }
.dd-docs { font-size: 13px; color: #374151; display: grid; gap: 6px; }
.dd-doc-row { display: flex; align-items: center; gap: 6px; }

.steps { margin-bottom: 22px; }
.form-row { display: flex; gap: 14px; }
.grow { flex: 1; }
:deep(.el-select) { width: 100%; }
.link { color: var(--brand-blue); }

.confirm-box h4 { margin: 0 0 12px; }
.cf-row {
  display: flex; justify-content: space-between; padding: 8px 0;
  border-bottom: 1px dashed var(--border); font-size: 14px;
}
.cf-row span { color: var(--text-secondary); }
.borrow-tips {
  background: #fefce8; border-radius: 10px; padding: 12px 14px; margin-top: 14px;
  font-size: 13px; color: #713f12;
}
.borrow-tips .notice-title { font-weight: 600; margin-bottom: 6px; }
.borrow-tips ul { margin: 0; padding-left: 18px; }

.success-box { text-align: center; padding: 18px 0; }
.success-icon { margin-bottom: 10px; display: flex; justify-content: center; }

.pager { display: flex; justify-content: center; margin: 24px 0; }
.agreement { font-size: 13px; color: #374151; line-height: 1.8; }
.agreement p { margin: 0 0 10px; }
.success-box h3 { margin: 0 0 6px; }
.success-box p { color: var(--text-secondary); margin: 0 0 14px; }
.req-no {
  display: inline-block; background: #f3f4f6; border-radius: 8px;
  padding: 8px 18px; font-size: 14px;
}
</style>
