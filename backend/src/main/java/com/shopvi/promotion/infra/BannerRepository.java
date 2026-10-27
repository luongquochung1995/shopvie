package com.shopvi.promotion.infra;

import com.shopvi.promotion.domain.Banner;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    List<Banner> findByActiveTrueOrderByPositionAscIdAsc();

    List<Banner> findAllByOrderByPositionAscIdAsc();
}
