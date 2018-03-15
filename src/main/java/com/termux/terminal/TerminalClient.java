package com.termux.terminal;

import java.nio.charset.StandardCharsets;

/** A client which receives callbacks from events triggered by feeding input to a {@link TerminalEmulator}. */
public interface TerminalClient {

    /** Write a string using the UTF-8 encoding to the terminal client. */
    default void write(String data) {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        write(bytes, 0, bytes.length);
    }

    /** Write bytes to the terminal client. */
    void write(byte[] data, int offset, int count);

    /** Notify the terminal client that the terminal title has changed. */
    void titleChanged(String oldTitle, String newTitle);

    /** Notify the terminal client that the terminal title has changed. */
    void clipboardText(String text);

    /** Notify the terminal client that a bell character (ASCII 7, bell, BEL, \a, ^G)) has been received. */
    void onBell();

    /** Notify the terminal client the terminal color palette has changed. */
    void onColorsChanged();

}
