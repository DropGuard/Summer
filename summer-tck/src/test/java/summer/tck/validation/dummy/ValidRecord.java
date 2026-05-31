package summer.tck.validation.dummy;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record ValidRecord(
		@NotEmpty(message = "Name cannot be empty") @Size(min = 2, message = "Name must be at least 2 characters") String name) {
}
