package com.minitb.common;

import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	// Our hand-written constraint names -> messages a client can understand.
	private static final Map<String, String> CONSTRAINT_MESSAGES = Map.of(
			"tenant_title_unq", "A tenant with this title already exists",
			"profile_name_unq", "A device profile with this name already exists",
			"device_name_unq", "A device with this name already exists",
			"profile_tenant_fk", "Tenant does not exist",
			"device_tenant_fk", "Tenant does not exist");

	@ExceptionHandler(NotFoundException.class)
	public ProblemDetail handleNotFound(NotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrity(DataIntegrityViolationException e) {
		String constraint = findConstraintName(e);
		String message = CONSTRAINT_MESSAGES.getOrDefault(constraint, "Request violates a data constraint");
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message);
	}

	private static String findConstraintName(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof ConstraintViolationException cve) {
				return cve.getConstraintName();
			}
		}
		return null;
	}

}
