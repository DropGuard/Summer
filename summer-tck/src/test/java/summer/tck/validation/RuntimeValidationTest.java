package summer.tck.validation;

import summer.core.ApplicationContext;
import summer.scanner.runtime.RuntimeDiEngine;
import summer.tck.validation.dummy.ValidationController;

public class RuntimeValidationTest extends AbstractValidationTCK {

	@Override
	protected ApplicationContext createAndInitializeContext() {
		return new RuntimeDiEngine().create(ValidationController.class);
	}
}
