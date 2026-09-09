<template>
  <div class="ans">
    <div class="body" v-html="render(data.answer || raw)"></div>
    <div v-if="data.keyPoints?.length" class="points">
      <div class="sec-title">关键要点</div>
      <ul><li v-for="k in data.keyPoints" :key="k">{{ k }}</li></ul>
    </div>
    <div v-if="data.steps?.length" class="steps">
      <div class="sec-title">操作步骤</div>
      <ol><li v-for="s in data.steps" :key="s">{{ s }}</li></ol>
    </div>
    <div v-if="data.related?.length" class="related">
      <div class="sec-title">站内相关</div>
      <div class="rel-list">
        <a v-for="(r, i) in data.related" :key="i" class="rel" @click="open(r)">
          <span class="rel-type">{{ r.type === 'equipment' ? '设备' : '项目' }}</span>
          <span class="rel-title">{{ r.title }}</span>
          <span class="rel-why">{{ r.why }}</span>
        </a>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

defineProps({ data: { type: Object, default: () => ({}) }, raw: { type: String, default: '' } })
const router = useRouter()
const render = (text) => {
  if (!text) return ''
  const esc = String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return esc
    .replace(/```([\s\S]*?)```/g, (m, code) => `<pre>${code.trim()}</pre>`)
    .replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>').replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/^#{1,4} (.*)$/gm, '<h4>$1</h4>').replace(/^[-*] (.*)$/gm, '<li>$1</li>').replace(/(<li>.*<\/li>\n?)+/g, (m) => `<ul>${m}</ul>`)
    .replace(/^(\d+)\. (.*)$/gm, '<div class="ol"><i>$1.</i>$2</div>').replace(/\n{2,}/g, '<br/><br/>').replace(/\n/g, '<br/>')
}
const open = (r) => (r.type === 'equipment' ? router.push({ path: '/app/equipment', query: { keyword: r.title } }) : router.push(`/app/projects/${r.id}`))
</script>

<style scoped>
.body { font-size: 14.5px; line-height: 1.85; color: #111827; }
.body :deep(code) { background: #f3f4f6; padding: 0 5px; border-radius: 4px; font-size: 13px; }
.body :deep(pre) { background: #0f172a; color: #e2e8f0; padding: 12px 14px; border-radius: 10px; font-size: 12.5px; overflow-x: auto; }
.body :deep(h4) { margin: 10px 0 4px; font-size: 15px; }
.body :deep(ul) { margin: 4px 0; padding-left: 20px; }
.body :deep(.ol) { display: flex; gap: 6px; } .body :deep(.ol i) { font-style: normal; color: #6366f1; font-weight: 700; }
.sec-title { font-size: 13px; font-weight: 700; color: #374151; margin-bottom: 6px; }
.points { margin-top: 16px; padding: 12px 16px; background: #eef2ff; border-radius: 12px; }
.points ul { margin: 0; padding-left: 18px; font-size: 13.5px; color: #312e81; line-height: 1.8; }
.steps { margin-top: 14px; }
.steps ol { margin: 0; padding-left: 20px; font-size: 13.5px; color: #374151; line-height: 1.8; }
.related { margin-top: 16px; }
.rel-list { display: flex; flex-direction: column; gap: 6px; }
.rel { display: flex; align-items: center; gap: 10px; padding: 8px 12px; border: 1px solid var(--border); border-radius: 10px; cursor: pointer; font-size: 13px; }
.rel:hover { background: #f9fafb; }
.rel-type { font-size: 11px; background: #f3f4f6; padding: 2px 6px; border-radius: 4px; color: #6b7280; }
.rel-title { font-weight: 600; }
.rel-why { color: #9ca3af; margin-left: auto; font-size: 12px; }
</style>
