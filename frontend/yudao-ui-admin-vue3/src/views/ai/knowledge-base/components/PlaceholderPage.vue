<template>
  <ContentWrap>
    <div class="py-40px text-center">
      <div class="mb-12px text-18px font-600">{{ title }}</div>
      <el-empty :description="description" />
      <div class="mt-12px text-13px text-gray-500">权限标识：{{ permission }}</div>
    </div>
  </ContentWrap>
</template>

<script setup lang="ts">
defineOptions({ name: 'AiKnowledgePlaceholderPage' })

defineProps<{
  title: string
  description: string
  permission: string
}>()
</script>
