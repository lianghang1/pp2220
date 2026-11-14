package io.github.lianghang1.conflux.invoice.engine;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 发票状态机：显式迁移表，杜绝非法跃迁。
 *
 * <p>支付域靠各 Orchestrator 内联 if/else 判断终态；发票链路因为存在
 * WAITING_AUTH / PROCESSING / 两段红冲，分支显著更多，把允许迁移集中成一张表
 * 更可控，也让「挂起态可被唤醒、终态不可再变」成为可测试契约。</p>
 *
 * <p>同态迁移（from == to）视为幂等重复回调，允许但不产生实际写入。</p>
 *
 * @author lianghang
 */
public final class InvoiceStateMachine {

    private static final Map<InvoiceState, Set<InvoiceState>> ALLOWED =
            new EnumMap<InvoiceState, Set<InvoiceState>>(InvoiceState.class);

    static {
        allow(InvoiceState.CREATED,
                InvoiceState.SUBMITTING, InvoiceState.FAILED, InvoiceState.CLOSED);
        allow(InvoiceState.SUBMITTING,
                InvoiceState.WAITING_AUTH, InvoiceState.PROCESSING, InvoiceState.SUCCEEDED,
                InvoiceState.FAILED, InvoiceState.CLOSED);
        allow(InvoiceState.WAITING_AUTH,
                InvoiceState.SUBMITTING, InvoiceState.PROCESSING, InvoiceState.SUCCEEDED,
                InvoiceState.FAILED, InvoiceState.CLOSED);
        allow(InvoiceState.PROCESSING,
                InvoiceState.WAITING_AUTH, InvoiceState.SUCCEEDED, InvoiceState.FAILED,
                InvoiceState.CLOSED);
        allow(InvoiceState.SUCCEEDED,
                InvoiceState.RED_APPLYING, InvoiceState.CLOSED);
        allow(InvoiceState.RED_APPLYING,
                InvoiceState.RED_APPLY_CONFIRMED, InvoiceState.SUCCEEDED,
                InvoiceState.FAILED, InvoiceState.CLOSED);
        allow(InvoiceState.RED_APPLY_CONFIRMED,
                InvoiceState.RED_SUCCEEDED, InvoiceState.FAILED, InvoiceState.CLOSED);
        ALLOWED.put(InvoiceState.FAILED, Collections.<InvoiceState>emptySet());
        ALLOWED.put(InvoiceState.CLOSED, Collections.<InvoiceState>emptySet());
        ALLOWED.put(InvoiceState.RED_SUCCEEDED, Collections.<InvoiceState>emptySet());
    }

    private InvoiceStateMachine() {
    }

    private static void allow(InvoiceState from, InvoiceState... targets) {
        EnumSet<InvoiceState> set = EnumSet.noneOf(InvoiceState.class);
        Collections.addAll(set, targets);
        ALLOWED.put(from, Collections.unmodifiableSet(set));
    }

    /** 是否允许从 from 迁移到 to。 */
    public static boolean can(InvoiceState from, InvoiceState to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        Set<InvoiceState> targets = ALLOWED.get(from);
        return targets != null && targets.contains(to);
    }

    /** 不允许时抛 STATE_ILLEGAL，携带当前态与目标态便于排障。 */
    public static void assertTransition(InvoiceState from, InvoiceState to) {
        if (!can(from, to)) {
            throw new GatewayException(InvoiceErrorCode.STATE_ILLEGAL,
                    "非法状态迁移: {} -> {}", from, to);
        }
    }

    /** 该状态允许迁移到的目标集合（只读）。 */
    public static Set<InvoiceState> allowedTargets(InvoiceState from) {
        Set<InvoiceState> targets = ALLOWED.get(from);
        return targets == null ? Collections.<InvoiceState>emptySet() : targets;
    }
}
