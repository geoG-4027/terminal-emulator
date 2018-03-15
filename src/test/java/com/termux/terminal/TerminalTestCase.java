package com.termux.terminal;

import junit.framework.AssertionFailedError;
import org.junit.Before;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.Assert.*;

/** Base class for test cases against {@link com.termux.terminal.TerminalEmulator}. */
public abstract class TerminalTestCase {

	public static class MockTerminalClient implements TerminalClient {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final List<ChangedTitle> titleChanges = new ArrayList<>();
		final List<String> clipboardPuts = new ArrayList<>();
		int bellsRung = 0;
        int colorsChanged = 0;

		@Override
		public void write(byte[] data, int offset, int count) {
			baos.write(data, offset, count);
		}

		String getOutputAndClear() {
			try {
				String result = new String(baos.toByteArray(), "UTF-8");
				baos.reset();
				return result;
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void titleChanged(String oldTitle, String newTitle) {
			titleChanges.add(new ChangedTitle(oldTitle, newTitle));
		}

		@Override
		public void clipboardText(String text) {
			clipboardPuts.add(text);
		}

		@Override
		public void onBell() {
			bellsRung++;
		}

        @Override
        public void onColorsChanged() {
            colorsChanged++;
        }
    }

	TerminalEmulator terminal;
	MockTerminalClient output;

	static final class ChangedTitle {
		final String oldTitle;
		final String newTitle;

		ChangedTitle(String oldTitle, String newTitle) {
			this.oldTitle = oldTitle;
			this.newTitle = newTitle;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof ChangedTitle)) return false;
			ChangedTitle other = (ChangedTitle) o;
			return Objects.equals(oldTitle, other.oldTitle) && Objects.equals(newTitle, other.newTitle);
		}

		@Override
		public int hashCode() {
			return Objects.hash(oldTitle, newTitle);
		}

		@Override
		public String toString() {
			return "ChangedTitle[oldTitle=" + oldTitle + ", newTitle=" + newTitle + "]";
		}

	}

	public TerminalTestCase enterString(String s) {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		terminal.append(bytes, bytes.length);
		assertInvariants();
		return this;
	}

	public void assertEnteringStringGivesResponse(String input, String expectedResponse) {
		enterString(input);
		String response = output.getOutputAndClear();
		assertEquals(expectedResponse, response);
	}

	@Before
	public void setUp() {
		output = new MockTerminalClient();
	}

	protected TerminalTestCase withTerminalSized(int columns, int rows) {
		terminal = new TerminalEmulator(output, columns, rows, rows * 2, System.out);
		return this;
	}

	public void assertHistoryStartsWith(String... rows) {
		assertTrue("About to check " + rows.length + " rows, but only " + terminal.getScreen().getActiveTranscriptRows() + " in history",
				terminal.getScreen().getActiveTranscriptRows() >= rows.length);
		for (int i = 0; i < rows.length; i++) {
			assertLineIs(-i - 1, rows[i]);
		}
	}

	private static final class LineWrapper {
		final TerminalRow mLine;

		public LineWrapper(TerminalRow line) {
			mLine = line;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(mLine);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof LineWrapper && ((LineWrapper) o).mLine == mLine;
		}
	}

	private void assertInvariants() {
		TerminalBuffer screen = terminal.getScreen();
		TerminalRow[] lines = screen.rows;

		Set<LineWrapper> linesSet = new HashSet<>();
		for (int i = 0; i < lines.length; i++) {
			if (lines[i] == null) continue;
			assertTrue("Line exists at multiple places: " + i, linesSet.add(new LineWrapper(lines[i])));
			char[] text = lines[i].text;
			int usedChars = lines[i].getSpaceUsed();
			int currentColumn = 0;
			for (int j = 0; j < usedChars; j++) {
				char c = text[j];
				int codePoint;
				if (Character.isHighSurrogate(c)) {
					char lowSurrogate = text[++j];
					assertTrue("High surrogate without following low surrogate", Character.isLowSurrogate(lowSurrogate));
					codePoint = Character.toCodePoint(c, lowSurrogate);
				} else {
					assertFalse("Low surrogate without preceding high surrogate", Character.isLowSurrogate(c));
					codePoint = c;
				}
				assertFalse("Screen should never contain unassigned characters", Character.getType(codePoint) == Character.UNASSIGNED);
				int width = WcWidth.width(codePoint);
				assertFalse("The first column should not start with combining character", currentColumn == 0 && width < 0);
				if (width > 0) currentColumn += width;
			}
			assertEquals("Line whose width does not match screens. line=" + new String(lines[i].text, 0, lines[i].getSpaceUsed()),
					screen.columns, currentColumn);
		}

		assertEquals("The alt buffer should have have no history", terminal.altBuffer.totalRows, terminal.altBuffer.screenRows);
		if (terminal.isAlternateBufferActive()) {
			assertEquals("The alt buffer should be the same size as the screen", terminal.rows, terminal.altBuffer.totalRows);
		}
	}

	protected void assertLineIs(int line, String expected) {
		TerminalRow l = terminal.getScreen().allocateFullLineIfNecessary(terminal.getScreen().externalToInternalRow(line));
		char[] chars = l.text;
		int textLen = l.getSpaceUsed();
		if (textLen != expected.length()) fail("Expected '" + expected + "' (len=" + expected.length() + "), was='"
				+ new String(chars, 0, textLen) + "' (len=" + textLen + ")");
		for (int i = 0; i < textLen; i++) {
			if (expected.charAt(i) != chars[i])
				fail("Expected '" + expected + "', was='" + new String(chars, 0, textLen) + "' - first different at index=" + i);
		}
	}

	public TerminalTestCase assertLinesAre(String... lines) {
		assertEquals(lines.length, terminal.getScreen().screenRows);
		for (int i = 0; i < lines.length; i++)
			try {
				assertLineIs(i, lines[i]);
			} catch (AssertionFailedError e) {
				throw new AssertionFailedError("Line: " + i + " - " + e.getMessage());
			}
		return this;
	}

	public TerminalTestCase resize(int cols, int rows) {
		terminal.resize(cols, rows);
		assertInvariants();
		return this;
	}

	public void assertLineWraps(boolean... lines) {
		for (int i = 0; i < lines.length; i++)
			assertEquals("line=" + i, lines[i], terminal.getScreen().rows[terminal.getScreen().externalToInternalRow(i)].lineWrap);
	}

	protected TerminalTestCase placeCursorAndAssert(int row, int col) {
		// +1 due to escape sequence being one based.
		enterString("\033[" + (row + 1) + ";" + (col + 1) + "H");
		assertCursorAt(row, col);
		return this;
	}

	public TerminalTestCase assertCursorAt(int row, int col) {
		int actualRow = terminal.getCursorRow();
		int actualCol = terminal.getCursorCol();
		if (!(row == actualRow && col == actualCol))
			fail("Expected cursor at (row,col)=(" + row + ", " + col + ") but was (" + actualRow + ", " + actualCol + ")");
		return this;
	}

	/** For testing only. Encoded style according to {@link TextStyle}. */
	public long getStyleAt(int externalRow, int column) {
		return terminal.getScreen().getStyleAt(externalRow, column);
	}

	public static class EffectLine {
		final int[] styles;

		public EffectLine(int[] styles) {
			this.styles = styles;
		}
	}

	protected EffectLine effectLine(int... bits) {
		return new EffectLine(bits);
	}

	public void assertEffectAttributesSet(EffectLine... lines) {
		assertEquals(lines.length, terminal.getScreen().screenRows);
		for (int i = 0; i < lines.length; i++) {
			int[] line = lines[i].styles;
			for (int j = 0; j < line.length; j++) {
				int effectsAtCell = TextStyle.decodeEffect(getStyleAt(i, j));
				int attributes = line[j];
				if ((effectsAtCell & attributes) != attributes) fail("Line=" + i + ", column=" + j + ", expected "
						+ describeStyle(attributes) + " set, was " + describeStyle(effectsAtCell));
			}
		}
	}

	public void assertForegroundIndices(EffectLine... lines) {
		assertEquals(lines.length, terminal.getScreen().screenRows);
		for (int i = 0; i < lines.length; i++) {
			int[] line = lines[i].styles;
			for (int j = 0; j < line.length; j++) {
				int actualColor = TextStyle.decodeForeColor(getStyleAt(i, j));
				int expectedColor = line[j];
				if (actualColor != expectedColor) fail("Line=" + i + ", column=" + j + ", expected color "
						+ Integer.toHexString(expectedColor) + " set, was " + Integer.toHexString(actualColor));
			}
		}
	}

	private static String describeStyle(int styleBits) {
		return "'" + ((styleBits & TextStyle.CHARACTER_ATTRIBUTE_BLINK) != 0 ? ":BLINK:" : "")
				+ ((styleBits & TextStyle.CHARACTER_ATTRIBUTE_BOLD) != 0 ? ":BOLD:" : "")
				+ ((styleBits & TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != 0 ? ":INVERSE:" : "")
				+ ((styleBits & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) != 0 ? ":INVISIBLE:" : "")
				+ ((styleBits & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0 ? ":ITALIC:" : "")
				+ ((styleBits & TextStyle.CHARACTER_ATTRIBUTE_PROTECTED) != 0 ? ":PROTECTED:" : "")
				+ ((styleBits & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0 ? ":STRIKETHROUGH:" : "")
				+ ((styleBits & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0 ? ":UNDERLINE:" : "") + "'";
	}

	public void assertForegroundColorAt(int externalRow, int column, int color) {
		long style = terminal.getScreen().rows[terminal.getScreen().externalToInternalRow(externalRow)].getStyle(column);
		assertEquals(color, TextStyle.decodeForeColor(style));
	}

	public TerminalTestCase assertColor(int colorIndex, int expected) {
		int actual = terminal.colors.currentColors[colorIndex];
		if (expected != actual) {
			fail("Color index=" + colorIndex + ", expected=" + Integer.toHexString(expected) + ", was=" + Integer.toHexString(actual));
		}
		return this;
	}
}
