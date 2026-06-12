package summer.fixtures.di.generic;

import summer.core.Component;

@Component
public class StringServiceImpl implements GenericService<String> {

	@Override
	public String process(String input) {
		return "processed:" + input;
	}
}
