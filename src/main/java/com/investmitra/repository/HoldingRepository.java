package com.investmitra.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.investmitra.entity.Holding;
import com.investmitra.entity.Portfolio;
import com.investmitra.entity.Stock;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

	Optional<Holding> findByPortfolioAndStock(Portfolio portfolio, Stock stock);

	@Query("select h from Holding h join fetch h.stock where h.portfolio = :portfolio order by h.stock.symbol")
	List<Holding> findByPortfolio(@Param("portfolio") Portfolio portfolio);

	@Query("select h from Holding h join fetch h.stock where h.portfolio in :portfolios")
	List<Holding> findByPortfolioIn(@Param("portfolios") Collection<Portfolio> portfolios);

	/** Holdings in the given stocks across every portfolio the user owns or advises. */
	@Query("""
			select h from Holding h join fetch h.stock
			where h.stock.id in :stockIds
			  and (h.portfolio.owner.id = :userId or h.portfolio.advisor.id = :userId)
			""")
	List<Holding> findManagedByUserIn(@Param("userId") Long userId, @Param("stockIds") Collection<Long> stockIds);
}
