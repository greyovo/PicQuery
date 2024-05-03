import { defineConfig } from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: "PicQuery",
  description: "A PicQuery documentation site",
  base: '/picquery/',
  head: [
    ['link', { rel: 'icon', href: '/logo.webp' }],
  ],
  themeConfig: {
    logo: '/logo.png',
    footer: {
      message: 'Released under the MIT License, visit our <a href="/privacy-policy">Privacy Policy</a>.',
      copyright: 'Copyright © 2023-present <a href="https://github.com/greyovo">Grey Liu</a>.',
    },
    // https://vitepress.dev/reference/default-theme-config
    nav: [
      // { text: 'Privacy Policy', link: '/privacy-policy' },
      // { text: 'Examples', link: '/markdown-examples' }
    ],

    // sidebar: [
    //   {
    //     text: 'Examples',
    //     items: [
    //       { text: 'Markdown Examples', link: '/markdown-examples' },
    //       { text: 'Runtime API Examples', link: '/api-examples' }
    //     ]
    //   }
    // ],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/greyovo/PicQuery' }
    ]
  }
})
