// Development-only fixture: deliberately missing media verifies that cards do not fetch eagerly.
import { createApp, h, ref, onMounted, onBeforeUnmount } from 'vue'
import '../src/styles/global.css'
import VideoPlayer from '../src/components/VideoPlayer.vue'
import RichContent from '../src/components/RichContent.vue'
createApp({setup() {
  const diagnostic=ref(''), narrow=ref(false), reset=ref(0)
  let timer
  onMounted(() => { timer=setInterval(() => {
    const count=performance.getEntriesByType('resource').filter(x=>x.name.includes('lazy-video-check')).length
    diagnostic.value=`原生 video 节点：${document.querySelectorAll('video').length}；视频请求：${count}`
  },250) })
  onBeforeUnmount(()=>clearInterval(timer))
  return ()=>h('main',{style:{maxWidth:narrow.value?'360px':'880px',margin:'24px auto',padding:'16px'}},[
    h('h1','视频按需加载验证'),h('p',diagnostic.value),
    h('button',{class:'pill',onClick:()=>narrow.value=!narrow.value},'切换窄屏'),
    h('button',{class:'pill',onClick:()=>reset.value++},'重建播放器'),
    h('h2','附件视频'),h(VideoPlayer,{key:reset.value,src:'/uploads/lazy-video-check.mp4'}),
    h('h2','富文本视频'),h(RichContent,{key:'rich-'+reset.value,html:'<p>此处使用真实富文本组件。</p><video controls preload="auto" src="/uploads/lazy-video-check-rich.mp4"></video>'})
  ])
}}).mount('#app')
