<template>
  <view class="files">
    <view v-if="editable" class="upload-panel"><view class="upload-title"><uni-icons type="cloud-upload" size="21" color="#2563eb" /><text>添加成果材料</text><text class="file-count">{{ modelValue.length }}/8</text></view><view class="buttons"><button :disabled="busy || disabled" @click="choose('image')"><uni-icons type="image" size="20" color="#2563eb" /><text>图片</text></button><button :disabled="busy || disabled" @click="choose('video')"><uni-icons type="videocam" size="20" color="#9333ea" /><text>视频</text></button><button :disabled="busy || disabled" @click="choose('document')"><uni-icons type="paperclip" size="20" color="#2563eb" /><text>文档 / 源码</text></button></view><text class="hint">单个100MB、合计200MB；视频5分钟以内；支持源码与压缩包</text></view>
    <view v-for="(file, i) in modelValue" :key="file.url" class="file">
      <image v-if="isImage(file.url)" :src="fullUrl(file.url)" mode="widthFix" @click="open(file)" />
      <VideoPlayer v-else-if="/\.(mp4|mov|webm)$/i.test(file.url)" :src="file.url" />
      <view class="file-caption"><view class="file-label" @click="open(file)"><uni-icons type="paperclip" size="17" color="#2563eb" /><text>{{ file.name || '成果附件' }}</text></view><text v-if="editable && !disabled && !busy" class="remove" @click="$emit('update:modelValue', modelValue.filter((_, index) => index !== i))">移除</text></view>
    </view>
    <view v-if="busy" class="uploading"><uni-icons type="spinner-cycle" size="18" color="#2563eb" /><text>正在上传，请稍候…</text></view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import VideoPlayer from './VideoPlayer.vue'
import { fullUrl } from '@/config'
import { uploadSubmissionFile } from '@/utils/request'
const props = defineProps({ modelValue: { type: Array, default: () => [] }, editable: Boolean, disabled: Boolean })
const emit = defineEmits(['update:modelValue', 'busy'])
const busy = ref(false)
const isImage = url => /\.(png|jpe?g|webp|gif)$/i.test(url || '')
const upload = async files => {
  if (busy.value || props.disabled) return
  busy.value = true; emit('busy', true)
  let next = [...props.modelValue]
  try {
    for (const file of files) {
      if (next.length >= 8 || file.size > 100 * 1024 * 1024 || next.reduce((n, f) => n + Number(f.size || 0), 0) + file.size > 200 * 1024 * 1024) {
        uni.showToast({ title: '文件数量或大小超出限制', icon: 'none' }); break
      }
      const data = await uploadSubmissionFile(file.path, file)
      next.push({ ...data, name: file.name || data.name, size: file.size })
      emit('update:modelValue', [...next])
    }
  } catch { uni.showToast({ title: '上传未完成，请重新选择失败的文件', icon: 'none' }) }
  finally { busy.value = false; emit('busy', false) }
}
const choose = type => {
  if (type === 'image') uni.chooseImage({ count: Math.max(1, 8 - props.modelValue.length), sizeType: ['compressed'], success: res => upload(res.tempFiles.map(file => ({ ...file, mediaType: 'image' }))) })
  else if (type === 'video') uni.chooseVideo({ maxDuration: 300, compressed: true, success: res => upload([{ path: res.tempFilePath, size: res.size, mediaType: 'video' }]) })
  else {
    // #ifdef MP-WEIXIN
    uni.chooseMessageFile({ count: Math.max(1, 8 - props.modelValue.length), type: 'file', extension: ['pdf', 'doc', 'docx', 'zip', 'rar', '7z', 'c', 'h', 'cpp', 'java', 'py', 'js', 'ts', 'vue', 'html', 'css', 'sql', 'json', 'xml', 'yaml', 'yml', 'md', 'txt', 'csv', 'ino', 'kicad_sch', 'kicad_pcb'], success: res => upload(res.tempFiles) })
    // #endif
    // #ifdef H5
    uni.chooseFile({ count: Math.max(1, 8 - props.modelValue.length), extension: ['.pdf', '.doc', '.docx', '.zip', '.rar', '.7z', '.c', '.h', '.cpp', '.java', '.py', '.js', '.ts', '.vue', '.html', '.css', '.sql', '.json', '.xml', '.yaml', '.yml', '.md', '.txt', '.csv', '.ino', '.kicad_sch', '.kicad_pcb'], success: res => upload(res.tempFiles) })
    // #endif
  }
}
const open = file => {
  if (isImage(file.url)) return uni.previewImage({ urls: [fullUrl(file.url)] })
  if (/\.(mp4|mov|webm)$/i.test(file.url)) return
  uni.downloadFile({ url: fullUrl(file.url), success: res => {
    if (res.statusCode === 200) uni.openDocument({ filePath: res.tempFilePath, showMenu: true })
  } })
}
</script>
<style scoped lang="scss">
.files { margin: 20rpx 0; }.upload-panel { border: 2rpx dashed $border-color; border-radius: 24rpx; padding: 24rpx; background: linear-gradient(135deg,#f8fbff,#fdfaff); }.upload-title { display: flex; align-items: center; gap: 12rpx; font-size: 27rpx; font-weight: 600; }.file-count { margin-left: auto; font-size: 23rpx; font-weight: 400; color: $text-sub; }
.buttons { display: flex; gap: 12rpx; margin-top: 20rpx; }.buttons button { flex: 1; min-width: 0; margin: 0; padding: 16rpx 4rpx; line-height: 1.6; font-size: 23rpx; background: #fff; border-radius: 16rpx; border: 1rpx solid $border-color; display: flex; flex-direction: column; align-items: center; gap: 5rpx; }.buttons button::after { border: none; }.hint { display: block; color: $text-sub; font-size: 22rpx; margin-top: 16rpx; }
.file { margin: 16rpx 0; font-size: 26rpx; border: 1rpx solid $border-color; border-radius: 20rpx; overflow: hidden; background: #fff; }.file image, .file video { display: block; width: 100%; max-width: 100%; }.file-caption { display: flex; align-items: center; padding: 20rpx; gap: 12rpx; }.file-label { flex: 1; min-width: 0; display: flex; align-items: center; gap: 10rpx; }.file-label text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.remove { color: $red; padding: 8rpx; font-size: 23rpx; }.uploading { display: flex; align-items: center; gap: 10rpx; color: $brand-blue; font-size: 24rpx; padding: 16rpx 4rpx; }
</style>
