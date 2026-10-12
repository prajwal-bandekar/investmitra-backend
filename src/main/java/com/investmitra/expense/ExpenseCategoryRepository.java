package com.investmitra.expense;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.investmitra.entity.AppUser;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

	/** Shared defaults plus the user's own, defaults first. */
	@Query("""
			select c from ExpenseCategory c
			where (c.user is null or c.user = :user) and c.archived = false
			order by case when c.user is null then 0 else 1 end, c.id
			""")
	List<ExpenseCategory> visibleTo(@Param("user") AppUser user);

	boolean existsByUserIsNull();
}
