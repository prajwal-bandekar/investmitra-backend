package com.investmitra.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.investmitra.entity.Portfolio;
import com.investmitra.entity.Trade;

public interface TradeRepository extends JpaRepository<Trade, Long> {

	java.util.Optional<Trade> findByPortfolioAndClientRequestId(Portfolio portfolio, String clientRequestId);

	@Query("select t from Trade t join fetch t.stock where t.portfolio = :portfolio order by t.executedAt desc, t.id desc")
	List<Trade> findByPortfolioNewestFirst(@Param("portfolio") Portfolio portfolio);

	@Query("select coalesce(sum(t.realizedPnl), 0) from Trade t where t.portfolio = :portfolio")
	BigDecimal totalRealizedPnl(@Param("portfolio") Portfolio portfolio);
}
