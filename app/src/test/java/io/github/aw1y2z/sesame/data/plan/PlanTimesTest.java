package io.github.aw1y2z.sesame.data.plan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** golden test：计划时间计算（窗口、轮次、当天已尝试判定）。 */
public class PlanTimesTest {

    private static final TimeZone SHANGHAI = TimeZone.getTimeZone("Asia/Shanghai");

    @Before
    public void setUp() {
        TimeZone.setDefault(SHANGHAI);
    }

    private static long at(int hour, int minute) {
        Calendar calendar = Calendar.getInstance(SHANGHAI);
        calendar.set(2026, Calendar.OCTOBER, 27, hour, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static Plan plan(String id, String start, String end, String... rounds) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setWindowStart(start);
        plan.setWindowEnd(end);
        plan.setRoundTimes(new ArrayList<>(Arrays.asList(rounds)));
        return plan;
    }

    private static PlanHistory.Record record(String planId, String roundTime, String status, long startedAt) {
        PlanHistory.Record record = new PlanHistory.Record();
        record.planId = planId;
        record.roundTime = roundTime;
        record.status = status;
        record.startedAt = startedAt;
        return record;
    }

    @Test
    public void dueRoundInsideWindow() {
        Plan plan = plan("p1", "07:00", "09:00", "07:30", "08:30");
        assertEquals("07:30", PlanTimes.firstDueTime(plan, at(8, 0), new ArrayList<>()));
    }

    @Test
    public void earlierRoundFirst() {
        Plan plan = plan("p1", "07:00", "09:00", "07:30", "08:30");
        assertEquals("07:30", PlanTimes.firstDueTime(plan, at(8, 45), new ArrayList<>()));
    }

    @Test
    public void beforeWindowNothingDue() {
        Plan plan = plan("p1", "07:00", "09:00", "07:30");
        assertNull(PlanTimes.firstDueTime(plan, at(6, 0), new ArrayList<>()));
    }

    @Test
    public void afterWindowNothingDue() {
        Plan plan = plan("p1", "07:00", "09:00", "07:30");
        assertNull(PlanTimes.firstDueTime(plan, at(10, 0), new ArrayList<>()));
    }

    @Test
    public void completedRoundIsNotDueAgain() {
        Plan plan = plan("p1", "07:00", "09:00", "07:30");
        List<PlanHistory.Record> history = new ArrayList<>();
        history.add(record("p1", "07:30", PlanHistory.COMPLETED, at(7, 31)));
        assertNull(PlanTimes.firstDueTime(plan, at(8, 0), history));
    }

    @Test
    public void skippedRoundStillDue() {
        Plan plan = plan("p1", "07:00", "09:00", "07:30");
        List<PlanHistory.Record> history = new ArrayList<>();
        history.add(record("p1", "07:30", PlanHistory.SKIPPED, at(7, 31)));
        assertEquals("07:30", PlanTimes.firstDueTime(plan, at(8, 0), history));
    }

    @Test
    public void otherPlanRecordDoesNotBlock() {
        Plan plan = plan("p1", "07:00", "09:00", "07:30");
        List<PlanHistory.Record> history = new ArrayList<>();
        history.add(record("p2", "07:30", PlanHistory.COMPLETED, at(7, 31)));
        assertEquals("07:30", PlanTimes.firstDueTime(plan, at(8, 0), history));
    }

    @Test
    public void yesterdayRecordDoesNotBlockToday() {
        Plan plan = plan("p1", "07:00", "09:00", "07:30");
        List<PlanHistory.Record> history = new ArrayList<>();
        history.add(record("p1", "07:30", PlanHistory.COMPLETED, at(7, 31) - 86_400_000L));
        assertEquals("07:30", PlanTimes.firstDueTime(plan, at(8, 0), history));
    }

    @Test
    public void todayAtZeroesSeconds() {
        long value = PlanTimes.todayAt("07:30", at(12, 45));
        Calendar calendar = Calendar.getInstance(SHANGHAI);
        calendar.setTimeInMillis(value);
        assertEquals(7, calendar.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, calendar.get(Calendar.MINUTE));
        assertEquals(0, calendar.get(Calendar.SECOND));
        assertEquals(0, calendar.get(Calendar.MILLISECOND));
    }

    @Test
    public void formatTimeRoundsToMinute() {
        assertEquals("07:30", PlanTimes.formatTime(at(7, 30)));
        assertEquals("23:05", PlanTimes.formatTime(at(23, 5)));
    }
}