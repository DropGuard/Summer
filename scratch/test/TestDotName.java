package test;
public class TestDotName {
    public static void main(String[] args) {
        System.out.println("Methods in DotName:");
        for (java.lang.reflect.Method m : org.jboss.jandex.DotName.class.getDeclaredMethods()) {
            if (m.getName().equals("createSimple")) {
                System.out.println(m);
            }
        }
    }
}
