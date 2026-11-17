package io.github.lianghang1.conflux.pay.store;

import io.github.lianghang1.conflux.pay.model.NotifyLog;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 下游通知记录持久化 SPI。可选能力：未注册时下游通知不落库、重试兜底不可用。
 *
 * <p>建议索引：{@code nextNotifyTime + completed} 组合索引（到期扫描）、
 * {@code notifyId} 主键。</p>
 *
 * @author lianghang
 */
public interface NotifyLogStore {

    /** 新增通知记录。 */
    void save(NotifyLog notifyLog);

    /** 全量更新通知记录（按 notifyId 覆盖）。 */
    void update(NotifyLog notifyLog);

    /** 查询到期未完成的通知记录（nextNotifyTime <= threshold 且 completed=false），按 nextNotifyTime 升序返回前 limit 条。 */
    List<NotifyLog> findDueNotifies(LocalDateTime threshold, int limit);
}
