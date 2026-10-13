package com.investmitra.expense;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.investmitra.entity.AppUser;

public interface SalarySettingRepository extends JpaRepository<SalarySetting, Long> {

	Optional<SalarySetting> findFirstByUserAndEffectiveMonthLessThanEqualOrderByEffectiveMonthDesc(AppUser user,
			String month);

	Optional<SalarySetting> findByUserAndEffectiveMonth(AppUser user, String month);
}
