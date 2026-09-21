<template>
  <div class="submission-uploader">
    <el-upload drag multiple :show-file-list="false" :http-request="upload" :disabled="disabled || attached.length + uploading.length >= 8" accept=".jpg,.jpeg,.png,.webp,.gif,.pdf,.doc,.docx,.mp4,.mov,.webm,.zip,.rar,.7z,.c,.h,.cc,.cpp,.cxx,.java,.py,.js,.jsx,.ts,.tsx,.vue,.html,.css,.scss,.sql,.json,.xml,.yaml,.yml,.md,.txt,.csv,.log,.sh,.bat,.ps1,.ino,.kicad_sch,.kicad_pcb,.sch,.brd,.hex,.bin">
      <div class="drop-icon"><CloudUpload :size="26" /></div>
      <div class="drop-title">点击添加成果材料<span class="desktop-hint">，或拖到这里</span></div>
      <div class="drop-sub">实物图片 · 演示视频 · PDF / Word 报告 · 源码或压缩包</div>
    </el-upload>
    <div class="upload-meta"><span>单个不超过100MB，合计200MB；视频5分钟以内</span><span>{{ attached.length }} / 8 个附件</span></div>
    <div v-if="attached.length || uploading.length || failures.length" class="file-list" aria-live="polite">
      <div v-for="file in attached" :key="file.url" class="file-row">
        <span class="file-icon" :class="fileType(file.name)"><component :is="icon(file.name)" :size="20" /></span>
        <div class="file-info"><a :href="file.url" target="_blank" rel="noopener" :title="file.name">{{ file.name }}</a><span>{{ formatSize(file.size) }}<span class="uploaded"><CheckCircle2 :size="12" />上传完成</span></span></div>
        <el-button text :disabled="disabled" :aria-label="`移除 ${file.name}`" @click="remove(file.url)"><X :size="17" /></el-button>
      </div>
      <div v-for="task in uploading" :key="task.id" class="file-row">
        <span class="file-icon"><component :is="icon(task.name)" :size="20" /></span>
        <div class="file-info"><b>{{ task.name }}</b><el-progress :percentage="task.progress" :stroke-width="5" /></div>
      </div>
      <div v-for="(task, i) in failures" :key="task.file.uid" class="file-row failed">
        <span class="file-icon"><AlertCircle :size="20" /></span>
        <div class="file-info"><b>{{ task.file.name }}</b><span>上传失败，请检查网络后重试</span></div>
        <el-button text type="primary" :disabled="disabled" @click="retry(task, i)">重试</el-button>
        <el-button text :aria-label="`移除失败记录 ${task.file.name}`" @click="failures.splice(i, 1)"><X :size="16" /></el-button>
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CloudUpload, Image, Film, FileText, X, CheckCircle2, AlertCircle } from 'lucide-vue-next'
import { uploadSubmissionFile } from '../api'
const props = defineProps({ modelValue: { type: Array, default: () => [] }, disabled: Boolean })
const emit = defineEmits(['update:modelValue', 'busy'])
const attached = ref([...props.modelValue])
const uploading = ref([])
const failures = ref([])
watch(() => props.modelValue, value => { attached.value = [...value] })
const publish = value => { attached.value = value; emit('update:modelValue', value) }
const remove = url => publish(attached.value.filter(f => f.url !== url))
const fileType = name => /\.(jpg|jpeg|png|webp)$/i.test(name) ? 'image' : /\.(mp4|mov|webm)$/i.test(name) ? 'video' : 'document'
const icon = name => ({ image: Image, video: Film, document: FileText }[fileType(name)])
const formatSize = size => size ? (size < 1024 * 1024 ? `${Math.ceil(size / 1024)} KB` : `${(size / 1024 / 1024).toFixed(1)} MB`) : ''
const retry = (task, index) => { failures.value.splice(index, 1); upload(task) }
const upload = async option => {
  const file = option.file
  if (!/\.(jpg|jpeg|png|webp|gif|pdf|doc|docx|mp4|mov|webm|zip|rar|7z|c|h|cc|cpp|cxx|java|py|js|jsx|ts|tsx|vue|html|css|scss|sql|json|xml|yaml|yml|md|txt|csv|log|sh|bat|ps1|ino|kicad_sch|kicad_pcb|sch|brd|hex|bin)$/i.test(file.name)) {
    ElMessage.warning('请选择图片、视频、文档、源码或压缩包'); option.onError(new Error('不支持的格式')); return
  }
  const total = [...attached.value, ...uploading.value].reduce((sum, f) => sum + (Number(f.size) || 0), 0)
  if (attached.value.length + uploading.value.length >= 8 || file.size > 100 * 1024 * 1024 || total + file.size > 200 * 1024 * 1024) {
    ElMessage.warning('附件数量或大小超过限制'); option.onError(new Error('附件超过限制')); return
  }
  uploading.value.push({ id: file.uid, name: file.name, size: file.size, progress: 0 }); emit('busy', true)
  try {
    const result = await uploadSubmissionFile(file, event => {
      const task = uploading.value.find(t => t.id === file.uid)
      if (task) task.progress = Math.min(99, Math.round(event.loaded / (event.total || file.size) * 100))
    })
    publish([...attached.value, { ...result, size: file.size }]); option.onSuccess(result)
  } catch (error) { failures.value.push(option); option.onError(error) }
  finally { uploading.value = uploading.value.filter(t => t.id !== file.uid); emit('busy', uploading.value.length > 0) }
}
</script>
<style scoped>
.submission-uploader { width: 100%; }
:deep(.el-upload) { width: 100%; }:deep(.el-upload-dragger) { border: 2px dashed var(--border); border-radius: 12px; padding: 24px 16px; background: #fff; transition: border-color .15s, background .15s; }
:deep(.el-upload-dragger:hover), :deep(.el-upload-dragger.is-dragover) { border-color: var(--brand-blue); background: #eff6ff; }
.drop-icon { width: 48px; height: 48px; border-radius: 14px; background: linear-gradient(135deg,#eff6ff,#faf5ff); color: var(--brand-blue); display: grid; place-items: center; margin: 0 auto 10px; }
.drop-title { color: var(--text-main); font-size: 14px; font-weight: 600; }.drop-sub { color: var(--text-secondary); font-size: 12px; margin-top: 5px; }
.upload-meta { display: flex; justify-content: space-between; gap: 12px; font-size: 12px; color: var(--text-secondary); margin: 9px 0 12px; line-height: 1.6; }
.upload-meta>span:last-child { white-space: nowrap; }.file-list { display: grid; gap: 8px; }
.file-row { display: flex; align-items: center; gap: 12px; padding: 12px; border: 1px solid var(--border); border-radius: 12px; background: #fff; }
.file-icon { width: 38px; height: 42px; flex-shrink: 0; border-radius: 10px; background: #eff6ff; color: var(--brand-blue); display: grid; place-items: center; }.file-icon.video { background: #faf5ff; color: var(--brand-purple); }.file-icon.image { background: #ecfdf5; color: #059669; }
.file-info { flex: 1; min-width: 0; display: grid; gap: 5px; }.file-info>a,.file-info>b { font-size: 13px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.file-info>a:hover { color: var(--brand-blue); }.file-info>span { display: flex; align-items: center; gap: 12px; font-size: 11px; color: var(--text-secondary); }
.uploaded { display: inline-flex; align-items: center; gap: 4px; color: #16a34a; }.failed { border-color: #fecaca; background: #fffafa; }.failed .file-icon { background: #fef2f2; color: #dc2626; }.file-row .el-button + .el-button { margin-left: 0; }
@media(max-width:600px) { .desktop-hint { display: none; }.upload-meta { flex-direction: column; gap: 3px; }.file-row { gap: 8px; padding: 10px; } }
</style>
