import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import java.io.FileInputStream;
public class ReadJandex {
    public static void main(String[] args) throws Exception {
        try (FileInputStream fis = new FileInputStream(args[0])) {
            IndexView idx = new IndexReader(fis).read();
            System.out.println("Classes: " + idx.getKnownClasses().size());
            for (org.jboss.jandex.ClassInfo ci : idx.getKnownClasses()) {
                if (ci.name().toString().contains("RuntimeWebConfiguration")) {
                    System.out.println("Found: " + ci.name());
                }
            }
        }
    }
}
