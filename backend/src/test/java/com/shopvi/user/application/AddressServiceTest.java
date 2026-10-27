package com.shopvi.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.user.domain.Address;
import com.shopvi.user.domain.Address.AddressData;
import com.shopvi.user.infra.AddressRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddressServiceTest {

    private static final AddressData DATA = new AddressData("A", "0901234567", "79", null, "26740", "1 Lê Lợi");

    private final AddressRepository repository = mock(AddressRepository.class);
    private final AddressService service = new AddressService(repository);

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void firstAddressBecomesDefault() {
        when(repository.countByUserId(1L)).thenReturn(0L);

        assertThat(service.create(1L, DATA, false).isDefault()).isTrue();
        verify(repository).clearDefault(1L);
    }

    @Test
    void laterAddressIsNotDefaultUnlessRequested() {
        when(repository.countByUserId(1L)).thenReturn(2L);

        assertThat(service.create(1L, DATA, false).isDefault()).isFalse();
        verify(repository, never()).clearDefault(any());
    }

    @Test
    void requestingDefaultClearsPreviousDefault() {
        when(repository.countByUserId(1L)).thenReturn(2L);

        assertThat(service.create(1L, DATA, true).isDefault()).isTrue();
        verify(repository).clearDefault(1L);
    }

    @Test
    void addressBookIsLimited() {
        when(repository.countByUserId(1L)).thenReturn((long) AddressService.MAX_ADDRESSES);

        assertThatThrownBy(() -> service.create(1L, DATA, false))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ADDRESS_LIMIT_REACHED);
    }

    @Test
    void makeDefaultOnAlreadyDefaultDoesNotClear() {
        Address address = Address.create(1L, DATA);
        address.markDefault(true);
        when(repository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(address));

        service.makeDefault(1L, 5L);

        verify(repository, never()).clearDefault(any());
        assertThat(address.isDefault()).isTrue();
    }

    @Test
    void deletingDefaultPromotesNewestRemaining() {
        Address current = Address.create(1L, DATA);
        current.markDefault(true);
        Address next = Address.create(1L, DATA);
        when(repository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(current));
        when(repository.findFirstByUserIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(Optional.of(next));

        service.delete(1L, 5L);

        verify(repository).delete(current);
        assertThat(next.isDefault()).isTrue();
    }

    @Test
    void addressOfAnotherUserIsNotFound() {
        when(repository.findByIdAndUserId(5L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(2L, 5L, DATA, false))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ADDRESS_NOT_FOUND);
    }
}
