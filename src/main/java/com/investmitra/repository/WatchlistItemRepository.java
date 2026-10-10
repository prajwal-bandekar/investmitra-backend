package com.investmitra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.investmitra.entity.AppUser;
import com.investmitra.entity.Stock;
import com.investmitra.entity.WatchlistItem;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

	@Query("select w from WatchlistItem w join fetch w.stock where w.user = :user order by w.addedOn desc, w.id desc")
	List<WatchlistItem> findByUser(@Param("user") AppUser user);

	Optional<WatchlistItem> findByUserAndStock(AppUser user, Stock stock);
}
