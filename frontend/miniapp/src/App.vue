<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { restoreSession } from '@/services/session'

onLaunch(() => { void restoreSession() })
</script>

<style>
@import './styles/global.css';
</style>
