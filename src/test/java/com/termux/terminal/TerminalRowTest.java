package com.termux.terminal;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class TerminalRowTest {

	/** The properties of these code points are validated in {@link #testStaticConstants()}. */
	private static final int ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1 = 0x679C;
	private static final int ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_2 = 0x679D;
	private static final int TWO_JAVA_CHARS_DISPLAY_WIDTH_TWO_1 = 0x2070E;
	private static final int TWO_JAVA_CHARS_DISPLAY_WIDTH_TWO_2 = 0x20731;

	/** Unicode Character 'MUSICAL SYMBOL G CLEF' (U+1D11E). Two java chars required for this. */
	static final int TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE_1 = 0x1D11E;
	/** Unicode Character 'MUSICAL SYMBOL G CLEF OTTAVA ALTA' (U+1D11F). Two java chars required for this. */
	private static final int TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE_2 = 0x1D11F;

	private final int COLUMNS = 80;

	/** A combining character. */
	private static final int DIARESIS_CODEPOINT = 0x0308;

	private TerminalRow row;

	@Before
	public void setUp() {
		row = new TerminalRow(COLUMNS, TextStyle.NORMAL);
	}

	private void assertLineStartsWith(int... codePoints) {
		char[] chars = row.text;
		int charIndex = 0;
		for (int i = 0; i < codePoints.length; i++) {
			int lineCodePoint = chars[charIndex++];
			if (Character.isHighSurrogate((char) lineCodePoint)) {
				lineCodePoint = Character.toCodePoint((char) lineCodePoint, chars[charIndex++]);
			}
			assertEquals("Differing a code point index=" + i, codePoints[i], lineCodePoint);
		}
	}

	private void assertColumnCharIndicesStartsWith(int... indices) {
		for (int i = 0; i < indices.length; i++) {
			int expected = indices[i];
			int actual = row.findStartOfColumn(i);
			assertEquals("At index=" + i, expected, actual);
		}
	}

	@Test
	public void testSimpleDiaresis() {
		row.setChar(0, DIARESIS_CODEPOINT, 0);
		assertEquals(81, row.getSpaceUsed());
		row.setChar(0, DIARESIS_CODEPOINT, 0);
		assertEquals(82, row.getSpaceUsed());
		assertLineStartsWith(' ', DIARESIS_CODEPOINT, DIARESIS_CODEPOINT, ' ');
	}

	@Test
	public void testStaticConstants() {
		assertEquals(1, Character.charCount(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1));
		assertEquals(1, Character.charCount(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_2));
		assertEquals(2, WcWidth.width(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1));
		assertEquals(2, WcWidth.width(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_2));

		assertEquals(2, Character.charCount(TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE_1));
		assertEquals(2, Character.charCount(TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE_2));
		assertEquals(1, WcWidth.width(TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE_1));
		assertEquals(1, WcWidth.width(TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE_2));

		assertEquals(2, Character.charCount(TWO_JAVA_CHARS_DISPLAY_WIDTH_TWO_1));
		assertEquals(2, Character.charCount(TWO_JAVA_CHARS_DISPLAY_WIDTH_TWO_2));
		assertEquals(2, WcWidth.width(TWO_JAVA_CHARS_DISPLAY_WIDTH_TWO_1));
		assertEquals(2, WcWidth.width(TWO_JAVA_CHARS_DISPLAY_WIDTH_TWO_2));

		assertEquals(1, Character.charCount(DIARESIS_CODEPOINT));
		assertEquals(0, WcWidth.width(DIARESIS_CODEPOINT));
	}

	@Test
	public void testOneColumn() {
		assertEquals(0, row.findStartOfColumn(0));
		row.setChar(0, 'a', 0);
		assertEquals(0, row.findStartOfColumn(0));
	}

	@Test
	public void testAscii() {
		assertEquals(0, row.findStartOfColumn(0));
		row.setChar(0, 'a', 0);
		assertLineStartsWith('a', ' ', ' ');
		assertEquals(1, row.findStartOfColumn(1));
		assertEquals(80, row.getSpaceUsed());
		row.setChar(0, 'b', 0);
		assertEquals(1, row.findStartOfColumn(1));
		assertEquals(2, row.findStartOfColumn(2));
		assertEquals(80, row.getSpaceUsed());
		assertColumnCharIndicesStartsWith(0, 1, 2, 3);

		char[] someChars = new char[]{'a', 'c', 'e', '4', '5', '6', '7', '8'};

		char[] rawLine = new char[80];
		Arrays.fill(rawLine, ' ');
		Random random = new Random();
		for (int i = 0; i < 1000; i++) {
			int lineIndex = random.nextInt(rawLine.length);
			int charIndex = random.nextInt(someChars.length);
			rawLine[lineIndex] = someChars[charIndex];
			row.setChar(lineIndex, someChars[charIndex], 0);
		}
		char[] lineChars = row.text;
		for (int i = 0; i < rawLine.length; i++) {
			assertEquals(rawLine[i], lineChars[i]);
		}
	}

	@Test
	public void testUnicode() {
		assertEquals(0, row.findStartOfColumn(0));
		assertEquals(80, row.getSpaceUsed());

		row.setChar(0, TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE_1, 0);
		assertEquals(81, row.getSpaceUsed());
		assertEquals(0, row.findStartOfColumn(0));
		assertEquals(2, row.findStartOfColumn(1));
		assertLineStartsWith(TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE_1, ' ', ' ');
		assertColumnCharIndicesStartsWith(0, 2, 3, 4);

		row.setChar(0, 'a', 0);
		assertEquals(80, row.getSpaceUsed());
		assertEquals(0, row.findStartOfColumn(0));
		assertEquals(1, row.findStartOfColumn(1));
		assertLineStartsWith('a', ' ', ' ');
		assertColumnCharIndicesStartsWith(0, 1, 2, 3);

		row.setChar(0, TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE_1, 0);
		row.setChar(1, 'a', 0);
		assertLineStartsWith(TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE_1, 'a', ' ');

		row.setChar(0, TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE_1, 0);
		row.setChar(1, TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE_2, 0);
		assertLineStartsWith(TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE_1, TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE_2, ' ');
		assertColumnCharIndicesStartsWith(0, 2, 4, 5);
		assertEquals(82, row.getSpaceUsed());
	}

	@Test
	public void testDoubleWidth() {
		row.setChar(0, 'a', 0);
		row.setChar(1, ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_2, 0);
		assertLineStartsWith('a', ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_2, ' ');
		assertColumnCharIndicesStartsWith(0, 1, 1, 2);
		row.setChar(0, ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, 0);
		assertLineStartsWith(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, ' ', ' ');
		assertColumnCharIndicesStartsWith(0, 0, 1, 2);

		row.setChar(0, ' ', 0);
		assertLineStartsWith(' ', ' ', ' ', ' ');
		assertColumnCharIndicesStartsWith(0, 1, 2, 3, 4);
		row.setChar(0, ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, 0);
		row.setChar(2, ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_2, 0);
		assertLineStartsWith(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_2);
		assertColumnCharIndicesStartsWith(0, 0, 1, 1, 2);
		row.setChar(0, 'a', 0);
		assertLineStartsWith('a', ' ', ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_2, ' ');
	}

	/** Just as {@link #testDoubleWidth()} but requires a surrogate pair. */
	@Test
	public void testDoubleWidthSurrogage() {
		row.setChar(0, 'a', 0);
		assertColumnCharIndicesStartsWith(0, 1, 2, 3, 4);

		row.setChar(1, TWO_JAVA_CHARS_DISPLAY_WIDTH_TWO_2, 0);
		assertColumnCharIndicesStartsWith(0, 1, 1, 3, 4);
		assertLineStartsWith('a', TWO_JAVA_CHARS_DISPLAY_WIDTH_TWO_2, ' ');
		row.setChar(0, TWO_JAVA_CHARS_DISPLAY_WIDTH_TWO_1, 0);
		assertColumnCharIndicesStartsWith(0, 0, 2, 3, 4);
		assertLineStartsWith(TWO_JAVA_CHARS_DISPLAY_WIDTH_TWO_1, ' ', ' ', ' ');

		row.setChar(0, ' ', 0);
		assertLineStartsWith(' ', ' ', ' ', ' ');
		row.setChar(0, TWO_JAVA_CHARS_DISPLAY_WIDTH_TWO_1, 0);
		row.setChar(1, TWO_JAVA_CHARS_DISPLAY_WIDTH_TWO_2, 0);
		assertLineStartsWith(' ', TWO_JAVA_CHARS_DISPLAY_WIDTH_TWO_2, ' ');
		row.setChar(0, 'a', 0);
		assertLineStartsWith('a', TWO_JAVA_CHARS_DISPLAY_WIDTH_TWO_2, ' ');
	}

	@Test
	public void testReplacementChar() {
		row.setChar(0, TerminalEmulator.UNICODE_REPLACEMENT_CHAR, 0);
		row.setChar(1, 'Y', 0);
		assertLineStartsWith(TerminalEmulator.UNICODE_REPLACEMENT_CHAR, 'Y', ' ', ' ');
	}

	@Test
	public void testSurrogateCharsWithNormalDisplayWidth() {
		// These requires a UTF-16 surrogate pair, and has a display width of one.
		int first = 0x1D306;
		int second = 0x1D307;
		// Assert the above statement:
		assertEquals(2, Character.toChars(first).length);
		assertEquals(2, Character.toChars(second).length);

		row.setChar(0, second, 0);
		assertEquals(second, Character.toCodePoint(row.text[0], row.text[1]));
		assertEquals(' ', row.text[2]);
		assertEquals(2, row.findStartOfColumn(1));

		row.setChar(0, first, 0);
		assertEquals(first, Character.toCodePoint(row.text[0], row.text[1]));
		assertEquals(' ', row.text[2]);
		assertEquals(2, row.findStartOfColumn(1));

		row.setChar(1, second, 0);
		row.setChar(2, 'a', 0);
		assertEquals(first, Character.toCodePoint(row.text[0], row.text[1]));
		assertEquals(second, Character.toCodePoint(row.text[2], row.text[3]));
		assertEquals('a', row.text[4]);
		assertEquals(' ', row.text[5]);
		assertEquals(0, row.findStartOfColumn(0));
		assertEquals(2, row.findStartOfColumn(1));
		assertEquals(4, row.findStartOfColumn(2));
		assertEquals(5, row.findStartOfColumn(3));
		assertEquals(6, row.findStartOfColumn(4));

		row.setChar(0, ' ', 0);
		assertEquals(' ', row.text[0]);
		assertEquals(second, Character.toCodePoint(row.text[1], row.text[2]));
		assertEquals('a', row.text[3]);
		assertEquals(' ', row.text[4]);
		assertEquals(0, row.findStartOfColumn(0));
		assertEquals(1, row.findStartOfColumn(1));
		assertEquals(3, row.findStartOfColumn(2));
		assertEquals(4, row.findStartOfColumn(3));
		assertEquals(5, row.findStartOfColumn(4));

		for (int i = 0; i < 80; i++) {
			row.setChar(i, i % 2 == 0 ? first : second, 0);
		}
		for (int i = 0; i < 80; i++) {
			int idx = row.findStartOfColumn(i);
			assertEquals(i % 2 == 0 ? first : second, Character.toCodePoint(row.text[idx], row.text[idx + 1]));
		}
		for (int i = 0; i < 80; i++) {
			row.setChar(i, i % 2 == 0 ? 'a' : 'b', 0);
		}
		for (int i = 0; i < 80; i++) {
			int idx = row.findStartOfColumn(i);
			assertEquals(i, idx);
			assertEquals(i % 2 == 0 ? 'a' : 'b', row.text[i]);
		}
	}

	@Test
	public void testOverwritingDoubleDisplayWidthWithNormalDisplayWidth() {
		// Initial "OO "
		row.setChar(0, ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, 0);
		assertEquals(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, row.text[0]);
		assertEquals(' ', row.text[1]);
		assertEquals(0, row.findStartOfColumn(0));
		assertEquals(0, row.findStartOfColumn(1));
		assertEquals(1, row.findStartOfColumn(2));

		// Setting first column to a clears second: "a  "
		row.setChar(0, 'a', 0);
		assertEquals('a', row.text[0]);
		assertEquals(' ', row.text[1]);
		assertEquals(0, row.findStartOfColumn(0));
		assertEquals(1, row.findStartOfColumn(1));
		assertEquals(2, row.findStartOfColumn(2));

		// Back to initial "OO "
		row.setChar(0, ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, 0);
		assertEquals(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, row.text[0]);
		assertEquals(' ', row.text[1]);
		assertEquals(0, row.findStartOfColumn(0));
		assertEquals(0, row.findStartOfColumn(1));
		assertEquals(1, row.findStartOfColumn(2));

		// Setting first column to a clears first: " a "
		row.setChar(1, 'a', 0);
		assertEquals(' ', row.text[0]);
		assertEquals('a', row.text[1]);
		assertEquals(' ', row.text[2]);
		assertEquals(0, row.findStartOfColumn(0));
		assertEquals(1, row.findStartOfColumn(1));
		assertEquals(2, row.findStartOfColumn(2));
	}

	@Test
	public void testOverwritingDoubleDisplayWidthWithSelf() {
		row.setChar(0, ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, 0);
		row.setChar(0, ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, 0);
		assertEquals(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, row.text[0]);
		assertEquals(' ', row.text[1]);
		assertEquals(0, row.findStartOfColumn(0));
		assertEquals(0, row.findStartOfColumn(1));
		assertEquals(1, row.findStartOfColumn(2));
	}

	@Test
	public void testNormalCharsWithDoubleDisplayWidth() {
		// These fit in one java char, and has a display width of two.
		assertEquals(1, Character.charCount(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1));
		assertEquals(1, Character.charCount(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_2));
		assertEquals(2, WcWidth.width(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1));
		assertEquals(2, WcWidth.width(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_2));

		row.setChar(0, ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, 0);
		assertEquals(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, row.text[0]);
		assertEquals(0, row.findStartOfColumn(1));
		assertEquals(' ', row.text[1]);

		row.setChar(0, 'a', 0);
		assertEquals('a', row.text[0]);
		assertEquals(' ', row.text[1]);
		assertEquals(1, row.findStartOfColumn(1));

		row.setChar(0, ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, 0);
		assertEquals(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, row.text[0]);
		// The first character fills both first columns.
		assertEquals(0, row.findStartOfColumn(1));
		row.setChar(2, 'a', 0);
		assertEquals(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, row.text[0]);
		assertEquals('a', row.text[1]);
		assertEquals(1, row.findStartOfColumn(2));

		row.setChar(0, 'c', 0);
		assertEquals('c', row.text[0]);
		assertEquals(' ', row.text[1]);
		assertEquals('a', row.text[2]);
		assertEquals(' ', row.text[3]);
		assertEquals(0, row.findStartOfColumn(0));
		assertEquals(1, row.findStartOfColumn(1));
		assertEquals(2, row.findStartOfColumn(2));
	}

	@Test
	public void testNormalCharsWithDoubleDisplayWidthOverlapping() {
		// These fit in one java char, and has a display width of two.
		row.setChar(0, ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, 0);
		row.setChar(2, ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_2, 0);
		row.setChar(4, 'a', 0);
		// O = ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO
		// A = ANOTHER_JAVA_CHAR_DISPLAY_WIDTH_TWO
		// "OOAAa    "
		assertEquals(0, row.findStartOfColumn(0));
		assertEquals(0, row.findStartOfColumn(1));
		assertEquals(1, row.findStartOfColumn(2));
		assertEquals(1, row.findStartOfColumn(3));
		assertEquals(2, row.findStartOfColumn(4));
		assertEquals(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_1, row.text[0]);
		assertEquals(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_2, row.text[1]);
		assertEquals('a', row.text[2]);
		assertEquals(' ', row.text[3]);

		row.setChar(1, ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_2, 0);
		// " AA a    "
		assertEquals(' ', row.text[0]);
		assertEquals(ONE_JAVA_CHAR_DISPLAY_WIDTH_TWO_2, row.text[1]);
		assertEquals(' ', row.text[2]);
		assertEquals('a', row.text[3]);
		assertEquals(' ', row.text[4]);
		assertEquals(0, row.findStartOfColumn(0));
		assertEquals(1, row.findStartOfColumn(1));
		assertEquals(1, row.findStartOfColumn(2));
		assertEquals(2, row.findStartOfColumn(3));
		assertEquals(3, row.findStartOfColumn(4));
	}

	@Test
	public void testNormalization() {
		int lowerCaseN = 0x006E;
		int combiningTilde = 0x0303;
		row.setChar(0, lowerCaseN, 0);
		assertEquals(80, row.getSpaceUsed());
		row.setChar(0, combiningTilde, 0);
		assertEquals(81, row.getSpaceUsed());
		assertLineStartsWith(lowerCaseN, combiningTilde, ' ');
	}

	@Test
	public void testInsertWideAtLastColumn() {
		row.setChar(COLUMNS - 2, 'Z', 0);
		row.setChar(COLUMNS - 1, 'a', 0);
		assertEquals('Z', row.text[row.findStartOfColumn(COLUMNS - 2)]);
		assertEquals('a', row.text[row.findStartOfColumn(COLUMNS - 1)]);
		row.setChar(COLUMNS - 1, 'ö', 0);
		assertEquals('Z', row.text[row.findStartOfColumn(COLUMNS - 2)]);
		assertEquals('ö', row.text[row.findStartOfColumn(COLUMNS - 1)]);
	}

}
