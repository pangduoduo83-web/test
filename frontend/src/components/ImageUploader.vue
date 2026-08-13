<template>
  <div class="uploader">
    <div v-if="modelValue" class="preview">
      <img :src="modelValue" alt="已上传图片" />
      <div class="preview-mask">
        <el-button size="small" @click="pick">更换</el-button>
        <el-button size="small" type="danger" @click="$emit('update:modelValue', '')">移除</el-button>
      </div>
    </div>
    <div v-else class="drop" @click="pick">
      <span class="drop-icon"><ImagePlus :size="26" color="#9ca3af" /></span>
      <span class="drop-text">{{ uploading ? '上传中...' : '点击上传图片' }}</span>
      <span class="drop-hint">支持 png / jpg / gif / webp,最大 30MB</span>
    </div>
    <input ref="inputRef" type="file" accept="image/*" class="hidden-input" @change="onChange" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ImagePlus } from 'lucide-vue-next'
import { uploadImage } from '../api'

defineProps({ modelValue: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue'])

const inputRef = ref(null)
const uploading = ref(false)

const pick = () => inputRef.value?.click()

const onChange = async (e) => {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (file.size > 30 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 30MB')
    return
  }
  uploading.value = true
  try {
    const { url } = await uploadImage(file)
    emit('update:modelValue', url)
    ElMessage.success('图片上传成功')
  } catch (err) { /* 错误已由拦截器提示 */ } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.uploader { width: 100%; }
.hidden-input { display: none; }

.drop {
  border: 2px dashed var(--border);
  border-radius: 12px;
  padding: 22px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  transition: border-color .15s, background .15s;
}
.drop:hover { border-color: var(--brand-blue); background: #eff6ff; }
.drop-icon { display: flex; }
.drop-text { font-size: 14px; color: #374151; }
.drop-hint { font-size: 12px; color: #9ca3af; }

.preview {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border);
}
.preview img { width: 100%; height: 160px; object-fit: cover; display: block; }
.preview-mask {
  position: absolute; inset: 0;
  background: rgba(0,0,0,.45);
  display: flex; align-items: center; justify-content: center; gap: 10px;
  opacity: 0;
  transition: opacity .15s;
}
.preview:hover .preview-mask { opacity: 1; }
</style>
