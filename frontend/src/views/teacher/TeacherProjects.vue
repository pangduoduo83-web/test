<template>
  <div class="projects-page" :aria-busy="loading">
    <el-alert v-if="loadFailed" title="部分项目数据未能加载，请重试。" type="error" :closable="false" class="load-error">
      <button class="text-action" type="button" @click="load">重新加载</button>
    </el-alert>
    <div class="project-summary-grid" aria-label="项目概览">
      <button v-for="item in projectSummary" :key="item.key" type="button" class="project-summary-card" :class="{ selected: projectTab === item.key }" @click="projectTab = item.key">
        <span class="summary-icon" :class="'summary-' + item.key"><component :is="item.icon" :size="23" /></span>
        <span><small>{{ item.label }}</small><strong>{{ loading ? '—' : item.count }}</strong></span>
        <ChevronRight :size="18" />
      </button>
    </div>

    <section id="teacher-project-section" class="projects-card" aria-labelledby="projects-title" v-loading="loading">
      <div class="section-heading">
        <div class="section-title-wrap">
          <span class="section-icon"><FolderOpen :size="22" /></span>
          <h2 id="projects-title">项目列表</h2>
          <span class="section-count">共 {{ projects.length }} 个项目 · {{ progressProjectCount }} 个进行中</span>
        </div>
        <div class="project-tabs" role="tablist" aria-label="项目筛选">
          <button type="button" role="tab" :aria-selected="projectTab === 'all'" :class="{ active: projectTab === 'all' }" @click="projectTab = 'all'">
            全部 <i>{{ projects.length }}</i>
          </button>
          <button type="button" role="tab" :aria-selected="projectTab === 'progress'" :class="{ active: projectTab === 'progress' }" @click="projectTab = 'progress'">
            进行中 <i>{{ progressProjectCount }}</i>
          </button>
          <button type="button" role="tab" :aria-selected="projectTab === 'draft'" :class="{ active: projectTab === 'draft' }" @click="projectTab = 'draft'">
            草稿 <i>{{ draftProjectCount }}</i>
          </button>
          <button type="button" role="tab" :aria-selected="projectTab === 'attention'" :class="{ active: projectTab === 'attention' }" @click="projectTab = 'attention'">
            需关注 <i>{{ riskLoaded ? attentionProjects.length : '—' }}</i>
          </button>
        </div>
      </div>

      <div class="project-controls">
        <el-select v-model="filterCategory" clearable placeholder="全部项目分类" aria-label="项目分类" class="category-filter">
          <el-option v-for="category in categories" :key="category" :label="category" :value="category" />
        </el-select>
        <button class="draft-project-btn" type="button" @click="draftVisible = true"><Sparkles :size="17" /> AI 起草项目</button>
      </div>
      <div v-if="search" class="search-summary">“{{ searchTerm }}” 的搜索结果：{{ filteredProjects.length }} 个项目<span v-if="progressLoading"> · 正在搜索学生...</span></div>
      <div v-if="filteredProjects.length" class="project-table" role="table" aria-label="项目列表">
        <div class="project-table-head">
          <span class="project-name-col">项目名称</span>
          <span class="project-students-col">学生人数</span>
          <span class="project-progress-col" title="报名学生的平均学习进度">平均进度</span>
          <span class="project-status-col">状态</span>
          <span class="project-date-col">最近更新</span>
          <span class="project-action-col">操作</span>
        </div>
        <div v-for="row in filteredProjects" :key="row.id" class="project-row">
          <div class="project-name-col project-name-cell">
            <img v-if="row.coverUrl" :src="row.coverUrl" class="project-cover" alt="" />
            <span v-else class="project-cover project-cover-fallback">{{ row.icon || '📦' }}</span>
            <div class="project-name-copy">
              <strong :title="row.title">{{ row.title }}</strong>
              <small>{{ row.category || '项目实践' }} · {{ row.difficulty || '进阶' }} · {{ row.duration || '自主安排' }}</small>
            </div>
          </div>
          <div class="project-students-col project-students-cell">{{ row.enrolledCount || 0 }} <em>人</em></div>
          <div class="project-progress-col project-progress-cell">
            <div class="progress-value">{{ projectProgress(row) === null ? '—' : projectProgress(row) + '%' }}</div>
            <div class="progress-track" :aria-label="row.title + '平均进度'" role="progressbar" :aria-valuenow="projectProgress(row)" aria-valuemin="0" aria-valuemax="100"><span :style="{ width: (projectProgress(row) || 0) + '%' }" /></div>
          </div>
          <div class="project-status-col">
            <span class="project-status" :class="'status-' + projectState(row)">
              <i />{{ projectStatusText(row) }}
            </span>
          </div>
          <div class="project-date-col project-date-cell">{{ formatDate(row.updatedAt) }}</div>
          <div class="project-action-col project-action-cell">
            <button class="edit-project-btn" type="button" :aria-label="'编辑' + row.title" @click="openEdit(row)">编辑</button>
            <button class="view-project-btn" type="button" :aria-label="'查看' + row.title + '学生进度'" @click="openStudents(row)">学生进度</button>
            <el-dropdown trigger="click" @command="(command) => handleProjectCommand(command, row)">
              <button class="more-project-btn" type="button" :disabled="publishing === row.id" :aria-label="row.title + '更多操作'"><MoreHorizontal :size="18" /></button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit">编辑项目</el-dropdown-item>
                  <el-dropdown-item command="resources">资料附件</el-dropdown-item>
                  <el-dropdown-item command="students">学生进度</el-dropdown-item>
                  <el-dropdown-item command="announce">发公告</el-dropdown-item>
                  <el-dropdown-item command="preview">学生端预览</el-dropdown-item>
                  <el-dropdown-item command="publish" :disabled="publishing === row.id">{{ publishing === row.id ? '提交中...' : row.hubItemId ? '更新到商店' : '发布到商店' }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
      <el-empty v-else-if="!loading" :description="loadFailed && !projects.length ? '项目数据未能加载，请重试' : projects.length ? '没有符合当前筛选条件的项目' : '暂无名下项目，可以新建项目或联系管理员指派'">
        <el-button v-if="!projects.length && !loadFailed" type="primary" @click="openEdit(null)">新建项目</el-button>
      </el-empty>
      <div class="project-footer">
        <span>进度为学生平均学习进度<span v-if="progressLoading">，正在更新...</span><span v-else-if="progressFailed">，部分数据暂不可用</span></span>
      </div>
    </section>

    <ProjectEditDialog v-model="editVisible" :project="editing" mode="teacher" compact :skill-dimensions="skillDimensions"
                       :save-fn="saveProject" :reference-answer-fn="teacherProjectReferenceAnswer"
                       :save-reference-answer-fn="teacherUpdateReferenceAnswer" @saved="load" />
    <AiDraftDialog v-model="draftVisible" :skill-dimensions="skillDimensions" @drafted="onDrafted" />

    <el-dialog v-model="resVisible" :title="'资料附件 - ' + (current?.title || '')" width="min(720px, 94vw)" top="5vh">
      <p class="res-tip">为每条资源上传附件后，学生端「学习资源」会出现下载链接；原理图、LAYOUT、3D 图会同时显示在 BOM 页签作为设计文件。</p>
      <div v-for="(r, i) in resRows" :key="i" class="res-edit-row">
        <el-select v-model="r.type" class="res-type-sel">
          <el-option v-for="t in ['文档', '视频', '代码', '手册', '工具', '课件', '原理图', 'LAYOUT', '3D图']" :key="t" :label="t" :value="t" />
        </el-select>
        <el-input v-model="r.name" placeholder="资源名称，如：项目开发指南.pdf" class="res-name-input" />
        <el-upload :show-file-list="false" :http-request="(opt) => doUploadRes(opt, r)" accept="*">
          <el-button size="small" :type="r.url ? 'success' : 'primary'" plain :loading="r.uploading">
            <template v-if="r.url"><Check :size="13" style="margin-right:4px" /> 已上传</template>
            <template v-else><Upload :size="13" style="margin-right:4px" /> 上传附件</template>
          </el-button>
        </el-upload>
        <el-button size="small" text type="danger" @click="resRows.splice(i, 1)">删除</el-button>
      </div>
      <el-button class="add-res-btn" plain @click="resRows.push({ type: '文档', name: '', url: '' })">+ 添加资源</el-button>
      <template #footer>
        <el-button @click="resVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveResources">保存资源列表</el-button>
      </template>
    </el-dialog>

    <TeacherComposeDialog v-model="announceVisible" title="发布项目公告" description="向报名学生同步安排、截止时间或项目进展。"
                          :busy="saving" confirm-text="发布公告" @submit="sendAnnounce">
      <div class="compose-context">
        <span class="recipient-avatar"><FolderOpen :size="23" /></span>
        <div class="compose-context-copy"><strong>{{ current?.title }}</strong><p>发送给该项目全部报名学生 · {{ current?.enrolledCount || 0 }} 人</p></div>
      </div>
      <el-form label-position="top" @submit.prevent="sendAnnounce">
        <el-form-item label="公告标题" for="teacher-announce-title" required :error="announceError">
          <el-input id="teacher-announce-title" v-model="announceForm.title" :disabled="saving" maxlength="100" show-word-limit placeholder="如：本周五前完成原理图阶段" @input="announceError = ''" />
        </el-form-item>
        <el-form-item label="公告正文（选填）" for="teacher-announce-content">
          <el-input id="teacher-announce-content" v-model="announceForm.content" :disabled="saving" type="textarea" :rows="8" maxlength="1000" show-word-limit placeholder="可以补充具体任务、时间安排或注意事项…" />
        </el-form-item>
      </el-form>
      <template #footer-note>每位报名学生会收到一条站内通知</template>
    </TeacherComposeDialog>

    <TeacherComposeDialog v-model="publishVisible" :title="publishProject?.hubItemId ? '更新到商店' : '发布到商店'"
                          description="项目将提交商店审核，审核通过后其他院校可以安装使用。" :busy="publishing !== null"
                          confirm-text="提交审核" @submit="submitToStore">
      <div class="compose-context">
        <span class="recipient-avatar"><FolderOpen :size="23" /></span>
        <div class="compose-context-copy"><strong>{{ publishProject?.title }}</strong><p>{{ publishProject?.hubItemId ? '提交新的项目版本' : '首次发布到项目商店' }}</p></div>
      </div>
      <el-form label-position="top" @submit.prevent="submitToStore">
        <el-form-item label="版本说明（选填）" for="teacher-publish-changelog" :error="publishError">
          <el-input id="teacher-publish-changelog" v-model="publishChangelog" type="textarea" :rows="8" :disabled="publishing !== null"
                    placeholder="例如：首次发布，包含完整的实践任务与学习资料。也可以逐条说明本次更新的内容…" @input="publishError = ''" />
        </el-form-item>
      </el-form>
      <template #footer-note>提交后等待平台审核</template>
    </TeacherComposeDialog>

    <el-dialog v-model="stuVisible" :title="'学生进度 - ' + (current?.title || '')" width="min(1120px, 96vw)" top="3vh" class="student-progress-dialog">
      <div class="student-dialog-content" v-loading="studentsLoading">
      <el-alert v-if="studentsFailed" title="学生进度未能加载，请关闭后重试。" type="error" :closable="false" />
      <el-empty v-else-if="!studentsLoading && students.length === 0" description="还没有学生报名该项目" />
      <el-table v-else :data="students" stripe row-key="enrollmentId" max-height="min(62vh, 620px)">
        <el-table-column type="expand" width="46">
          <template #default="{ row }">
            <div class="student-progress-detail">
              <div><span>当前任务</span><strong>{{ row.currentTask || '尚未记录' }}</strong></div>
              <div><span>报名时间</span><strong>{{ formatDate(row.enrolledAt) }}</strong></div>
              <div><span>截止时间</span><strong>{{ row.deadline || '未设置' }}</strong></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="studentName" label="姓名" width="100" />
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="major" label="专业" width="130" />
        <el-table-column label="进度" min-width="190">
          <template #default="{ row }"><el-progress :percentage="studentProgress(row)" :stroke-width="8" /></template>
        </el-table-column>
        <el-table-column prop="currentTask" label="当前任务" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="badge" :class="row.status === 'COMPLETED' ? 'badge-green' : 'badge-blue'">{{ row.status === 'COMPLETED' ? '已完成' : '进行中' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="deadline" label="截止" width="110" />
      </el-table>
      </div>
      <p class="dialog-tip dialog-foot-tip">进度由学生自己记录；学生提交成果并由你评审 ≥60 分后才会变为「已完成」。</p>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { AlertTriangle, Check, ChevronRight, CircleCheckBig, FilePenLine, FolderOpen, MoreHorizontal, Sparkles, Upload } from 'lucide-vue-next'
import {
  storePublish, teacherAnnounceProject, teacherAtRisk, teacherCreateProject, teacherProjectReferenceAnswer, teacherProjectStudents,
  teacherProjects, teacherSkillDimensions, teacherUpdateProject, teacherUpdateReferenceAnswer, teacherUpdateResources, uploadDocFile
} from '../../api'
import ProjectEditDialog from '../../components/ProjectEditDialog.vue'
import AiDraftDialog from '../../components/AiDraftDialog.vue'
import TeacherComposeDialog from '../../components/TeacherComposeDialog.vue'

const props = defineProps({ searchTerm: { type: String, default: '' } })
const router = useRouter()
const route = useRoute()
const projects = ref([])
const atRisk = ref([])
const loading = ref(true)
const loadFailed = ref(false)
const riskLoaded = ref(false)
const progressLoading = ref(false)
const progressFailed = ref(false)
const projectStudents = ref({})
const filterCategory = ref('')
const draftVisible = ref(false)
const announceVisible = ref(false)
const announceForm = reactive({ title: '', content: '' })
const announceError = ref('')
const current = ref(null)
const saving = ref(false)
const publishing = ref(null)
const publishVisible = ref(false)
const publishProject = ref(null)
const publishChangelog = ref('')
const publishError = ref('')
const editVisible = ref(false)
const editing = ref(null)
const skillDimensions = ref([])
const resVisible = ref(false)
const resRows = ref([])
const stuVisible = ref(false)
const students = ref([])
const studentsLoading = ref(false)
const studentsFailed = ref(false)
let loadVersion = 0
let studentRequestVersion = 0

const arr = value => Array.isArray(value) ? value : []
const normalize = value => String(value || '').trim().toLowerCase()
const projectTab = computed({
  get: () => ['progress', 'attention', 'draft'].includes(route.query.filter) ? route.query.filter : 'all',
  set: value => {
    const query = { ...route.query }
    if (value === 'all') delete query.filter
    else query.filter = value
    router.replace({ path: '/teacher/projects', query })
  }
})
const projectHasRisk = row => atRisk.value.some(r => String(r.projectId) === String(row.id))
const attentionProjects = computed(() => projects.value.filter(projectHasRisk))
const progressProjectCount = computed(() => projects.value.filter(row => row.status === 'PUBLISHED').length)
const draftProjectCount = computed(() => projects.value.filter(row => row.status === 'DRAFT').length)
const categories = computed(() => [...new Set(projects.value.map(row => row.category).filter(Boolean))])
const search = computed(() => normalize(props.searchTerm))
const filteredProjects = computed(() => projects.value
  .filter(row => projectTab.value === 'all'
    || (projectTab.value === 'attention' && projectHasRisk(row))
    || (projectTab.value === 'progress' && row.status === 'PUBLISHED')
    || (projectTab.value === 'draft' && row.status === 'DRAFT'))
  .filter(row => !filterCategory.value || row.category === filterCategory.value)
  .filter(row => !search.value || [row.title, row.category, row.difficulty, row.duration].some(value => normalize(value).includes(search.value))
    || (projectStudents.value[row.id] || []).some(student => [student.studentName, student.studentNo].some(value => normalize(value).includes(search.value)))))
const projectSummary = computed(() => [
  { key: 'all', label: '全部项目', count: projects.value.length, icon: FolderOpen },
  { key: 'progress', label: '进行中', count: progressProjectCount.value, icon: CircleCheckBig },
  { key: 'draft', label: '草稿', count: draftProjectCount.value, icon: FilePenLine },
  { key: 'attention', label: '需关注', count: riskLoaded.value ? attentionProjects.value.length : '—', icon: AlertTriangle }
])
const studentProgress = (row) => row.status === 'COMPLETED' ? 100 : Math.max(0, Math.min(100, Number(row.progress) || 0))
const projectProgress = (row) => {
  const list = projectStudents.value[row.id]
  if (!list) return null
  return list.length ? Math.round(list.reduce((sum, student) => sum + studentProgress(student), 0) / list.length) : 0
}
const projectState = (row) => projectHasRisk(row) ? 'attention' : row.status === 'DRAFT' ? 'draft' : riskLoaded.value ? 'progress' : 'unknown'
const projectStatusText = (row) => ({ attention: '需关注', draft: '草稿', progress: '进行中', unknown: '待同步' }[projectState(row)])
const formatDate = (value) => {
  if (!value) return '暂无记录'
  const date = String(value).replace('T', ' ')
  return date.slice(0, 10)
}


const onDrafted = draft => { editing.value = draft; editVisible.value = true }
const openPreview = row => router.push('/app/projects/' + row.id)
const handleProjectCommand = (command, row) => {
  if (command === 'edit') return openEdit(row)
  if (command === 'resources') return openResources(row)
  if (command === 'students') return openStudents(row)
  if (command === 'announce') return openAnnounce(row)
  if (command === 'publish') return publishToStore(row)
  openPreview(row)
}

const openAnnounce = (row) => {
  current.value = row
  Object.assign(announceForm, { title: '', content: '' })
  announceError.value = ''
  announceVisible.value = true
}
const sendAnnounce = async () => {
  if (saving.value) return
  if (!announceForm.title.trim()) { announceError.value = '请填写公告标题'; return }
  announceError.value = ''
  saving.value = true
  try {
    const result = await teacherAnnounceProject(current.value.id, { title: announceForm.title.trim(), content: announceForm.content.trim() })
    ElMessage.success('公告已发送给 ' + result.notified + ' 名学生')
    announceVisible.value = false
  } catch (e) {
    announceError.value = '发布未成功，内容已保留，请稍后重试。'
  } finally { saving.value = false }
}

const load = async () => {
  const version = ++loadVersion
  loading.value = true
  const results = await Promise.allSettled([teacherProjects(), teacherAtRisk()])
  if (version !== loadVersion) return
  const [projectsResult, riskResult] = results
  loadFailed.value = results.some(result => result.status === 'rejected')
  riskLoaded.value = riskResult.status === 'fulfilled'
  if (riskLoaded.value) atRisk.value = arr(riskResult.value)
  if (projectsResult.status === 'fulfilled') projects.value = arr(projectsResult.value)
  loading.value = false
  if (projectsResult.status === 'fulfilled') loadProjectProgress(version)
}
// 复用已有学生进度接口，并限制同时请求的数量；完成率不是平均学习进度。
const loadProjectProgress = async (version) => {
  progressLoading.value = true
  progressFailed.value = false
  const queue = [...projects.value]
  const snapshot = {}
  const worker = async () => {
    while (queue.length && version === loadVersion) {
      const row = queue.shift()
      try {
        snapshot[row.id] = arr(await teacherProjectStudents(row.id))
        if (version === loadVersion) projectStudents.value = { ...projectStudents.value, [row.id]: snapshot[row.id] }
      } catch (e) {
        if (version === loadVersion) progressFailed.value = true
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(4, queue.length) }, worker))
  if (version === loadVersion) {
    projectStudents.value = snapshot
    progressLoading.value = false
  }
  if (!queue.length && version === loadVersion) progressLoading.value = false
}
const openEdit = (row) => {
  editing.value = row
  editVisible.value = true
}
const saveProject = (id, payload) => id ? teacherUpdateProject(id, payload) : teacherCreateProject(payload)

const publishToStore = (row) => {
  publishProject.value = row
  publishChangelog.value = ''
  publishError.value = ''
  publishVisible.value = true
}
const submitToStore = async () => {
  if (publishing.value !== null || !publishProject.value) return
  const row = publishProject.value
  publishing.value = row.id
  publishError.value = ''
  try {
    const item = await storePublish(row.id, { changelog: publishChangelog.value.trim() })
    ElMessage.success(item.newItem ? '已提交到项目商店（条目 #' + item.id + '），等待平台审核' : '已追加新版本 v' + item.latestVersionNo + '，等待平台审核')
    publishVisible.value = false
    await load()
  } catch (e) {
    publishError.value = '提交未成功，版本说明已保留，请稍后重试。'
  } finally { publishing.value = null }
}

const openResources = (row) => {
  current.value = row
  resRows.value = arr(row.resources).map((r) => ({ type: r.type || '文档', name: r.name || '', url: r.url || '', uploading: false }))
  resVisible.value = true
}
const doUploadRes = async (opt, row) => {
  if (opt.file.size > 500 * 1024 * 1024) { ElMessage.warning('附件不能超过 500MB'); return }
  row.uploading = true
  try {
    const result = await uploadDocFile(opt.file)
    row.url = result.url
    if (!row.name) row.name = result.name
    ElMessage.success('附件上传成功：' + result.name)
  } catch (e) { /* 已提示 */ } finally { row.uploading = false }
}
const saveResources = async () => {
  const cleaned = resRows.value.filter((r) => r.name.trim()).map((r) => ({ type: r.type, name: r.name.trim(), url: r.url || '' }))
  saving.value = true
  try {
    await teacherUpdateResources(current.value.id, JSON.stringify(cleaned))
    ElMessage.success('资源列表已保存，学生端即时生效')
    resVisible.value = false
    await load()
  } catch (e) { /* 已提示 */ } finally { saving.value = false }
}
const openStudents = async (row) => {
  const version = ++studentRequestVersion
  current.value = row
  students.value = projectStudents.value[row.id] || []
  stuVisible.value = true
  studentsLoading.value = true
  studentsFailed.value = false
  try {
    const list = arr(await teacherProjectStudents(row.id))
    projectStudents.value = { ...projectStudents.value, [row.id]: list }
    if (version === studentRequestVersion) students.value = list
  } catch (e) {
    if (version === studentRequestVersion) studentsFailed.value = true
  } finally {
    if (version === studentRequestVersion) studentsLoading.value = false
  }
}


watch([() => route.query, loading], async ([query, isLoading]) => {
  if (isLoading) return
  if (query.new !== '1' && !query.action) return
  if (query.new === '1') openEdit(null)
  else {
    const project = projects.value.find(row => String(row.id) === String(query.project))
    if (project && ['edit', 'students', 'announce', 'resources'].includes(query.action)) handleProjectCommand(query.action, project)
    else ElMessage.warning('未找到对应项目，请在列表中选择。')
  }
  const nextQuery = { ...query }
  delete nextQuery.new
  delete nextQuery.action
  delete nextQuery.project
  await nextTick()
  router.replace({ path: '/teacher/projects', query: nextQuery })
}, { immediate: true })

onMounted(() => {
  load()
  teacherSkillDimensions().then(list => { skillDimensions.value = list }).catch(() => {})
})
</script>

<style scoped>
.projects-page { max-width: 1600px; margin: 0 auto; }
.load-error { margin-bottom: 20px; }
.text-action { border: 0; padding: 0; background: transparent; color: #4269dc; font: inherit; cursor: pointer; }
.project-summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 18px; margin-bottom: 24px; }
.project-summary-card { display: flex; align-items: center; gap: 16px; padding: 22px; border: 1px solid #e6ebf5; border-radius: 15px; background: #fff; font: inherit; text-align: left; cursor: pointer; }
.project-summary-card.selected { border-color: #8fa5ff; background: #f8faff; }
.project-summary-card > span:nth-child(2) { flex: 1; }
.project-summary-card small { display: block; margin-bottom: 6px; color: #75839d; font-size: 14px; }
.project-summary-card strong { color: #213358; font-size: 27px; }
.project-summary-card > svg { flex-shrink: 0; color: #a4aec4; }
.summary-icon { display: grid; flex-shrink: 0; place-items: center; width: 48px; height: 48px; border-radius: 13px; color: #4772e9; background: #edf3ff; }
.summary-progress { color: #16a06e; background: #eaf9f1; }
.summary-draft { color: #8755de; background: #f3edff; }
.summary-attention { color: #e85761; background: #fff0f1; }
.project-controls { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 22px; }
.category-filter { width: 220px; }
.draft-project-btn { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-height: 40px; padding: 0 15px; border: 1px solid #dfe4f6; border-radius: 9px; color: #6156cc; background: #f8f7ff; font: inherit; font-size: 14px; white-space: nowrap; cursor: pointer; }
.projects-card { margin-bottom: 22px; padding: 24px 25px 12px; border: 1px solid #edf0f6; border-radius: 17px; background: #fff; box-shadow: 0 8px 22px rgba(70, 82, 135, .045); }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 20px; }
.section-title-wrap { display: flex; align-items: center; gap: 12px; }
.section-icon { width: 32px; height: 32px; display: inline-flex; align-items: center; justify-content: center; border-radius: 9px; color: #3d74ef; background: #edf3ff; }
.section-title-wrap h2 { margin: 0; color: #172554; font-size: 21px; font-weight: 800; }
.section-count { margin-left: 4px; color: #8793ad; font-size: 12px; }
.project-tabs { display: flex; align-items: center; gap: 26px; height: 37px; }
.project-tabs button { position: relative; height: 37px; padding: 0; border: 0; color: #75829c; background: transparent; font: inherit; font-size: 14px; cursor: pointer; }
.project-tabs button.active { color: #2768ec; font-weight: 700; }
.project-tabs button.active::after { position: absolute; right: 0; bottom: -1px; left: 0; height: 2px; border-radius: 2px; background: #3978f5; content: ''; }
.project-tabs i { display: inline-grid; min-width: 20px; height: 20px; margin-left: 4px; padding: 0 4px; place-items: center; border-radius: 10px; color: #8492ab; background: #f0f3f9; font-size: 11px; font-style: normal; }
.project-tabs button.active i { color: #2466e8; background: #eaf1ff; }
.search-summary { margin: -7px 0 12px; color: #7080a0; font-size: 12px; }
.project-table { overflow-x: auto; }
.project-table-head, .project-row { min-width: 1040px; display: grid; grid-template-columns: minmax(270px, 2.3fr) minmax(80px, .7fr) minmax(130px, 1.15fr) minmax(100px, .85fr) minmax(115px, .85fr) minmax(210px, 1.3fr); align-items: center; column-gap: 18px; }
.project-table-head { min-height: 42px; padding: 0 14px; border-radius: 9px; color: #8290aa; background: #f7f9fc; font-size: 12px; }
.project-row { min-height: 75px; padding: 11px 14px; border-bottom: 1px solid #eef1f6; color: #26365d; font-size: 13px; }
.project-row:last-child { border-bottom: 0; }
.project-name-cell { display: flex; align-items: center; gap: 13px; min-width: 0; }
.project-cover { width: 57px; height: 43px; flex-shrink: 0; display: inline-flex; align-items: center; justify-content: center; overflow: hidden; border-radius: 9px; object-fit: cover; background: linear-gradient(135deg, #ebf1ff, #f5edff); }
.project-cover-fallback { font-size: 21px; }
.project-name-copy { min-width: 0; }
.project-name-copy strong { display: block; overflow: hidden; color: #1b2d57; font-size: 16px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.project-name-copy small { display: block; overflow: hidden; margin-top: 5px; color: #8492ae; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.project-students-cell { color: #34466f; font-size: 14px; }
.project-students-cell em { color: #8d99af; font-size: 11px; font-style: normal; }
.project-progress-cell { min-width: 0; }
.progress-value { margin-bottom: 6px; color: #2b3c63; font-size: 12px; font-weight: 700; }
.progress-track { height: 8px; overflow: hidden; border-radius: 5px; background: #e9eef6; }
.progress-track span { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #377cf5, #5182f2); }
.project-status { display: inline-flex; align-items: center; gap: 7px; padding: 7px 11px; border-radius: 999px; font-size: 12px; white-space: nowrap; }
.project-status i { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
.status-progress { color: #18ad70; background: #eafaf2; }
.status-attention { color: #ec4d53; background: #fff0f1; }
.status-draft { color: #8a6aee; background: #f2eeff; }
.status-unknown { color: #7b89a5; background: #f2f5fa; }
.project-date-cell { color: #6b7b9b; font-size: 12px; }
.project-action-cell { display: flex; align-items: center; gap: 7px; }
.view-project-btn, .more-project-btn { height: 36px; display: inline-flex; align-items: center; justify-content: center; border: 1px solid #dce4f2; border-radius: 8px; color: #536481; background: #fff; font: inherit; font-size: 12px; cursor: pointer; }
.view-project-btn { gap: 4px; padding: 0 11px; }
.more-project-btn { width: 34px; padding: 0; color: #7585a4; }
.view-project-btn:hover, .more-project-btn:hover { border-color: #9cb8f5; color: #2d6de8; background: #f7faff; }
.project-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 44px; color: #99a4b8; font-size: 11px; }
.project-footer .text-action { color: #4269dc; }

.student-dialog-content { min-height: 80px; }
.dialog-tip { margin: 0 0 15px; color: #76839b; font-size: 13px; line-height: 1.7; }
.student-progress-detail { display: flex; flex-wrap: wrap; gap: 14px 28px; padding: 2px 14px 8px 54px; color: #76839b; font-size: 13px; }
.student-progress-detail div { display: flex; gap: 8px; min-width: 220px; }
.student-progress-detail strong { color: #334155; font-weight: 500; }
:deep(.student-progress-dialog .el-dialog__body) { padding: 12px 20px 18px; }
:deep(.student-progress-dialog .el-dialog__header) { padding-bottom: 12px; }
.compose-context { display: flex; align-items: flex-start; gap: 14px; margin-bottom: 24px; padding: 18px; border: 1px solid #e6ebf7; border-radius: 12px; background: #f7f9ff; }
.recipient-avatar { display: inline-grid; flex-shrink: 0; place-items: center; width: 44px; height: 44px; border-radius: 12px; color: #5265d8; background: #e9edff; font-size: 20px; font-weight: 600; }
.compose-context-copy { min-width: 0; flex: 1; overflow-wrap: anywhere; }
.compose-context-copy strong { display: flex; align-items: baseline; flex-wrap: wrap; gap: 6px 12px; color: #273653; font-size: 16px; line-height: 1.6; }
.compose-context-copy small { color: #74829b; font-size: 14px; font-weight: 400; }
.compose-context-copy p { margin: 4px 0 0; color: #697792; font-size: 14px; line-height: 1.7; }
.recipient-progress { flex-shrink: 0; padding-top: 4px; color: #697792; font-size: 14px; }
.recipient-progress b { margin-left: 5px; color: #d7464d; }

.res-tip { margin: 0 0 14px; padding: 10px 14px; border-radius: 10px; color: #255dc4; background: #eff6ff; font-size: 13px; }
.res-edit-row { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.res-type-sel { width: 100px; flex-shrink: 0; }
.res-name-input { flex: 1; }
.add-res-btn { width: 100%; border-style: dashed; }
.dialog-foot-tip { margin: 10px 0 0; }

.edit-project-btn { height: 36px; padding: 0 13px; border: 1px solid #d6e2fa; border-radius: 8px; color: #3669d5; background: #f4f8ff; font: inherit; cursor: pointer; }
.project-action-cell > button { flex-shrink: 0; }
@media (max-width: 1100px) {
  .project-summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .section-heading { align-items: flex-start; flex-direction: column; }
}
@media (max-width: 600px) {
  .project-summary-grid { gap: 10px; }
  .project-summary-card { gap: 10px; padding: 15px 12px; }
  .summary-icon { width: 38px; height: 38px; }
  .project-summary-card > svg { display: none; }
  .projects-card { padding: 20px 14px 12px; }
  .section-title-wrap { flex-wrap: wrap; }
  .section-count { width: 100%; margin-left: 44px; }
  .project-tabs { width: 100%; justify-content: space-between; gap: 12px; }
  .project-controls { flex-wrap: wrap; gap: 12px; }
  .category-filter { flex: 1; min-width: 140px; }
  .compose-context { padding: 14px; }
}
</style>
