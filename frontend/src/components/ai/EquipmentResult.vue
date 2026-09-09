<template>
  <div>
    <p v-if="data.summary" class="lead">{{ data.summary }}</p>
    <div class="grid">
      <div v-for="(e, i) in data.items || []" :key="i" class="eq" :class="{ essential: e.essential, out: !e.available }">
        <div class="eq-head">
          <span class="eq-icon">🔧</span>
          <div class="grow">
            <div class="eq-name">{{ e.name }} <span v-if="e.essential" class="tag-must">必需</span></div>
            <div class="eq-meta">{{ e.category }}<span v-if="e.location"> · {{ e.location }}</span></div>
          </div>
          <span class="stock" :class="{ zero: !e.available }">{{ e.available }}<small>/{{ e.total }} 可借</small></span>
        </div>
        <div class="eq-reason">{{ e.reason }}</div>
        <div v-if="e.tips" class="eq-tips">💡 {{ e.tips }}</div>
        <div class="eq-actions">
          <el-button size="small" text type="primary" @click="$router.push({ path: '/app/equipment', query: { keyword: e.name } })">查看设备</el-button>
          <el-button size="small" type="primary" plain :disabled="!e.available" @click="$router.push({ path: '/app/equipment', query: { keyword: e.name, borrow: e.equipmentId } })">{{ e.available ? '去借用' : '已借完' }}</el-button>
        </div>
      </div>
    </div>
    <div v-if="data.steps?.length" class="sec">
      <div class="sec-title">实验要点</div>
      <ol><li v-for="s in data.steps" :key="s">{{ s }}</li></ol>
    </div>
    <div v-if="data.alternatives?.length" class="sec">
      <div class="sec-title">替代方案</div>
      <ul><li v-for="a in data.alternatives" :key="a">{{ a }}</li></ul>
    </div>
    <div v-if="data.safety" class="safety">⚠ {{ data.safety }}</div>
  </div>
</template>

<script setup>
defineProps({ data: { type: Object, required: true } })
</script>

<style scoped>
.lead { font-size: 15px; color: #111827; line-height: 1.7; margin: 0 0 16px; padding: 12px 16px; background: linear-gradient(90deg, #ecfeff, #fff); border-left: 3px solid #0891b2; border-radius: 0 10px 10px 0; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }
.grow { flex: 1; min-width: 0; }
.eq { border: 1px solid var(--border); border-radius: 14px; padding: 14px 16px; background: #fff; }
.eq.essential { border-color: #a5f3fc; background: linear-gradient(180deg, #ecfeff, #fff); }
.eq.out { opacity: .75; }
.eq-head { display: flex; align-items: center; gap: 10px; }
.eq-icon { width: 38px; height: 38px; border-radius: 10px; background: #e0f2fe; display: grid; place-items: center; font-size: 18px; }
.eq-name { font-weight: 700; font-size: 15px; display: flex; align-items: center; gap: 6px; }
.tag-must { font-size: 10px; background: #0891b2; color: #fff; padding: 1px 6px; border-radius: 4px; font-weight: 600; }
.eq-meta { font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
.stock { font-size: 20px; font-weight: 800; color: #0891b2; white-space: nowrap; }
.stock small { font-size: 11px; color: var(--text-secondary); font-weight: 500; }
.stock.zero { color: #dc2626; }
.eq-reason { font-size: 13px; color: #374151; margin-top: 10px; line-height: 1.6; }
.eq-tips { font-size: 12px; color: #92400e; background: #fffbeb; padding: 6px 10px; border-radius: 8px; margin-top: 8px; }
.eq-actions { display: flex; justify-content: flex-end; gap: 6px; margin-top: 10px; }
.sec { margin-top: 16px; }
.sec-title { font-size: 13px; font-weight: 700; color: #374151; margin-bottom: 6px; }
.sec ol, .sec ul { margin: 0; padding-left: 20px; font-size: 13px; color: #4b5563; line-height: 1.8; }
.safety { margin-top: 14px; font-size: 13px; color: #b91c1c; background: #fef2f2; padding: 8px 12px; border-radius: 8px; }
</style>
