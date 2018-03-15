package com.termux.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScreenBufferTest extends TerminalTestCase {

	@Test
	public void testBasics() {
		TerminalBuffer screen = new TerminalBuffer(5, 3, 3);
		assertEquals("", screen.getTranscriptText());
		screen.setChar(0, 0, 'a', 0);
		assertEquals("a", screen.getTranscriptText());
		screen.setChar(0, 0, 'b', 0);
		assertEquals("b", screen.getTranscriptText());
		screen.setChar(2, 0, 'c', 0);
		assertEquals("b c", screen.getTranscriptText());
		screen.setChar(2, 2, 'f', 0);
		assertEquals("b c\n\n  f", screen.getTranscriptText());
		screen.blockSet(0, 0, 2, 2, 'X', 0);
	}

	@Test
	public void testBlockSet() {
		TerminalBuffer screen = new TerminalBuffer(5, 3, 3);
		screen.blockSet(0, 0, 2, 2, 'X', 0);
		assertEquals("XX\nXX", screen.getTranscriptText());
		screen.blockSet(1, 1, 2, 2, 'Y', 0);
		assertEquals("XX\nXYY\n YY", screen.getTranscriptText());
	}

	@Test
	public void testGetSelectedText() {
		withTerminalSized(5, 3).enterString("ABCDEFGHIJ").assertLinesAre("ABCDE", "FGHIJ", "     ");
		assertEquals("AB", terminal.getSelectedText(0, 0, 1, 0));
		assertEquals("BC", terminal.getSelectedText(1, 0, 2, 0));
		assertEquals("CDE", terminal.getSelectedText(2, 0, 4, 0));
		assertEquals("FG", terminal.getSelectedText(0, 1, 1, 1));
		assertEquals("GH", terminal.getSelectedText(1, 1, 2, 1));
		assertEquals("HIJ", terminal.getSelectedText(2, 1, 4, 1));

		assertEquals("ABCDEFG", terminal.getSelectedText(0, 0, 1, 1));
		withTerminalSized(5, 3).enterString("ABCDE\r\nFGHIJ").assertLinesAre("ABCDE", "FGHIJ", "     ");
		assertEquals("ABCDE\nFG", terminal.getSelectedText(0, 0, 1, 1));
	}

}
