package com.investmitra.expense;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WishProductRepository extends JpaRepository<WishProduct, Long> {

	List<WishProduct> findByActiveTrueOrderByPriceAsc();
}
