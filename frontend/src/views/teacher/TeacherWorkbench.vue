<template>
  <div>
    <!-- 统计卡 -->
    <div class="stat-grid">
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#60a5fa,#2563eb)"><BookOpen :size="22" color="#fff" /></div>
        <div><div class="ref-stat-value">{{ stats.projectCount }}</div><div class="ref-stat-label">我的项目</div></div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#4ade80,#16a34a)"><Users :size="22" color="#fff" /></div>
        <div><div class="ref-stat-value">{{ stats.studentTotal }}</div><div class="ref-stat-label">报名学生 · 已完成 {{ stats.completedTotal }}</div></div>
      </div>
      <div class="ref-stat-card clickable" @click="$router.push('/teacher/submissions')">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#facc15,#f59e0b)"><ClipboardCheck :size="22" color="#fff" /></div>
        <div><div class="ref-stat-value">{{ stats.pendingSubmissions }}</div><div class="ref-stat-label">待评审成果 →</div></div>
      </div>
      <div class="ref-stat-card clickable" @click="riskVisible = true">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#f87171,#dc2626)"><AlertTriangle :size="22" color="#fff" /></div>
        <div><div class="ref-stat-value">{{ atRisk.length }}</div><div class="ref-stat-label">掉队学生 →</div></div>
      </div>
    </div>

    <!-- 掉队名单 -->
    <div v-if="atRisk.length" class="card risk-card">
      <div class="card-head">
        <h3><AlertTriangle :size="16" color="#dc2626" /> 需要关注的学生</h3>
        <span class="hint">已过截止、14 天没有学习动作、或时间过半进度不到 30%</span>
      </div>
      <div class="risk-list">
        <div v-for="(r, i) in atRisk.slice(0, riskVisible ? 100 : 5)" :key="i" class="risk-item">
          <div class="grow">
            <b>{{ r.studentName }}</b><span class="muted"> {{ r.studentNo || '' }} · {{ r.projectTitle }}</span>
            <div class="risk-reasons"><span v-for="rs in r.reasons" :key="rs" class="badge badge-red">{{ rs }}</span></div>
          </div>
          <div class="risk-meta">进度 {{ r.progress }}%<br /><span class="muted">最近活动 {{ fmt(r.lastActiveAt) }}</span></div>
          <el-button size="small" @click="nudge(r)">提醒 TA</el-button>
        </div>
        <el-button v-if="atRisk.length > 5 && !riskVisible" text type="primary" @click="riskVisible = true">查看全部 {{ atRisk.length }} 人</el-button>
      </div>
    </div>

    <TeacherBriefCard :user-id="authStore.user?.id" @action="onBriefAction" />

    <!-- 我的项目 -->
    <div class="card">
      <div class="card-head">
        <h3>我的项目</h3>
        <div class="head-actions">
          <span class="hint">可以直接编辑自己项目的教学内容;学生提交的成果在「成果评审」里打分</span>
          <el-button size="small" type="primary" plain @click="draftVisible = true"><Sparkles :size="14" style="margin-right:4px" />AI 起草项目</el-button>
          <el-button size="small" @click="openEdit(null)">+ 新建项目</el-button>
        </div>
      </div>
      <el-empty v-if="projects.length === 0" description="暂无名下项目,请联系管理员在项目管理中指派讲师" />
      <el-table v-else :data="projects" stripe>
        <el-table-column label="项目" min-width="240">
          <template #default="{ row }">
            <div class="proj-cell">
              <img v-if="row.coverUrl" :src="row.coverUrl" class="proj-thumb" alt="" />
              <span v-else class="proj-thumb">{{ row.icon || '📦' }}</span>
              <div>
                <b>{{ row.title }}</b>
                <div class="sub-text">{{ row.category }} · {{ row.difficulty }} · {{ row.duration }}
                  <span class="badge" :class="row.status === 'PUBLISHED' ? 'badge-green' : 'badge-gray'" style="margin-left:6px">{{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="enrolledCount" label="报名" width="70" />
        <el-table-column label="完成率" width="80">
          <template #default="{ row }">{{ row.enrolledCount > 0 ? row.completionRate + '%' : '–' }}</template>
        </el-table-column>
        <el-table-column label="大纲/考核" width="100">
          <template #default="{ row }">{{ arr(row.syllabus).length }} 阶段 · {{ arr(row.assessments).length || '整体' }}</template>
        </el-table-column>
        <el-table-column label="资料附件" width="90">
          <template #default="{ row }">
            <span class="badge" :class="uploadedCount(row) > 0 ? 'badge-green' : 'badge-gray'">{{ uploadedCount(row) }} / {{ arr(row.resources).length }}</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="110">
          <template #default="{ row }">{{ (row.updatedAt || '').slice(0, 10) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="500" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openEdit(row)">编辑项目</el-button>
            <el-button size="small" @click="openResources(row)">资料附件</el-button>
            <el-button size="small" @click="openStudents(row)">学生进度</el-button>
            <el-button size="small" @click="openAnnounce(row)">发公告</el-button>
            <el-button size="small" text @click="$router.push(`/app/projects/${row.id}`)">预览</el-button>
            <el-button size="small" plain :loading="publishing === row.id" @click="publishToStore(row)">
              {{ row.hubItemId ? '更新到商店' : '发布到商店' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 编辑项目(与管理端同一套表单,讲师归属由服务端保留) -->
    <ProjectEditDialog v-model="editVisible" :project="editing" mode="teacher" :skill-dimensions="skillDimensions"
                       :save-fn="saveProject" @saved="load" />
    <AiDraftDialog v-model="draftVisible" :skill-dimensions="skillDimensions" @drafted="onDrafted" />

    <!-- 资源管理弹窗(快捷上传附件) -->
    <el-dialog v-model="resVisible" :title="`资料附件 - ${current?.title || ''}`" width="720px" top="5vh">
      <p class="res-tip">为每条资源上传附件后,学生端「学习资源」出现下载链接;原理图 / LAYOUT / 3D 图类型会同时显示在 BOM 页签作为设计文件。</p>
      <div v-for="(r, i) in resRows" :key="i" class="res-edit-row">
        <el-select v-model="r.type" class="res-type-sel">
          <el-option v-for="t in ['文档', '视频', '代码', '手册', '工具', '课件', '原理图', 'LAYOUT', '3D图']" :key="t" :label="t" :value="t" />
        </el-select>
        <el-input v-model="r.name" placeholder="资源名称,如: 项目开发指南.pdf" class="res-name-input" />
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

    <!-- 项目公告 -->
    <el-dialog v-model="announceVisible" :title="`发公告 - ${current?.title || ''}`" width="520px">
      <p class="hint" style="margin:0 0 12px">发给该项目全部报名学生({{ current?.enrolledCount || 0 }} 人),每人收到一条站内通知。</p>
      <el-form label-position="top">
        <el-form-item label="标题" required><el-input v-model="announceForm.title" maxlength="100" placeholder="如: 本周五前完成原理图阶段" /></el-form-item>
        <el-form-item label="正文"><el-input v-model="announceForm.content" type="textarea" :rows="4" maxlength="1000" placeholder="选填" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="announceVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="sendAnnounce">发布</el-button>
      </template>
    </el-dialog>

    <!-- 学生进度弹窗 -->
    <el-dialog v-model="stuVisible" :title="`学生进度 - ${current?.title || ''}`" width="760px" top="5vh">
      <el-empty v-if="students.length === 0" description="还没有学生报名该项目" />
      <el-table v-else :data="students" stripe max-height="480">
        <el-table-column prop="studentName" label="姓名" width="100" />
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="major" label="专业" width="130" />
        <el-table-column label="进度" min-width="160">
          <template #default="{ row }"><el-progress :percentage="row.progress" :stroke-width="8" /></template>
        </el-table-column>
        <el-table-column prop="currentTask" label="当前任务" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="badge" :class="row.status === 'COMPLETED' ? 'badge-green' : 'badge-blue'">{{ row.status === 'COMPLETED' ? '已完成' : '进行中' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="deadline" label="截止" width="110" />
      </el-table>
      <p class="hint" style="margin-top:10px">进度由学生自己记录;学生提交成果并由你评审 ≥60 分后才会变为「已完成」。</p>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { AlertTriangle, BookOpen, Check, ClipboardCheck, Sparkles, Upload, Users } from 'lucide-vue-next'
import {
  storePublish, teacherAnnounceProject, teacherAtRisk, teacherCreateProject, teacherProjectStudents, teacherProjects, teacherRemindStudent,
  teacherSkillDimensions, teacherStats, teacherUpdateProject, teacherUpdateResources, uploadDocFile
} from '../../api'
import ProjectEditDialog from '../../components/ProjectEditDialog.vue'
import AiDraftDialog from '../../components/AiDraftDialog.vue'
import TeacherBriefCard from '../../components/TeacherBriefCard.vue'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const draftVisible = ref(false)
const onDrafted = (draft) => {
  editing.value = draft
  editVisible.value = true
}
const onBriefAction = (a) => {
  if (a.kind === 'grade') return router.push('/teacher/submissions')
  if (a.kind === 'remind') { riskVisible.value = true; return }
  const p = projects.value.find((x) => x.id === a.projectId)
  if (a.kind === 'announce' && p) return openAnnounce(p)
  if (p) return router.push(`/app/projects/${p.id}`)
  router.push('/teacher/classes')
}

const stats = reactive({ projectCount: 0, studentTotal: 0, completedTotal: 0, pendingSubmissions: 0, resourceCount: 0 })
const projects = ref([])
const atRisk = ref([])
const riskVisible = ref(false)
const announceVisible = ref(false)
const announceForm = reactive({ title: '', content: '' })
const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '无')

const openAnnounce = (row) => {
  current.value = row
  Object.assign(announceForm, { title: '', content: '' })
  announceVisible.value = true
}
const sendAnnounce = async () => {
  if (!announceForm.title.trim()) { ElMessage.warning('请填写标题'); return }
  saving.value = true
  try {
    const r = await teacherAnnounceProject(current.value.id, { ...announceForm })
    ElMessage.success(`公告已发送给 ${r.notified} 名学生`)
    announceVisible.value = false
  } finally {
    saving.value = false
  }
}
const nudge = async (r) => {
  let message = ''
  try {
    const res = await ElMessageBox.prompt(
      `将以站内通知发给 ${r.studentName}(${r.reasons.join(';')})。可以修改提醒内容:`,
      '提醒学生',
      { confirmButtonText: '发送', cancelButtonText: '取消', inputType: 'textarea',
        inputValue: `《${r.projectTitle}》${r.currentTask ? '当前任务「' + r.currentTask + '」' : ''}进度有些落后了,这周抽时间推进一下,有困难随时在讨论区问我或找 AI 导师。`,
        inputValidator: (v) => !!(v && v.trim()) || '提醒内容不能为空' })
    message = res.value
  } catch (e) { return }
  await teacherRemindStudent(r.projectId, r.userId, message)
  ElMessage.success('已发送提醒')
}
const current = ref(null)
const saving = ref(false)
const publishing = ref(null)
const editVisible = ref(false)
const editing = ref(null)
const skillDimensions = ref([])

const arr = (v) => Array.isArray(v) ? v : []
const uploadedCount = (row) => arr(row.resources).filter((r) => r.url).length

const load = async () => {
  Object.assign(stats, await teacherStats())
  projects.value = await teacherProjects()
  teacherAtRisk().then((list) => { atRisk.value = list }).catch(() => {})
}

const openEdit = (row) => {
  editing.value = row
  editVisible.value = true
}
const saveProject = (id, payload) => (id ? teacherUpdateProject(id, payload) : teacherCreateProject(payload))

const publishToStore = async (row) => {
  let changelog = ''
  try {
    const r = await ElMessageBox.prompt(
      row.hubItemId ? `将「${row.title}」的当前内容作为新版本提交商店审核,请填写版本说明:` : `将「${row.title}」发布到项目商店,审核通过后其他院校可以安装使用。可填写版本说明:`,
      row.hubItemId ? '更新到商店' : '发布到商店',
      { confirmButtonText: '提交审核', cancelButtonText: '取消', inputPlaceholder: '例如:首次发布', inputValidator: () => true }
    )
    changelog = r.value || ''
  } catch (e) { return }
  publishing.value = row.id
  try {
    const item = await storePublish(row.id, { changelog })
    ElMessage.success(item.newItem ? `已提交到项目商店(条目 #${item.id}),等待平台审核` : `已追加新版本 v${item.latestVersionNo},等待平台审核`)
    await load()
  } finally {
    publishing.value = null
  }
}

const resVisible = ref(false)
const resRows = ref([])
const stuVisible = ref(false)
const students = ref([])

const openResources = (row) => {
  current.value = row
  resRows.value = arr(row.resources).map((r) => ({ type: r.type || '文档', name: r.name || '', url: r.url || '', uploading: false }))
  resVisible.value = true
}

const doUploadRes = async (opt, row) => {
  if (opt.file.size > 30 * 1024 * 1024) { ElMessage.warning('附件不能超过 30MB'); return }
  row.uploading = true
  try {
    const { url, name } = await uploadDocFile(opt.file)
    row.url = url
    if (!row.name) row.name = name
    ElMessage.success(`附件上传成功: ${name}`)
  } catch (e) { /* 已提示 */ } finally {
    row.uploading = false
  }
}

const saveResources = async () => {
  const cleaned = resRows.value.filter((r) => r.name.trim()).map((r) => ({ type: r.type, name: r.name.trim(), url: r.url || '' }))
  saving.value = true
  try {
    await teacherUpdateResources(current.value.id, JSON.stringify(cleaned))
    ElMessage.success('资源列表已保存,学生端即时生效')
    resVisible.value = false
    await load()
  } catch (e) { /* 已提示 */ } finally {
    saving.value = false
  }
}

const openStudents = async (row) => {
  current.value = row
  students.value = await teacherProjectStudents(row.id)
  stuVisible.value = true
}

onMounted(async () => {
  await load()
  teacherSkillDimensions().then((list) => { skillDimensions.value = list }).catch(() => {})
})
</script>

<style scoped>
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px; }
@media (max-width: 1000px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }
.clickable { cursor: pointer; }
.clickable:hover { box-shadow: var(--shadow-lg); }
.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; gap: 12px; }
.head-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.card-head h3 { margin: 0; font-size: 16px; }
.hint { font-size: 12px; color: #9ca3af; }
.muted { font-size: 12px; color: var(--text-secondary); }
.grow { flex: 1; min-width: 0; }
.risk-card { margin-bottom: 20px; border-left: 4px solid #f87171; }
.risk-card h3 { display: flex; align-items: center; gap: 6px; }
.risk-list { display: flex; flex-direction: column; gap: 8px; }
.risk-item { display: flex; align-items: center; gap: 14px; padding: 10px 12px; border-radius: 10px; background: #fef2f2; font-size: 14px; }
.risk-reasons { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 4px; }
.risk-meta { font-size: 13px; text-align: right; white-space: nowrap; }
.sub-text { font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
.proj-cell { display: flex; align-items: center; gap: 12px; }
.proj-thumb { width: 56px; height: 40px; border-radius: 8px; object-fit: cover; background: #f3f4f6; display: inline-flex; align-items: center; justify-content: center; font-size: 20px; flex-shrink: 0; }
.res-tip { background: #eff6ff; border-radius: 10px; padding: 10px 14px; font-size: 13px; color: #1d4ed8; margin: 0 0 14px; }
.res-edit-row { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
.res-type-sel { width: 100px; flex-shrink: 0; }
.res-name-input { flex: 1; }
.add-res-btn { width: 100%; border-style: dashed; }
</style>
