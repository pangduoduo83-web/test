<template>
  <div>
    <!-- 页头 -->
    <div class="head-row">
      <div>
        <h2 class="page-title">开源硬件项目中心</h2>
        <p class="page-subtitle">从原理图到 PCB 的完整工程实践</p>
      </div>
      <div class="head-right">
        <span class="head-count">开源项目: <b>{{ filtered.length.toLocaleString() }}</b> 个</span>
        <div class="view-toggle">
          <button class="vt-btn" :class="{ active: viewMode === 'grid' }" @click="viewMode = 'grid'">
            <LayoutGrid :size="15" />
          </button>
          <button class="vt-btn" :class="{ active: viewMode === 'list' }" @click="viewMode = 'list'">
            <List :size="15" />
          </button>
        </div>
      </div>
    </div>

    <!-- 搜索与筛选 -->
    <div class="card filter-bar">
      <el-input v-model="keyword" class="search" placeholder="搜索项目名称、芯片型号、作者..."
                clearable @keyup.enter="load" @clear="load">
        <template #prefix><Search :size="15" /></template>
      </el-input>
      <el-button :type="advancedOpen ? 'primary' : 'default'" @click="advancedOpen = !advancedOpen">
        <SlidersHorizontal :size="14" style="margin-right:5px" /> 高级筛选
      </el-button>
      <el-select v-model="sort" class="sort" @change="load">
        <el-option label="最热门" value="popular" />
        <el-option label="评分最高" value="rating" />
        <el-option label="最新更新" value="newest" />
        <el-option label="下载最多" value="downloads" />
      </el-select>
    </div>

    <!-- 高级筛选(难度) -->
    <div v-if="advancedOpen" class="card advanced-bar">
      <span class="adv-label">项目难度:</span>
      <span v-for="d in ['全部', '入门', '进阶', '挑战']" :key="d" class="pill"
            :class="{ active: difficulty === d }" @click="difficulty = d">{{ d }}</span>
      <el-checkbox v-model="onlyVerified" class="adv-check">只看硬件已验证</el-checkbox>
    </div>

    <!-- 热门标签 -->
    <div class="pills-row">
      <span class="pill" :class="{ active: activeTag === '' }" @click="activeTag = ''">全部</span>
      <span v-for="t in hotTags" :key="t" class="pill" :class="{ active: activeTag === t }"
            @click="activeTag = activeTag === t ? '' : t">{{ t }}</span>
    </div>

    <!-- 分类 tab -->
    <div class="pills-row">
      <span class="pill" :class="{ active: activeCategory === '' }" @click="activeCategory = ''">
        <LayoutGrid :size="14" /> 全部项目 <span class="pill-count">{{ items.length }}</span>
      </span>
      <span v-for="c in categories" :key="c.name" class="pill"
            :class="{ active: activeCategory === c.name }"
            @click="activeCategory = activeCategory === c.name ? '' : c.name">
        <component :is="c.icon" :size="14" /> {{ c.name }} <span class="pill-count">{{ c.count }}</span>
      </span>
    </div>

    <!-- 项目卡片(网格) -->
    <el-empty v-if="pageItems.length === 0" description="未找到匹配的项目,请尝试调整筛选条件" />
    <div v-else-if="viewMode === 'grid'" class="grid">
      <div v-for="p in pageItems" :key="p.id" class="project-card"
           @click="$router.push(`/app/projects/${p.id}`)">
        <!-- 封面 -->
        <div class="pc-cover">
          <img v-if="p.coverUrl && !failedCovers.has(p.id)" :src="p.coverUrl" :alt="p.title"
               @error="failedCovers.add(p.id)" />
          <div v-else class="pc-cover-fallback"><CircuitBoard :size="52" color="rgba(255,255,255,.85)" /></div>
          <span class="cover-badge" :class="diffColor(p.difficulty)" style="top:10px;left:10px">{{ p.difficulty }}</span>
          <span class="cover-badge rating" style="top:10px;right:10px">
            <Star :size="12" fill="#facc15" color="#facc15" /> {{ p.rating }}
          </span>
          <span class="cover-badge green" style="bottom:10px;left:10px">开源</span>
          <span v-if="p.verified" class="cover-badge blue" style="bottom:10px;right:10px">
            <BadgeCheck :size="12" /> 已验证
          </span>
        </div>

        <div class="pc-body">
          <h3 class="pc-title">{{ p.title }}</h3>
          <p class="pc-summary">{{ p.summary }}</p>
          <div class="pc-tags">
            <span v-for="t in arr(p.tags).slice(0, 3)" :key="t" class="chip">{{ t }}</span>
            <span v-if="arr(p.tags).length > 3" class="chip">+{{ arr(p.tags).length - 3 }}</span>
          </div>

          <div class="pc-stats">
            <span><Eye :size="13" /> {{ fmtNum(p.views) }}</span>
            <span><Star :size="13" /> {{ fmtNum(p.favoriteCount) }}</span>
            <span><GitFork :size="13" /> {{ fmtNum(p.forks || 0) }}</span>
            <span><Download :size="13" /> {{ fmtNum(p.downloads) }}</span>
          </div>
          <div class="pc-meta-row">
            <span><Clock :size="13" /> {{ p.duration }}</span>
            <span><Users :size="13" /> {{ p.teamSize }}</span>
            <span class="pc-enrolled">{{ (p.enrolledCount || 0).toLocaleString() }}人参与</span>
          </div>

          <div class="pc-author">
            <span class="pc-avatar">{{ (p.author || '匿')[0] }}</span>
            <span class="pc-author-name">{{ p.author || '匿名' }}</span>
            <span class="pc-license">{{ p.license }}</span>
          </div>

          <div class="pc-heat">
            <div class="pc-heat-head">
              <span>项目热度值</span>
              <b>{{ p.completionRate }}%</b>
            </div>
            <div class="pc-heat-bar">
              <div class="pc-heat-inner" :style="{ width: p.completionRate + '%' }"></div>
            </div>
          </div>

          <div class="pc-pcb-row">
            <span class="pc-pcb">{{ pcbText(p) }}</span>
            <span v-if="p.cost" class="pc-cost">成本: ¥{{ p.cost }}</span>
          </div>
          <div class="pc-features">
            <span v-for="f in arr(p.features).slice(0, 4)" :key="f" class="feature-chip">{{ f }}</span>
          </div>

          <button class="pc-detail-btn" @click.stop="$router.push(`/app/projects/${p.id}`)">
            查看详情 →
          </button>
        </div>
      </div>
    </div>

    <!-- 项目列表(横排) -->
    <div v-else class="list-col">
      <div v-for="p in pageItems" :key="p.id" class="list-card"
           @click="$router.push(`/app/projects/${p.id}`)">
        <div class="lc-cover">
          <img v-if="p.coverUrl && !failedCovers.has(p.id)" :src="p.coverUrl" :alt="p.title"
               @error="failedCovers.add(p.id)" />
          <div v-else class="pc-cover-fallback small"><CircuitBoard :size="32" color="rgba(255,255,255,.85)" /></div>
        </div>
        <div class="lc-main">
          <div class="lc-title-row">
            <h3 class="pc-title">{{ p.title }}</h3>
            <span class="badge" :class="diffBadge(p.difficulty)">{{ p.difficulty }}</span>
            <span v-if="p.verified" class="badge badge-blue"><BadgeCheck :size="12" /> 已验证</span>
          </div>
          <p class="pc-summary">{{ p.summary }}</p>
          <div class="pc-stats">
            <span><Eye :size="13" /> {{ fmtNum(p.views) }}</span>
            <span><Star :size="13" /> {{ fmtNum(p.favoriteCount) }}</span>
            <span><GitFork :size="13" /> {{ fmtNum(p.forks || 0) }}</span>
            <span><Download :size="13" /> {{ fmtNum(p.downloads) }}</span>
            <span><Users :size="13" /> {{ (p.enrolledCount || 0).toLocaleString() }}人参与</span>
          </div>
        </div>
        <div class="lc-right">
          <span class="lc-rating"><Star :size="14" fill="#facc15" color="#facc15" /> {{ p.rating }}</span>
          <button class="pc-detail-btn slim" @click.stop="$router.push(`/app/projects/${p.id}`)">查看详情</button>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pager">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length"
                     layout="prev, pager, next" background />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  BadgeCheck, CircuitBoard, Clock, Cog, Cpu, Download, Eye, Folder, Gauge,
  GitFork, LayoutGrid, List, Radio, Search, ShoppingCart, SlidersHorizontal,
  Star, Users, Wifi, Zap
} from 'lucide-vue-next'
import { fetchProjects } from '../../api'
import { loadSiteConfig, siteConfig as site } from '../../utils/siteConfig'

const route = useRoute()
const keyword = ref(route.query.keyword || '')
const sort = ref('popular')
const items = ref([])
const page = ref(1)
const pageSize = computed(() => site.projectPageSize || 9)
const viewMode = ref('grid')
const advancedOpen = ref(false)
const difficulty = ref('全部')
const onlyVerified = ref(false)
const activeTag = ref('')
const activeCategory = ref('')
const failedCovers = ref(new Set())

const categoryIcons = {
  '开发板/评估板': CircuitBoard, '物联网应用': Wifi, '电源管理': Zap, '电机控制': Cog,
  '测量仪器': Gauge, '通信模块': Radio, 'FPGA/EDA': Cpu, '消费电子': ShoppingCart
}

const arr = (v) => Array.isArray(v) ? v : []

const fmtNum = (n) => {
  const v = n || 0
  return v >= 1000 ? (v / 1000).toFixed(1) + 'k' : String(v)
}

const pcbText = (p) => {
  const parts = []
  if (p.layers) parts.push(p.layers + '层')
  if (p.pcbSize) parts.push(p.pcbSize)
  return parts.length ? 'PCB: ' + parts.join(' ') : ''
}

const diffColor = (d) => d === '入门' ? 'green' : d === '进阶' ? 'purple' : 'red'
const diffBadge = (d) => d === '入门' ? 'badge-green' : d === '进阶' ? 'badge-purple' : 'badge-red'

// 热门标签:按出现频次取前 10
const hotTags = computed(() => {
  const freq = {}
  items.value.forEach((p) => arr(p.tags).forEach((t) => { freq[t] = (freq[t] || 0) + 1 }))
  return Object.keys(freq).sort((a, b) => freq[b] - freq[a]).slice(0, 10)
})

// 分类计数
const categories = computed(() => {
  const map = {}
  items.value.forEach((p) => {
    if (!p.category) return
    map[p.category] = (map[p.category] || 0) + 1
  })
  return Object.keys(map).map((name) => ({
    name, count: map[name], icon: categoryIcons[name] || Folder
  }))
})

const filtered = computed(() => items.value.filter((p) => {
  if (difficulty.value !== '全部' && p.difficulty !== difficulty.value) return false
  if (onlyVerified.value && !p.verified) return false
  if (activeTag.value && !arr(p.tags).includes(activeTag.value)) return false
  if (activeCategory.value && p.category !== activeCategory.value) return false
  return true
}))

const pageItems = computed(() =>
  filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))

watch([difficulty, onlyVerified, activeTag, activeCategory], () => { page.value = 1 })

const load = async () => {
  items.value = await fetchProjects({
    keyword: keyword.value || undefined,
    sort: sort.value
  })
  page.value = 1
}

onMounted(() => {
  loadSiteConfig()
  load()
})
</script>

<style scoped>
.head-row { display: flex; justify-content: space-between; align-items: flex-start; }
.head-right { display: flex; align-items: center; gap: 14px; }
.head-count { font-size: 13px; color: var(--text-secondary); }
.head-count b { color: var(--brand-blue); font-size: 15px; }
.view-toggle { display: flex; gap: 6px; }
.vt-btn {
  width: 32px; height: 32px; border-radius: 8px;
  border: 1px solid var(--border); background: #fff;
  cursor: pointer; color: #6b7280;
  display: flex; align-items: center; justify-content: center;
}
.vt-btn.active { background: var(--brand-blue); border-color: var(--brand-blue); color: #fff; }

.filter-bar {
  display: flex; gap: 12px; align-items: center;
  padding: 14px 16px; margin-bottom: 12px;
}
.search { flex: 1; }
.search :deep(.el-input__wrapper) { border-radius: 10px; }
.sort { width: 130px; }

.advanced-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 16px; margin-bottom: 12px;
}
.adv-label { font-size: 13px; color: var(--text-secondary); }
.adv-check { margin-left: auto; }

.pills-row {
  display: flex; gap: 8px; flex-wrap: wrap;
  margin-bottom: 12px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.project-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: var(--shadow-card);
  cursor: pointer;
  transition: transform .15s, box-shadow .15s;
  display: flex; flex-direction: column;
  overflow: hidden;
}
.project-card:hover { transform: translateY(-3px); box-shadow: 0 20px 25px -5px rgba(0,0,0,.1); }

.pc-cover { position: relative; height: 160px; }
.pc-cover img { width: 100%; height: 100%; object-fit: cover; display: block; }
.pc-cover-fallback {
  width: 100%; height: 100%;
  background: linear-gradient(135deg, #3b82f6, #9333ea);
  display: flex; align-items: center; justify-content: center;
}
.pc-cover-fallback span { font-size: 52px; }
.pc-cover-fallback.small span { font-size: 32px; }

.pc-body { padding: 16px 18px 18px; display: flex; flex-direction: column; flex: 1; }
.pc-title { margin: 0 0 6px; font-size: 16px; color: #111827; }
.pc-summary {
  margin: 0 0 10px; font-size: 13px; color: var(--text-secondary);
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
  min-height: 38px;
}
.pc-tags { margin-bottom: 6px; }

.pc-stats {
  display: flex; gap: 14px; font-size: 12px; color: var(--text-secondary);
  padding: 6px 0; flex-wrap: wrap;
}
.pc-stats span { display: inline-flex; align-items: center; gap: 4px; }
.pc-meta-row {
  display: flex; gap: 14px; align-items: center;
  font-size: 12px; color: var(--text-secondary);
  padding-bottom: 8px; border-bottom: 1px solid var(--border);
}
.pc-meta-row span { display: inline-flex; align-items: center; gap: 4px; }
.pc-enrolled { margin-left: auto; color: #374151; }

.pc-author { display: flex; align-items: center; gap: 8px; padding: 10px 0 4px; }
.pc-avatar {
  width: 26px; height: 26px; border-radius: 50%; flex-shrink: 0;
  background: linear-gradient(135deg, #60a5fa, #a855f7); color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 12px;
}
.pc-author-name { font-size: 13px; color: #374151; }
.pc-license { margin-left: auto; font-size: 12px; color: #9ca3af; }

.pc-heat { margin: 6px 0 8px; }
.pc-heat-head {
  display: flex; justify-content: space-between;
  font-size: 12px; color: var(--text-secondary); margin-bottom: 5px;
}
.pc-heat-head b { color: #111827; }
.pc-heat-bar { height: 6px; border-radius: 999px; background: #f3f4f6; overflow: hidden; }
.pc-heat-inner {
  height: 100%; border-radius: 999px;
  background: linear-gradient(to right, #3b82f6, #9333ea);
}

.pc-pcb-row {
  display: flex; justify-content: space-between;
  font-size: 12px; color: var(--text-secondary); margin-bottom: 6px;
}
.pc-cost { color: #111827; font-weight: 500; }
.pc-features { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; min-height: 22px; }
.feature-chip {
  font-size: 11px; color: #6b7280; background: #f3f4f6;
  border-radius: 6px; padding: 2px 8px;
}

.pc-detail-btn {
  margin-top: auto;
  width: 100%; padding: 10px 0;
  background: var(--brand-blue); color: #fff;
  border: none; border-radius: 10px;
  font-size: 14px; font-weight: 500; cursor: pointer;
  transition: background .15s;
}
.pc-detail-btn:hover { background: #1d4ed8; }
.pc-detail-btn.slim { width: auto; padding: 8px 18px; margin: 0; }

/* 列表模式 */
.list-col { display: flex; flex-direction: column; gap: 12px; }
.list-card {
  background: #fff; border-radius: 16px; box-shadow: var(--shadow-card);
  display: flex; gap: 16px; padding: 14px; cursor: pointer;
  transition: transform .15s, box-shadow .15s;
}
.list-card:hover { transform: translateY(-2px); box-shadow: 0 20px 25px -5px rgba(0,0,0,.08); }
.lc-cover { width: 180px; height: 110px; border-radius: 12px; overflow: hidden; flex-shrink: 0; }
.lc-cover img { width: 100%; height: 100%; object-fit: cover; }
.lc-main { flex: 1; min-width: 0; }
.lc-title-row { display: flex; align-items: center; gap: 10px; margin-bottom: 4px; }
.lc-right { display: flex; flex-direction: column; align-items: flex-end; justify-content: space-between; flex-shrink: 0; }
.lc-rating { font-size: 13px; color: #374151; display: inline-flex; align-items: center; gap: 4px; }

.pager { display: flex; justify-content: center; margin: 24px 0; }
</style>
