package io.github.aw1y2z.sesame.replay;

import java.util.ArrayList;
import java.util.List;

/**
 * 4.0.0 回放执行器：把语料里的响应喂给纯策略层（归类 / 风控解析 / 任务能力），
 * 逐条比对期望，返回失败清单。不连设备、不发网络请求。
 */
public final class ReplayRunner {

    public static final class Failure {
        public final String id;
        public final String detail;

        Failure(String id, String detail) {
            this.id = id;
            this.detail = detail;
        }
    }

    /** 单步断言：返回 null 表示通过，否则返回失败原因。 */
    public interface Step {
        String apply(ReplayCorpus.Case item);
    }

    private final List<Step> steps = new ArrayList<>();
    private final List<Failure> failures = new ArrayList<>();

    public ReplayRunner add(Step step) {
        steps.add(step);
        return this;
    }

    public List<Failure> run(ReplayCorpus corpus) {
        failures.clear();
        for (ReplayCorpus.Case item : corpus.cases()) {
            for (Step step : steps) {
                String detail = step.apply(item);
                if (detail != null) {
                    failures.add(new Failure(item.id, detail));
                }
            }
        }
        return failures;
    }

    public static String expect(String what, Object expected, Object actual) {
        if (expected == null || String.valueOf(expected).isEmpty()) {
            return null;
        }
        if (!String.valueOf(expected).equals(String.valueOf(actual))) {
            return what + " 期望 " + expected + "，实际 " + actual;
        }
        return null;
    }
}