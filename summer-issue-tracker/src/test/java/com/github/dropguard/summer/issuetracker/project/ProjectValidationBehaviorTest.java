package com.github.dropguard.summer.issuetracker.project;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.avaje.validation.ConstraintViolationException;
import io.avaje.validation.Validator;
import org.junit.jupiter.api.Test;

/**
 * Behavior contract for the request validation: the avaje-generated adapter must be present and the
 * constraints actually enforced. A missing {@code @ImportValidPojo} adapter throws
 * IllegalArgumentException ("No ValidationAdapter") at validate time instead — which used to
 * surface as a 500 in the demo ITs; this unit test pins the validation behavior at the component
 * layer.
 */
class ProjectValidationBehaviorTest {

    private final Validator validator = Validator.builder().build();

    @Test
    void blankProjectRequestIsRejected() {
        assertThrows(
                ConstraintViolationException.class,
                () -> validator.validate(new ProjectController.CreateProjectRequest("", "")));
    }

    @Test
    void validProjectRequestPassesValidation() {
        assertDoesNotThrow(
                () ->
                        validator.validate(
                                new ProjectController.CreateProjectRequest("KEY", "Team")));
    }
}
