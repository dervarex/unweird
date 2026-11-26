import { defineConfig } from 'vitepress'

const examples = [
  ['counter', 'Counter'],
  ['todo', 'Todo list'],
  ['signup', 'Form validation'],
  ['login', 'Login and header'],
  ['navigation', 'Sidebar navigation'],
  ['settings', 'Settings and themes'],
  ['search', 'Async search'],
  ['contacts', 'Master / detail'],
  ['dialogs', 'Dialogs and toasts'],
  ['stopwatch', 'Timers and shortcuts'],
  ['files', 'Dropping files'],
  ['rating', 'Custom widget'],
  ['chart', 'Custom drawing'],
  ['cube', '3D view'],
]

export default defineConfig({
  title: 'unweird',
  description: 'A Java UI library with its own renderer.',
  base: '/unweird/',
  lang: 'en',
  appearance: 'force-dark',
  cleanUrls: true,
  lastUpdated: false,
  srcExclude: ['README.md'],
  head: [
    ['link', { rel: 'icon', href: '/unweird/logo.png' }],
    ['meta', { name: 'theme-color', content: '#111114' }],
  ],
  markdown: {
    theme: 'github-dark',
    lineNumbers: false,
  },
  themeConfig: {
    logo: '/logo.png',
    nav: [
      { text: 'Guide', link: '/', activeMatch: '^/(?!examples|snippets)' },
      { text: 'Examples', link: '/examples/', activeMatch: '^/examples' },
      { text: 'Snippets', link: '/snippets', activeMatch: '^/snippets' },
    ],
    sidebar: {
      '/examples/': [
        { text: 'All examples', link: '/examples/' },
        { text: 'Apps', items: examples.map(([id, text]) => ({ text, link: `/examples/${id}` })) },
      ],
      '/': [
        {
          text: 'Basics',
          items: [
            { text: 'Getting started', link: '/' },
            { text: 'Screens and components', link: '/screens-and-components' },
            { text: 'Layout', link: '/layout' },
            { text: 'Widgets', link: '/widgets' },
            { text: 'State and events', link: '/state-and-events' },
            { text: 'Dialogs and toasts', link: '/dialogs-and-toasts' },
            { text: 'Styling', link: '/styling' },
          ],
        },
        {
          text: 'Going further',
          items: [
            { text: 'Your own widgets', link: '/custom-widgets' },
            { text: '3D', link: '/3d' },
            { text: 'Installing content', link: '/installing-content' },
          ],
        },
        {
          text: 'Platforms and tools',
          items: [
            { text: 'Android', link: '/android' },
            { text: 'Desktop + Android', link: '/multiplatform' },
            { text: 'IntelliJ plugin', link: '/intellij' },
            { text: 'Troubleshooting', link: '/troubleshooting' },
          ],
        },
        { text: 'Snippets', link: '/snippets' },
      ],
    },
    socialLinks: [{ icon: 'github', link: 'https://github.com/dervarex/unweird' }],
    search: { provider: 'local' },
    outline: { level: [2, 3], label: 'On this page' },
    docFooter: { prev: false, next: false },
    footer: undefined,
  },
})
