package com.investmitra.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.investmitra.entity.AppUser;

import jakarta.persistence.LockModeType;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	boolean existsByEmailIgnoreCase(String email);

	Optional<AppUser> findByEmailIgnoreCase(String email);

	/** Locks the row so concurrent trades cannot spend the same cash twice. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from AppUser u where u.id = :id")
	Optional<AppUser> findByIdForUpdate(@Param("id") Long id);
}
