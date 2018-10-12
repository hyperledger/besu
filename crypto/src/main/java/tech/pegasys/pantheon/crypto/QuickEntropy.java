package net.consensys.pantheon.crypto;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class QuickEntropy {

  public byte[] getQuickEntropy() {
    final byte[] nanoTimeBytes = Longs.toByteArray(System.nanoTime());
    final byte[] objectHashBytes = Ints.toByteArray(new Object().hashCode());
    return Bytes.concat(nanoTimeBytes, objectHashBytes);
  }
}
