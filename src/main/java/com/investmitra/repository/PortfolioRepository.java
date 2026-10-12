package com.investmitra.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.investmitra.entity.Portfolio;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

	boolean existsByOwnerIdAndAdvisorId(Long ownerId, Long advisorId);

	@Query("select p from Portfolio p where p.owner.id = :userId or p.advisor.id = :userId order by p.id")
	List<Portfolio> findManagedBy(@Param("userId") Long userId);
}
