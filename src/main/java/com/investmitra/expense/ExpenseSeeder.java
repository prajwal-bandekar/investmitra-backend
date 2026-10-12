package com.investmitra.expense;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the shared expense categories and the wish-product list on first start.
 * Prices are approximate Indian retail prices; edit the WishProduct rows to keep them current.
 */
@Component
public class ExpenseSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ExpenseSeeder.class);

	record SeedCategory(String name, String icon, boolean want) {
	}

	record SeedProduct(String name, long price, String image) {
	}

	static final List<SeedCategory> CATEGORIES = List.of(
			new SeedCategory("Food delivery", "🍔", true),
			new SeedCategory("Eating out", "☕", true),
			new SeedCategory("Shopping", "🛍️", true),
			new SeedCategory("Entertainment", "🎬", true),
			new SeedCategory("Groceries", "🥦", false),
			new SeedCategory("Travel", "🛺", false),
			new SeedCategory("Rent", "🏠", false),
			new SeedCategory("Bills", "💡", false),
			new SeedCategory("Education", "📚", false),
			new SeedCategory("Other", "🧾", false));

	static final List<SeedProduct> PRODUCTS = List.of(
			new SeedProduct("movie night for two", 1_000, "ticket"),
			new SeedProduct("concert ticket", 6_000, "ticket"),
			new SeedProduct("pair of Air Jordans", 16_995, "sneaker"),
			new SeedProduct("pair of AirPods Pro", 24_900, "earbuds"),
			new SeedProduct("Goa trip", 25_000, "travel"),
			new SeedProduct("pair of Sony XM5 headphones", 29_990, "headphones"),
			new SeedProduct("PS5", 54_990, "console"),
			new SeedProduct("MacBook Air", 1_14_900, "laptop"),
			new SeedProduct("iPhone Pro Max", 1_49_900, "phone"));

	private final ExpenseCategoryRepository categories;
	private final WishProductRepository products;

	public ExpenseSeeder(ExpenseCategoryRepository categories, WishProductRepository products) {
		this.categories = categories;
		this.products = products;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!categories.existsByUserIsNull()) {
			for (SeedCategory s : CATEGORIES) {
				ExpenseCategory c = new ExpenseCategory();
				c.setName(s.name());
				c.setIcon(s.icon());
				c.setWant(s.want());
				categories.save(c);
			}
			log.info("Seeded {} expense categories", CATEGORIES.size());
		}
		if (products.count() == 0) {
			for (SeedProduct s : PRODUCTS) {
				WishProduct p = new WishProduct();
				p.setName(s.name());
				p.setPrice(BigDecimal.valueOf(s.price()));
				p.setImage(s.image());
				products.save(p);
			}
			log.info("Seeded {} wish products", PRODUCTS.size());
		}
	}
}
