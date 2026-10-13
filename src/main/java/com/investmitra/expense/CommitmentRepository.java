package com.investmitra.expense;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.investmitra.entity.AppUser;

public interface CommitmentRepository extends JpaRepository<Commitment, Long> {

	List<Commitment> findByUserOrderByStartMonthDescIdDesc(AppUser user);
}
