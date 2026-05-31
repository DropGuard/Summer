package summer.tck.validation.dummy;

import jakarta.validation.Valid;
import summer.core.Component;
import summer.web.annotation.Post;
import summer.web.annotation.RestController;

@Component
@RestController("/api/validation")
public class ValidationController {

	@Post("/submit")
	public String submit(@Valid ValidRecord body) {
		return "ok:" + body.name();
	}
}
