#!/usr/bin/env bash
# 回放语料脱敏：从 stdin 读一条响应，输出脱敏后的 JSON。
# 用法: bash tools/replay/desensitize.sh < raw_response.json >> app/src/test/resources/replay/<domain>.jsonl
set -euo pipefail

INPUT="$(cat)"

# 字段级：账号/好友/令牌/设备
CLEAN="$(printf '%s' "$INPUT" | sed -E \
  -e 's/"(userId|uid|alipayUserId|friendId|friendUserId|loginId|logonId|token|sessionId|sId|authToken|mobile|phone|mobilePhone|idCard|realName|certNo|deviceId|utdid|tid|imei|imsi|nickName|userName|avatar|headImageUrl|email)"[[:space:]]*:[[:space:]]*"[^"]*"/"\1":"<redacted>"/g')"

# 查询参数值：?a=1&b=2 → ?a=<redacted>&b=<redacted>
CLEAN="$(printf '%s' "$CLEAN" | sed -E 's/([?&][A-Za-z0-9_]+=)[^&"'"'"' ]*/\1<redacted>/g')"

printf '%s' "$CLEAN"