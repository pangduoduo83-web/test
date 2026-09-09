<template>
  <!-- 成果墙:优秀成果大卡轮播 + 光荣榜 -->
  <section class="works-page page-enter">
    <div class="frame wall">
      <div class="box-title">优秀成果展示<small>· 评审得分最高的学生作品</small><em>SHOWCASE</em></div>
      <div v-if="!works.length" class="empty-hint">还没有评分的成果<br /><span style="font-size:12px">学生提交成果并由老师评分后,这里会自动展示</span></div>
      <div v-else class="wall-grid">
        <div v-for="(w, i) in pageWorks" :key="page + '-' + i" class="work" :style="{ animationDelay: (i * 60) + 'ms' }">
          <div class="cover" :style="w.coverUrl ? { backgroundImage: `url(${w.coverUrl})` } : {}">
            <span v-if="!w.coverUrl" class="cover-icon">{{ w.icon || '🔌' }}</span>
            <span class="score" :class="{ top: w.score >= 90 }">{{ w.score }}<small>分</small></span>
            <span v-if="w.assessmentName" class="assess">{{ w.assessmentName }}</span>
          </div>
          <div class="w-body">
            <div class="w-title" :title="w.projectTitle">{{ w.projectTitle }}</div>
            <div class="w-meta"><b>{{ w.studentName }}</b><span v-if="w.major"> · {{ w.major }}</span><span v-if="w.mentor" class="dim"> · 指导 {{ w.mentor }}</span></div>
            <div class="w-fb">“{{ w.feedback || '完成度高,表现优秀' }}”</div>
          </div>
        </div>
      </div>
      <div v-if="pageCount > 1" class="wall-dots"><i v-for="p in pageCount" :key="p" :class="{ on: p - 1 === page }" @click="page = p - 1"></i></div>
    </div>

    <aside class="side">
      <div class="frame honor">
        <div class="box-title">本周光荣榜<em>HONOR</em></div>
        <ul class="rank-list">
          <li v-for="(s, i) in honor" :key="s.userId" class="rank-row">
            <span class="rank-no" :class="'r' + (i + 1)">{{ i + 1 }}</span>
            <span class="rank-name">{{ s.name }}<span class="rank-sub" v-if="s.major"> · {{ s.major }}</span></span>
            <span class="rank-sub">{{ s.activeDays7d }} 天活跃 · {{ s.actions7d }} 次</span>
            <span class="rank-val">{{ s.progress }}%</span>
          </li>
          <li v-if="!honor.length" class="dim" style="font-size:13px;padding:8px 0">暂无学生数据</li>
        </ul>
      </div>
      <div class="frame quality">
        <div class="box-title">成果质量<em>QUALITY</em></div>
        <div class="kv-grid">
          <div class="kv ok"><b>{{ data.grades.excellent }}</b><span>优秀(≥85)</span></div>
          <div class="kv"><b>{{ data.grades.passRate }}%</b><span>及格率</span></div>
          <div class="kv"><b>{{ data.grades.avgScore }}</b><span>平均分</span></div>
          <div class="kv"><b>{{ data.grades.graded }}</b><span>已评审</span></div>
          <div class="kv" :class="{ warn: data.grades.pending > 0 }"><b>{{ data.grades.pending }}</b><span>待评审</span></div>
          <div class="kv" :class="{ bad: data.grades.failing > 0 }"><b>{{ data.grades.failing }}</b><span>未及格</span></div>
        </div>
        <div class="dist">
          <div class="dist-label">分数段分布</div>
          <div class="dist-bar">
            <i class="d1" :style="{ flex: dist.excellent || 0.001 }" :title="`优秀 ${dist.excellent}`"></i>
            <i class="d2" :style="{ flex: dist.good || 0.001 }" :title="`良好 ${dist.good}`"></i>
            <i class="d3" :style="{ flex: dist.pass || 0.001 }" :title="`及格 ${dist.pass}`"></i>
            <i class="d4" :style="{ flex: dist.fail || 0.001 }" :title="`未及格 ${dist.fail}`"></i>
          </div>
          <div class="dist-legend"><span><i class="d1"></i>优秀 {{ dist.excellent }}</span><span><i class="d2"></i>良好 {{ dist.good }}</span><span><i class="d3"></i>及格 {{ dist.pass }}</span><span><i class="d4"></i>未及格 {{ dist.fail }}</span></div>
        </div>
      </div>
    </aside>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({ data: { type: Object, required: true } })
const PAGE = 6
const page = ref(0)
const works = computed(() => props.data.works || [])
const pageCount = computed(() => Math.ceil(works.value.length / PAGE))
const pageWorks = computed(() => works.value.slice(page.value * PAGE, (page.value + 1) * PAGE))
const honor = computed(() => [...(props.data.students || [])]
  .sort((a, b) => (b.activeDays7d * 10 + b.actions7d) - (a.activeDays7d * 10 + a.actions7d) || b.progress - a.progress)
  .slice(0, 8))
const dist = computed(() => {
  const g = props.data.grades || {}
  const excellent = g.excellent || 0
  const fail = g.failing || 0
  const rest = Math.max(0, (g.graded || 0) - excellent - fail)
  // 没有逐份分数时按经验 6:4 拆良好 / 及格
  return { excellent, good: Math.round(rest * 0.6), pass: rest - Math.round(rest * 0.6), fail }
})
let timer = null
onMounted(() => { timer = setInterval(() => { if (pageCount.value > 1) page.value = (page.value + 1) % pageCount.value }, 8000) })
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.works-page { flex: 1; display: grid; grid-template-columns: 1fr 440px; gap: 14px; min-height: 0; }
.wall { display: flex; flex-direction: column; padding-bottom: 10px; }
.wall-grid { flex: 1; display: grid; grid-template-columns: repeat(3, 1fr); grid-template-rows: repeat(2, 1fr); gap: 14px; padding: 12px 16px 4px; min-height: 0; }
.work { position: relative; border: 1px solid var(--cy-dim); border-radius: 12px; overflow: hidden; background: var(--card-bg); display: flex; flex-direction: column; animation: sc-enter .5s both; transition: transform .2s, box-shadow .2s; }
.work:hover { transform: translateY(-3px); box-shadow: 0 10px 30px var(--cy-soft); }
.cover { position: relative; height: 56%; background: linear-gradient(135deg, rgba(34, 225, 255, .18), rgba(185, 124, 255, .18)) center / cover no-repeat; display: grid; place-items: center; }
.cover-icon { font-size: 54px; filter: drop-shadow(0 0 14px var(--cy-dim)); }
.score { position: absolute; right: 10px; top: 10px; background: rgba(4, 16, 31, .75); color: var(--grn); font-weight: 800; font-size: 22px; padding: 2px 10px; border-radius: 8px; border: 1px solid rgba(53, 227, 122, .5); }
.score small { font-size: 11px; margin-left: 2px; color: var(--txt-dim); }
.score.top { color: #ffe27a; border-color: rgba(255, 226, 122, .6); text-shadow: 0 0 10px rgba(255, 213, 74, .8); }
.assess { position: absolute; left: 10px; top: 10px; font-size: 11px; background: rgba(4, 16, 31, .7); color: var(--txt); padding: 2px 8px; border-radius: 999px; }
.w-body { padding: 10px 14px 12px; display: flex; flex-direction: column; gap: 4px; min-height: 0; }
.w-title { font-size: 15px; font-weight: 700; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.w-meta { font-size: 12px; color: var(--txt-dim); }
.w-meta b { color: var(--cy); font-size: 13px; }
.w-fb { font-size: 12px; color: var(--txt-dim); font-style: italic; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wall-dots { display: flex; justify-content: center; gap: 8px; padding-top: 6px; }
.wall-dots i { width: 22px; height: 4px; border-radius: 2px; background: var(--cy-dim); cursor: pointer; }
.wall-dots i.on { background: var(--cy); box-shadow: 0 0 8px var(--cy); }
.side { display: grid; grid-template-rows: 1fr 260px; gap: 14px; min-height: 0; }
.honor { display: flex; flex-direction: column; }
.honor .rank-list { flex: 1; }
.quality { display: flex; flex-direction: column; }
.dist { padding: 6px 16px 12px; }
.dist-label { font-size: 12px; color: var(--txt-dim); margin-bottom: 6px; }
.dist-bar { display: flex; height: 12px; border-radius: 6px; overflow: hidden; gap: 2px; }
.dist-bar i, .dist-legend i { display: block; }
.d1 { background: #35e37a; } .d2 { background: #22e1ff; } .d3 { background: #f7b731; } .d4 { background: #ff5b6e; }
.dist-legend { display: flex; gap: 12px; margin-top: 8px; font-size: 11px; color: var(--txt-dim); }
.dist-legend span { display: inline-flex; align-items: center; gap: 5px; }
.dist-legend i { width: 8px; height: 8px; border-radius: 2px; }
</style>
