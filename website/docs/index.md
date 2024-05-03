---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

hero:
  name: PicQuery
  text: Search Local Images with Natural Language
  # tagline: 🔍 Search local images with natural language on Android
  tagline: E.g., "a laptop on the desk", "sunset by the sea", "kitty in the grass"...

  actions:
    - theme: brand
      text: Get on Google Play
      link: https://play.google.com/store/apps/details?id=me.grey.picquery
    - theme: alt
      text: Release
      link: https://github.com/greyovo/PicQuery/releases
    - theme: alt
      text: Source Code
      link: https://github.com/greyovo/PicQuery

  image:
    src: logo.svg
    alt: PicQuery Logo

features:
  - title: Privacy First
    icon: 🔒
    details: Indexing and searching of images works completely offline without worrying about privacy.
  - title: Optimized for Android
    icon: ⚡️
    details: Works on Android 10+, show results in 1 sec when searching for 8,000+ photos.
  - title: Free & Open Source
    icon: 🆓
    details: Totally free, NO in-app purchases, supports both English and Chinese.
---

<style>
:root {
  --vp-home-hero-name-color: transparent;
  --vp-home-hero-name-background: -webkit-linear-gradient(120deg, #0078D7 30%, #41d1ff);

  --vp-c-brand-1: rgb(33, 99, 150);
  --vp-c-brand-2: rgba(0, 120, 215, 0.8);
  --vp-c-brand-3: rgb(0, 120, 215);
  
  --vp-home-hero-image-background-image: linear-gradient(-45deg, #0078D7 1%, #FFFFFF 50%);
  --vp-home-hero-image-filter: blur(44px);
}

.dark {
  /* --vp-home-hero-name-color: transparent;
  --vp-home-hero-name-background: -webkit-linear-gradient(120deg, #bd34fe 30%, #41d1ff); */
  
  --vp-home-hero-image-background-image: linear-gradient(-45deg, #18486e 55%, #000 50%);
  --vp-home-hero-image-filter: blur(11px);
}

@media (min-width: 640px) {
  :root {
    --vp-home-hero-image-filter: blur(56px);
  }
}

@media (min-width: 960px) {
  :root {
    --vp-home-hero-image-filter: blur(68px);
  }
}
</style>
