<template>
  <div>
    <!-- 统计卡 -->
    <div class="stat-grid">
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#60a5fa,#2563eb)">
          <BookOpen :size="22" color="#fff" />
        </div>
        <div>
          <div class="ref-stat-value">{{ stats.projectCount }}</div>
          <div class="ref-stat-label">我的项目</div>
        </div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#4ade80,#16a34a)">
          <Users :size="22" color="#fff" />
        </div>
        <div>
          <div class="ref-stat-value">{{ stats.studentTotal }}</div>
          <div class="ref-stat-label">报名学生</div>
        </div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#c084fc,#9333ea)">
          <FileText :size="22" color="#fff" />
        </div>
        <div>
          <div class="ref-stat-value">{{ stats.resourceCount }}</div>
          <div class="ref-stat-label">教学资源</div>
        </div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#facc15,#f59e0b)">
          <Star :size="22" color="#fff" />
        </div>
        <div>
          <div class="ref-stat-value">{{ stats.avgRating }}</div>
          <div class="ref-stat-label">项目平均评分</div>
        </div>
      </div>
    </div>

    <!-- 我的项目 -->
    <div class="card">
      <div class="card-head">
        <h3>我的项目</h3>
        <span class="hint">上传的资源学生可在项目详情「学习资源」直接下载</span>
      </div>
      <el-empty v-if="projects.length === 0" description="暂无名下项目,请联系管理员在项目管理中指派讲师" />
      <el-table v-else :data="projects" stripe>
        <el-table-column label="项目" min-width="220">
          <template #default="{ row }">
            <b>{{ row.title }}</b>
            <div class="sub-text">{{ row.category }} · {{ row.difficulty }} · {{ row.duration }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="enrolledCount" label="报名数" width="80" />
        <el-table-column prop="rating" label="评分" width="70" />
        <el-table-column label="资源数" width="80">
          <template #default="{ row }">{{ arr(row.resources).length }}</template>
        </el-table-column>
        <el-table-column label="已传附件" width="90">
          <template #default="{ row }">
            <span class="badge" :class="uploadedCount(row) > 0 ? 'badge-green' : 'badge-gray'">
              {{ uploadedCount(row) }} / {{ arr(row.resources).length }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="110">
          <template #default="{ row }">{{ (row.updatedAt || '').slice(0, 10) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openResources(row)">管理资源</el-button>
            <el-button size="small" @click="openCover(row)">更换封面</el-button>
            <el-button size="small" @click="openStudents(row)">学生进度</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 资源管理弹窗 -->
    <el-dialog v-model="resVisible" :title="`管理资源 - ${current?.title || ''}`" width="720px" top="5vh">
      <p class="res-tip">为每条资源上传附件后,学生端「学习资源」将出现真实下载链接;未上传附件的资源学生端会提示联系导师。</p>
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
      <el-button class="add-res-btn" plain @click="resRows.push({ type: '文档', name: '', url: '' })">
        + 添加资源
      </el-button>
      <template #footer>
        <el-button @click="resVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveResources">保存资源列表</el-button>
      </template>
    </el-dialog>

    <!-- 封面弹窗 -->
    <el-dialog v-model="coverVisible" :title="`更换封面 - ${current?.title || ''}`" width="480px">
      <ImageUploader v-model="coverUrl" />
      <template #footer>
        <el-button @click="coverVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCover">保存封面</el-button>
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
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column prop="currentTask" label="当前任务" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="badge" :class="row.status === 'COMPLETED' ? 'badge-green' : 'badge-blue'">
              {{ row.status === 'COMPLETED' ? '已完成' : '进行中' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="deadline" label="截止" width="110" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { BookOpen, Check, FileText, Star, Upload, Users } from 'lucide-vue-next'
import {
  teacherProjectStudents, teacherProjects, teacherStats,
  teacherUpdateCover, teacherUpdateResources, uploadDocFile
} from '../../api'
import ImageUploader from '../../components/ImageUploader.vue'

const stats = reactive({ projectCount: 0, studentTotal: 0, resourceCount: 0, avgRating: 0 })
const projects = ref([])
const current = ref(null)
const saving = ref(false)

const resVisible = ref(false)
const resRows = ref([])
const coverVisible = ref(false)
const coverUrl = ref('')
const stuVisible = ref(false)
const students = ref([])

const arr = (v) => Array.isArray(v) ? v : []
const uploadedCount = (row) => arr(row.resources).filter((r) => r.url).length

const load = async () => {
  Object.assign(stats, await teacherStats())
  projects.value = await teacherProjects()
}

const openResources = (row) => {
  current.value = row
  resRows.value = arr(row.resources).map((r) => ({
    type: r.type || '文档', name: r.name || '', url: r.url || '', uploading: false
  }))
  resVisible.value = true
}

// el-upload 自定义上传:走 /api/upload/file,成功后把 url 写回该行
const doUploadRes = async (opt, row) => {
  if (opt.file.size > 30 * 1024 * 1024) {
    ElMessage.warning('附件不能超过 30MB')
    return
  }
  row.uploading = true
  try {
    const { url, name } = await uploadDocFile(opt.file)
    row.url = url
    if (!row.name) row.name = name
    ElMessage.success(`附件上传成功: ${name}`)
  } catch (e) { /* 错误已提示 */ } finally {
    row.uploading = false
  }
}

const saveResources = async () => {
  const cleaned = resRows.value
    .filter((r) => r.name.trim())
    .map((r) => ({ type: r.type, name: r.name.trim(), url: r.url || '' }))
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

const openCover = (row) => {
  current.value = row
  coverUrl.value = row.coverUrl || ''
  coverVisible.value = true
}

const saveCover = async () => {
  saving.value = true
  try {
    await teacherUpdateCover(current.value.id, coverUrl.value)
    ElMessage.success('封面已更新')
    coverVisible.value = false
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

onMounted(load)
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
@media (max-width: 1000px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }

.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.card-head h3 { margin: 0; font-size: 16px; }
.hint { font-size: 12px; color: #9ca3af; }
.sub-text { font-size: 12px; color: var(--text-secondary); }

.res-tip {
  background: #eff6ff; border-radius: 10px; padding: 10px 14px;
  font-size: 13px; color: #1d4ed8; margin: 0 0 14px;
}
.res-edit-row { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
.res-type-sel { width: 100px; flex-shrink: 0; }
.res-name-input { flex: 1; }
.add-res-btn { width: 100%; border-style: dashed; }
</style>
