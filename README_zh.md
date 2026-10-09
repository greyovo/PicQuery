# PicQuery

[English](README.md) | 中文

![PicQuery](README_assets/cover_cn.jpg)

PicQuery 是一款离线图片搜索应用：在本地建立文件夹或相册索引，然后通过文字或相似图片进行搜索。图片、索引和模型推理均保留在设备本地。

支持文件夹/相册索引、中文搜索词翻译、图片相似度搜索、限定文件夹搜索、增量更新和深色模式。

> ✨ PicQuery V2 已完全使用 Flutter 重写，增加了对桌面端（Windows/macOS/Linux）的支持，但与旧版的数据库不兼容。升级后需要重新建立图片索引。
>
> Play Store 版本待更新。

## 安装

从项目的 [GitHub Releases](https://github.com/greyovo/PicQuery/releases) 下载对应平台的安装包。

### macOS

由于应用尚未使用付费 Apple Developer 账户签名和公证，macOS 可能阻止其运行。要允许运行，打开“终端”执行下列命令：

```bash
sudo xattr -rd com.apple.quarantine "/Applications/PicQuery.app"
```

## 从源码构建

需要安装 Git、[Git LFS](https://git-lfs.com/)、Flutter 3.47.4（stable）以及目标平台所需的 Flutter 工具链。

```bash
git lfs install
git clone https://github.com/greyovo/PicQuery.git
cd PicQuery
git lfs pull
flutter pub get
```

运行或构建前，请确保 `assets/models/` 中存在：

- `mobileclip2_s0_visual.onnx`
- `mobileclip2_s0_text.onnx`
- `mt_zho-eng.fp32.quantized.onnx` //  在 Git LFS 存储

模型由 [`greyovo/picquery-models`](https://github.com/greyovo/picquery-models) 中的 `export.sh` 导出。安装 [uv](https://docs.astral.sh/uv/getting-started/installation/) 后运行：

```bash
bash ci/prepare_models.sh
```

连接设备或在桌面端运行：

```bash
flutter run

# 测试用
flutter analyze lib/
flutter test
```

## 贡献与致谢

欢迎提交 issue 和 pull request。请附上复现步骤、受影响平台及相关检查结果；模型或预处理变更应附数值和检索验证。

PicQuery 基于 Apple [MobileCLIP](https://github.com/apple/ml-mobileclip)。感谢 [@mazzzystar](https://github.com/mazzzystar) 和 [@Young-Flash](https://github.com/Young-Flash) 在开发中的帮助，相关交流见[原始讨论](https://github.com/mazzzystar/Queryable/issues/12)。

- [mazzzystar/Queryable](https://github.com/mazzzystar/Queryable)：本项目的灵感来源，也提供 [iOS 应用](https://apps.apple.com/us/app/queryable-find-photo-by-text/id1661598353)
- [IacobIonut01/Gallery](https://github.com/IacobIonut01/Gallery)

## 许可证

项目代码使用 [MIT License](LICENSE)。第三方依赖及模型资产遵循各自的许可证，包括 [Apple MobileCLIP 模型许可](https://github.com/apple/ml-mobileclip/blob/main/LICENSE_MODELS)。
