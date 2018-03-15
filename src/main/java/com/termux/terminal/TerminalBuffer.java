package com.termux.terminal;

/**
 * A circular buffer of {@link TerminalRow}:s which keeps notes about what is visible on a logical screen and the scroll
 * history.
 * <p>
 * See {@link #externalToInternalRow(int)} for how to map from logical screen rows to array indices.
 */
public final class TerminalBuffer {

    TerminalRow[] rows;
    /** The length of {@link #rows}. */
    int totalRows;
    /** The number of rows and columns visible on the screen. */
    int screenRows, columns;
    /** The number of rows kept in history. */
    private int activeTranscriptRows = 0;
    /** The index in the circular buffer where the visible screen starts. */
    private int screenFirstRow = 0;

    /**
     * Create a terminal buffer.
     *
     * @param columns    the width of the screen in characters.
     * @param totalRows  the height of the entire text area, in rows of text.
     * @param rows       the height of just the screen, not including the transcript that holds rows that have scrolled off
     *                   the top of the screen.
     */
    public TerminalBuffer(int columns, int totalRows, int rows) {
        this.columns = columns;
        this.totalRows = totalRows;
        this.screenRows = rows;
        this.rows = new TerminalRow[totalRows];

        blockSet(0, 0, columns, screenRows, ' ', TextStyle.NORMAL);
    }

    public String getTranscriptText() {
        return getSelectedText(0, -getActiveTranscriptRows(), columns, screenRows).trim();
    }

    public String getSelectedText(int selX1, int selY1, int selX2, int selY2) {
        final StringBuilder builder = new StringBuilder();
        final int columns = this.columns;

        if (selY1 < -getActiveTranscriptRows()) selY1 = -getActiveTranscriptRows();
        if (selY2 >= screenRows) selY2 = screenRows - 1;

        for (int row = selY1; row <= selY2; row++) {
            int x1 = (row == selY1) ? selX1 : 0;
            int x2;
            if (row == selY2) {
                x2 = selX2 + 1;
                if (x2 > columns) x2 = columns;
            } else {
                x2 = columns;
            }
            TerminalRow lineObject = rows[externalToInternalRow(row)];
            int x1Index = lineObject.findStartOfColumn(x1);
            int x2Index = (x2 < this.columns) ? lineObject.findStartOfColumn(x2) : lineObject.getSpaceUsed();
            if (x2Index == x1Index) {
                // Selected the start of a wide character.
                x2Index = lineObject.findStartOfColumn(x2 + 1);
            }
            char[] line = lineObject.text;
            int lastPrintingCharIndex = -1;
            int i;
            boolean rowLineWrap = getLineWrap(row);
            if (rowLineWrap && x2 == columns) {
                // If the line was wrapped, we shouldn't lose trailing space:
                lastPrintingCharIndex = x2Index - 1;
            } else {
                for (i = x1Index; i < x2Index; ++i) {
                    char c = line[i];
                    if (c != ' ') lastPrintingCharIndex = i;
                }
            }
            if (lastPrintingCharIndex != -1)
                builder.append(line, x1Index, lastPrintingCharIndex - x1Index + 1);
            if (!rowLineWrap && row < selY2 && row < screenRows - 1) builder.append('\n');
        }
        return builder.toString();
    }

    public int getActiveTranscriptRows() {
        return activeTranscriptRows;
    }

    public int getActiveRows() {
        return activeTranscriptRows + screenRows;
    }

    /**
     * Convert a row value from the public external coordinate system to our internal private coordinate system.
     *
     * <pre>
     * - External coordinate system: -activeTranscriptRows to screenRows-1, with the screen being 0..screenRows-1.
     * - Internal coordinate system: the screenRows rows starting at screenFirstRow comprise the screen, while the
     *   activeTranscriptRows rows ending at screenFirstRow-1 form the transcript (as a circular buffer).
     *
     * External ↔ Internal:
     *
     * [ ...                            ]     [ ...                                     ]
     * [ -activeTranscriptRows         ]     [ screenFirstRow - activeTranscriptRows ]
     * [ ...                            ]     [ ...                                     ]
     * [ 0 (visible screen starts here) ]  ↔  [ screenFirstRow                         ]
     * [ ...                            ]     [ ...                                     ]
     * [ screenRows-1                  ]     [ screenFirstRow + screenRows-1         ]
     * </pre>
     *
     * @param externalRow a row in the external coordinate system.
     * @return The row corresponding to the input argument in the private coordinate system.
     */
    public int externalToInternalRow(int externalRow) {
        if (externalRow < -activeTranscriptRows || externalRow > screenRows)
            throw new IllegalArgumentException("extRow=" + externalRow + ", screenRows=" + screenRows + ", activeTranscriptRows=" + activeTranscriptRows);
        final int internalRow = screenFirstRow + externalRow;
        return (internalRow < 0) ? (totalRows + internalRow) : (internalRow % totalRows);
    }

    public void setLineWrap(int row) {
        rows[externalToInternalRow(row)].lineWrap = true;
    }

    public boolean getLineWrap(int row) {
        return rows[externalToInternalRow(row)].lineWrap;
    }

    public void clearLineWrap(int row) {
        rows[externalToInternalRow(row)].lineWrap = false;
    }

    /**
     * Resize the screen which this transcript backs. Currently, this only works if the number of columns does not
     * change or the rows expand (that is, it only works when shrinking the number of rows).
     *
     * @param newColumns The number of columns the screen should have.
     * @param newRows    The number of rows the screen should have.
     * @param cursor     An int[2] containing the (column, row) cursor location.
     */
    public void resize(int newColumns, int newRows, int newTotalRows, int[] cursor, long currentStyle, boolean altScreen) {
        // newRows > totalRows should not normally happen since totalRows is TRANSCRIPT_ROWS (10000):
        if (newColumns == columns && newRows <= totalRows) {
            // Fast resize where just the rows changed.
            int shiftDownOfTopRow = screenRows - newRows;
            if (shiftDownOfTopRow > 0 && shiftDownOfTopRow < screenRows) {
                // Shrinking. Check if we can skip blank rows at bottom below cursor.
                for (int i = screenRows - 1; i > 0; i--) {
                    if (cursor[1] >= i) break;
                    int r = externalToInternalRow(i);
                    if (rows[r] == null || rows[r].isBlank()) {
                        if (--shiftDownOfTopRow == 0) break;
                    }
                }
            } else if (shiftDownOfTopRow < 0) {
                // Negative shift down = expanding. Only move screen up if there is transcript to show:
                int actualShift = Math.max(shiftDownOfTopRow, -activeTranscriptRows);
                if (shiftDownOfTopRow != actualShift) {
                    // The new rows revealed by the resizing are not all from the transcript. Blank the below ones.
                    for (int i = 0; i < actualShift - shiftDownOfTopRow; i++)
                        allocateFullLineIfNecessary((screenFirstRow + screenRows + i) % totalRows).clear(currentStyle);
                    shiftDownOfTopRow = actualShift;
                }
            }
            screenFirstRow += shiftDownOfTopRow;
            screenFirstRow = (screenFirstRow < 0) ? (screenFirstRow + totalRows) : (screenFirstRow % totalRows);
            totalRows = newTotalRows;
            activeTranscriptRows = altScreen ? 0 : Math.max(0, activeTranscriptRows + shiftDownOfTopRow);
            cursor[1] -= shiftDownOfTopRow;
            screenRows = newRows;
        } else {
            // Copy away old state and update new:
            TerminalRow[] oldLines = rows;
            rows = new TerminalRow[newTotalRows];
            for (int i = 0; i < newTotalRows; i++)
                rows[i] = new TerminalRow(newColumns, currentStyle);

            final int oldActiveTranscriptRows = activeTranscriptRows;
            final int oldScreenFirstRow = screenFirstRow;
            final int oldScreenRows = screenRows;
            final int oldTotalRows = totalRows;
            totalRows = newTotalRows;
            screenRows = newRows;
            activeTranscriptRows = screenFirstRow = 0;
            columns = newColumns;

            int newCursorRow = -1;
            int newCursorColumn = -1;
            int oldCursorRow = cursor[1];
            int oldCursorColumn = cursor[0];
            boolean newCursorPlaced = false;

            int currentOutputExternalRow = 0;
            int currentOutputExternalColumn = 0;

            // Loop over every character in the initial state.
            // Blank rows should be skipped only if at end of transcript (just as is done in the "fast" resize), so we
            // keep track how many blank rows we have skipped if we later on find a non-blank line.
            int skippedBlankLines = 0;
            for (int externalOldRow = -oldActiveTranscriptRows; externalOldRow < oldScreenRows; externalOldRow++) {
                // Do what externalToInternalRow() does but for the old state:
                int internalOldRow = oldScreenFirstRow + externalOldRow;
                internalOldRow = (internalOldRow < 0) ? (oldTotalRows + internalOldRow) : (internalOldRow % oldTotalRows);

                TerminalRow oldLine = oldLines[internalOldRow];
                boolean cursorAtThisRow = externalOldRow == oldCursorRow;
                // The cursor may only be on a non-null line, which we should not skip:
                if (oldLine == null || (!(!newCursorPlaced && cursorAtThisRow)) && oldLine.isBlank()) {
                    skippedBlankLines++;
                    continue;
                } else if (skippedBlankLines > 0) {
                    // After skipping some blank rows we encounter a non-blank line. Insert the skipped blank rows.
                    for (int i = 0; i < skippedBlankLines; i++) {
                        if (currentOutputExternalRow == screenRows - 1) {
                            scrollDownOneLine(0, screenRows, currentStyle);
                        } else {
                            currentOutputExternalRow++;
                        }
                        currentOutputExternalColumn = 0;
                    }
                    skippedBlankLines = 0;
                }

                int lastNonSpaceIndex = 0;
                boolean justToCursor = false;
                if (cursorAtThisRow || oldLine.lineWrap) {
                    // Take the whole line, either because of cursor on it, or if line wrapping.
                    lastNonSpaceIndex = oldLine.getSpaceUsed();
                    if (cursorAtThisRow) justToCursor = true;
                } else {
                    for (int i = 0; i < oldLine.getSpaceUsed(); i++)
                        // NEWLY INTRODUCED BUG! Should not index oldLine.style with char indices
                        if (oldLine.text[i] != ' '/* || oldLine.style[i] != currentStyle */)
                            lastNonSpaceIndex = i + 1;
                }

                int currentOldCol = 0;
                long styleAtCol = 0;
                for (int i = 0; i < lastNonSpaceIndex; i++) {
                    // Note that looping over java character, not cells.
                    char c = oldLine.text[i];
                    int codePoint = (Character.isHighSurrogate(c)) ? Character.toCodePoint(c, oldLine.text[++i]) : c;
                    int displayWidth = WcWidth.width(codePoint);
                    // Use the last style if this is a zero-width character:
                    if (displayWidth > 0) styleAtCol = oldLine.getStyle(currentOldCol);

                    // Line wrap as necessary:
                    if (currentOutputExternalColumn + displayWidth > columns) {
                        setLineWrap(currentOutputExternalRow);
                        if (currentOutputExternalRow == screenRows - 1) {
                            if (newCursorPlaced) newCursorRow--;
                            scrollDownOneLine(0, screenRows, currentStyle);
                        } else {
                            currentOutputExternalRow++;
                        }
                        currentOutputExternalColumn = 0;
                    }

                    int offsetDueToCombiningChar = ((displayWidth <= 0 && currentOutputExternalColumn > 0) ? 1 : 0);
                    int outputColumn = currentOutputExternalColumn - offsetDueToCombiningChar;
                    setChar(outputColumn, currentOutputExternalRow, codePoint, styleAtCol);

                    if (displayWidth > 0) {
                        if (oldCursorRow == externalOldRow && oldCursorColumn == currentOldCol) {
                            newCursorColumn = currentOutputExternalColumn;
                            newCursorRow = currentOutputExternalRow;
                            newCursorPlaced = true;
                        }
                        currentOldCol += displayWidth;
                        currentOutputExternalColumn += displayWidth;
                        if (justToCursor && newCursorPlaced) break;
                    }
                }
                // Old row has been copied. Check if we need to insert newline if old line was not wrapping:
                if (externalOldRow != (oldScreenRows - 1) && !oldLine.lineWrap) {
                    if (currentOutputExternalRow == screenRows - 1) {
                        if (newCursorPlaced) newCursorRow--;
                        scrollDownOneLine(0, screenRows, currentStyle);
                    } else {
                        currentOutputExternalRow++;
                    }
                    currentOutputExternalColumn = 0;
                }
            }

            cursor[0] = newCursorColumn;
            cursor[1] = newCursorRow;
        }

        // Handle cursor scrolling off screen:
        if (cursor[0] < 0 || cursor[1] < 0) cursor[0] = cursor[1] = 0;
    }

    /**
     * Block copy rows and associated metadata from one location to another in the circular buffer, taking wraparound
     * into account.
     *
     * @param srcInternal The first line to be copied.
     * @param len         The number of rows to be copied.
     */
    private void blockCopyLinesDown(int srcInternal, int len) {
        if (len == 0) return;
        int totalRows = this.totalRows;

        int start = len - 1;
        // Save away line to be overwritten:
        TerminalRow lineToBeOverWritten = rows[(srcInternal + start + 1) % totalRows];
        // Do the copy from bottom to top.
        for (int i = start; i >= 0; --i)
            rows[(srcInternal + i + 1) % totalRows] = rows[(srcInternal + i) % totalRows];
        // Put back overwritten line, now above the block:
        rows[(srcInternal) % totalRows] = lineToBeOverWritten;
    }

    /**
     * Scroll the screen down one line. To scroll the whole screen of a 24 line screen, the arguments would be (0, 24).
     *
     * @param topMargin    First line that is scrolled.
     * @param bottomMargin One line after the last line that is scrolled.
     * @param style        the style for the newly exposed line.
     */
    public void scrollDownOneLine(int topMargin, int bottomMargin, long style) {
        if (topMargin > bottomMargin - 1 || topMargin < 0 || bottomMargin > screenRows)
            throw new IllegalArgumentException("topMargin=" + topMargin + ", bottomMargin=" + bottomMargin + ", screenRows=" + screenRows);

        // Copy the fixed topMargin rows one line down so that they remain on screen in same position:
        blockCopyLinesDown(screenFirstRow, topMargin);
        // Copy the fixed screenRows-bottomMargin rows one line down so that they remain on screen in same
        // position:
        blockCopyLinesDown(externalToInternalRow(bottomMargin), screenRows - bottomMargin);

        // Update the screen location in the ring buffer:
        screenFirstRow = (screenFirstRow + 1) % totalRows;
        // Note that the history has grown if not already full:
        if (activeTranscriptRows < totalRows - screenRows) activeTranscriptRows++;

        // Blank the newly revealed line above the bottom margin:
        int blankRow = externalToInternalRow(bottomMargin - 1);
        if (rows[blankRow] == null) {
            rows[blankRow] = new TerminalRow(columns, style);
        } else {
            rows[blankRow].clear(style);
        }
    }

    /**
     * Block copy characters from one position in the screen to another. The two positions can overlap. All characters
     * of the source and destination must be within the bounds of the screen, or else an InvalidParameterException will
     * be thrown.
     *
     * @param sx source X coordinate
     * @param sy source Y coordinate
     * @param w  width
     * @param h  height
     * @param dx destination X coordinate
     * @param dy destination Y coordinate
     */
    public void blockCopy(int sx, int sy, int w, int h, int dx, int dy) {
        if (w == 0) return;
        if (sx < 0 || sx + w > columns || sy < 0 || sy + h > screenRows || dx < 0 || dx + w > columns || dy < 0 || dy + h > screenRows)
            throw new IllegalArgumentException();
        boolean copyingUp = sy > dy;
        for (int y = 0; y < h; y++) {
            int y2 = copyingUp ? y : (h - (y + 1));
            TerminalRow sourceRow = allocateFullLineIfNecessary(externalToInternalRow(sy + y2));
            allocateFullLineIfNecessary(externalToInternalRow(dy + y2)).copyInterval(sourceRow, sx, sx + w, dx);
        }
    }

    /**
     * Block set characters. All characters must be within the bounds of the screen, or else and
     * InvalidParemeterException will be thrown. Typically this is called with a "val" argument of 32 to clear a block
     * of characters.
     */
    public void blockSet(int sx, int sy, int w, int h, int val, long style) {
        if (sx < 0 || sx + w > columns || sy < 0 || sy + h > screenRows) {
            throw new IllegalArgumentException(
                "Illegal arguments! blockSet(" + sx + ", " + sy + ", " + w + ", " + h + ", " + val + ", " + columns + ", " + screenRows + ")");
        }
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                setChar(sx + x, sy + y, val, style);
    }

    public TerminalRow allocateFullLineIfNecessary(int row) {
        return (rows[row] == null) ? (rows[row] = new TerminalRow(columns, 0)) : rows[row];
    }

    public void setChar(int column, int row, int codePoint, long style) {
        if (row >= screenRows || column >= columns)
            throw new IllegalArgumentException("row=" + row + ", column=" + column + ", screenRows=" + screenRows + ", columns=" + columns);
        row = externalToInternalRow(row);
        allocateFullLineIfNecessary(row).setChar(column, codePoint, style);
    }

    public long getStyleAt(int externalRow, int column) {
        return allocateFullLineIfNecessary(externalToInternalRow(externalRow)).getStyle(column);
    }

    /** Support for http://vt100.net/docs/vt510-rm/DECCARA and http://vt100.net/docs/vt510-rm/DECCARA */
    public void setOrClearEffect(int bits, boolean setOrClear, boolean reverse, boolean rectangular, int leftMargin, int rightMargin, int top, int left,
                                 int bottom, int right) {
        for (int y = top; y < bottom; y++) {
            TerminalRow line = rows[externalToInternalRow(y)];
            int startOfLine = (rectangular || y == top) ? left : leftMargin;
            int endOfLine = (rectangular || y + 1 == bottom) ? right : rightMargin;
            for (int x = startOfLine; x < endOfLine; x++) {
                long currentStyle = line.getStyle(x);
                int foreColor = TextStyle.decodeForeColor(currentStyle);
                int backColor = TextStyle.decodeBackColor(currentStyle);
                int effect = TextStyle.decodeEffect(currentStyle);
                if (reverse) {
                    // Clear out the bits to reverse and add them back in reversed:
                    effect = (effect & ~bits) | (bits & ~effect);
                } else if (setOrClear) {
                    effect |= bits;
                } else {
                    effect &= ~bits;
                }
                line.style[x] = TextStyle.encode(foreColor, backColor, effect);
            }
        }
    }

}
