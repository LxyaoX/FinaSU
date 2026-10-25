/**
 * Plume 主题配置。
 */

import { defineThemeConfig } from 'vuepress-theme-plume'
import { docsSidebar, enCollections, enDocsSidebar, zhCollections } from './collections'
import { enNavbar, zhNavbar } from './navbar'

export default defineThemeConfig({
  logo: '/icon.png',

  social: [
    { icon: 'github', link: 'https://github.com/LyraVoid/FinaSU' },
    { icon: 'telegram', link: 'https://t.me/FinaSU' },
  ],

  appearance: true,

  locales: {
    '/': {
      profile: {
        avatar: '/icon.png',
        name: 'FinaSU',
        description: '基于内核的 Android Root 方案',
      },

      selectLanguageName: '简体中文',

      copyPageText: '复制页面',
      copiedPageText: '已复制',
      copingPageText: '复制中…',
      copyTagline: '复制为 Markdown，供 LLM 使用',
      viewMarkdown: '查看 Markdown',
      viewMarkdownTagline: '以纯文本查看此页',
      askAIText: '在 {name} 中打开',
      askAITagline: '向 {name} 询问此页',
      askAIMessage: '阅读 {link}，并回答与页面内容相关的问题。',

      navbar: zhNavbar,
      sidebar: { '/': docsSidebar },
      collections: zhCollections,
    },

    '/en/': {
      profile: {
        avatar: '/icon.png',
        name: 'FinaSU',
        description: 'A kernel-based Root solution for Android',
      },

      selectLanguageName: 'English',

      copyPageText: 'Copy page',
      copiedPageText: 'Copied',
      copingPageText: 'Copying…',
      copyTagline: 'Copy page as Markdown for LLMs',
      viewMarkdown: 'View Markdown',
      viewMarkdownTagline: 'View this page as plain text',
      askAIText: 'Open in {name}',
      askAITagline: 'Ask {name} about this page',
      askAIMessage: 'Read {link} and answer questions about this page.',

      navbar: enNavbar,
      sidebar: { '/': enDocsSidebar },
      collections: enCollections,
    },
  },

  footer: {
    message: false,
    copyright: 'Copyright © 2026-present LyraVoid/FinaSU',
  },

  transition: {
    page: false,
    appearance: 'fade',
  },
})
