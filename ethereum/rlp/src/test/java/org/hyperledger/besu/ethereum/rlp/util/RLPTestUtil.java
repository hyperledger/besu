/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.rlp.util;

import static java.lang.String.format;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class RLPTestUtil {

  /**
   * Recursively decodes an RLP encoded value. Byte strings are assumed to be non-scalar (leading
   * zeros are allowed).
   *
   * @param value The RLP encoded value to decode.
   * @return The output of decoding {@code value}. It will be either directly a {@link BytesValue},
   *     or a list whose elements are either {@link BytesValue}, or similarly composed sub-lists.
   * @throws RLPException if {@code value} is not a properly formed RLP encoding.
   */
  public static Object decode(final BytesValue value) {
    return decode(RLP.input(value));
  }

  private static Object decode(final RLPInput in) {
    if (!in.nextIsList()) {
      return in.readBytesValue();
    }

    final int size = in.enterList();
    final List<Object> l = new ArrayList<>(size);
    for (int i = 0; i < size; i++) l.add(decode(in));
    in.leaveList();
    return l;
  }

  /**
   * Recursively RLP encode an object consisting of recursive lists of {@link BytesValue}.
   * BytesValues are assumed to be non-scalar (leading zeros are not trimmed).
   *
   * @param obj An object that must be either directly a {@link BytesValue}, or a list whose
   *     elements are either {@link BytesValue}, or similarly composed sub-lists.
   * @return The RLP encoding corresponding to {@code obj}.
   * @throws IllegalArgumentException if {@code obj} is not a valid input (not entirely composed
   *     from lists and {@link BytesValue}).
   */
  public static BytesValue encode(final Object obj) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    encode(obj, out);
    return out.encoded();
  }

  private static void encode(final Object obj, final RLPOutput out) {
    if (obj instanceof BytesValue) {
      out.writeBytesValue((BytesValue) obj);
    } else if (obj instanceof List) {
      final List<?> l = (List<?>) obj;
      out.startList();
      for (final Object o : l) encode(o, out);
      out.endList();
    } else {
      throw new IllegalArgumentException(
          format("Invalid input type %s for RLP encoding", obj.getClass()));
    }
  }

  /**
   * Generate a random rlp-encoded value.
   *
   * @param randomSeed Seed to use for random generation.
   * @return a random rlp-encoded value
   */
  public static BytesValueRLPOutput randomRLPValue(final int randomSeed) {
    final Random random = new Random(randomSeed);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    final AtomicInteger listDepth = new AtomicInteger(0);
    int iterations = 0;
    do {
      if (iterations > 1000) {
        out.endList();
        listDepth.decrementAndGet();
        continue;
      }
      iterations += 1;

      writeRandomRLPData(out, random, listDepth);
    } while (listDepth.get() > 0);

    return out;
  }

  private static void writeRandomRLPData(
      final RLPOutput out, final Random random, final AtomicInteger listDepth) {
    switch (random.nextInt(12)) {
      case 0:
        // Write empty byte string
        out.writeBytesValue(BytesValue.EMPTY);
        break;
      case 1:
        // Small single byte
        out.writeByte((byte) random.nextInt(128));
        break;
      case 2:
        // Large single byte
        byte value = (byte) (random.nextInt(128) + 128);
        out.writeByte(value);
        break;
      case 3:
        // Small byte string
        int smallBytesSize = random.nextInt(54) + 2;
        out.writeBytesValue(randomBytesValue(random, smallBytesSize));
        break;
      case 4:
        // Large byte string
        int largeBytesSize = random.nextInt(500) + 56;
        out.writeBytesValue(randomBytesValue(random, largeBytesSize));
        break;
      case 5:
        // Close list
        if (listDepth.get() == 0) {
          // If we're outside of a list try again
          writeRandomRLPData(out, random, listDepth);
          return;
        }
        out.endList();
        listDepth.decrementAndGet();
        break;
      default:
        // Start list
        out.startList();
        listDepth.incrementAndGet();
        break;
    }
  }

  private static BytesValue randomBytesValue(final Random random, final int size) {
    final byte[] bytes = new byte[size];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) random.nextInt(256);
    }
    return BytesValue.wrap(bytes);
  }
}
