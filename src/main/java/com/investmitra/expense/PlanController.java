package com.investmitra.expense;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.investmitra.expense.PlanDtos.CommitmentRequest;
import com.investmitra.expense.PlanDtos.CommitmentView;
import com.investmitra.expense.PlanDtos.ExtraIncomeRequest;
import com.investmitra.expense.PlanDtos.ExtraIncomeView;
import com.investmitra.expense.PlanDtos.PlanView;
import com.investmitra.expense.PlanDtos.SalaryRequest;
import com.investmitra.web.dto.ResponseStructure;

import jakarta.validation.Valid;

/** Free to spend endpoints. Months are YYYY-MM and default to the current IST month. */
@RestController
@RequestMapping("/api")
public class PlanController {

	private final PlanService service;

	public PlanController(PlanService service) {
		this.service = service;
	}

	@GetMapping("/plan")
	public ResponseStructure<PlanView> plan(@RequestHeader("X-User-Id") Long userId,
			@RequestParam(required = false) String month) {
		return ResponseStructure.ok(service.plan(userId, month), "Plan");
	}

	@PutMapping("/plan/salary")
	public ResponseStructure<PlanView> setSalary(@RequestHeader("X-User-Id") Long userId,
			@Valid @RequestBody SalaryRequest request) {
		return ResponseStructure.ok(service.setSalary(userId, request), "Salary saved");
	}

	@GetMapping("/commitments")
	public ResponseStructure<List<CommitmentView>> commitments(@RequestHeader("X-User-Id") Long userId) {
		return ResponseStructure.ok(service.commitments(userId), "Commitments");
	}

	@PostMapping("/commitments")
	public ResponseStructure<CommitmentView> createCommitment(@RequestHeader("X-User-Id") Long userId,
			@Valid @RequestBody CommitmentRequest request) {
		return ResponseStructure.created(service.createCommitment(userId, request), "Commitment added");
	}

	@PutMapping("/commitments/{id}")
	public ResponseStructure<CommitmentView> updateCommitment(@RequestHeader("X-User-Id") Long userId,
			@PathVariable Long id, @Valid @RequestBody CommitmentRequest request) {
		return ResponseStructure.ok(service.updateCommitment(userId, id, request), "Commitment updated");
	}

	@DeleteMapping("/commitments/{id}")
	public ResponseStructure<Void> deleteCommitment(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
		service.deleteCommitment(userId, id);
		return ResponseStructure.ok(null, "Commitment deleted");
	}

	@GetMapping("/extra-income")
	public ResponseStructure<List<ExtraIncomeView>> extraIncome(@RequestHeader("X-User-Id") Long userId,
			@RequestParam(required = false) String month) {
		return ResponseStructure.ok(service.extraIncome(userId, month), "Extra income");
	}

	@PostMapping("/extra-income")
	public ResponseStructure<ExtraIncomeView> addExtraIncome(@RequestHeader("X-User-Id") Long userId,
			@Valid @RequestBody ExtraIncomeRequest request) {
		return ResponseStructure.created(service.addExtraIncome(userId, request), "Extra income added");
	}

	@DeleteMapping("/extra-income/{id}")
	public ResponseStructure<Void> deleteExtraIncome(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
		service.deleteExtraIncome(userId, id);
		return ResponseStructure.ok(null, "Extra income deleted");
	}
}
