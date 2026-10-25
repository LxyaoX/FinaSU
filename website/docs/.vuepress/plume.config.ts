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
