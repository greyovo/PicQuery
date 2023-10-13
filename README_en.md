# PicQuery

[中文](README.md)| English

![cover_en](assets/cover_en.jpg)

🔍 Search for your local images with natural language, running completely offline. For example, "a laptop on the desk", "sunset by the sea", "kitty in the grass", and so on.

- Totally free, NO in-app purchases
- Support both English and Chinese
- Indexing and searching of images works completely offline without worrying about privacy
- Show results in less than 1 second when searcing for 8,000+ photos
- Wait for indexing on the first time you launch, and search immediately afterwards



## Installation

<a href='https://play.google.com/store/apps/details?id=me.grey.picquery&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img style="width:130px" src='./assets/google-play-badge-en.png'/></a> 

- Google Play - Search for “PicQuery”，or click the button above
- Download APK from [Release](https://github.com/greyovo/PicQuery/releases)

> 🍎 For iOS users, please refer to *[Queryable](https://apps.apple.com/us/app/queryable-find-photo-by-text/id1661598353)*, the inspiration behind this application, developed and open-sourced by [@mazzzystar](https://github.com/mazzzystar).

## Implementation

> Thanks to [@mazzzystar](https://github.com/mazzzystar) and [@Young-Flash](https://github.com/Young-Flash) for their assistance during the development. The discussion can be viewed [here](https://github.com/mazzzystar/Queryable/issues/12).

*PicQuery* is powered by OpenAI's [CLIP model](https://github.com/openai/CLIP). 

First, the images to be searched are encoded into vectors using an image encoder and stored in a database. The text provided by the user during the search is also encoded into a vector. The encoded text vector is then compared with the indexed image vectors to calculate the similarity. The top K images with the highest similarity scores are selected as the query results.

## Build & Run

To build this project, you need to obtain a quantized CLIP model. Run this [jupyter notebook](https://colab.research.google.com/drive/1bW1aMg0er1T4aOcU5pCNYVgmVzBJ4-x4#scrollTo=hPscj2wlZlHb) to get the following model files：

- `clip-image-encoder-quant-int8.onnx`
- `clip-text-encoder-quant-int8.onnx`

Put them into `app\src\main\assets` and you're ready to build and run.


## Acknowledgement

- [mazzzystar/Queryable](https://github.com/mazzzystar/Queryable)
- [Young-Flash](https://github.com/Young-Flash)
- [IacobIonut01/Gallery](https://github.com/IacobIonut01/Gallery)

## License

This project is open source under MIT license. All rights reserved.

