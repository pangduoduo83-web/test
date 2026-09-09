<template>
  <ClassManager mode="admin" :teachers="teachers" />
</template>

<script setup>
import { onMounted, ref } from 'vue'
import ClassManager from '../../components/ClassManager.vue'
import { adminListUsers } from '../../api'

const teachers = ref([])
onMounted(async () => {
  const users = await adminListUsers()
  teachers.value = users.filter((u) => u.role === 'TEACHER' || u.role === 'ADMIN')
})
</script>
