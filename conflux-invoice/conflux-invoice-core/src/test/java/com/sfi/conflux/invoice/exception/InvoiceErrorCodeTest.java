package com.sfi.conflux.invoice.exception;

import com.sfi.conflux.common.exception.CommonErrorCode;
import com.sfi.conflux.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 错误码三段分层：1xxx 通用 / 2xxx 支付 / 3xxx 发票，互不重叠。 */
class InvoiceErrorCodeTest {

    @Test
    void allInvoiceCodesStayIn3xxxSegment() {
        for (InvoiceErrorCode code : InvoiceErrorCode.values()) {
            assertTrue(code.getCode() >= 3000 && code.getCode() < 4000,
                    "发票错误码应落在 3xxx: " + code);
            assertTrue(code.getMessage() != null && code.getMessage().length() > 0);
            assertTrue(code.getHttpStatus() >= 400 && code.getHttpStatus() < 600);
        }
    }

    @Test
    void commonCodesStayIn1xxxSegment() {
        for (CommonErrorCode code : CommonErrorCode.values()) {
            assertTrue(code.getCode() >= 1000 && code.getCode() < 2000,
                    "通用错误码应落在 1xxx: " + code);
        }
    }

    @Test
    void codesDoNotOverlapBetweenCommonAndInvoice() {
        // 2xxx 段由 pay 能力域独立持有；core 不依赖 pay，此处只校验本模块可见的两段不重叠，
        // pay 段自身的唯一性由其模块内测试覆盖。
        Set<Integer> all = new HashSet<Integer>();
        collect(all, CommonErrorCode.values());
        collect(all, InvoiceErrorCode.values());
        int expected = CommonErrorCode.values().length + InvoiceErrorCode.values().length;
        assertEquals(expected, all.size(), "通用段与发票段不应出现重复数值");
    }

    @Test
    void invoiceSegmentsAreGroupedByMeaning() {
        assertTrue(InvoiceErrorCode.AUTH_SESSION_EXPIRED.getCode() / 100 == 32,
                "授权会话应落在 32xx");
        assertTrue(InvoiceErrorCode.RED_CONFIRM_PENDING.getCode() / 100 == 33,
                "红字确认单应落在 33xx");
        assertTrue(InvoiceErrorCode.FILE_URL_EXPIRED.getCode() / 100 == 34,
                "版式文件应落在 34xx");
    }

    private void collect(Set<Integer> target, ErrorCode[] codes) {
        for (ErrorCode code : codes) {
            target.add(code.getCode());
        }
    }
}
