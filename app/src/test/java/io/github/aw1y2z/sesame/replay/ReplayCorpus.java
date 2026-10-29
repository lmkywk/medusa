package io.github.aw1y2z.sesame.replay;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 4.0.0 回放语料：JSONL，一行一个用例。字段见 tools/replay/README.md。
 * <p>语料必须已脱敏（见 {@link Desensitizer}）；加载时会再跑一遍脱敏作为兜底。
 */
public final class ReplayCorpus {

    public static final class Case {
        public String id = "";
        public String domain = "";
        public String method = "";
        public String response = "";
        /** 期望 RPC 归类：OK / EMPTY / C1009 / C1004 / FAIL */
        public String expectOutcome = "";
        /** 期望风控信号：NONE / LIMIT / SERVER_REFUSAL */
        public String expectSignal = "";
        /** 期望任务能力分类：CLAIM / WAIT / REAL_ACTION / GONE / UNKNOWN */
        public String expectKind = "";
        /** 分类输入（只有 kind 用例需要） */
        public String content = "";
        public long timeCount;
        public int taskRequire;
        public int taskProgress;
        public boolean autoComplete;
        public String note = "";

        static Case from(JSONObject jo) {
            Case item = new Case();
            item.id = jo.optString("id", "");
            item.domain = jo.optString("domain", "");
            item.method = jo.optString("method", "");
            item.response = Desensitizer.sanitize(jo.optString("response", ""));
            JSONObject expect = jo.optJSONObject("expect");
            if (expect != null) {
                item.expectOutcome = expect.optString("outcome", "");
                item.expectSignal = expect.optString("signal", "");
                item.expectKind = expect.optString("kind", "");
            }
            JSONObject input = jo.optJSONObject("input");
            if (input != null) {
                item.content = input.optString("content", "");
                item.timeCount = input.optLong("timeCount", 0L);
                item.taskRequire = input.optInt("taskRequire", 0);
                item.taskProgress = input.optInt("taskProgress", 0);
                item.autoComplete = input.optBoolean("autoComplete", false);
            }
            item.note = jo.optString("note", "");
            return item;
        }
    }

    private final List<Case> cases = new ArrayList<>();

    public List<Case> cases() {
        return cases;
    }

    public static ReplayCorpus fromResource(String resourcePath) {
        ReplayCorpus corpus = new ReplayCorpus();
        try (InputStream in = ReplayCorpus.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return corpus;
            }
            corpus.load(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (Throwable ignored) {
        }
        return corpus;
    }

    public static ReplayCorpus fromFile(File file) {
        ReplayCorpus corpus = new ReplayCorpus();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
            corpus.load(reader);
        } catch (Throwable ignored) {
        }
        return corpus;
    }

    private void load(BufferedReader reader) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                continue;
            }
            try {
                cases.add(Case.from(new JSONObject(trimmed)));
            } catch (Throwable ignored) {
                // 坏行跳过，不让一条脏语料拦住整轮回放
            }
        }
    }
}