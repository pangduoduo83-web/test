<template>
  <div class="rich-editor">
    <Toolbar class="re-toolbar" :editor="editorRef" :default-config="toolbarConfig" mode="simple" />
    <Editor
      class="re-body"
      :default-config="editorConfig"
      mode="simple"
      :model-value="modelValue"
      @on-created="onCreated"
      @on-change="onChange"
    />
  </div>
</template>

<script setup>
import { onBeforeUnmount, shallowRef, watch } from 'vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import { ElMessage } from 'element-plus'
import { uploadDocFile, uploadImage } from '../api'
import '@wangeditor/editor/dist/css/style.css'

const props = defineProps({ modelValue: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue'])

const editorRef = shallowRef(null)
let ready = false

const toolbarConfig = {
  toolbarKeys: [
    'headerSelect', 'bold', 'italic', 'underline', 'color', 'bgColor', '|',
    'bulletedList', 'numberedList', 'insertLink', 'insertTable', 'divider', '|',
    'uploadImage', 'uploadVideo', '|', 'undo', 'redo', 'fullScreen'
  ]
}

const editorConfig = {
  placeholder: '请输入详细描述,支持插入图片与视频...',
  MENU_CONF: {
    uploadImage: {
      async customUpload(file, insertFn) {
        if (file.size > 30 * 1024 * 1024) {
          ElMessage.warning('图片不能超过 30MB')
          return
        }
        try {
          const { url } = await uploadImage(file)
          insertFn(url, file.name, url)
        } catch (e) { /* 已提示 */ }
      }
    },
    uploadVideo: {
      async customUpload(file, insertFn) {
        if (file.size > 30 * 1024 * 1024) {
          ElMessage.warning('视频不能超过 30MB,建议压缩后上传')
          return
        }
        try {
          const { url } = await uploadDocFile(file)
          insertFn(url)
        } catch (e) { /* 已提示 */ }
      }
    }
  }
}

const onCreated = (editor) => {
  editorRef.value = editor
  const html = props.modelValue || ''
  if (html) editor.setHtml(html)
  ready = true
}

const onChange = (editor) => {
  if (!ready) return
  const html = editor.getHtml()
  const next = html === '<p><br></p>' ? '' : html
  if (next !== (props.modelValue || '')) emit('update:modelValue', next)
}

watch(() => props.modelValue, (v) => {
  const editor = editorRef.value
  if (!editor || !ready) return
  const next = v || ''
  const cur = editor.getHtml() === '<p><br></p>' ? '' : editor.getHtml()
  if (cur !== next) editor.setHtml(next)
})

onBeforeUnmount(() => {
  editorRef.value?.destroy()
})
</script>

<style scoped>
.rich-editor {
  border: 1px solid var(--border);
  border-radius: 10px;
  overflow: hidden;
  width: 100%;
  z-index: 10;
}
.re-toolbar { border-bottom: 1px solid var(--border); }
.re-body { height: 320px; overflow-y: hidden; }
:deep(.w-e-text-container) { min-height: 260px; }
</style>
