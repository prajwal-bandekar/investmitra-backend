package com.investmitra.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.investmitra.entity.Stock;
import com.investmitra.entity.StockPrice;

public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

	Optional<StockPrice> findByStockAndTradeDate(Stock stock, LocalDate tradeDate);

	Optional<StockPrice> findTopByStockOrderByTradeDateDesc(Stock stock);

	/** Latest snapshot for each of the given stocks. */
	@Query("""
			select p from StockPrice p join fetch p.stock
			where p.stock.id in :stockIds
			  and p.tradeDate = (select max(p2.tradeDate) from StockPrice p2 where p2.stock = p.stock)
			""")
	List<StockPrice> findLatestFor(@Param("stockIds") Collection<Long> stockIds);

	/** Most recent fetch time per tracked stock, used to fetch the stalest stocks first. */
	@Query("select p.stock.id, max(p.fetchedAt) from StockPrice p group by p.stock.id")
	List<Object[]> lastFetchedPerStock();
}
