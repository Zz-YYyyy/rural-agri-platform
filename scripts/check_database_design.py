"""Check the V1 schema inventory and its generated Markdown dictionary without a DB.

This deliberately supports this project's CREATE TABLE style, not all MySQL SQL.
It is NOT a replacement for executing migrations against isolated MySQL 8.0.
"""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
SQL = ROOT / "backend/src/main/resources/db/migration/V1__initial_schema.sql"
DOC = ROOT / "docs/database/数据库字段字典.md"
START = "<!-- BEGIN GENERATED V1 DICTIONARY -->"
END = "<!-- END GENERATED V1 DICTIONARY -->"
EXPECTED = set("""sys_user user_wechat_binding user_address farmer_profile
farmer_application region product_category product product_sku stock_log
user_favorite cart_item trade_order order_item refund_request order_action_log
order_review campaign campaign_product campaign_participation_log assistance_post
origin_content consultation_thread consultation_message promotion_event
ai_conversation ai_message ai_usage_log admin_operation_log""".split())
TABLE = re.compile(
    r"CREATE TABLE `(?P<name>\w+)` \(\n(?P<body>.*?)\n\) "
    r"ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin "
    r"COMMENT='(?P<comment>(?:[^']|'')*)';", re.S
)
COLUMN = re.compile(
    r"`(?P<name>\w+)` (?P<type>(?:BIGINT|INT|TINYINT|SMALLINT|TEXT|MEDIUMTEXT|JSON|"
    r"VARCHAR\(\d+\)|CHAR\(\d+\)|DECIMAL\(\d+,\s*\d+\)|DATETIME\(3\))) "
    r"(?P<options>.*?)COMMENT '(?P<comment>(?:[^']|'')*)'$"
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def read_clean(path: Path) -> str:
    raw = path.read_bytes()
    require(not raw.startswith(b"\xef\xbb\xbf"), f"BOM: {path}")
    require(b"\r" not in raw, f"CRLF: {path}")
    text = raw.decode("utf-8")
    require(text.endswith("\n"), f"Missing final newline: {path}")
    require(all(line == line.rstrip() for line in text.splitlines()), f"Trailing spaces: {path}")
    return text


def parse_schema(text: str) -> list[dict]:
    text = re.sub(r"^--.*$", "", text, flags=re.M).strip()
    matches = list(TABLE.finditer(text))
    remainder = TABLE.sub("", text).strip()
    require(not remainder, f"Unsupported SQL or statement outside CREATE TABLE: {remainder[:120]}")
    tables, check_names = [], set()
    for match in matches:
        table = {**match.groupdict(), "columns": [], "keys": []}
        name = table["name"]
        unquoted = re.sub(r"'(?:[^']|'')*'", "''", match.group(0))
        require("'" not in unquoted.replace("''", ""), f"Unbalanced SQL string: {name}")
        depth = 0
        for char in unquoted:
            depth += (char == "(") - (char == ")")
            require(depth >= 0, f"Unbalanced parentheses: {name}")
        require(depth == 0, f"Unbalanced parentheses: {name}")
        lines = table.pop("body").splitlines()
        for i, raw in enumerate(lines):
            line = raw.strip()
            require(line.endswith(",") == (i < len(lines) - 1), f"Comma placement: {name}: {line}")
            line = line.removesuffix(",")
            column = COLUMN.fullmatch(line)
            if column:
                field = column.groupdict()
                field["options"] = field["options"].strip()
                table["columns"].append(field)
            else:
                require(line.startswith(("PRIMARY KEY (", "UNIQUE KEY `", "KEY `", "CONSTRAINT `")),
                        f"Unsupported column/key: {name}: {line}")
                table["keys"].append(line)
        fields = {field["name"]: field for field in table["columns"]}
        require(len(fields) == len(table["columns"]), f"Duplicate column: {name}")
        require("id" in fields and fields["id"]["type"] == "BIGINT" and
                "AUTO_INCREMENT" in fields["id"]["options"], f"ID convention: {name}")
        require(table["keys"].count("PRIMARY KEY (`id`)") == 1, f"Primary key: {name}")
        key_names = set()
        for key in table["keys"]:
            identifiers = re.findall(r"`(\w+)`", key)
            refs = identifiers if key.startswith("PRIMARY") else identifiers[1:]
            require(all(ref in fields for ref in refs), f"Unknown key/check column: {name}: {key}")
            if not key.startswith("PRIMARY"):
                key_name = identifiers[0]
                require(len(key_name) <= 64 and key_name not in key_names, f"Invalid/duplicate key: {name}.{key_name}")
                key_names.add(key_name)
                if key.startswith("CONSTRAINT"):
                    require(" CHECK (" in key, f"Expected CHECK: {key}")
                    require(key_name not in check_names, f"Schema-wide CHECK name collision: {key_name}")
                    check_names.add(key_name)
                    require("id" not in refs, f"CHECK must not reference AUTO_INCREMENT id: {name}")
                else:
                    width = 0
                    for ref in refs:
                        dtype = fields[ref]["type"]
                        size = re.search(r"(?:VAR)?CHAR\((\d+)\)", dtype)
                        width += 4 * int(size[1]) if size else 8
                        require(dtype not in ("TEXT", "MEDIUMTEXT", "JSON"), f"Unbounded indexed type: {name}.{ref}")
                    require(width <= 3072, f"Index estimated byte length >3072: {name}.{key_name}")
        tables.append(table)
    names = [table["name"] for table in tables]
    require(len(names) == len(set(names)), "Duplicate tables")
    require(set(names) == EXPECTED, f"Table inventory differs: {set(names) ^ EXPECTED}")
    return tables


def cell(text: str) -> str:
    return text.replace("|", "\\|").replace("\n", " ")


def render(tables: list[dict]) -> str:
    lines = [START, "", "## 完整字段、索引与数据库校验", "",
             "本节由 V1 SQL 的字段注释和定义生成。业务含义、JSON 结构及事务规则见前文与《数据库实现与迁移说明》。", "",
             "| 序号 | 表名 | 中文用途 | 字段数 |", "| --- | --- | --- | --- |"]
    for index, table in enumerate(tables, 1):
        lines.append(f"| {index} | `{table['name']}` | {cell(table['comment'])} | {len(table['columns'])} |")
    for index, table in enumerate(tables, 1):
        lines.extend(["", f"### {index}. {table['name']} — {table['comment']}", "",
                      "| 字段 | 类型 | 允许空 | 默认与自动值 | 含义 |",
                      "| --- | --- | --- | --- | --- |"])
        for field in table["columns"]:
            options = field["options"]
            nullable = "否" if "NOT NULL" in options else "是"
            value = re.sub(r"\bNOT NULL\b", "", options).strip()
            if value.startswith("NULL "):
                value = value[5:]
            if value.startswith("DEFAULT "):
                value = value[8:]
            value = value or ("NULL" if nullable == "是" else "无，写入时必须提供")
            lines.append(f"| `{field['name']}` | `{field['type']}` | {nullable} | {cell(value)} | {cell(field['comment'])} |")
        lines.extend(["", "索引与数据库校验（CHECK 为单行校验，不代替权限及事务）：", "", "```sql"])
        lines.extend(table["keys"])
        lines.extend(["```"])
    lines.extend(["", END])
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write-dictionary", action="store_true", help="Regenerate only the marked V1 dictionary section")
    args = parser.parse_args()
    tables = parse_schema(read_clean(SQL))
    document = read_clean(DOC)
    require(document.count(START) == document.count(END) == 1, "Dictionary requires exactly one marker pair")
    before, section = document.split(START)
    _, after = section.split(END)
    expected = before + render(tables) + after
    if args.write_dictionary:
        DOC.write_text(expected, encoding="utf-8", newline="\n")
    else:
        require(document == expected, "Dictionary differs from SQL; review SQL and run --write-dictionary")
    print(f"PASS: {len(tables)} tables, {sum(len(t['columns']) for t in tables)} columns, "
          f"{sum(len(t['keys']) for t in tables)} key/check definitions; V1 dictionary matches SQL.")
    print("Static checks only. No database connection or MySQL execution performed.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
