<template>
  <div class="feedback-page">
    <div class="page-intro">
      <div>
        <div class="eyebrow">HELP & FEEDBACK</div>
        <h2>问题反馈</h2>
        <p>遇到流程、操作问题，或有更好的优化建议，都可以告诉我们。</p>
      </div>
      <div class="intro-tip"><MessageSquarePlus :size="18" /><span>反馈会交给当前站点管理员处理</span></div>
    </div>

    <div class="feedback-grid">
      <section class="card form-card">
        <div class="card-head"><div><h3>提交新反馈</h3><p class="muted">描述得越具体，越方便我们复现和跟进。</p></div></div>
        <el-form :model="form" label-position="top" @submit.prevent="submit">
          <el-form-item label="反馈类型" required>
            <el-radio-group v-model="form.category" class="category-group">
              <el-radio-button label="PROCESS">流程问题</el-radio-button>
              <el-radio-button label="OPERATION">操作问题</el-radio-button>
              <el-radio-button label="SUGGESTION">优化建议</el-radio-button>
              <el-radio-button label="OTHER">其他</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="问题标题" required>
            <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="例如：提交成果时无法选择附件" />
          </el-form-item>
          <el-form-item label="详细描述" required>
            <el-input v-model="form.content" type="textarea" :rows="7" maxlength="5000" show-word-limit
                      placeholder="请说明发生了什么、你原本想完成什么，以及可以复现的步骤。" />
          </el-form-item>
          <el-form-item label="问题截图（可选）">
            <div class="upload-area">
              <input ref="fileInput" type="file" class="hidden-input" accept="image/png,image/jpeg,image/gif,image/webp,image/svg+xml" multiple @change="onFiles" />
              <el-button plain :loading="uploading" @click="fileInput?.click()"><Upload :size="16" />选择图片</el-button>
              <span class="upload-hint">最多 6 张，单张不超过 30MB</span>
              <div v-if="form.attachments.length" class="attachment-list">
                <div v-for="(file, index) in form.attachments" :key="file.url" class="attachment">
                  <el-image :src="file.url" fit="cover" :preview-src-list="form.attachments.map(a => a.url)" />
                  <span :title="file.name">{{ file.name }}</span>
                  <el-button link type="danger" @click="removeAttachment(index)">移除</el-button>
                </div>
              </div>
            </div>
          </el-form-item>
          <div class="form-foot">
            <span class="muted">提交后可在下方查看处理进度和管理员回复。</span>
            <el-button type="primary" :loading="saving" :disabled="uploading" @click="submit">提交反馈</el-button>
          </div>
        </el-form>
      </section>

      <section class="card history-card">
        <div class="card-head"><div><h3>我的反馈</h3><p class="muted">当前站点内的历史记录</p></div><el-button text @click="load">刷新</el-button></div>
        <el-empty v-if="!loading && !items.length" description="还没有提交过反馈" />
        <div v-else class="history-list">
          <article v-for="item in items" :key="item.id" class="history-item">
            <div class="history-top"><span class="category">{{ categoryText(item.category) }}</span><span class="status" :class="'status-' + item.status.toLowerCase()">{{ statusText(item.status) }}</span></div>
            <h4>{{ item.title }}</h4>
            <p>{{ item.content }}</p>
            <div v-if="attachments(item).length" class="history-images">
              <el-image v-for="file in attachments(item)" :key="file.url" :src="file.url" fit="cover" :preview-src-list="attachments(item).map(a => a.url)" />
            </div>
            <div class="history-time">提交于 {{ formatTime(item.createdAt) }}</div>
            <div v-if="item.adminReply" class="reply"><b>{{ item.adminName || '管理员' }} 回复</b><span>{{ item.adminReply }}</span></div>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { MessageSquarePlus, Upload } from 'lucide-vue-next'
import { createFeedback, myFeedbacks, uploadImage } from '../../api'

const fileInput = ref(null)
const saving = ref(false)
const uploading = ref(false)
const loading = ref(false)
const items = ref([])
const form = reactive({ category: 'PROCESS', title: '', content: '', attachments: [] })

const categoryText = (value) => ({ PROCESS: '流程问题', OPERATION: '操作问题', SUGGESTION: '优化建议', OTHER: '其他' }[value] || value)
const statusText = (value) => ({ OPEN: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已解决', CLOSED: '已关闭' }[value] || value)
const formatTime = (value) => (value || '').replace('T', ' ').slice(0, 16)
const attachments = (item) => {
  try { return item.attachments ? JSON.parse(item.attachments) : [] } catch { return [] }
}

const load = async () => {
  loading.value = true
  try { items.value = await myFeedbacks() } finally { loading.value = false }
}

const onFiles = async (event) => {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return
  if (form.attachments.length + files.length > 6) { ElMessage.warning('最多上传 6 张图片'); return }
  uploading.value = true
  try {
    for (const file of files) {
      if (file.size > 30 * 1024 * 1024) { ElMessage.warning(`${file.name} 超过 30MB，已跳过`); continue }
      const result = await uploadImage(file)
      form.attachments.push({ url: result.url, name: file.name })
    }
  } finally { uploading.value = false }
}

const removeAttachment = (index) => form.attachments.splice(index, 1)

const submit = async () => {
  if (!form.title.trim() || !form.content.trim()) { ElMessage.warning('请填写问题标题和详细描述'); return }
  saving.value = true
  try {
    await createFeedback({
      category: form.category,
      title: form.title,
      content: form.content,
      pageUrl: window.location.pathname,
      attachments: form.attachments.length ? JSON.stringify(form.attachments) : null
    })
    ElMessage.success('反馈已提交，感谢你的建议')
    Object.assign(form, { category: 'PROCESS', title: '', content: '', attachments: [] })
    await load()
  } finally { saving.value = false }
}

onMounted(load)
</script>

<style scoped>
.feedback-page { max-width: 1180px; margin: 0 auto; }
.page-intro { display: flex; justify-content: space-between; align-items: flex-end; gap: 18px; margin-bottom: 20px; }
.eyebrow { color: #2563eb; font-size: 11px; letter-spacing: .12em; font-weight: 700; }
.page-intro h2 { margin: 5px 0 6px; font-size: 25px; color: #111827; }
.page-intro p { margin: 0; color: #64748b; font-size: 14px; }
.intro-tip { display: flex; gap: 8px; align-items: center; color: #2563eb; background: #eff6ff; border-radius: 10px; padding: 10px 13px; font-size: 12px; white-space: nowrap; }
.feedback-grid { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(330px, .8fr); gap: 18px; align-items: start; }
.card-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 20px; }
.card-head h3 { margin: 0 0 5px; font-size: 17px; }
.muted { color: #94a3b8; font-size: 12px; margin: 0; }
.category-group { display: flex; flex-wrap: wrap; gap: 8px; }
.category-group :deep(.el-radio-button__inner) { border: 1px solid #dbe3ef; border-radius: 8px !important; box-shadow: none !important; padding: 9px 14px; }
.category-group :deep(.el-radio-button:first-child .el-radio-button__inner), .category-group :deep(.el-radio-button:last-child .el-radio-button__inner) { border-left: 1px solid #dbe3ef; }
.upload-area { width: 100%; }
.hidden-input { display: none; }
.upload-area :deep(.el-button) { display: inline-flex; align-items: center; gap: 6px; }
.upload-hint { color: #94a3b8; font-size: 12px; margin-left: 10px; }
.attachment-list { display: grid; gap: 8px; margin-top: 12px; }
.attachment { display: flex; align-items: center; gap: 9px; border: 1px solid #e5e7eb; border-radius: 8px; padding: 6px 8px; }
.attachment :deep(.el-image) { width: 44px; height: 44px; border-radius: 5px; flex: 0 0 auto; }
.attachment > span { flex: 1; min-width: 0; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #475569; }
.form-foot { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-top: 20px; }
.history-list { display: grid; gap: 12px; max-height: 680px; overflow-y: auto; padding-right: 3px; }
.history-item { border: 1px solid #edf0f5; border-radius: 11px; padding: 14px; }
.history-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 9px; }
.category { color: #2563eb; font-size: 11px; background: #eff6ff; border-radius: 999px; padding: 4px 8px; }
.status { font-size: 11px; border-radius: 999px; padding: 4px 8px; }
.status-open { color: #b45309; background: #fffbeb; }.status-in_progress { color: #2563eb; background: #eff6ff; }.status-resolved { color: #15803d; background: #f0fdf4; }.status-closed { color: #64748b; background: #f1f5f9; }
.history-item h4 { margin: 0 0 6px; color: #1e293b; font-size: 14px; }
.history-item p { color: #64748b; font-size: 13px; line-height: 1.65; white-space: pre-wrap; margin: 0; }
.history-images { display: flex; gap: 6px; margin-top: 10px; }.history-images :deep(.el-image) { width: 52px; height: 52px; border-radius: 6px; }
.history-time { color: #a1a1aa; font-size: 11px; margin-top: 10px; }
.reply { margin-top: 11px; padding: 9px 10px; background: #f8fafc; border-radius: 7px; color: #475569; font-size: 12px; line-height: 1.6; }.reply b { display: block; color: #334155; margin-bottom: 2px; }
@media (max-width: 850px) { .feedback-grid { grid-template-columns: 1fr; }.page-intro { align-items: flex-start; flex-direction: column; }.intro-tip { white-space: normal; } }
</style>
