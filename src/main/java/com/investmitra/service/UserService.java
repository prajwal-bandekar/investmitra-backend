package com.investmitra.service;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.investmitra.entity.AppUser;
import com.investmitra.repository.AppUserRepository;
import com.investmitra.web.dto.Dtos;
import com.investmitra.web.dto.Dtos.CreateUserRequest;
import com.investmitra.web.dto.Dtos.LoginRequest;
import com.investmitra.web.dto.Dtos.UserView;

@Service
public class UserService {

	private final AppUserRepository users;
	private final BigDecimal startingCash;
	private final PasswordEncoder passwords = new BCryptPasswordEncoder();

	public UserService(AppUserRepository users, @Value("${trading.starting-cash}") BigDecimal startingCash) {
		this.users = users;
		this.startingCash = startingCash;
	}

	@Transactional
	public UserView create(CreateUserRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		if (users.existsByEmailIgnoreCase(email)) {
			throw ApiException.conflict("Email already registered: " + email);
		}
		AppUser user = new AppUser();
		user.setName(request.name().trim());
		user.setEmail(email);
		user.setPasswordHash(passwords.encode(request.password()));
		user.setRole(request.role());
		user.setCashBalance(startingCash);
		try {
			return view(users.saveAndFlush(user));
		} catch (DataIntegrityViolationException e) {
			// Two signups with the same email raced past the check above; the unique index caught it.
			throw ApiException.conflict("Email already registered: " + email);
		}
	}

	/** Checks email and password. The same error is used for both so emails can't be probed. */
	@Transactional(readOnly = true)
	public UserView login(LoginRequest request) {
		return users.findByEmailIgnoreCase(request.email().trim())
				.filter(u -> passwords.matches(request.password(), u.getPasswordHash()))
				.map(UserService::view)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Wrong email or password"));
	}

	@Transactional(readOnly = true)
	public UserView get(Long id) {
		return view(require(id));
	}

	public AppUser require(Long id) {
		return users.findById(id).orElseThrow(() -> ApiException.notFound("Unknown user: " + id));
	}

	static UserView view(AppUser u) {
		return new UserView(u.getId(), Dtos.clientId(u.getId()), u.getName(), u.getEmail(), u.getRole(),
				u.getCashBalance());
	}
}
