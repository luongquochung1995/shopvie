package com.shopvi.shipping.infra;

import com.shopvi.shipping.domain.Shipment;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByOrderCode(String orderCode);

    List<Shipment> findByOrderCodeIn(Collection<String> orderCodes);

    /** Khóa vận đơn khi xử lý webhook để các bản tin đến cùng lúc không ghi đè nhau. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shipment s WHERE s.trackingCode = :trackingCode")
    Optional<Shipment> findByTrackingCodeForUpdate(String trackingCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shipment s WHERE s.orderCode = :orderCode")
    Optional<Shipment> findByOrderCodeForUpdate(String orderCode);

    boolean existsByTrackingCode(String trackingCode);
}
