package com.shopvi.user.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.user.domain.Address;
import com.shopvi.user.domain.Address.AddressData;
import com.shopvi.user.infra.AddressRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sổ địa chỉ. Quy tắc: mỗi user có đúng 1 địa chỉ mặc định khi sổ không rỗng.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressService {

    static final int MAX_ADDRESSES = 20;

    private final AddressRepository addressRepository;

    public List<Address> list(Long userId) {
        return addressRepository.findAllOfUser(userId);
    }

    @Transactional
    public Address create(Long userId, AddressData data, boolean makeDefault) {
        long count = addressRepository.countByUserId(userId);
        if (count >= MAX_ADDRESSES) {
            throw new BusinessException(ErrorCode.ADDRESS_LIMIT_REACHED);
        }
        // Địa chỉ đầu tiên luôn là mặc định
        boolean isDefault = makeDefault || count == 0;
        if (isDefault) {
            addressRepository.clearDefault(userId);
        }
        Address address = Address.create(userId, data);
        address.markDefault(isDefault);
        return addressRepository.save(address);
    }

    @Transactional
    public Address update(Long userId, Long addressId, AddressData data, boolean makeDefault) {
        Address address = getOwned(userId, addressId);
        address.apply(data);
        if (makeDefault) {
            setDefault(userId, address);
        }
        return address;
    }

    @Transactional
    public Address makeDefault(Long userId, Long addressId) {
        Address address = getOwned(userId, addressId);
        setDefault(userId, address);
        return address;
    }

    @Transactional
    public void delete(Long userId, Long addressId) {
        Address address = getOwned(userId, addressId);
        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);
        addressRepository.flush();
        if (wasDefault) {
            // Chuyển mặc định sang địa chỉ mới nhất còn lại
            addressRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId)
                    .ifPresent(next -> next.markDefault(true));
        }
    }

    private void setDefault(Long userId, Address address) {
        if (!address.isDefault()) {
            addressRepository.clearDefault(userId);
            address.markDefault(true);
        }
    }

    /** Không tìm thấy hoặc không thuộc user → 404 (không tiết lộ địa chỉ của người khác). */
    private Address getOwned(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
    }
}
