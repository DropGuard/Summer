package summer.aot.generator.dummy;
import summer.web.annotation.RestController;
@RestController
public class UserController {
    private final ServiceA serviceA;
    public UserController(ServiceA serviceA) { this.serviceA = serviceA; }
}
