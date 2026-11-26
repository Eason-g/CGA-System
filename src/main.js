import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './services/http'
import { useAppStore } from './stores/appStore'
import './styles/tokens.css'
import './styles/fluent.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// token 失效（后端返回 401）时清除登录状态并回到登录页
setUnauthorizedHandler(() => {
  const store = useAppStore()
  store.clearSession()
  if (router.currentRoute.value.name !== 'Login') {
    store.notify('登录已失效，请重新登录', 'caution')
    router.push('/login')
  }
})
app.mount('#app')
