package summer.compiler;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.*;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.File;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

public class AotZeroReflectionTest {

	@Test
	public void testAotProxyDoesNotUseReflection() throws Exception {
		// Read the dummy Java files located in the same test directory
		File dummyDir = new File("src/test/java/summer/compiler/dummy");

		Compilation compilation = javac().withProcessors(new SummerProcessor()).compile(
				JavaFileObjects.forResource(new File(dummyDir, "DummyInterface.java").toURI().toURL()),
				JavaFileObjects.forResource(new File(dummyDir, "DummyAnnotation.java").toURI().toURL()),
				JavaFileObjects.forResource(new File(dummyDir, "DummyInterceptor.java").toURI().toURL()),
				JavaFileObjects.forResource(new File(dummyDir, "DummyService.java").toURI().toURL()));

		assertThat(compilation).succeeded();

		Optional<JavaFileObject> proxyFile = compilation
				.generatedSourceFile("summer.compiler.dummy.DummyService$$AotProxy");
		assertTrue(proxyFile.isPresent(), "AotProxy should have been generated");

		String proxySource = proxyFile.get().getCharContent(true).toString();

		// True Zero Reflection Assertion is now strictly enforced by ASM Bytecode
		// Scanning below

		// We only want to test the main proxy class, not its inner classes
		Optional<JavaFileObject> proxyClassFile = compilation.generatedFiles().stream().filter(
				f -> f.getKind() == JavaFileObject.Kind.CLASS && f.getName().endsWith("DummyService$$AotProxy.class"))
				.findFirst();
		assertTrue(proxyClassFile.isPresent(), "AotProxy class file should have been generated");

		byte[] classBytes;
		try (java.io.InputStream is = proxyClassFile.get().openInputStream()) {
			classBytes = is.readAllBytes();
		}

		boolean[] hasRef = new boolean[1];
		org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(classBytes);
		cr.accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
			@Override
			public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor,
					String signature, String[] exceptions) {
				if (descriptor != null && descriptor.contains("java/lang/reflect/Method"))
					hasRef[0] = true;
				if (signature != null && signature.contains("java/lang/reflect/Method"))
					hasRef[0] = true;
				return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
							boolean isInterface) {
						if (owner != null && owner.contains("java/lang/reflect/Method"))
							hasRef[0] = true;
						if (descriptor != null && descriptor.contains("java/lang/reflect/Method"))
							hasRef[0] = true;
					}
					@Override
					public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
						if (owner != null && owner.contains("java/lang/reflect/Method"))
							hasRef[0] = true;
						if (descriptor != null && descriptor.contains("java/lang/reflect/Method"))
							hasRef[0] = true;
					}
					@Override
					public void visitLdcInsn(Object value) {
						if (value instanceof org.objectweb.asm.Type) {
							if (((org.objectweb.asm.Type) value).getClassName().contains("java.lang.reflect.Method"))
								hasRef[0] = true;
						}
					}
					@Override
					public void visitTypeInsn(int opcode, String type) {
						if (type != null && type.contains("java/lang/reflect/Method"))
							hasRef[0] = true;
					}
				};
			}
			@Override
			public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor,
					String signature, Object value) {
				if (descriptor != null && descriptor.contains("java/lang/reflect/Method"))
					hasRef[0] = true;
				if (signature != null && signature.contains("java/lang/reflect/Method"))
					hasRef[0] = true;
				return super.visitField(access, name, descriptor, signature, value);
			}
		}, 0);

		assertFalse(hasRef[0], "Generated proxy class bytecodes must NOT reference java/lang/reflect/Method anywhere");
	}
}
