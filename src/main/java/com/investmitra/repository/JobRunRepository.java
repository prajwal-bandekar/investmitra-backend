package com.investmitra.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.investmitra.entity.JobRun;

public interface JobRunRepository extends JpaRepository<JobRun, Long> {

	List<JobRun> findTop20ByOrderByStartedAtDesc();

	boolean existsBySourceAndStatusAndStartedAtGreaterThanEqualAndPricesSavedGreaterThan(String source, com.investmitra.entity.JobStatus status,
			Instant since, int minPrices);

	@Query("select coalesce(sum(j.callsMade), 0) from JobRun j where j.startedAt >= :since")
	int callsMadeSince(@Param("since") Instant since);
}
