# 离线报告检查

[English](README.md)

在仓库根目录，使用现有 MobileCLIP 评测 Python 环境运行：

```sh
python -m unittest discover -s script/model-MobileCLIP2/tests -p 'test_*.py' -v
```

所有测试均不执行模型、不操作手机、不下载数据。报告与评估器回归使用确定性的合成清单、合成特征和小型临时假模型文件；检查错误哈希、样本映射、不完整运行、无效向量、不同运行库/线程数、损坏指标和双语数值漂移。临时报告明确标注为合成数据，每次测试后删除。

`test_report_archive.py` 将保留的中英表格与 `results/measured-summary.json` 对齐，覆盖当前主机/真机准确率、真机速度、ORT/v1 对比和历史准确率摘要。其余测试用合成 fixture 重新驱动报告校验器和设备评估器，在不依赖原始 benchmark 日志的前提下继续覆盖哈希、路径、schema、统计值和共享文本输出检查。

使用[模型说明](../README_zh.md)已列出的依赖即可。测试不新增依赖，不需要 Git 忽略的模型二进制或多 GB 输入张量；提交到仓库的测量输入只剩精简版 `results/measured-summary.json`。新实验输出请放在 `build/`，避免本地证据覆盖已发布摘要。
