package com.termux.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ByteQueueTest {

	private static void assertArrayEquals(byte[] expected, byte[] actual) {
		if (expected.length != actual.length) {
			fail("Difference array length");
		}
		for (int i = 0; i < expected.length; i++) {
			if (expected[i] != actual[i]) {
				fail("Inequals at index=" + i + ", expected=" + (int) expected[i] + ", actual=" + (int) actual[i]);
			}
		}
	}

	@Test
	public void testCompleteWrites() {
		ByteQueue q = new ByteQueue(10);
		assertEquals(true, q.write(new byte[]{1, 2, 3}, 0, 3));

		byte[] arr = new byte[10];
		assertEquals(3, q.read(arr, true));
		assertArrayEquals(new byte[]{1, 2, 3}, new byte[]{arr[0], arr[1], arr[2]});

		assertEquals(true, q.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 0, 10));
		assertEquals(10, q.read(arr, true));
		assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, arr);
	}

	@Test
	public void testQueueWraparound() {
		ByteQueue q = new ByteQueue(10);

		byte[] origArray = new byte[]{1, 2, 3, 4, 5, 6};
		byte[] readArray = new byte[origArray.length];
		for (int i = 0; i < 20; i++) {
			q.write(origArray, 0, origArray.length);
			assertEquals(origArray.length, q.read(readArray, true));
			assertArrayEquals(origArray, readArray);
		}
	}

	@Test
	public void testWriteNotesClosing() {
		ByteQueue q = new ByteQueue(10);
		q.close();
		assertEquals(false, q.write(new byte[]{1, 2, 3}, 0, 3));
	}

	@Test
	public void testReadNonBlocking() {
		ByteQueue q = new ByteQueue(10);
		assertEquals(0, q.read(new byte[128], false));
	}

}
