<template>
  <div class="materials">
    <div v-for="file in files" :key="file.url" class="material">
      <div class="material-head"><span class="file-icon"><component :is="/\.(mp4|mov|webm)$/i.test(file.url) ? Film : /\.(png|jpe?g|webp|gif)$/i.test(file.url) ? Image : FileText" :size="18" /></span><a :href="file.url" target="_blank" rel="noopener" :title="file.name">{{ file.name || '成果附件' }}</a><a :href="file.url" target="_blank" rel="noopener" aria-label="打开原件" class="open-link"><ExternalLink :size="15" /></a></div>
      <el-image v-if="/\.(png|jpe?g|webp|gif)$/i.test(file.url)" :src="file.url" :preview-src-list="[file.url]" preview-teleported fit="contain" lazy alt="成果图片"><template #error><span class="preview-error">图片加载失败，可点击文件名查看</span></template></el-image>
      <VideoPlayer v-else-if="/\.(mp4|mov|webm)$/i.test(file.url)" :src="file.url" :title="file.name" />
      <p v-else class="document-hint">点击文件名查看完整报告</p>
    </div>
  </div>
</template>
<script setup>
import { computed } from 'vue'
import VideoPlayer from './VideoPlayer.vue'
import { Image, Film, FileText, ExternalLink } from 'lucide-vue-next'
const props = defineProps({ submission: { type: Object, default: null } })
const files = computed(() => {
  let items = props.submission?.attachments || []
  if (typeof items === 'string') { try { items = JSON.parse(items) } catch { items = [] } }
  items = Array.isArray(items) ? [...items] : []
  const old = props.submission?.attachmentUrl
  if (old && !items.some(f => f.url === old)) items.push({ url: old, name: '历史成果附件' })
  return items.filter(f => typeof f.url === 'string' && /^\/uploads\/\d{6}\/[\w-]+\.[a-z0-9]+$/i.test(f.url))
})
</script>
<style scoped>
.materials { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr)); gap: 12px; margin: 14px 0; }
.material { min-width: 0; overflow: hidden; border: 1px solid var(--border); border-radius: 12px; background: #fff; align-self: start; }
.material-head { display: flex; align-items: center; gap: 10px; padding: 12px; }.file-icon { display: grid; place-items: center; width: 32px; height: 34px; border-radius: 9px; color: var(--brand-blue); background: #eff6ff; flex-shrink: 0; }
.material-head>a { color: var(--text-main); font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }.material-head>a:hover { color: var(--brand-blue); }.material-head .open-link { flex: 0 0 auto; color: var(--text-secondary); }
.el-image, video { display: block; width: 100%; height: 220px; background: #f3f4f6; }.document-hint { margin: 0; padding: 0 12px 14px; color: var(--text-secondary); font-size: 12px; }.preview-error { display: grid; place-items: center; height: 100%; font-size: 12px; color: var(--text-secondary); }
</style>
