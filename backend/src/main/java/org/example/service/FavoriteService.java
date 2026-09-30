package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.Favorite;
import org.example.domain.StoreListing;
import org.example.domain.User;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.FavoriteRepository;
import org.example.repository.StoreListingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 찜하기. 추가/해제 모두 여러 번 눌러도 결과가 같다 (이미 찜했으면 그대로, 안 찜했으면 해제해도 그대로).
 * 목록 조회는 상품 정보와 함께 내려야 해서 ProductCatalogService.favorites가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final StoreListingRepository storeListingRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public void add(Long productId, Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        if (favoriteRepository.findByUserIdAndStoreListingProductId(user.getId(), productId).isPresent()) return;
        StoreListing listing = storeListingRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("판매 중인 상품이 아닙니다: productId=" + productId));
        try {
            favoriteRepository.saveAndFlush(Favorite.builder().user(user).storeListing(listing).build());
        } catch (DataIntegrityViolationException e) {
            // 두 번 빠르게 눌러 동시에 들어온 경우 - 이미 찜된 상태이므로 성공으로 본다
        }
    }

    @Transactional
    public void remove(Long productId, Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        favoriteRepository.findByUserIdAndStoreListingProductId(user.getId(), productId)
                .ifPresent(favoriteRepository::delete);
    }
}
