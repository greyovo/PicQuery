# PicQuery

中文|[English](README.md)

![cover_en](assets/cover_cn.jpg)

🔍 使用自然语言搜索本地图片，完全离线运行。例如："书桌上的笔记本电脑"、"海边日落"、"草丛中的小猫"等。  
支持通过相册选图进行相似图片搜索
- 完全免费，无内购
- 支持中英文双语搜索
- 图片索引和搜索全程离线运行，隐私无忧
- 8000+照片搜索1秒内出结果
- 首次启动等待索引构建，后续搜索立等可取

## 安装

<a href='https://play.google.com/store/apps/details?id=me.grey.picquery&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img style="width:130px" src='./assets/google-play-badge-en.png'/></a>

- Google Play - 搜索 "PicQuery"
- [Release](https://github.com/greyovo/PicQuery/releases) 下载APK
- 若无法访问上述资源，请参考[其他安装方式](README_zh.md##其他方式)

> 🍎 iOS用户请参考灵感来源应用 _[Queryable](https://apps.apple.com/us/app/queryable-find-photo-by-text/id1661598353)_（[代码](https://github.com/mazzzystar/Queryable)），由[@mazzzystar](https://github.com/mazzzystar/Queryable) 开发。

## 实现原理

> 感谢 [@mazzzystar](https://github.com/mazzzystar) 和 [@Young-Flash](https://github.com/Young-Flash) 在开发过程中给予的帮助，讨论记录可[查看此处](https://github.com/mazzzystar/Queryable/issues/12)。

_PicQuery_ 的核心技术基于OpenAI的[CLIP模型](https://github.com/openai/CLIP)和Apple的[mobile clip](https://github.com/apple/ml-mobileclip)

首先通过图像编码器将待搜索的图片编码为向量并建立索引存储。当用户输入搜索文本时，使用文本编码器将文本同样编码为向量。通过计算文本向量与已索引图片向量的相似度，选取相似度最高的K张图片作为搜索结果。

## 使用 CLIP 模型构建

要构建本项目，您需要获取量化后的CLIP模型。

请按步骤运行此[jupyter notebook](https://colab.research.google.com/drive/1bW1aMg0er1T4aOcU5pCNYVgmVzBJ4-x4#scrollTo=hPscj2wlZlHb)，当运行至_"You are done"_章节时，您应该在`./result`目录下获得以下模型文件：
- `clip-image-int8.ort`
- `clip-text-int8.ort`
> 若不想运行脚本，可直接从[Google Drive](https://drive.google.com/drive/folders/1VHgEvYyKsiVte8-lywD8qS8SfgcvMc3z?usp=drive_link)下载

## 使用 mobile-clip 模型构建

要构建本项目，您需要获取量化后的模型文件：

- `vision_model.ort`
- `text_model.ort`

> 可从[Google Drive](https://drive.google.com/drive/folders/1HgGDfsHHIlDK_Fx0Spnujxt51SgguNCq?usp=drive_link)下载

将文件放入`app\src\main\assets`目录即可使用。

## 使用 TF / TFLite 模型构建

要通过 LiteRT 运行 TensorFlow Lite 模型，请将以下文件放入`app\src\main\assets`：

- `image_model.tflite`
- `text_model.tflite`

图像模型应接受与`PreprocessorMobileCLIPv2`一致的 MobileCLIP 风格预处理输入；文本模型应接受 CLIP BPE token ids，输入类型支持`INT32`或`INT64`。
可通过`python script/model-MobileCLIP2/export_mobileclip2_tflite.py`导出默认的 MobileCLIP2-S0 资产。

## 选择模型模块
在`val AppModules = listOf(viewModelModules, dataModules, modulesCLIP, domainModules)`中选择需要的模块，CLIP 对应`modulesCLIP`模块，mobile-clip 对应`modulesMobileCLIP`模块，TF/TFLite 对应`modulesTF`模块。

## FAQ
### Issue 1
java.lang.RuntimeException: java.lang.reflect.InvocationTarget Exception
> Don't forget to add model files to `app\src\main\assets` directory

### Issue 2
java.io.FileNotFoundException: clip-image-int8.ort
> Make sure the model files are in the correct directory. If you are using mobile-clip or TF/TFLite, make sure you are using the correct model files and change the module to modulesMobileCLIP or modulesTF.

## 致谢

- [mazzzystar/Queryable](https://github.com/mazzzystar/Queryable)
- [Young-Flash](https://github.com/Young-Flash)
- [IacobIonut01/Gallery](https://github.com/IacobIonut01/Gallery)

## 许可证

本项目基于MIT协议开源。保留所有权利。
