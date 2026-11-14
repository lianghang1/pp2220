package io.github.lianghang1.conflux.invoice.engine;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 状态机：挂起态可唤醒、终态不可变、非法跃迁明确报错。 */
class InvoiceStateMachineTest {

    @Test
    void submittingCanSuspendToWaitingAuth() {
        assertTrue(InvoiceStateMachine.can(InvoiceState.SUBMITTING, InvoiceState.WAITING_AUTH));
        assertTrue(InvoiceStateMachine.can(InvoiceState.WAITING_AUTH, InvoiceState.SUBMITTING));
        assertTrue(InvoiceStateMachine.can(InvoiceState.WAITING_AUTH, InvoiceState.SUCCEEDED));
    }

    @Test
    void asyncProcessingCanFinishOrReenterAuth() {
        assertTrue(InvoiceStateMachine.can(InvoiceState.PROCESSING, InvoiceState.SUCCEEDED));
        assertTrue(InvoiceStateMachine.can(InvoiceState.PROCESSING, InvoiceState.WAITING_AUTH));
    }

    @Test
    void twoStageRedPathIsExplicitlyAllowed() {
        assertTrue(InvoiceStateMachine.can(InvoiceState.SUCCEEDED, InvoiceState.RED_APPLYING));
        assertTrue(InvoiceStateMachine.can(InvoiceState.RED_APPLYING,
                InvoiceState.RED_APPLY_CONFIRMED));
        assertTrue(InvoiceStateMachine.can(InvoiceState.RED_APPLY_CONFIRMED,
                InvoiceState.RED_SUCCEEDED));
        assertFalse(InvoiceStateMachine.can(InvoiceState.SUCCEEDED, InvoiceState.RED_SUCCEEDED));
    }

    @Test
    void terminalStatesHaveNoTargets() {
        for (InvoiceState state : InvoiceState.values()) {
            if (state.isTerminal()) {
                assertTrue(InvoiceStateMachine.allowedTargets(state).isEmpty(),
                        "终态不应有迁移目标: " + state);
                assertFalse(InvoiceStateMachine.can(state, InvoiceState.PROCESSING));
            }
        }
    }

    @Test
    void sameStateIsIdempotentForDuplicateCallbacks() {
        for (InvoiceState state : InvoiceState.values()) {
            assertTrue(InvoiceStateMachine.can(state, state));
        }
    }

    @Test
    void illegalTransitionThrowsStateIllegal() {
        GatewayException ex = assertThrows(GatewayException.class,
                () -> InvoiceStateMachine.assertTransition(
                        InvoiceState.SUCCEEDED, InvoiceState.PROCESSING));
        assertSame(InvoiceErrorCode.STATE_ILLEGAL, ex.getErrorCode());
    }

    @Test
    void suspendedStatesAreNotTerminal() {
        assertTrue(InvoiceState.WAITING_AUTH.isSuspended());
        assertTrue(InvoiceState.RED_APPLYING.isSuspended());
        assertFalse(InvoiceState.WAITING_AUTH.isTerminal());
        assertEquals(2, countSuspended());
    }

    private int countSuspended() {
        int count = 0;
        for (InvoiceState state : InvoiceState.values()) {
            if (state.isSuspended()) {
                count++;
            }
        }
        return count;
    }
}
