declare module 'vuepress-theme-plume/features/*.vue' {
  import type { DefineComponent } from 'vue'

  const component: DefineComponent<Record<string, unknown>>
  export default component
}

declare module '*.css'
