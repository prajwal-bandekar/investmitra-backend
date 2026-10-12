package com.investmitra.expense;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.investmitra.entity.AppUser;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

	List<Budget> findByUserAndMonth(AppUser user, String month);
}
