<template>
  <div>
    <div class="head-row">
      <div>
        <h2 class="page-title">我的班级</h2>
        <p class="page-subtitle">老师布置的项目、截止日期和班级公告都在这里</p>
      </div>
      <button class="btn-gradient join-btn" @click="joinVisible = true"><KeyRound :size="15" /> 输入加入码</button>
    </div>

    <el-empty v-if="classes.length === 0" description="你还没有加入任何班级。向老师索取 6 位加入码,点右上角输入即可加入。" />

    <div v-for="c in classes" :key="c.id" class="card class-block">
      <div class="cb-head">
        <span class="cb-avatar">{{ c.name[0] }}</span>
        <div class="grow">
          <div class="cb-title">{{ c.name }} <span v-if="c.status !== 'ACTIVE'" class="badge badge-gray">已归档</span></div>
          <div class="cb-sub">授课教师 {{ c.teacherName }} · {{ c.memberCount }} 名同学<span v-if="c.description"> · {{ c.description }}</span></div>
        </div>
      </div>

      <div class="cb-grid">
        <div>
          <h4>布置的项目</h4>
          <el-empty v-if="c.assignments.length === 0" description="老师还没有布置项目" :image-size="60" />
          <div v-for="a in c.assignments" :key="a.id" class="assign" :class="{ overdue: a.overdue, done: a.status === 'COMPLETED' }" @click="$router.push(`/app/projects/${a.projectId}`)">
            <div class="grow">
              <div class="as-title">{{ a.projectTitle }}</div>
              <div class="as-meta">
                <span v-if="a.deadline">截止 {{ a.deadline }}{{ a.overdue ? ' · 已过截止' : daysLeft(a.deadline) }}</span>
                <span v-else>未设截止</span>
                <span v-if="a.note"> · {{ a.note }}</span>
              </div>
            </div>
            <div class="as-right">
              <span v-if="a.status === 'COMPLETED'" class="badge badge-green">已完成</span>
              <template v-else>
                <el-progress type="circle" :percentage="a.progress || 0" :width="44" :stroke-width="5" :color="a.overdue ? '#dc2626' : '#3b82f6'" />
              </template>
            </div>
          </div>
        </div>
        <div>
          <h4>班级公告</h4>
          <el-empty v-if="c.announcements.length === 0" description="暂无公告" :image-size="60" />
          <div v-for="an in c.announcements" :key="an.id" class="announce">
            <div class="an-title">{{ an.title }}</div>
            <div v-if="an.content" class="an-body">{{ an.content }}</div>
            <div class="an-meta">{{ an.authorName }} · {{ fmt(an.createdAt) }}</div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="joinVisible" title="加入班级" width="420px">
      <p class="join-tip">输入老师给你的 6 位加入码。加入后老师已布置的项目会自动为你报名。</p>
      <el-input v-model="code" maxlength="8" placeholder="如 A7K2PQ" size="large" class="code-input" @keyup.enter="join" />
      <template #footer>
        <el-button @click="joinVisible = false">取消</el-button>
        <el-button type="primary" :loading="joining" @click="join">加入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { KeyRound } from 'lucide-vue-next'
import { joinClass, myClasses } from '../../api'

const classes = ref([])
const joinVisible = ref(false)
const joining = ref(false)
const code = ref('')

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')
const daysLeft = (d) => {
  const diff = Math.ceil((new Date(String(d).replace(/-/g, '/')) - new Date()) / 86400000)
  return diff >= 0 ? ` · 还剩 ${diff} 天` : ''
}

const load = async () => { classes.value = await myClasses() }

const join = async () => {
  if (!code.value.trim()) { ElMessage.warning('请输入加入码'); return }
  joining.value = true
  try {
    const c = await joinClass(code.value.trim())
    ElMessage.success(`已加入「${c.name}」`)
    joinVisible.value = false
    code.value = ''
    await load()
  } catch (e) { /* 已提示 */ } finally {
    joining.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.head-row { display: flex; justify-content: space-between; align-items: flex-start; }
.join-btn { display: inline-flex; align-items: center; gap: 6px; }
.grow { flex: 1; min-width: 0; }
.class-block { margin-bottom: 16px; }
.cb-head { display: flex; align-items: center; gap: 14px; margin-bottom: 16px; }
.cb-avatar { width: 48px; height: 48px; border-radius: 14px; background: var(--brand-gradient-br); color: #fff; font-weight: 800; font-size: 18px; display: grid; place-items: center; flex-shrink: 0; }
.cb-title { font-size: 17px; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.cb-sub { font-size: 13px; color: var(--text-secondary); margin-top: 3px; }
.cb-grid { display: grid; grid-template-columns: 3fr 2fr; gap: 20px; }
@media (max-width: 1000px) { .cb-grid { grid-template-columns: 1fr; } }
h4 { margin: 0 0 10px; font-size: 14px; color: #374151; }
.assign { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border: 1px solid var(--border); border-radius: 12px; margin-bottom: 10px; cursor: pointer; transition: box-shadow .15s; }
.assign:hover { box-shadow: var(--shadow-card); }
.assign.overdue { border-color: #fecaca; background: #fef2f2; }
.assign.done { background: #f0fdf4; border-color: #bbf7d0; }
.as-title { font-weight: 600; font-size: 14px; }
.as-meta { font-size: 12px; color: var(--text-secondary); margin-top: 3px; }
.assign.overdue .as-meta { color: #dc2626; }
.as-right :deep(.el-progress__text) { font-size: 11px !important; }
.announce { padding: 10px 12px; border-left: 3px solid #a78bfa; background: #faf5ff; border-radius: 0 10px 10px 0; margin-bottom: 10px; }
.an-title { font-weight: 600; font-size: 14px; color: #4c1d95; }
.an-body { font-size: 13px; color: #374151; margin-top: 4px; white-space: pre-wrap; line-height: 1.6; }
.an-meta { font-size: 11px; color: #9ca3af; margin-top: 6px; }
.join-tip { font-size: 13px; color: var(--text-secondary); margin: 0 0 12px; line-height: 1.6; }
.code-input :deep(.el-input__inner) { text-align: center; letter-spacing: 6px; font-size: 22px; font-weight: 700; text-transform: uppercase; font-family: ui-monospace, Menlo, Consolas, monospace; }
</style>
