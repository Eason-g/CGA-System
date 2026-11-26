import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler, setToken } from './services/http'
import { takeSsoTicketFromUrl, setPendingSsoTicket } from './services/integrationApi'
import { useAppStore } from './stores/appStore'
import './styles/tokens.css'
import './styles/fluent.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

// P2 B.1：统一身份认证回跳时地址为 /?ticket=ST-…#/…（ticket 在 hash 之前，vue-router 读不到），
// 在路由启动前取出并清掉地址栏里的 ticket；新的统一认证登录取代本地旧 token，登录页负责换票
const pendingSsoTicket = takeSsoTicketFromUrl()
if (pendingSsoTicket) {
  setToken(null)
  setPendingSsoTicket(pendingSsoTicket)
}
useAppStore().loadSite()

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
