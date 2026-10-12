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

import com.investmitra.expense.ExpenseDtos.BudgetRequest;
import com.investmitra.expense.ExpenseDtos.BudgetView;
import com.investmitra.expense.ExpenseDtos.CategoryRequest;
import com.investmitra.expense.ExpenseDtos.CategoryView;
import com.investmitra.expense.ExpenseDtos.ExpenseRequest;
import com.investmitra.expense.ExpenseDtos.ExpenseView;
import com.investmitra.expense.ExpenseDtos.MonthSummary;
import com.investmitra.web.dto.ResponseStructure;

import jakarta.validation.Valid;

/** ExpenseMitra endpoints. Months are YYYY-MM and default to the current IST month. */
@RestController
@RequestMapping("/api")
public class ExpenseController {

	private final ExpenseService service;

	public ExpenseController(ExpenseService service) {
		this.service = service;
	}

	@GetMapping("/expense-categories")
	public ResponseStructure<List<CategoryView>> categories(@RequestHeader("X-User-Id") Long userId) {
		return ResponseStructure.ok(service.categories(userId), "Categories");
	}

	@PostMapping("/expense-categories")
	public ResponseStructure<CategoryView> createCategory(@RequestHeader("X-User-Id") Long userId,
			@Valid @RequestBody CategoryRequest request) {
		return ResponseStructure.created(service.createCategory(userId, request), "Category added");
	}

	@PutMapping("/expense-categories/{id}")
	public ResponseStructure<CategoryView> updateCategory(@RequestHeader("X-User-Id") Long userId,
			@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
		return ResponseStructure.ok(service.updateCategory(userId, id, request), "Category updated");
	}

	@DeleteMapping("/expense-categories/{id}")
	public ResponseStructure<Void> archiveCategory(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
		service.archiveCategory(userId, id);
		return ResponseStructure.ok(null, "Category archived");
	}

	@GetMapping("/expenses")
	public ResponseStructure<List<ExpenseView>> list(@RequestHeader("X-User-Id") Long userId,
			@RequestParam(required = false) String month, @RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) PaymentMode paymentMode) {
		return ResponseStructure.ok(service.list(userId, month, categoryId, paymentMode), "Expenses");
	}

	@PostMapping("/expenses")
	public ResponseStructure<ExpenseView> create(@RequestHeader("X-User-Id") Long userId,
			@Valid @RequestBody ExpenseRequest request) {
		return ResponseStructure.created(service.create(userId, request), "Expense logged");
	}

	@PutMapping("/expenses/{id}")
	public ResponseStructure<ExpenseView> update(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id,
			@Valid @RequestBody ExpenseRequest request) {
		return ResponseStructure.ok(service.update(userId, id, request), "Expense updated");
	}

	@DeleteMapping("/expenses/{id}")
	public ResponseStructure<Void> delete(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
		service.delete(userId, id);
		return ResponseStructure.ok(null, "Expense deleted");
	}

	@GetMapping("/expenses/summary")
	public ResponseStructure<MonthSummary> summary(@RequestHeader("X-User-Id") Long userId,
			@RequestParam(required = false) String month) {
		return ResponseStructure.ok(service.summary(userId, month), "Summary");
	}

	@GetMapping("/budgets")
	public ResponseStructure<List<BudgetView>> budgets(@RequestHeader("X-User-Id") Long userId,
			@RequestParam(required = false) String month) {
		return ResponseStructure.ok(service.budgets(userId, month), "Budgets");
	}

	@PutMapping("/budgets")
	public ResponseStructure<List<BudgetView>> saveBudgets(@RequestHeader("X-User-Id") Long userId,
			@Valid @RequestBody BudgetRequest request) {
		return ResponseStructure.ok(service.saveBudgets(userId, request), "Budgets saved");
	}
}
