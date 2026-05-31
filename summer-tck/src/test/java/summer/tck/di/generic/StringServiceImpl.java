package summer.tck.di.generic;

import summer.core.Component;

/**
 * Implementation of GenericService<String>.
 */
@Component
public class StringServiceImpl implements GenericService<String> {

	@Override
	public String process(String input) {
		return "processed:" + input;
	}
}
