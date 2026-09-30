package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.DietType;
import org.example.domain.Product;
import org.example.domain.ProductCategory;
import org.example.domain.StoreListing;
import org.example.dto.admin.AdminProductRequest;
import org.example.dto.admin.AdminProductResponse;
import org.example.exception.DuplicateResourceException;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.ProductRepository;
import org.example.repository.StoreListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 관리자 상품 관리 (판매 상품 = StoreListing이 있는 Product).
 * 삭제는 하지 않는다 - 과거 주문/리뷰/찜이 판매 정보를 참조하므로, 판매를 멈추려면 재고를 0으로 만든다.
 */
@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final ProductRepository productRepository;
    private final StoreListingRepository storeListingRepository;
    private final RestockAlertService restockAlertService;

    @Transactional(readOnly = true)
    public List<AdminProductResponse> list() {
        return storeListingRepository.findAll().stream()
                .sorted(Comparator.comparing(AdminProductService::categoryOf)
                        .thenComparing(listing -> listing.getProduct().getName()))
                .map(AdminProductService::toResponse)
                .toList();
    }

    /**
     * 새 판매 상품 등록. 바코드 스캔으로 이미 저장된 제품(OpenFoodFacts)이면 그 제품에 판매 정보만 붙인다.
     */
    @Transactional
    public AdminProductResponse create(AdminProductRequest request) {
        String barcode = request.getBarcode().trim();
        Product product = productRepository.findByBarcode(barcode).orElse(null);
        if (product != null && storeListingRepository.findByProductId(product.getId()).isPresent()) {
            throw new DuplicateResourceException("이미 판매 중인 바코드입니다: " + barcode);
        }
        if (product == null) {
            product = Product.builder().barcode(barcode).ingredients(Set.of()).build();
        }
        applyProduct(product, request);
        product = productRepository.save(product);

        StoreListing listing = StoreListing.builder()
                .product(product)
                .stock(request.getStock() != null ? request.getStock() : 0)
                .build();
        applyListing(listing, request);
        return toResponse(storeListingRepository.save(listing));
    }

    /** 상품 정보 수정 (재고는 adjustStock으로만 바꾼다) */
    @Transactional
    public AdminProductResponse update(Long productId, AdminProductRequest request) {
        StoreListing listing = findListing(productId);
        Product product = listing.getProduct();

        String barcode = request.getBarcode().trim();
        if (!barcode.equals(product.getBarcode())) {
            productRepository.findByBarcode(barcode).ifPresent(other -> {
                throw new DuplicateResourceException("다른 상품이 이미 쓰고 있는 바코드입니다: " + barcode);
            });
            product.setBarcode(barcode);
        }
        applyProduct(product, request);
        applyListing(listing, request);
        return toResponse(listing);
    }

    @Transactional
    public AdminProductResponse adjustStock(Long productId, int delta) {
        StoreListing listing = storeListingRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("판매 중인 상품이 아닙니다: productId=" + productId));
        int next = listing.getStock() + delta;
        if (next < 0) {
            throw new IllegalArgumentException("재고는 0개 미만이 될 수 없습니다 (현재 " + listing.getStock() + "개)");
        }
        int before = listing.getStock();
        listing.setStock(next);
        // 품절이던 상품에 재고가 생겼으면 재입고 알림 신청자에게 바로 메일 (커밋 후 발송)
        restockAlertService.notifyIfRestocked(listing, before);
        return toResponse(listing);
    }

    private StoreListing findListing(Long productId) {
        return storeListingRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("판매 중인 상품이 아닙니다: productId=" + productId));
    }

    private static void applyProduct(Product product, AdminProductRequest request) {
        product.setName(request.getName().trim());
        product.setRawIngredientsText(blankToNull(request.getRawIngredientsText()));
    }

    private static void applyListing(StoreListing listing, AdminProductRequest request) {
        listing.setPrice(request.getPrice());
        listing.setCategory(request.getCategory());
        listing.setImageUrl(blankToNull(request.getImageUrl()));
        listing.setDescription(blankToNull(request.getDescription()));

        Set<String> allergens = new LinkedHashSet<>();
        if (request.getAllergens() != null) {
            request.getAllergens().stream().map(String::trim).filter(s -> !s.isEmpty()).forEach(allergens::add);
        }
        listing.getAllergens().clear();
        listing.getAllergens().addAll(allergens);

        listing.getSuitableDiets().clear();
        if (request.getDiets() != null) {
            listing.getSuitableDiets().addAll(request.getDiets());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ProductCategory categoryOf(StoreListing listing) {
        return listing.getCategory() != null ? listing.getCategory() : ProductCategory.ETC;
    }

    private static AdminProductResponse toResponse(StoreListing listing) {
        Product product = listing.getProduct();
        ProductCategory category = categoryOf(listing);
        return new AdminProductResponse(
                product.getId(),
                product.getBarcode(),
                product.getName(),
                listing.getPrice(),
                listing.getStock(),
                category.name(),
                category.getLabel(),
                listing.getImageUrl(),
                listing.getDescription(),
                product.getRawIngredientsText(),
                listing.getAllergens().stream().sorted().toList(),
                listing.getSuitableDiets().stream().sorted().map(DietType::name).toList());
    }
}
