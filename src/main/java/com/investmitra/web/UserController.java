package com.investmitra.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.investmitra.service.UserService;
import com.investmitra.web.dto.Dtos.CreateUserRequest;
import com.investmitra.web.dto.Dtos.LoginRequest;
import com.investmitra.web.dto.Dtos.UserView;
import com.investmitra.web.dto.ResponseStructure;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService users;

	public UserController(UserService users) {
		this.users = users;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseStructure<UserView> create(@Valid @RequestBody CreateUserRequest request) {
		return ResponseStructure.created(users.create(request), "User created");
	}

	/**
	 * Checks email and password and returns the user. The client then sends X-User-Id;
	 * swap this for issued tokens before going public.
	 */
	@PostMapping("/login")
	public ResponseStructure<UserView> login(@Valid @RequestBody LoginRequest request) {
		return ResponseStructure.ok(users.login(request), "Signed in");
	}

	@GetMapping("/{id}")
	public ResponseStructure<UserView> get(@PathVariable Long id) {
		return ResponseStructure.ok(users.get(id), "User found");
	}
}
