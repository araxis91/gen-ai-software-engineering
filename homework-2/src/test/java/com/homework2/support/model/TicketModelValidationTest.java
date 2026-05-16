package com.homework2.support.model;

import com.homework2.support.TestDataFactory;
import com.homework2.support.api.dto.TicketRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TicketModelValidationTest {
    private static Validator validator;

    @BeforeAll
    static void setupValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validRequestHasNoViolations() {
        TicketRequest request = TestDataFactory.validTicketRequest();

        Set<ConstraintViolation<TicketRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void invalidEmailIsRejected() {
        TicketRequest request = new TicketRequest(
                "cust-001",
                "not-an-email",
                "Jane Customer",
                "Cannot access account",
                "Cannot access account after 2FA setup and need immediate support.",
                TestDataFactory.validTicketRequest().category(),
                TestDataFactory.validTicketRequest().priority(),
                TestDataFactory.validTicketRequest().status(),
                null,
                null,
                TestDataFactory.validTicketRequest().tags(),
                TestDataFactory.validTicketRequest().metadata()
        );

        Set<ConstraintViolation<TicketRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("customerEmail");
    }

    @Test
    void shortDescriptionIsRejected() {
        TicketRequest request = TestDataFactory.validTicketRequest("Subject", "short");

        Set<ConstraintViolation<TicketRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("description");
    }

    @Test
    void blankSubjectIsRejected() {
        TicketRequest request = TestDataFactory.validTicketRequest("   ", "Valid description with enough length.");

        Set<ConstraintViolation<TicketRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("subject");
    }

    @Test
    void nullMetadataIsRejected() {
        TicketRequest valid = TestDataFactory.validTicketRequest();
        TicketRequest request = new TicketRequest(
                valid.customerId(),
                valid.customerEmail(),
                valid.customerName(),
                valid.subject(),
                valid.description(),
                valid.category(),
                valid.priority(),
                valid.status(),
                valid.resolvedAt(),
                valid.assignedTo(),
                valid.tags(),
                null
        );

        Set<ConstraintViolation<TicketRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("metadata");
    }

    @Test
    void nullEnumFieldsAreRejected() {
        TicketRequest valid = TestDataFactory.validTicketRequest();
        TicketRequest request = new TicketRequest(
                valid.customerId(),
                valid.customerEmail(),
                valid.customerName(),
                valid.subject(),
                valid.description(),
                null,
                null,
                null,
                valid.resolvedAt(),
                valid.assignedTo(),
                valid.tags(),
                valid.metadata()
        );

        Set<ConstraintViolation<TicketRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("category", "priority", "status");
    }

    @Test
    void blankTagsAreRejected() {
        TicketRequest valid = TestDataFactory.validTicketRequest();
        TicketRequest request = new TicketRequest(
                valid.customerId(),
                valid.customerEmail(),
                valid.customerName(),
                valid.subject(),
                valid.description(),
                valid.category(),
                valid.priority(),
                valid.status(),
                valid.resolvedAt(),
                valid.assignedTo(),
                java.util.List.of("valid-tag", " "),
                valid.metadata()
        );

        Set<ConstraintViolation<TicketRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .anyMatch(path -> path.contains("tags"));
    }
}
