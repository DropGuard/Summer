package summer.tck.validation.dummy;

import summer.core.Component;
import summer.web.annotation.Post;
import summer.web.annotation.RestController;

@Component
@RestController("/api/validation")
public class ValidationController {

	@Post("/submit")
	public String submit(@summer.web.annotation.Valid ValidRecord body) {
		return "ok:" + body.name();
	}
}
