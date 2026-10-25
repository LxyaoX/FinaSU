/// <reference path="./features.d.ts" />

import { h } from 'vue'
import { defineClientConfig } from 'vuepress/client'
import { Layout } from 'vuepress-theme-plume/client'
import PageContextMenu from 'vuepress-theme-plume/features/PageContextMenu.vue'
import RepoCard from 'vuepress-theme-plume/features/RepoCard.vue'

import './theme/styles/custom.css'

export default defineClientConfig({
  enhance({ app }) {
    app.component('RepoCard', RepoCard)
  },
  layouts: {
    Layout: h(Layout, null, {
      'doc-title-after': () => h(PageContextMenu),
    }),
  },
})
