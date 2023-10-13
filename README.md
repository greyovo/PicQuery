# 图搜 PicQuery

中文 | [English](README_en.md)

![cover_cn](assets/cover_cn.jpg)

🔍 用平常说话的方式搜索本地相册中的图片——无需联网、本地运行

借助图搜，您可以用平常说话的方式来搜索手机里的照片。例如：“草丛中的猫咪”、“桌上的笔记本电脑”、“粉色衣服的女孩”，等等。

- 完全免费，没有任何内购
- 对图像的索引和搜索完全离线运行，无需担心隐私安全
- 支持中英双语搜索
- 为移动设备专门优化的模型，上万张照片也能1秒呈现结果
- 只需要首次使用时进行索引，后续即可立刻搜索

## 获取方式

<a href='https://play.google.com/store/apps/details?id=me.grey.picquery&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img style="width:130px" src='./assets/google-play-badge-cn.png'/></a> 

- Google Play： 搜索 “图搜” 或 “PicQuery”，或点击上方链接
- 从本仓库下载：[Release](https://github.com/greyovo/PicQuery/releases)

**其他方式：**

- [蒲公英内测分发](https://www.pgyer.com/picquery)（每日500次下载）
- 镜像站加速：将 [Release](https://github.com/greyovo/PicQuery/releases) 中的文件下载链接复制到 [GitHub Proxy](https://ghproxy.com/) 中下载
- 国内应用市场（待上线）

> 🍎 iOS 用户请使用 [“寻隐”](https://apps.apple.com/cn/app/寻隐-用句子描述找照片/id1664361663)，它是图搜的灵感来源，由 [@mazzzystar](https://github.com/mazzzystar) 开发并开源。



## 实现原理

> 感谢 [@mazzzystar](https://github.com/mazzzystar) 和 [@Young-Flash](https://github.com/Young-Flash) 在本应用开发过程中的[协助与讨论](https://github.com/mazzzystar/Queryable/issues/12)。

本应用基于 OpenAI 的 [CLIP 模型](https://github.com/openai/CLIP) 实现。

首先将要搜索的图片通过图像编码器编码为向量，并存储到数据库中；将用户搜索时提供的文字也编码为向量，与已索引的图片向量遍历计算相似度，选取 TopK 相似度的图像集合作为查询结果。



## 构建运行

要构建运行本项目，需要获取量化后的 CLIP 模型。在 Colab 中 运行此 [jupyter notebook](https://colab.research.google.com/drive/1bW1aMg0er1T4aOcU5pCNYVgmVzBJ4-x4#scrollTo=hPscj2wlZlHb)，将分别得到：

- `clip-image-encoder-quant-int8.onnx`
- `clip-text-encoder-quant-int8.onnx`

将它们放入 `app\src\main\assets` 中，即可构建和运行。




## 鸣谢

- [mazzzystar/Queryable](https://github.com/mazzzystar/Queryable)
- [Young-Flash](https://github.com/Young-Flash)
- [IacobIonut01/Gallery](https://github.com/IacobIonut01/Gallery)



## 开源协议

本项目基于 MIT 许可协议开源，保留所有权利。