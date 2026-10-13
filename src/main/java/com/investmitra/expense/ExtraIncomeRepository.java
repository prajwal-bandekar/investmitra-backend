package com.investmitra.expense;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.investmitra.entity.AppUser;

public interface ExtraIncomeRepository extends JpaRepository<ExtraIncome, Long> {

	List<ExtraIncome> findByUserAndIncomeMonthOrderByIdDesc(AppUser user, String incomeMonth);
}
