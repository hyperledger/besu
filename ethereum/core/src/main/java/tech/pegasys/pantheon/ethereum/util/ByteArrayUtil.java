package net.consensys.pantheon.ethereum.util;

import com.google.common.primitives.Longs;

public final class ByteArrayUtil {

  private ByteArrayUtil() {
    // Utility Class
  }

  public static int compare(
      final byte[] buffer1,
      final int offset1,
      final int length1,
      final byte[] buffer2,
      final int offset2,
      final int length2) {
    if (buffer1 == buffer2 && offset1 == offset2 && length1 == length2) {
      return 0;
    }
    final int end1 = offset1 + length1;
    final int end2 = offset2 + length2;
    for (int i = offset1, j = offset2; i < end1 && j < end2; i++, j++) {
      final int a = buffer1[i] & 0xff;
      final int b = buffer2[j] & 0xff;
      if (a != b) {
        return a - b;
      }
    }
    return length1 - length2;
  }

  public static long readLong(final int index, final byte[] buffer) {
    return Longs.fromBytes(
        buffer[index],
        buffer[index + 1],
        buffer[index + 2],
        buffer[index + 3],
        buffer[index + 4],
        buffer[index + 5],
        buffer[index + 6],
        buffer[index + 7]);
  }
}
