<template>
    <TeacherComposeDialog v-model="visible" :title="reminderStudent ? '提醒学生' : '需要关注的学生'"
                          :description="reminderStudent ? '给学生一条具体的学习建议，帮助 TA 继续推进项目。' : '以下学生已过截止、长时间没有学习动作，或项目进度明显落后。可以直接发送站内提醒。'"
                          :busy="reminding" confirm-text="发送提醒" @submit="sendReminder" @closed="resetReminder">
      <template v-if="reminderStudent">
        <button type="button" class="compose-back" :disabled="reminding" @click="resetReminder"><ArrowLeft :size="16" />返回学生名单</button>
        <div class="compose-context">
          <span class="recipient-avatar">{{ (reminderStudent.studentName || '学')[0] }}</span>
          <div class="compose-context-copy">
            <strong>{{ reminderStudent.studentName }}<small>{{ reminderStudent.studentNo || '未填写学号' }}</small></strong>
            <p>{{ reminderStudent.projectTitle }}</p>
            <div class="risk-reasons"><span v-for="reason in reminderStudent.reasons" :key="reason">{{ reason }}</span></div>
          </div>
          <span class="recipient-progress">当前进度 <b>{{ reminderStudent.progress || 0 }}%</b></span>
        </div>
        <el-form label-position="top" @submit.prevent="sendReminder">
          <el-form-item label="提醒内容" for="teacher-reminder-message" required :error="reminderError">
            <el-input id="teacher-reminder-message" ref="reminderInput" v-model="reminderMessage" type="textarea" :rows="8"
                      :disabled="reminding" placeholder="写下希望学生完成的任务、建议的时间，以及可以获得的帮助…" @input="reminderError = ''" />
          </el-form-item>
        </el-form>
      </template>
      <div v-else class="risk-dialog-list">
        <div v-for="(r, i) in students" :key="i" class="risk-dialog-item">
          <div class="risk-dialog-main">
            <strong>{{ r.studentName }}</strong>
            <span>{{ r.studentNo || '未填写学号' }} · {{ r.projectTitle }}</span>
            <div class="risk-reasons"><span v-for="reason in r.reasons" :key="reason">{{ reason }}</span></div>
          </div>
          <div class="risk-dialog-progress">进度 {{ r.progress || 0 }}%</div>
          <el-button type="primary" plain @click="nudge(r)">提醒 TA</el-button>
        </div>
        <el-empty v-if="!students.length" :description="loaded ? '当前没有需要关注的学生' : '风险数据未能加载，请重试'" />
      </div>
      <template #footer-note>通过站内通知发送给 {{ reminderStudent?.studentName }}</template>
      <template v-if="!reminderStudent" #footer><el-button @click="visible = false">关闭</el-button></template>
    </TeacherComposeDialog>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from 'lucide-vue-next'
import { teacherRemindStudent } from '../api'
import TeacherComposeDialog from './TeacherComposeDialog.vue'

const props = defineProps({
  modelValue: Boolean,
  students: { type: Array, default: () => [] },
  loaded: Boolean,
  initialStudent: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const reminderStudent = ref(null)
const reminderMessage = ref('')
const reminderError = ref('')
const reminderInput = ref(null)
const reminding = ref(false)
const nudge = async (row) => {
  reminderStudent.value = row
  reminderMessage.value = '《' + row.projectTitle + '》' + (row.currentTask ? '当前任务「' + row.currentTask + '」' : '') + '进度有些落后了，这周抽时间推进一下，有困难随时在讨论区问我或找 AI 导师。'
  reminderError.value = ''
  await nextTick()
  reminderInput.value?.focus()
}
const resetReminder = () => {
  reminderStudent.value = null
  reminderMessage.value = ''
  reminderError.value = ''
}
const sendReminder = async () => {
  if (reminding.value || !reminderStudent.value) return
  const message = reminderMessage.value.trim()
  if (!message) { reminderError.value = '提醒内容不能为空'; reminderInput.value?.focus(); return }
  const row = reminderStudent.value
  reminding.value = true
  reminderError.value = ''
  try {
    await teacherRemindStudent(row.projectId, row.userId, message)
    ElMessage.success('已发送提醒')
    resetReminder()
  } catch (e) {
    reminderError.value = '发送未成功，内容已保留，请稍后重试。'
  } finally { reminding.value = false }
}


watch(() => props.modelValue, (open) => {
  if (!open) return
  resetReminder()
  if (props.initialStudent) nudge(props.initialStudent)
}, { immediate: true })
</script>

<style scoped>
.risk-dialog-list { min-height: 100px; }
.dialog-tip { margin: 0 0 15px; color: #76839b; font-size: 13px; line-height: 1.7; }
.risk-dialog-item { display: flex; align-items: center; gap: 18px; padding: 18px 0; border-bottom: 1px solid #edf0f5; }
.risk-dialog-main { min-width: 0; flex: 1; }
.risk-dialog-main strong { color: #24365e; font-size: 16px; }
.risk-dialog-main > span { display: block; margin-top: 6px; color: #697792; font-size: 14px; line-height: 1.6; overflow-wrap: anywhere; }
.risk-reasons { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 7px; }
.risk-reasons span { padding: 3px 8px; border-radius: 5px; color: #d7464d; background: #fff0f1; font-size: 13px; line-height: 1.5; }
.risk-dialog-progress { color: #657592; font-size: 14px; white-space: nowrap; }
.compose-back { display: inline-flex; align-items: center; gap: 6px; margin: -8px 0 16px; padding: 6px 0; border: 0; color: #5269d3; background: transparent; font: inherit; font-size: 14px; cursor: pointer; }
.compose-back:hover { color: #354cc2; }
.compose-back:disabled { opacity: .5; cursor: wait; }
.compose-context { display: flex; align-items: flex-start; gap: 14px; margin-bottom: 24px; padding: 18px; border: 1px solid #e6ebf7; border-radius: 12px; background: #f7f9ff; }
.recipient-avatar { display: inline-grid; flex-shrink: 0; place-items: center; width: 44px; height: 44px; border-radius: 12px; color: #5265d8; background: #e9edff; font-size: 20px; font-weight: 600; }
.compose-context-copy { min-width: 0; flex: 1; overflow-wrap: anywhere; }
.compose-context-copy strong { display: flex; align-items: baseline; flex-wrap: wrap; gap: 6px 12px; color: #273653; font-size: 16px; line-height: 1.6; }
.compose-context-copy small { color: #74829b; font-size: 14px; font-weight: 400; }
.compose-context-copy p { margin: 4px 0 0; color: #697792; font-size: 14px; line-height: 1.7; }
.recipient-progress { flex-shrink: 0; padding-top: 4px; color: #697792; font-size: 14px; }
.recipient-progress b { margin-left: 5px; color: #d7464d; }

@media (max-width: 600px) {
  .risk-dialog-item { flex-wrap: wrap; gap: 12px; }
  .risk-dialog-main { flex-basis: 100%; }
  .risk-dialog-item > .el-button { margin-left: auto; }
  .compose-context { flex-wrap: wrap; padding: 14px; gap: 10px; }
  .recipient-progress { width: 100%; padding: 0 0 0 54px; }
}
</style>
