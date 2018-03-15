package com.termux.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * <pre>
 * "CSI ? Pm h", DEC Private Mode Set (DECSET)
 * </pre>
 * <p/>
 * and
 * <p/>
 * <pre>
 * "CSI ? Pm l", DEC Private Mode Reset (DECRST)
 * </pre>
 * <p/>
 * controls various aspects of the terminal
 */
public class DecSetTest extends TerminalTestCase {

	/** DECSET 25, DECTCEM, controls visibility of the cursor. */
	@Test
	public void testShowHideCursor() {
		withTerminalSized(3, 3);
		assertTrue("Initially the cursor should be visible", terminal.isShowingCursor());
		enterString("\033[?25l"); // Hide Cursor (DECTCEM).
		assertFalse(terminal.isShowingCursor());
		enterString("\033[?25h"); // Show Cursor (DECTCEM).
		assertTrue(terminal.isShowingCursor());

		enterString("\033[?25l"); // Hide Cursor (DECTCEM), again.
		assertFalse(terminal.isShowingCursor());
		terminal.reset();
		assertTrue("Resetting the terminal should show the cursor", terminal.isShowingCursor());

		enterString("\033[?25l");
		assertFalse(terminal.isShowingCursor());
		enterString("\033c"); // RIS resetting should reveal cursor.
		assertTrue(terminal.isShowingCursor());
	}

	/** DECSET 2004, controls bracketed paste mode. */
	@Test
	public void testBracketedPasteMode() {
		withTerminalSized(3, 3);

		terminal.paste("a");
		assertEquals("Pasting 'a' should output 'a' when bracketed paste mode is disabled", "a", output.getOutputAndClear());

		enterString("\033[?2004h"); // Enable bracketed paste mode.
		terminal.paste("a");
		assertEquals("Pasting when in bracketed paste mode should be bracketed", "\033[200~a\033[201~", output.getOutputAndClear());

		enterString("\033[?2004l"); // Disable bracketed paste mode.
		terminal.paste("a");
		assertEquals("Pasting 'a' should output 'a' when bracketed paste mode is disabled", "a", output.getOutputAndClear());

		enterString("\033[?2004h"); // Enable bracketed paste mode, again.
		terminal.paste("a");
		assertEquals("Pasting when in bracketed paste mode again should be bracketed", "\033[200~a\033[201~", output.getOutputAndClear());

		terminal.paste("\033ab\033cd\033");
		assertEquals("Pasting an escape character should not input it", "\033[200~abcd\033[201~", output.getOutputAndClear());
		terminal.paste("\u0081ab\u0081cd\u009F");
		assertEquals("Pasting C1 control codes should not input it", "\033[200~abcd\033[201~", output.getOutputAndClear());

		terminal.reset();
		terminal.paste("a");
		assertEquals("Terminal reset() should disable bracketed paste mode", "a", output.getOutputAndClear());
	}

	/** DECSET 7, DECAWM, controls wraparound mode. */
	@Test
	public void testWrapAroundMode() {
		// Default with wraparound:
		withTerminalSized(3, 3).enterString("abcd").assertLinesAre("abc", "d  ", "   ");
		// With wraparound disabled:
		withTerminalSized(3, 3).enterString("\033[?7labcd").assertLinesAre("abd", "   ", "   ");
		enterString("efg").assertLinesAre("abg", "   ", "   ");
		// Re-enabling wraparound:
		enterString("\033[?7hhij").assertLinesAre("abh", "ij ", "   ");
	}

}
