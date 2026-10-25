/**
 * VuePress 与 Plume 的基础配置。
 */

import { viteBundler } from '@vuepress/bundler-vite'
import { defineUserConfig } from 'vuepress'
import { plumeTheme } from 'vuepress-theme-plume'

export default defineUserConfig({
  base: '/',
  lang: 'zh-CN',
  locales: {
    '/': {
      title: 'FinaSU',
      lang: 'zh-CN',
      description: '基于内核的 Android Root 方案',
    },
    '/en/': {
      title: 'FinaSU',
      lang: 'en-US',
      description: 'A kernel-based Root solution for Android',
    },
  },

  head: [
    ['link', { rel: 'icon', type: 'image/png', href: '/icon.png' }],
  ],

  bundler: viteBundler(),
  shouldPrefetch: false,

  docsRepo: 'https://github.com/LyraVoid/FinaSU',
  docsDir: 'website',
  docsBranch: 'refactor/docs',

  theme: plumeTheme({
    /* 版权信息 */
    copyright: true,

    /**
     * 编译缓存。
     */
    cache: 'filesystem',

    search: { provider: 'local' },

    llmstxt: {
      domain: 'https://fina.mysqil.com',
      locale: 'all',
      llmsTxtTemplateGetter: {
        alternateLinks: (_pages, state) =>
          state.currentLocale === '/'
            ? `\n\n## Alternate Language Versions\n\n- [en-US](${state.domain}/en/llms.txt)\n\n`
            : '',
      },
    },

    codeHighlighter: {
      twoslash: false,
      whitespace: false,
      lineNumbers: true,
    },

    markdown: {
      collapse: true,
      table: true,
      mermaid: true,
      codeTree: true,
      fileTree: true,
      codeTabs: { icon: true },
      field: true,
      timeline: true,
      abbr: true,
      annotation: true,
      mark: 'eager',
      icon: { provider: 'iconify' },
      caniuse: true,
      qrcode: true,
      chat: true,
    },
  }),
})
