package org.example.repository;

import org.example.domain.ScanHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScanHistoryRepository extends JpaRepository<ScanHistory, Long> {
    // 검사 기록 화면 - 최근 것부터 한 페이지씩 (같은 시각이면 id로 순서 고정)
    Slice<ScanHistory> findByUserIdOrderByScannedAtDescIdDesc(Long userId, Pageable pageable);

    // 식습관 통계 - 이 시각 이후 검사 기록 (오래된 순)
    List<ScanHistory> findByUserIdAndScannedAtGreaterThanEqualOrderByScannedAtAsc(Long userId, java.time.LocalDateTime from);
    Optional<ScanHistory> findByIdAndUserId(Long id, Long userId);
    void deleteByUserId(Long userId);
}
