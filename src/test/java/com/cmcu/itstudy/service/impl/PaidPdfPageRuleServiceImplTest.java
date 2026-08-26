package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.PreviewLockedReason;
import com.cmcu.itstudy.handle.PaidPreviewUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaidPdfPageRuleServiceImplTest {

    private PaidPdfPageRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaidPdfPageRuleServiceImpl();
    }

    @Test
    void testOver20PagesShows5Pages() {
        assertEquals(5, service.calculateLimitedPreviewPageCount(25));
        assertEquals(5, service.calculateLimitedPreviewPageCount(50));
        assertEquals(5, service.calculateLimitedPreviewPageCount(20));
    }

    @Test
    void testBetween5And19PagesShows2Pages() {
        assertEquals(2, service.calculateLimitedPreviewPageCount(19));
        assertEquals(2, service.calculateLimitedPreviewPageCount(10));
        assertEquals(2, service.calculateLimitedPreviewPageCount(5));
    }

    @Test
    void testUnder5PagesShows1Page() {
        assertEquals(1, service.calculateLimitedPreviewPageCount(4));
        assertEquals(1, service.calculateLimitedPreviewPageCount(3));
        assertEquals(1, service.calculateLimitedPreviewPageCount(2));
    }

    @Test
    void testSinglePageThrowsLockedException() {
        PaidPreviewUnavailableException ex = assertThrows(
                PaidPreviewUnavailableException.class,
                () -> service.calculateLimitedPreviewPageCount(1)
        );
        assertEquals(PreviewLockedReason.ONE_PAGE_PAID_DOCUMENT, ex.getReason());
    }

    @Test
    void testZeroOrNegativeThrowsUnavailableException() {
        PaidPreviewUnavailableException ex = assertThrows(
                PaidPreviewUnavailableException.class,
                () -> service.calculateLimitedPreviewPageCount(0)
        );
        assertEquals(PreviewLockedReason.PREVIEW_UNAVAILABLE, ex.getReason());
    }
}
