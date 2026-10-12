package com.investmitra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.investmitra.entity.Stock;

public interface StockRepository extends JpaRepository<Stock, Long> {

	Optional<Stock> findBySymbolIgnoreCase(String symbol);

	List<Stock> findByAvSymbolIsNotNull();

	@Query("""
			select s from Stock s
			where (cast(:sector as string) is null or s.sector = :sector)
			  and (cast(:q as string) is null
			       or lower(s.symbol) like lower(concat('%', cast(:q as string), '%'))
			       or lower(s.name) like lower(concat('%', cast(:q as string), '%')))
			order by case when exists (select 1 from StockPrice p where p.stock = s) then 0 else 1 end, s.symbol
			""")
	Page<Stock> search(@Param("q") String query, @Param("sector") String sector, Pageable pageable);

	@Query("select s.sector, count(s), count(s.avSymbol) from Stock s group by s.sector order by s.sector")
	List<Object[]> sectorSummary();
}
