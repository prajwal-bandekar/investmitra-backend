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
			where (:sector is null or s.sector = :sector)
			  and (:q is null
			       or lower(s.symbol) like lower(concat('%', :q, '%'))
			       or lower(s.name) like lower(concat('%', :q, '%')))
			order by case when s.avSymbol is null then 1 else 0 end, s.symbol
			""")
	Page<Stock> search(@Param("q") String query, @Param("sector") String sector, Pageable pageable);

	@Query("select s.sector, count(s), count(s.avSymbol) from Stock s group by s.sector order by s.sector")
	List<Object[]> sectorSummary();
}
