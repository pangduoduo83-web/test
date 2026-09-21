<template><div ref="root"></div></template>
<script setup>
import { ref, watch, onMounted, onBeforeUnmount, h, render, getCurrentInstance } from 'vue'
import VideoPlayer from './VideoPlayer.vue'
// HTML is sanitized by the existing server-side rich-text sanitizer (or safeHtml at the caller).
const props = defineProps({ html: { type: String, default: '' } })
const root = ref(null)
const context = getCurrentInstance().appContext
let players = []
function clear() { players.forEach(element => render(null, element)); players = [] }
function update() {
  if (!root.value) return
  clear()
  // Parse in an inert template so video/audio sources never start fetching before replacement.
  const template = document.createElement('template')
  template.innerHTML = props.html || ''
  const pending = []
  template.content.querySelectorAll('video').forEach(element => {
    const src = element.getAttribute('src') || element.querySelector('source')?.getAttribute('src')
    const host = document.createElement('div')
    element.replaceWith(host)
    if (src && (/^\/(uploads|hub-assets|api\/public\/store-assets)\//.test(src) || /^https?:\/\//i.test(src))) pending.push({ host, src })
  })
  template.content.querySelectorAll('img').forEach(element => { element.loading = 'lazy'; element.decoding = 'async' })
  root.value.replaceChildren(template.content)
  pending.forEach(({ host, src }) => {
    const node = h(VideoPlayer, { src })
    node.appContext = context
    render(node, host)
    players.push(host)
  })
}
onMounted(update)
watch(() => props.html, update)
onBeforeUnmount(clear)
</script>
