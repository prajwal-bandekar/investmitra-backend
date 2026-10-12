package com.investmitra.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.investmitra.entity.AppUser;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

	@Query("""
			select e from Expense e join fetch e.category
			where e.user = :user and e.spentOn between :from and :to
			  and (cast(:categoryId as long) is null or e.category.id = :categoryId)
			  and (:mode is null or e.paymentMode = :mode)
			order by e.spentOn desc, e.id desc
			""")
	List<Expense> find(@Param("user") AppUser user, @Param("from") LocalDate from, @Param("to") LocalDate to,
			@Param("categoryId") Long categoryId, @Param("mode") PaymentMode mode);

	/** Rows of [categoryId, sum(amount), count]. */
	@Query("""
			select e.category.id, sum(e.amount), count(e) from Expense e
			where e.user = :user and e.spentOn between :from and :to
			group by e.category.id
			""")
	List<Object[]> totalsByCategory(@Param("user") AppUser user, @Param("from") LocalDate from,
			@Param("to") LocalDate to);

	/** Null when there are no expenses in the range. */
	@Query("select sum(e.amount) from Expense e where e.user = :user and e.spentOn between :from and :to")
	BigDecimal total(@Param("user") AppUser user, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
