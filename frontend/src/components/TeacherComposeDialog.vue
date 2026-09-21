<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="min(880px, calc(100vw - 32px))"
    class="teacher-compose-dialog"
    align-center
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :show-close="!busy"
    :before-close="beforeClose"
    @update:model-value="updateVisible"
    @opened="$emit('opened')"
    @closed="$emit('closed')"
  >
    <p v-if="description" class="compose-description">{{ description }}</p>
    <slot />
    <template #footer>
      <slot name="footer">
        <div class="compose-footer">
          <span class="compose-footer-note"><slot name="footer-note" /></span>
          <div class="compose-footer-actions">
            <el-button :disabled="busy" @click="updateVisible(false)">取消</el-button>
            <el-button type="primary" :loading="busy" @click="!busy && $emit('submit')">{{ confirmText }}</el-button>
          </div>
        </div>
      </slot>
    </template>
  </el-dialog>
</template>

<script setup>
const props = defineProps({
  modelValue: Boolean,
  title: { type: String, required: true },
  description: { type: String, default: '' },
  confirmText: { type: String, default: '保存' },
  busy: Boolean
})
const emit = defineEmits(['update:modelValue', 'submit', 'opened', 'closed'])
const updateVisible = (value) => { if (!props.busy) emit('update:modelValue', value) }
const beforeClose = (done) => { if (!props.busy) done() }
</script>

<style>
.teacher-compose-dialog.el-dialog {
  display: flex;
  flex-direction: column;
  max-height: calc(100dvh - 40px);
  padding: 0;
  overflow: hidden;
  border-radius: 18px;
  color: #273653;
  box-shadow: 0 24px 80px rgba(22, 38, 78, .2);
}
.teacher-compose-dialog .el-dialog__header {
  flex-shrink: 0;
  margin: 0;
  padding: 26px 64px 22px 32px;
  border-bottom: 1px solid #e9edf5;
}
.teacher-compose-dialog .el-dialog__title { color: #192950; font-size: 22px; font-weight: 700; line-height: 1.4; }
.teacher-compose-dialog .el-dialog__headerbtn { top: 14px; right: 16px; width: 44px; height: 44px; font-size: 22px; }
.teacher-compose-dialog .el-dialog__body { min-height: 0; padding: 24px 32px; overflow-y: auto; overscroll-behavior: contain; }
.teacher-compose-dialog .compose-description { margin: 0 0 22px; color: #697792; font-size: 15px; line-height: 1.75; overflow-wrap: anywhere; }
.teacher-compose-dialog .el-form-item { margin-bottom: 24px; }
.teacher-compose-dialog .el-form-item:last-child { margin-bottom: 0; }
.teacher-compose-dialog .el-form-item__label { height: auto; margin-bottom: 10px; color: #273653; font-size: 16px; font-weight: 600; line-height: 24px; }
.teacher-compose-dialog .el-form-item__error { position: static; padding-top: 8px; font-size: 14px; line-height: 1.5; }
.teacher-compose-dialog .el-input__wrapper { min-height: 46px; padding: 0 14px; border-radius: 10px; }
.teacher-compose-dialog .el-input__inner { font-size: 16px; }
.teacher-compose-dialog .el-textarea__inner {
  min-height: 240px !important;
  padding: 16px 18px 30px;
  border-radius: 12px;
  color: #273653;
  font-family: inherit;
  font-size: 16px;
  line-height: 1.8;
  resize: vertical;
}
.teacher-compose-dialog .el-input__count { font-size: 12px; }
.teacher-compose-dialog .el-textarea .el-input__count { right: 14px; bottom: 8px; }
.teacher-compose-dialog .el-dialog__footer { flex-shrink: 0; padding: 18px 32px; border-top: 1px solid #e9edf5; background: #fafbfe; }
.teacher-compose-dialog .compose-footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.teacher-compose-dialog .compose-footer-note { color: #8290a8; font-size: 13px; text-align: left; line-height: 1.6; }
.teacher-compose-dialog .compose-footer-actions { display: flex; flex-shrink: 0; gap: 12px; }
.teacher-compose-dialog .el-button { min-height: 44px; padding: 12px 22px; border-radius: 9px; font-size: 15px; }
.teacher-compose-dialog .compose-footer-actions .el-button { min-width: 96px; margin-left: 0; }
.teacher-compose-dialog .el-button--primary { --el-button-bg-color: #4a61ee; --el-button-border-color: #4a61ee; --el-button-hover-bg-color: #6276f5; --el-button-hover-border-color: #6276f5; --el-button-active-bg-color: #3f51d1; --el-button-active-border-color: #3f51d1; }
@media (max-width: 600px) {
  .teacher-compose-dialog.el-dialog { max-height: calc(100dvh - 24px); border-radius: 14px; }
  .teacher-compose-dialog .el-dialog__header { padding: 20px 54px 18px 20px; }
  .teacher-compose-dialog .el-dialog__title { font-size: 20px; }
  .teacher-compose-dialog .el-dialog__headerbtn { top: 8px; right: 8px; }
  .teacher-compose-dialog .el-dialog__body { padding: 20px; }
  .teacher-compose-dialog .el-textarea__inner { min-height: 220px !important; padding: 14px 14px 28px; }
  .teacher-compose-dialog .el-dialog__footer { padding: 16px 20px; }
  .teacher-compose-dialog .compose-footer { flex-wrap: wrap; gap: 10px; }
  .teacher-compose-dialog .compose-footer-actions { width: 100%; }
  .teacher-compose-dialog .compose-footer-actions .el-button { flex: 1; min-width: 0; }
}
</style>
