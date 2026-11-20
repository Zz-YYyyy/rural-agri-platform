# 辅助脚本

本目录保存本地检查、文档契约校验、构建和维护脚本。

`check_database_design.py`：使用 Python 3.10+ 标准库，从仓库任意位置运行均会定位项目路径。默认只读检查 V1 的 29 表、字段与键定义及 SQL／字典同步，不连接数据库。使用 `--write-dictionary` 时只重建 [字段字典](../docs/database/数据库字段字典.md) 的生成标记区，保留人工说明。

从仓库根目录运行：

```powershell
python scripts/check_database_design.py
python scripts/check_database_design.py --write-dictionary
```

它不是完整 MySQL 解析器，不替代目标 MySQL 8.0 空库迁移测试；目前只覆盖 V1，未来增加增量迁移时需同步扩展检查范围，详见 [迁移说明](../docs/database/数据库实现与迁移说明.md)。

`build_api_contract.py`：使用 Python 3.10+ 和 `jsonschema`，读取 [分模块契约](../docs/api/分模块契约/) 的公共定义与四个模块 JSON，核对重复键、路径、操作编号、引用、鉴权、需求／验收／表名关联及示例。默认只读检查；`--write` 会覆盖生成的 [OpenAPI 文件](../docs/api/接口契约.openapi.json) 与 [接口字段说明](../docs/api/接口字段说明.md)，不会修改源契约、数据库或调用 HTTP／模型服务。

```powershell
# 仅在尚未安装时准备文档检查依赖
python -m pip install jsonschema
python scripts/build_api_contract.py --write
python scripts/build_api_contract.py
```

修改源契约和对应中文说明后重新生成，再执行只读检查。该脚本不是完整 OpenAPI 元 schema 验证器，不能代替导入工具核对、实际 Controller 实现检查和接口联调。

脚本应说明用途、运行环境、输入输出和数据影响。涉及数据库初始化、覆盖或清理的操作应明确目标，避免在环境不明时执行。
