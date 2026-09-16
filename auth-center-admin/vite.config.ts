import { defineConfig, type ProxyOptions } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

/**
 * 转发前摘掉浏览器的 Origin 头。
 *
 * 对浏览器而言 8091 → 8091 本就是同源(请求由本 dev server 代理),但代理默认把 Origin 原样带给上游,于是网关按跨域处理:
 * 它的 `bi.cors.allowed-origins` 只有生产域名,任何带 Origin: localhost:8091 的请求一律 403(空 body),表现是登录页
 * 报「登录失败,请检查用户名和密码」—— 与凭据无关。
 *
 * 摘掉 Origin 后上游视其为同源调用,放行;浏览器这侧本来就不需要 CORS 响应头。这样既不必为一个开发端口去放宽生产网关的
 * CORS,也不必回退到手工 port-forward。
 *
 * 注意 `changeOrigin` 改的是 Host 头,与本函数无关,两者都要。
 */
const stripOrigin: ProxyOptions['configure'] = (proxy) => {
  proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
}

export default defineConfig({
  plugins: [vue()],
  base: '/admin/',
  define: {
    // vue-i18n 生产构建必须显式定义这些打包器特性开关。缺失时 dev(esbuild)正常,
    // 但 prod(rollup + 压缩)会把消息编译器 tree-shake 掉 → 运行时无法编译/解析消息 →
    // 所有翻译显示成原始 key。与 bi-front 同款:DROP_MESSAGE_COMPILER=false 保留编译器,
    // JIT_COMPILATION=true 启用运行时即时编译。共享 i18n 工厂用 globalInjection,需此保障。
    __VUE_I18N_FULL_INSTALL__: true,
    __VUE_I18N_LEGACY_API__: false,
    __INTLIFY_JIT_COMPILATION__: true,
    __INTLIFY_DROP_MESSAGE_COMPILER__: false,
    __INTLIFY_PROD_DEVTOOLS__: false,
  },
  resolve: {
    // shared-ui 以源码消费,其裸依赖(vue / vue-i18n 等)在打包期统一解析到本 app 的副本:
    // 既避免双实例,也让共享库源码里的裸 import 能解析到本 app 自身的 node_modules。
    // 与 bi-front / agent-console 同款 dedupe 清单保持一致。
    dedupe: ['vue', 'vue-i18n', 'vue-router', 'pinia', 'axios', 'ant-design-vue', '@ant-design/icons-vue'],
    alias: {
      '@': resolve(__dirname, 'src'),
      '@loom/shared-ui': resolve(__dirname, '../../libs/shared-ui/src'),
    },
  },
  server: {
    port: 8091,
    proxy: {
      // 打集群 ingress(k3d 把宿主机 80 映射到它),而不是 localhost:8090。
      //
      // 后者要求手工开着 `kubectl port-forward svc/auth-center 8090:8090` —— 那是宿主机上的一个独立进程,
      // pod 一重建、Docker 一重启、机器一休眠它就退且不重连,现象是本页所有 /api/auth/* 变成
      // `500 text/plain 空 body`(Vite 对上游 ECONNREFUSED 的兜底,不是后端抛的),极易误判成认证服务故障。
      //
      // 路由:/api → bi-gateway → auth-center;/.well-known 与 /cas 由 ingress 直连 auth-center。
      // 仅影响 dev server;生产是 auth-center 自己服务这个 SPA(同源,不经代理)。
      // 目标写 127.0.0.1 而非 localhost:Node 17+ 的 DNS 解析默认 verbatim,`localhost` 会先试 ::1,
      // 而 k3d 的 80 端口映射只有 IPv4 通 → 代理连不上上游,同样兜底成 500 空 body。curl 偏好 IPv4,
      // 于是表现为「命令行好好的、浏览器全 500」。
      '/api/auth': { target: 'http://127.0.0.1', changeOrigin: true, configure: stripOrigin },
      // JWKS 公钥集(外部应用与密钥页查看用)
      '/.well-known': { target: 'http://127.0.0.1', changeOrigin: true, configure: stripOrigin },
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
})
