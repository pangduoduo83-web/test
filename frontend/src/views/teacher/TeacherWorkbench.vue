<template>
  <div class="workbench-page" :aria-busy="loading">
    <el-alert v-if="loadFailed" title="部分工作台数据未能加载，请重试。" type="error" :closable="false" class="load-error">
      <button class="text-action" type="button" @click="load">重新加载</button>
    </el-alert>
    <section class="todo-panel" :class="{ 'todo-clear': !totalTodo && !loading && !loadFailed }" aria-labelledby="todo-title">
      <div class="todo-heading">
        <div class="todo-heading-main">
          <span class="todo-alert"><component :is="totalTodo || loading || loadFailed ? AlertTriangle : CircleCheckBig" :size="27" /></span>
          <div>
            <div class="todo-title-row">
              <h2 id="todo-title">今日待办</h2>
              <span v-if="loading" class="todo-count">正在同步教学数据...</span>
              <span v-else-if="loadFailed" class="todo-count">待办数据待更新</span>
              <span v-else-if="totalTodo" class="todo-count">今天有 <b>{{ totalTodo }}</b> 项高优先任务需要处理</span>
              <span v-else class="todo-count">暂无紧急待办，教学有序进行</span>
            </div>
            <p>先处理影响学生进度的事项，再安排项目内容更新。</p>
          </div>
        </div>
        <button class="todo-primary" type="button" :disabled="loading" @click="handlePrimaryTodo">
          <span>{{ loadFailed ? '重新加载' : totalTodo ? '立即处理' : '查看项目' }}</span><ArrowRight :size="18" />
        </button>
      </div>

      <div class="todo-grid">
        <button class="todo-item todo-danger" type="button" @click="openRiskDialog()">
          <span class="todo-item-icon"><AlertTriangle :size="22" /></span>
          <span class="todo-item-copy">
            <span class="todo-item-label">风险学生</span>
            <strong>{{ loading || !riskLoaded ? '—' : riskStudentCount }}</strong>
            <small :title="riskSummary">{{ riskSummary }}</small>
          </span>
          <ChevronRight :size="18" class="todo-chevron" />
        </button>
        <button class="todo-item todo-slate" type="button" @click="goSubmissions">
          <span class="todo-item-icon"><FileCheck2 :size="22" /></span>
          <span class="todo-item-copy">
            <span class="todo-item-label">待评审成果</span>
            <strong>{{ loading || !statsLoaded ? '—' : stats.pendingSubmissions }}</strong>
            <small>{{ !statsLoaded ? '待评审数据待更新' : stats.pendingSubmissions ? '学生已提交成果，等待你的评审' : '暂无待处理成果' }}</small>
          </span>
          <ChevronRight :size="18" class="todo-chevron" />
        </button>
        <button class="todo-item todo-blue" type="button" @click="goProjects('attention')">
          <span class="todo-item-icon"><FolderOpen :size="22" /></span>
          <span class="todo-item-copy">
            <span class="todo-item-label">待跟进项目</span>
            <strong>{{ loading || !riskLoaded ? '—' : attentionProjects.length }}</strong>
            <small :title="attentionSummary">{{ attentionSummary }}</small>
          </span>
          <ChevronRight :size="18" class="todo-chevron" />
        </button>
      </div>
      <button class="todo-all" type="button" :disabled="loading" @click="todoVisible = true">
        查看全部待办 <ChevronRight :size="17" />
      </button>
    </section>

    <div class="workbench-overview">
      <section class="followup-card" aria-labelledby="followup-title">
        <div class="overview-heading">
          <div><h2 id="followup-title">学生跟进</h2><p>先把学生遇到的困难和落后的任务跟进到位。</p></div>
          <button v-if="atRisk.length" type="button" class="text-action" @click="openRiskDialog()">查看全部 {{ atRisk.length }} 项 <ChevronRight :size="16" /></button>
        </div>
        <div v-if="loading" class="followup-empty">正在同步学生进度...</div>
        <div v-else-if="!riskLoaded" class="followup-empty"><span>学生跟进数据暂不可用</span><button type="button" class="text-action" @click="load">重新加载</button></div>
        <div v-else-if="!atRisk.length" class="followup-empty"><CircleCheckBig :size="30" /><strong>暂无需要提醒的学生</strong><span>可以安排下一阶段教学，或去评审学生成果。</span></div>
        <div v-else class="followup-list">
          <div v-for="student in atRisk.slice(0, 3)" :key="student.projectId + ':' + student.userId" class="followup-row">
            <span class="followup-avatar">{{ (student.studentName || '学')[0] }}</span>
            <div class="followup-copy">
              <strong>{{ student.studentName }}<span>进度 {{ student.progress || 0 }}%</span></strong>
              <p>{{ student.projectTitle }}</p>
              <small>{{ arr(student.reasons).join(' · ') || '需要关注学习进度' }}</small>
            </div>
            <button type="button" class="followup-remind" :aria-label="'提醒' + student.studentName + '推进' + student.projectTitle" @click="openRiskDialog(student)">写提醒 <ArrowRight :size="15" /></button>
          </div>
        </div>
      </section>

      <section class="overview-card" aria-labelledby="overview-title">
        <div class="overview-heading"><div><h2 id="overview-title">教学概览</h2><p>名下项目的整体教学情况</p></div><GraduationCap :size="24" /></div>
        <div class="overview-metrics">
          <div v-for="item in overviewMetrics" :key="item.label"><strong>{{ loading || !statsLoaded ? '—' : item.value }}</strong><span>{{ item.label }}</span></div>
        </div>
        <div class="overview-links">
          <button type="button" @click="goProjects()"><FolderOpen :size="18" />管理项目<ChevronRight :size="16" /></button>
          <button type="button" @click="router.push('/teacher/classes')"><UsersRound :size="18" />管理班级<ChevronRight :size="16" /></button>
        </div>
      </section>
    </div>

    <TeacherBriefCard :user-id="authStore.user?.id" @action="onBriefAction" />

    <el-dialog v-model="todoVisible" title="全部待办" width="min(620px, 94vw)" top="8vh">
      <div class="all-todos">
        <button class="todo-list-entry" type="button" @click="todoVisible = false; openRiskDialog()"><AlertTriangle :size="20" /><span><b>关注风险学生</b><small>{{ riskLoaded ? riskStudentCount + ' 名学生，涉及 ' + atRisk.length + ' 条项目进度提醒' : '风险数据暂不可用' }}</small></span><ChevronRight :size="18" /></button>
        <button class="todo-list-entry" type="button" @click="todoVisible = false; goSubmissions()"><FileCheck2 :size="20" /><span><b>评审学生成果</b><small>{{ statsLoaded ? stats.pendingSubmissions + ' 份成果待评审' : '评审数据暂不可用' }}</small></span><ChevronRight :size="18" /></button>
        <button class="todo-list-entry" type="button" @click="todoVisible = false; goProjects('attention')"><FolderOpen :size="20" /><span><b>跟进需关注的项目</b><small>按项目查看上述学生的学习进度</small></span><ChevronRight :size="18" /></button>
      </div>
    </el-dialog>

    <TeacherRiskDialog v-model="riskVisible" :students="atRisk" :loaded="riskLoaded" :initial-student="selectedStudent" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AlertTriangle, ArrowRight, ChevronRight, CircleCheckBig, FileCheck2, FolderOpen, GraduationCap, UsersRound } from 'lucide-vue-next'
import { teacherAtRisk, teacherProjects, teacherStats } from '../../api'
import TeacherBriefCard from '../../components/TeacherBriefCard.vue'
import TeacherRiskDialog from '../../components/TeacherRiskDialog.vue'
import { useAuthStore } from '../../stores/auth'

const emit = defineEmits(['workbench-stats'])
const router = useRouter()
const authStore = useAuthStore()
const stats = reactive({ projectCount: 0, studentTotal: 0, completedTotal: 0, pendingSubmissions: 0, resourceCount: 0 })
const projects = ref([])
const atRisk = ref([])
const riskVisible = ref(false)
const selectedStudent = ref(null)
const todoVisible = ref(false)
const loading = ref(true)
const loadFailed = ref(false)
const riskLoaded = ref(false)
const statsLoaded = ref(false)
let loadVersion = 0
const arr = value => Array.isArray(value) ? value : []
const attentionProjects = computed(() => projects.value.filter(project => atRisk.value.some(student => String(student.projectId) === String(project.id))))
const riskStudentCount = computed(() => new Set(atRisk.value.map((row) => row.userId)).size)
// 项目关注项来自同一批风险学生，不再次累计到优先任务数。
const totalTodo = computed(() => riskStudentCount.value + Number(stats.pendingSubmissions || 0))
const riskSummary = computed(() => {
  if (!riskLoaded.value) return '风险数据待更新'
  const first = atRisk.value[0]
  return first ? first.studentName + '进度 ' + (first.progress || 0) + '% · ' + (arr(first.reasons)[0] || '需要关注') : '当前没有需要提醒的学生'
})
const attentionSummary = computed(() => !riskLoaded.value ? '项目关注数据待更新' : attentionProjects.value.length ? attentionProjects.value.map((p) => p.title).slice(0, 2).join('、') : '暂无需要跟进的项目')


const overviewMetrics = computed(() => [
  { label: '负责项目', value: stats.projectCount },
  { label: '报名人次', value: stats.studentTotal },
  { label: '完成人次', value: stats.completedTotal },
  { label: '学习资源', value: stats.resourceCount }
])
const openRiskDialog = (student = null) => { selectedStudent.value = student; riskVisible.value = true }
const goSubmissions = () => router.push('/teacher/submissions')
const goProjects = (filter = 'all') => router.push({ path: '/teacher/projects', query: filter === 'all' ? {} : { filter } })
const handlePrimaryTodo = () => {
  if (loadFailed.value) return load()
  if (atRisk.value.length) return openRiskDialog()
  if (Number(stats.pendingSubmissions || 0) > 0) return goSubmissions()
  goProjects()
}
const onBriefAction = action => {
  if (action.kind === 'grade') return goSubmissions()
  if (action.kind === 'remind') return openRiskDialog()
  const project = projects.value.find(row => String(row.id) === String(action.projectId))
  if (!project) return goProjects()
  const projectAction = action.kind === 'announce' ? 'announce' : action.kind === 'adjust' ? 'edit' : 'students'
  router.push({ path: '/teacher/projects', query: { project: project.id, action: projectAction } })
}
const load = async () => {
  const version = ++loadVersion
  loading.value = true
  const results = await Promise.allSettled([teacherStats(), teacherProjects(), teacherAtRisk()])
  if (version !== loadVersion) return
  const [statsResult, projectsResult, riskResult] = results
  loadFailed.value = results.some(result => result.status === 'rejected')
  statsLoaded.value = statsResult.status === 'fulfilled'
  riskLoaded.value = riskResult.status === 'fulfilled'
  if (statsLoaded.value) {
    Object.assign(stats, statsResult.value)
    emit('workbench-stats', statsResult.value)
  }
  if (riskLoaded.value) atRisk.value = arr(riskResult.value)
  if (projectsResult.status === 'fulfilled') projects.value = arr(projectsResult.value)
  loading.value = false
}
onMounted(load)
</script>

<style scoped>
.workbench-page { max-width: 1600px; margin: 0 auto; }
.load-error { margin-bottom: 15px; }
.text-action { display: inline-flex; align-items: center; gap: 5px; padding: 0; border: 0; color: #3268df; background: transparent; font: inherit; font-size: 12px; font-weight: 700; cursor: pointer; }
.text-action:hover { color: #5437d6; }
.todo-panel {
  margin-bottom: 22px;
  padding: 25px 25px 19px;
  border: 1px solid #f1e2ea;
  border-radius: 17px;
  background: linear-gradient(112deg, #fff9fb 0%, #fff8fb 48%, #f3f6ff 100%);
  box-shadow: 0 8px 22px rgba(70, 82, 135, .05);
}
.todo-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 22px; }
.todo-heading-main { display: flex; align-items: center; gap: 15px; }
.todo-alert { width: 52px; height: 52px; display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 50%; color: #fff; background: linear-gradient(135deg, #ff7370, #ef3e4f); box-shadow: 0 8px 14px rgba(239, 62, 79, .2); }
.todo-title-row { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.todo-title-row h2 { margin: 0; color: #162655; font-size: 26px; font-weight: 800; letter-spacing: -.03em; }
.todo-count { padding: 7px 13px; border-radius: 999px; color: #7f8aa7; background: rgba(255, 255, 255, .78); font-size: 12px; }
.todo-count b { color: #ef4454; }
.todo-heading-main p { margin: 7px 0 0; color: #9a9fb5; font-size: 12px; }
.todo-primary { min-width: 160px; height: 48px; display: inline-flex; align-items: center; justify-content: center; gap: 12px; border: 0; border-radius: 12px; color: #fff; background: linear-gradient(101deg, #3d78f6, #6c3eef); box-shadow: 0 10px 19px rgba(77, 75, 225, .23); font: inherit; font-size: 14px; font-weight: 700; cursor: pointer; }
.todo-primary:hover { filter: brightness(1.04); transform: translateY(-1px); }
.todo-primary:disabled, .todo-all:disabled { cursor: wait; opacity: .58; transform: none; }
.todo-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 13px; }
.todo-item { min-width: 0; min-height: 114px; display: flex; align-items: center; gap: 13px; padding: 17px 16px; border: 1px solid rgba(229, 233, 242, .9); border-radius: 13px; text-align: left; background: rgba(255, 255, 255, .9); cursor: pointer; transition: transform .18s, box-shadow .18s, border-color .18s; }
.todo-item:hover { transform: translateY(-2px); border-color: #cdd7ef; box-shadow: 0 8px 16px rgba(62, 76, 132, .1); }
.todo-item-icon { width: 48px; height: 48px; display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 14px; }
.todo-danger .todo-item-icon { color: #f15358; background: #fff0f1; }
.todo-slate .todo-item-icon { color: #60708e; background: #f1f3f7; }
.todo-blue .todo-item-icon { color: #3972f3; background: #edf3ff; }
.todo-item-copy { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.todo-item-label { color: #687796; font-size: 13px; font-weight: 600; }
.todo-item-copy strong { color: #172554; font-size: 24px; line-height: 1.1; }
.todo-item-copy small { overflow: hidden; color: #9aa6bc; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.todo-chevron { flex-shrink: 0; margin-left: auto; color: #aab5ca; }
.todo-all { margin: 15px 1px 0 auto; display: flex; align-items: center; gap: 3px; border: 0; color: #356cf0; background: transparent; font: inherit; font-size: 12px; font-weight: 600; cursor: pointer; }
.todo-all:hover { color: #5536db; }
.todo-clear .todo-alert { color: #fff; background: linear-gradient(135deg, #3bcf91, #1dab72); box-shadow: 0 8px 14px rgba(35, 183, 119, .19); }


.all-todos { display: flex; flex-direction: column; gap: 9px; }
.todo-list-entry { width: 100%; min-height: 64px; display: flex; align-items: center; gap: 12px; padding: 11px 13px; border: 1px solid #e7ebf4; border-radius: 10px; color: #526381; background: #fbfcff; text-align: left; cursor: pointer; }
.todo-list-entry:hover { border-color: #a8baf2; color: #3568db; background: #f6f8ff; }
.todo-list-entry > span { display: flex; flex-direction: column; gap: 3px; flex: 1; min-width: 0; }
.todo-list-entry b { color: #263961; font-size: 13px; }
.todo-list-entry small { overflow: hidden; color: #8793ac; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.todo-list-entry > svg:last-child { color: #9aa6bd; }

.workbench-overview { display: grid; grid-template-columns: minmax(0, 1.6fr) minmax(320px, 1fr); gap: 22px; margin-bottom: 22px; }
.followup-card, .overview-card { min-width: 0; padding: 25px; border: 1px solid #e7ecf5; border-radius: 17px; background: #fff; }
.overview-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 22px; }
.overview-heading h2 { margin: 0; color: #1e3058; font-size: 21px; }
.overview-heading p { margin: 8px 0 0; color: #8490a6; font-size: 13px; line-height: 1.7; }
.overview-heading > svg { color: #8297d5; }
.overview-heading .text-action { flex-shrink: 0; padding-top: 6px; font-size: 13px; }
.followup-row { display: flex; align-items: center; gap: 14px; padding: 18px 0; border-top: 1px solid #edf0f7; }
.followup-avatar { display: grid; place-items: center; flex-shrink: 0; width: 44px; height: 44px; border-radius: 12px; color: #5d65cb; background: #eef0ff; font-size: 18px; font-weight: 600; }
.followup-copy { flex: 1; min-width: 0; }
.followup-copy strong { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; color: #273a60; font-size: 16px; }
.followup-copy strong span { color: #a58084; font-size: 13px; font-weight: 400; white-space: nowrap; }
.followup-copy p { margin: 7px 0 5px; color: #77859c; font-size: 14px; line-height: 1.6; overflow-wrap: anywhere; }
.followup-copy small { color: #d96269; font-size: 13px; line-height: 1.6; }
.followup-remind { display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; gap: 8px; min-height: 40px; padding: 0 13px; border: 1px solid #dbe3f9; border-radius: 9px; color: #466bd4; background: #f6f8ff; font: inherit; font-size: 14px; cursor: pointer; }
.followup-empty { display: flex; min-height: 184px; align-items: center; justify-content: center; flex-direction: column; gap: 12px; color: #8693a9; font-size: 14px; text-align: center; line-height: 1.7; }
.followup-empty svg { color: #30b780; }
.followup-empty strong { color: #435575; font-size: 16px; }
.overview-metrics { display: grid; grid-template-columns: repeat(2, 1fr); gap: 24px; margin: 25px 0; }
.overview-metrics strong { display: block; color: #283c66; font-size: 27px; }
.overview-metrics span { display: block; margin-top: 7px; color: #8a97ac; font-size: 13px; }
.overview-links { display: flex; gap: 12px; padding-top: 18px; border-top: 1px solid #edf0f7; }
.overview-links button { display: inline-flex; align-items: center; flex: 1; gap: 8px; padding: 10px 0; border: 0; color: #6178b4; background: transparent; font: inherit; font-size: 14px; white-space: nowrap; cursor: pointer; }
.overview-links button > svg:last-child { margin-left: auto; }
@media (max-width: 1100px) {
  .todo-grid { grid-template-columns: 1fr; }
  .todo-item { min-height: 90px; }
  .workbench-overview { grid-template-columns: 1fr; }
}
@media (max-width: 600px) {
  .todo-panel, .followup-card, .overview-card { padding: 18px; }
  .todo-heading { align-items: flex-start; flex-direction: column; }
  .todo-primary { width: 100%; }
  .todo-title-row h2 { font-size: 22px; }
  .overview-heading { flex-wrap: wrap; }
  .followup-row { flex-wrap: wrap; gap: 12px; }
  .followup-remind { margin-left: 56px; }
}
</style>
