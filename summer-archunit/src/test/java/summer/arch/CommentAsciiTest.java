package summer.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ensures comments do not contain CJK (Chinese/Japanese/Korean) characters.
 *
 * <p>
 * CJK characters in comments create visual inconsistency when the project
 * language is English. This rule scans every {@code .java} source file and
 * reports any comment that contains CJK text. Common non-ASCII symbols
 * ({@code →}, {@code —}, etc.) are intentionally allowed.
 * </p>
 */
class CommentAsciiTest {

	// CJK Unified Ideographs, Extension A, Compatibility, Hiragana, Katakana, Hangul
	private static final Pattern CJK = Pattern.compile("[\\u4E00-\\u9FFF\\u3400-\\u4DBF\\uF900-\\uFAFF"
			+ "\\u3040-\\u309F\\u30A0-\\u30FF\\uAC00-\\uD7AF]");

	@Test
	@DisplayName("Comments should not contain CJK characters")
	void commentsShouldNotContainCjk() throws IOException {
		Path root = Path.of(System.getProperty("user.dir")).getParent();
		List<String> violations = new ArrayList<>();

		try (var stream = Files.walk(root)) {
			stream.filter(p -> p.toString().endsWith(".java"))
					// skip generated and external sources
					.filter(p -> !p.toString().contains("target"))
					.filter(p -> !p.toString().contains("generated"))
					.forEach(file -> checkFile(file, violations));
		}

		assertTrue(violations.isEmpty(),
				"CJK characters found in comments:\n" + String.join("\n", violations));
	}

	private void checkFile(Path file, List<String> violations) {
		try {
			String content = Files.readString(file);
			String[] lines = content.split("\\R");
			Path root = Path.of(System.getProperty("user.dir")).getParent();
			String relative = root.relativize(file).toString().replace('\\', '/');

			boolean inBlockComment = false;

			for (int i = 0; i < lines.length; i++) {
				String line = lines[i];
				int lineNum = i + 1;
				CommentResult commentText = extractComment(line, inBlockComment);

				if (commentText.endOfBlock >= 0) {
					// block comment ends on this line
					inBlockComment = false;
				}

				if (commentText.text != null) {
					var matcher = CJK.matcher(commentText.text);
					if (matcher.find()) {
						violations.add(relative + ":" + lineNum + " " + commentText.text.trim());
					}
				}

				if (commentText.inBlock) {
					inBlockComment = true;
				}
			}
		} catch (IOException e) {
			// skip unreadable files
		}
	}

	/**
	 * Extracts comment text from a single line, skipping string literals.
	 */
	private CommentResult extractComment(String line, boolean inBlockComment) {
		StringBuilder comment = new StringBuilder();
		boolean inString = false;
		boolean inChar = false;
		int endOfBlock = -1;
		boolean newBlockStart = false;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			char next = (i + 1 < line.length()) ? line.charAt(i + 1) : 0;

			if (inBlockComment) {
				if (c == '*' && next == '/') {
					endOfBlock = i;
					inBlockComment = false;
					i++; // skip '/'
					continue;
				}
				comment.append(c);
				continue;
			}

			if (inString) {
				if (c == '\\') {
					i++; // skip escaped char
				} else if (c == '"') {
					inString = false;
				}
				continue;
			}

			if (inChar) {
				if (c == '\\') {
					i++;
				} else if (c == '\'') {
					inChar = false;
				}
				continue;
			}

			// not in any special context
			if (c == '"' ) {
				inString = true;
			} else if (c == '\'') {
				inChar = true;
			} else if (c == '/' && next == '/') {
				// line comment — rest of line is comment
				comment.append(line.substring(i + 2));
				break;
			} else if (c == '/' && next == '*') {
				newBlockStart = true;
				inBlockComment = true;
				i++; // skip '*'
			}
		}

		return new CommentResult(
				comment.length() > 0 ? comment.toString() : null,
				inBlockComment || newBlockStart,
				endOfBlock);
	}

	private record CommentResult(String text, boolean inBlock, int endOfBlock) {
	}
}
