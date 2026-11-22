#!/usr/bin/env bash
# 机械修复流水线：java-gen（137 个 mojmap 反编译源码）+ forge 手写的平台层整体编译。
#
# 步骤：
#   1. 同步 compile classpath（forge 模块的真实 compileClasspath，含 Forge + MTR-forge）
#   2. tools/fix-java-gen.js          —— 表格修复 + 结构性修复（switch 作用域 / 重复局部变量 / mixin 自身强转）
#   3. tools/fix-java-gen-platform.js —— Fabric API → Forge 平台 shim（导入 / 注册 / 页签 / 网络 / 指令 / 环境）
#   4. tools/m4b3-javac.sh            —— 一次性 javac 全部源码（-Xmaxerrs 20000，不截断）
#   5. 若仍有「重复局部变量」，重复 2–4（最多 8 轮），直到没有可机械修的错误
#   6. tools/m4b3-classify.js         —— 输出按类别的错误统计表
#
# 幂等：java-gen 可被 tools/remap-to-mojmap.sh 覆盖，重跑本脚本即得到同样的修复结果。
#
# 用法：
#   ./tools/fix-java-gen.sh              # 修复 + 全量编译 + 分类
#   ./tools/fix-java-gen.sh --dry-run    # 只看会改什么（不改文件、不编译）
set -uo pipefail
mp() { cygpath -m "$1" 2>/dev/null || printf '%s' "$1"; }
R="$(mp "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)")"
JAVAC_HOME="C:/Program Files/Java/jdk-17"
DRY=0
[ "${1:-}" = "--dry-run" ] && DRY=1

cd "$R" || exit 2
mkdir -p "$R/.remap-work"

if [ $DRY -eq 1 ]; then
  node tools/fix-java-gen.js --dry-run
  node tools/fix-java-gen-platform.js --dry-run
  exit 0
fi

echo "== 1/4 同步 compile classpath"
(cd forge && JAVA_HOME="$JAVAC_HOME" ./gradlew -q printCompileCp --offline 2>/dev/null) \
  | sed -n '/^CP_BEGIN$/,/^CP_END$/p' > "$R/.remap-work/m4b3-cp.txt"
[ -s "$R/.remap-work/m4b3-cp.txt" ] || { echo "classpath 导出失败" >&2; exit 2; }

for round in 1 2 3 4 5 6 7 8; do
  echo "== 2/4 全量 javac（第 $round 轮）"
  ./tools/m4b3-javac.sh | tail -2
  echo "== 3/4 机械修复（第 $round 轮）"
  # 注意：不能在「日志里没有重复变量」时 break —— 反编译的语法级瑕疵（label 构造器）会让
  # javac 在归属阶段之前中止，那一轮日志里既没有重复变量、也没有真实错误列表。
  # 收敛判据是「修复器自己没东西可改」（幂等），而不是某类错误是否出现。
  FIX_OUT=$(node tools/fix-java-gen.js)
  printf '%s\n' "$FIX_OUT" | head -1
  node tools/fix-java-gen-platform.js
  if printf '%s\n' "$FIX_OUT" | grep -q "无需修改"; then break; fi
done

echo "== 3b/4 机械修复（表格，幂等）"
node tools/fix-java-gen.js
node tools/fix-java-gen-platform.js
./tools/m4b3-javac.sh | tail -2

echo "== 4/4 分类"
node tools/m4b3-classify.js
