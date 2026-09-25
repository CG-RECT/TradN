package com.tradn.timeline.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

/** 验证黄金每日复盘只在时间线新增关联笔记时触发。 */
class TimelineServiceTest {
    @Test
    void onlyNewLinkedNoteTriggersSummary() {
        HashSet<Long> previous = new HashSet<Long>(Collections.singletonList(10L));

        assertFalse(TimelineService.hasNewLinkedNote(previous, Collections.singletonList(10L)));
        assertTrue(TimelineService.hasNewLinkedNote(previous, Arrays.asList(10L, 11L)));
        assertFalse(TimelineService.hasNewLinkedNote(previous, Collections.<Long>emptyList()));
        assertFalse(TimelineService.hasNewLinkedNote(previous, null));
    }

    @Test
    void reorderMustContainEveryEntryExactlyOnce() {
        assertTrue(
                TimelineService.isCompleteEntryOrder(
                        Arrays.asList(10L, 11L, 12L), Arrays.asList(12L, 10L, 11L)));
        assertFalse(
                TimelineService.isCompleteEntryOrder(
                        Arrays.asList(10L, 11L, 12L), Arrays.asList(10L, 11L)));
        assertFalse(
                TimelineService.isCompleteEntryOrder(
                        Arrays.asList(10L, 11L, 12L), Arrays.asList(10L, 10L, 12L)));
        assertFalse(
                TimelineService.isCompleteEntryOrder(
                        Arrays.asList(10L, 11L, 12L), Arrays.asList(10L, 11L, 99L)));
    }
}
