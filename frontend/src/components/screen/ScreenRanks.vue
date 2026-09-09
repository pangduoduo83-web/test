<template>
  <!-- 班级榜 · 教师榜 · 专业分布 · 技能雷达 -->
  <section class="ranks-page page-enter">
    <div class="frame classes">
      <div class="box-title">班级进度排行<small>· 平均进度 / 完成率 / 周活跃率</small><em>CLASSES</em></div>
      <div v-if="!classes.length" class="empty-hint">还没有班级数据<br /><span style="font-size:12px">老师在「我的班级」建班并布置项目后自动出现</span></div>
      <ul v-else class="rank-list">
        <li v-for="(c, i) in classes.slice(0, 8)" :key="c.name" class="rank-row cls">
          <span class="rank-no" :class="'r' + (i + 1)">{{ i + 1 }}</span>
          <span class="rank-name">{{ c.name }}<span class="rank-sub"> · {{ c.teacher || '未指定' }} · {{ c.members }} 人</span></span>
          <span class="rank-bar"><i :style="{ width: c.avgProgress + '%' }"></i></span>
          <span class="rank-val">{{ c.avgProgress }}%</span>
          <span class="tag grn">完成 {{ c.completionRate }}%</span>
          <span class="tag" :class="c.activeRate >= 60 ? '' : 'amb'">活跃 {{ c.activeRate }}%</span>
        </li>
      </ul>
    </div>

    <div class="frame teachers">
      <div class="box-title">教师带教榜<em>MENTORS</em></div>
      <div v-if="!teachers.length" class="empty-hint">还没有指派讲师的项目</div>
      <table v-else class="tbl">
        <thead><tr><th></th><th>教师</th><th>项目</th><th>学生</th><th>完成</th><th>已评审</th><th>均分</th><th>待评</th></tr></thead>
        <tbody>
          <tr v-for="(t, i) in teachers.slice(0, 8)" :key="t.name">
            <td><span class="rank-no" :class="'r' + (i + 1)">{{ i + 1 }}</span></td>
            <td class="name">{{ t.name }}</td>
            <td>{{ t.projects }}</td>
            <td><b>{{ t.students }}</b></td>
            <td>{{ t.completed }}</td>
            <td>{{ t.graded }}</td>
            <td>{{ t.avgScore ?? '–' }}</td>
            <td :class="{ warn: t.pending > 0 }">{{ t.pending }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="frame majors">
      <div class="box-title">学生专业分布<em>MAJORS</em></div>
      <div ref="majorEl" class="chart"></div>
    </div>
    <div class="frame skills">
      <div class="box-title">全校技能画像<small>· 各维度平均掌握度</small><em>SKILLS</em></div>
      <div ref="skillEl" class="chart"></div>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({ data: { type: Object, required: true }, palette: { type: Object, required: true } })
const classes = computed(() => props.data.classes || [])
const teachers = computed(() => props.data.teachers || [])
const majorEl = ref(null)
const skillEl = ref(null)
let majorChart = null
let skillChart = null

const render = () => {
  const p = props.palette
  if (majorEl.value) {
    if (!majorChart) majorChart = echarts.init(majorEl.value)
    const majors = (props.data.majors || []).slice(0, 7)
    majorChart.setOption({
      backgroundColor: 'transparent', animationDuration: 1000,
      color: ['#22e1ff', '#35e37a', '#f7b731', '#b97cff', '#ff5b6e', '#3b9dff', '#94a3b8'],
      tooltip: { trigger: 'item', backgroundColor: p.tooltipBg, borderColor: p.border, textStyle: { color: p.text } },
      legend: { orient: 'vertical', right: 10, top: 'middle', textStyle: { color: p.textDim, fontSize: 11 }, itemWidth: 10, itemHeight: 10,
        formatter: (name) => { const m = majors.find((x) => x.name === name); return `${name}  ${m ? m.value : ''}` } },
      series: [{ type: 'pie', radius: ['48%', '74%'], center: ['32%', '52%'], avoidLabelOverlap: true,
        itemStyle: { borderColor: p.pieBorder, borderWidth: 2 }, label: { show: false },
        emphasis: { label: { show: true, formatter: '{b}\n{d}%', color: p.text, fontSize: 13, fontWeight: 700 } },
        data: majors.length ? majors : [{ name: '暂无数据', value: 1 }] }]
    })
  }
  if (skillEl.value) {
    if (!skillChart) skillChart = echarts.init(skillEl.value)
    const skills = props.data.skills || []
    skillChart.setOption({
      backgroundColor: 'transparent', animationDuration: 1000,
      tooltip: { backgroundColor: p.tooltipBg, borderColor: p.border, textStyle: { color: p.text } },
      legend: { bottom: 0, textStyle: { color: p.textDim, fontSize: 11 }, itemWidth: 12, itemHeight: 8 },
      radar: { center: ['50%', '48%'], radius: '62%',
        indicator: skills.length ? skills.map((s) => ({ name: s.name, max: 100 })) : [{ name: '暂无', max: 100 }],
        axisName: { color: p.textDim, fontSize: 11 }, splitLine: { lineStyle: { color: p.grid } }, splitArea: { areaStyle: { color: ['transparent', p.chip] } }, axisLine: { lineStyle: { color: p.grid } } },
      series: [{ type: 'radar', data: [
        { name: '全校平均', value: skills.map((s) => s.avg), areaStyle: { color: 'rgba(34,225,255,.25)' }, lineStyle: { color: '#22e1ff', width: 2 }, itemStyle: { color: '#22e1ff' } },
        { name: '最高分', value: skills.map((s) => s.max), areaStyle: { color: 'rgba(53,227,122,.08)' }, lineStyle: { color: '#35e37a', width: 1, type: 'dashed' }, itemStyle: { color: '#35e37a' } }
      ] }]
    })
  }
}
onMounted(() => nextTick(render))
watch(() => [props.data, props.palette], () => nextTick(render), { deep: true })
onBeforeUnmount(() => { majorChart?.dispose(); skillChart?.dispose() })
</script>

<style scoped>
.ranks-page { flex: 1; display: grid; grid-template-columns: 1.25fr 1fr; grid-template-rows: 1fr 1fr; gap: 14px; min-height: 0; }
.classes, .teachers, .majors, .skills { display: flex; flex-direction: column; position: relative; }
.classes .rank-list { flex: 1; justify-content: space-around; }
.rank-row.cls .rank-bar { flex: 1.4; }
.rank-row.cls .rank-val { width: 52px; }
.tbl { width: 100%; border-collapse: collapse; font-size: 13px; margin: 6px 0 8px; }
.tbl th { font-weight: 600; color: var(--txt-dim); font-size: 11px; padding: 6px 10px; text-align: left; border-bottom: 1px solid var(--cy-dim); }
.tbl td { padding: 7px 10px; border-bottom: 1px dashed var(--grid-line); color: var(--txt); }
.tbl td.name { color: var(--ink); font-weight: 700; }
.tbl td b { color: var(--cy); }
.tbl td.warn { color: var(--amb); font-weight: 700; }
</style>
