package com.termux.terminal;

/**
 * A circular byte buffer allowing one producer and one consumer thread.
 *
 * Intented to be used by terminal client implementations to communicate data between a user interface thread
 * and threads processing input and output from processes.
 */
final class ByteQueue {

    private final byte[] buffer;
    private int head;
    private int storedBytes;
    private boolean open = true;

    public ByteQueue(int size) {
        buffer = new byte[size];
    }

    public synchronized void close() {
        open = false;
        notify();
    }

    public synchronized int read(byte[] buffer, boolean block) {
        while (storedBytes == 0 && open) {
            if (block) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // Ignore.
                }
            } else {
                return 0;
            }
        }
        if (!open) return -1;

        int totalRead = 0;
        int bufferLength = this.buffer.length;
        boolean wasFull = bufferLength == storedBytes;
        int length = buffer.length;
        int offset = 0;
        while (length > 0 && storedBytes > 0) {
            int oneRun = Math.min(bufferLength - head, storedBytes);
            int bytesToCopy = Math.min(length, oneRun);
            System.arraycopy(this.buffer, head, buffer, offset, bytesToCopy);
            head += bytesToCopy;
            if (head >= bufferLength) head = 0;
            storedBytes -= bytesToCopy;
            length -= bytesToCopy;
            offset += bytesToCopy;
            totalRead += bytesToCopy;
        }
        if (wasFull) notify();
        return totalRead;
    }

    /**
     * Attempt to write the specified portion of the provided buffer to the queue.
     * <p/>
     * Returns whether the output was totally written, false if it was closed before.
     */
    public boolean write(byte[] buffer, int offset, int lengthToWrite) {
        if (lengthToWrite + offset > buffer.length) {
            throw new IllegalArgumentException("length + offset > buffer.length");
        } else if (lengthToWrite <= 0) {
            throw new IllegalArgumentException("length <= 0");
        }

        final int bufferLength = this.buffer.length;

        synchronized (this) {
            while (lengthToWrite > 0) {
                while (bufferLength == storedBytes && open) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // Ignore.
                    }
                }
                if (!open) return false;
                final boolean wasEmpty = storedBytes == 0;
                int bytesToWriteBeforeWaiting = Math.min(lengthToWrite, bufferLength - storedBytes);
                lengthToWrite -= bytesToWriteBeforeWaiting;

                while (bytesToWriteBeforeWaiting > 0) {
                    int tail = head + storedBytes;
                    int oneRun;
                    if (tail >= bufferLength) {
                        // Buffer: [.............]
                        // ________________H_______T
                        // =>
                        // Buffer: [.............]
                        // ___________T____H
                        // onRun= _____----_
                        tail = tail - bufferLength;
                        oneRun = head - tail;
                    } else {
                        oneRun = bufferLength - tail;
                    }
                    int bytesToCopy = Math.min(oneRun, bytesToWriteBeforeWaiting);
                    System.arraycopy(buffer, offset, this.buffer, tail, bytesToCopy);
                    offset += bytesToCopy;
                    bytesToWriteBeforeWaiting -= bytesToCopy;
                    storedBytes += bytesToCopy;
                }
                if (wasEmpty) notify();
            }
        }
        return true;
    }
}
