package io.github.aw1y2z.sesame.replay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import io.github.aw1y2z.sesame.data.metrics.RpcMetrics;
import io.github.aw1y2z.sesame.data.task.TaskCapability;
import io.github.aw1y2z.sesame.util.RiskGuard;

/**
 * 4.0.0 回放 harness 自检：starter 语料必须全部通过纯策略层（归类 / 风控解析 / 任务能力）。
 * <p>这是“回放能跑起来”的最小闭环；真实抓包语料在数据期补入同一目录。
 */
public class ReplayCorpusTest {

    @Test
    public void starterCorpusPassesAllSteps() {
        ReplayCorpus corpus = ReplayCorpus.fromResource("replay/starter.jsonl");
        assertTrue("starter 语料不应为空", corpus.cases().size() >= 10);

        ReplayRunner runner = new ReplayRunner();
        runner.add(item -> ReplayRunner.expect("outcome",
                item.expectOutcome, RpcMetrics.classify(item.response).name()));
        runner.add(item -> ReplayRunner.expect("signal",
                item.expectSignal, RiskGuard.classify(item.response).signal.name()));
        runner.add(item -> item.expectKind.isEmpty() ? null : ReplayRunner.expect("kind",
                item.expectKind, TaskCapability.classify(item.content, item.timeCount,
                        item.taskRequire, item.taskProgress, item.autoComplete).name()));

        List<ReplayRunner.Failure> failures = runner.run(corpus);
        StringBuilder message = new StringBuilder();
        for (ReplayRunner.Failure failure : failures) {
            message.append(failure.id).append(": ").append(failure.detail).append('\n');
        }
        assertEquals(message.toString(), 0, failures.size());
    }

    @Test
    public void desensitizerRedactsSensitiveValues() {
        String raw = "{\"userId\":\"2088000000000000\",\"friendId\":\"2088xxxx\",\"actionUrl\":\"https://x.com/a?token=abc&uid=123\"}";
        String clean = Desensitizer.sanitize(raw);
        assertTrue(clean.contains("\"userId\":\"<redacted>\""));
        assertTrue(!clean.contains("2088000000000000"));
        assertTrue(!clean.contains("abc"));
        assertTrue(!clean.contains("uid=123"));
    }

    @Test
    public void replayEnvIsDeterministic() {
        ReplayEnv env = new ReplayEnv(1_700_000_000_000L).withRandom(1, 2, 3);
        env.advance(60_000L);
        assertEquals(1_700_000_060_000L, env.now());
        assertEquals(1, env.nextInt(10));
        assertEquals(2, env.nextInt(10));
        assertEquals(3, env.nextInt(10));
        assertEquals(1, env.nextInt(10));
    }
}