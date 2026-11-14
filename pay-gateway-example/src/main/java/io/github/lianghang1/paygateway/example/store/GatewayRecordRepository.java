package io.github.lianghang1.paygateway.example.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 网关记录表访问接口（演示用方的存储实现姿势）。
 *
 * @author lianghang
 */
public interface GatewayRecordRepository extends JpaRepository<GatewayRecordEntity, String> {

    Optional<GatewayRecordEntity> findByRecordTypeAndIdempotentKey(String recordType, String idempotentKey);

    List<GatewayRecordEntity> findByRecordType(String recordType);

    List<GatewayRecordEntity> findByRecordTypeAndChannelCode(String recordType, String channelCode);

    Optional<GatewayRecordEntity> findByRecordTypeAndChannelOrderNo(String recordType, String channelOrderNo);
}
