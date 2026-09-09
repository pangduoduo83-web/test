<template>
  <div>
    <p v-if="data.summary" class="lead">{{ data.summary }}</p>
    <div class="path">
      <div v-for="(p, i) in data.path || []" :key="i" class="stage">
        <div class="stage-head">
          <span class="stage-no">{{ i + 1 }}</span>
          <span class="stage-name">{{ p.stage }}</span>
          <span class="badge" :class="diffClass(p.difficulty)">{{ p.difficulty }}</span>
          <span class="muted">{{ p.duration }}</span>
          <span class="match"><i :style="{ width: (p.match || 0) + '%' }"></i><b>{{ p.match }}% 匹配</b></span>
        </div>
        <div class="stage-title">{{ p.title }}</div>
        <div class="stage-reason">{{ p.reason }}</div>
        <div v-if="p.firstStep" class="stage-first">▶ 第一步:{{ p.firstStep }}</div>
        <div class="stage-foot">
          <span class="skills"><span v-for="s in p.skills || []" :key="s" class="chip">{{ s }}</span></span>
          <span class="actions">
            <el-button size="small" text type="primary" @click="$router.push(`/app/projects/${p.projectId}`)">查看项目</el-button>
            <el-button size="small" type="primary" plain :loading="enrolling === p.projectId" @click="enroll(p)">一键报名</el-button>
          </span>
        </div>
      </div>
    </div>
    <div v-if="data.weeklyPlan" class="plan">📅 {{ data.weeklyPlan }}</div>
    <ul v-if="data.tips?.length" class="tips"><li v-for="t in data.tips" :key="t">{{ t }}</li></ul>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { enrollProject } from '../../api'

defineProps({ data: { type: Object, required: true } })
const enrolling = ref(null)
const diffClass = (d) => ({ 入门: 'badge-green', 进阶: 'badge-blue', 挑战: 'badge-red' }[d] || 'badge-gray')
const enroll = async (p) => {
  enrolling.value = p.projectId
  try {
    await enrollProject(p.projectId)
    ElMessage.success(`已报名《${p.title}》,去项目页看第一步吧`)
  } catch (e) { /* 已提示(可能已报名) */ } finally {
    enrolling.value = null
  }
}
</script>

<style scoped>
.lead { font-size: 15px; color: #111827; line-height: 1.7; margin: 0 0 16px; padding: 12px 16px; background: linear-gradient(90deg, #eef2ff, #fff); border-left: 3px solid #6366f1; border-radius: 0 10px 10px 0; }
.path { display: flex; flex-direction: column; gap: 12px; position: relative; }
.stage { border: 1px solid var(--border); border-radius: 14px; padding: 14px 16px; position: relative; background: #fff; transition: box-shadow .15s; }
.stage:hover { box-shadow: var(--shadow-card); }
.stage-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.stage-no { width: 26px; height: 26px; border-radius: 50%; background: var(--brand-gradient-br); color: #fff; font-weight: 800; font-size: 13px; display: grid; place-items: center; }
.stage-name { font-weight: 700; color: #4338ca; }
.muted { font-size: 12px; color: var(--text-secondary); }
.match { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.match i { display: block; width: 90px; height: 6px; border-radius: 3px; background: linear-gradient(90deg, #a5b4fc, #4f46e5); }
.match b { font-size: 12px; color: #4338ca; }
.stage-title { font-size: 17px; font-weight: 800; margin-top: 8px; }
.stage-reason { font-size: 13px; color: #4b5563; line-height: 1.7; margin-top: 4px; }
.stage-first { font-size: 13px; color: #065f46; background: #ecfdf5; padding: 6px 10px; border-radius: 8px; margin-top: 8px; }
.stage-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; gap: 10px; flex-wrap: wrap; }
.skills { display: flex; gap: 6px; flex-wrap: wrap; }
.chip { font-size: 11px; background: #f3f4f6; color: #374151; padding: 2px 8px; border-radius: 999px; }
.plan { margin-top: 14px; font-size: 13px; color: #374151; }
.tips { margin: 10px 0 0; padding-left: 18px; font-size: 13px; color: #4b5563; line-height: 1.8; }
</style>
