<template>
  <TopProgressBar />
  <router-view />
  <ToastContainer />
  <ArchiveModal
    v-if="store.archiveContractId"
    :contract-id="store.archiveContractId"
    @close="store.closeArchive()"
  />
</template>

<script setup>
import { useAppStore } from '@/stores/appStore'
import TopProgressBar from '@/components/TopProgressBar.vue'
import ToastContainer from '@/components/ToastContainer.vue'
import ArchiveModal from '@/components/ArchiveModal.vue'

const store = useAppStore()
</script>
