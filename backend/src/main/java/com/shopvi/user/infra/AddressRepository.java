package com.shopvi.user.infra;

import com.shopvi.user.domain.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AddressRepository extends JpaRepository<Address, Long> {

    /** Địa chỉ mặc định lên đầu, sau đó mới nhất trước. */
    @Query("SELECT a FROM Address a WHERE a.userId = :userId ORDER BY a.isDefault DESC, a.createdAt DESC, a.id DESC")
    List<Address> findAllOfUser(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    Optional<Address> findFirstByUserIdAndIsDefaultTrue(Long userId);

    Optional<Address> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /**
     * Bỏ cờ mặc định của mọi địa chỉ (chạy trước khi đặt default mới để không vi phạm unique index).
     * Không clear persistence context: entity đang được sửa vẫn managed.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.userId = :userId AND a.isDefault = true")
    int clearDefault(Long userId);
}
