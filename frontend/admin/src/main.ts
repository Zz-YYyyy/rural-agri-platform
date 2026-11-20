import { createApp } from 'vue'
import 'element-plus/dist/index.css'
import './styles.css'
import App from './App.vue'
import { pinia } from './stores'
import { router } from './router'

createApp(App).use(pinia).use(router).mount('#app')
