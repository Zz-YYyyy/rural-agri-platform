import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { defineConfig, loadEnv } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const wechatAppId = env.VITE_WECHAT_APP_ID?.trim()
  if (wechatAppId && !/^wx[a-f\d]{16}$/i.test(wechatAppId)) {
    throw new Error('VITE_WECHAT_APP_ID must be a valid WeChat mini-program AppID.')
  }

  return {
    plugins: [
      uni(),
      {
        name: 'local-wechat-appid',
        // Keep local identity out of the tracked manifest; only update build output.
        writeBundle() {
          if (process.env.UNI_PLATFORM !== 'mp-weixin' || !wechatAppId) return
          const outputDir = process.env.UNI_OUTPUT_DIR
          if (!outputDir) throw new Error('WeChat output directory is missing.')
          const configPath = resolve(outputDir, 'project.config.json')
          const config = JSON.parse(readFileSync(configPath, 'utf8'))
          config.appid = wechatAppId
          writeFileSync(configPath, JSON.stringify(config, null, 2) + '\n', 'utf8')
        },
      },
    ],
    server: {
      host: '127.0.0.1',
      port: 5174,
      strictPort: true,
      proxy: { '/api': { target: env.VITE_DEV_PROXY_TARGET || 'http://127.0.0.1:8080' } },
    },
  }
})
