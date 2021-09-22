/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.evm.frame;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.apache.tuweni.units.bigints.UInt256s;

/**
 * A EVM memory implementation.
 *
 * <p>Note: this is meant to map to I in Section 9.1 "Basics" and Section 9.4.1 "Machine State" in
 * the Yellow Paper Revision 59dccd.
 */
public class Memory {

  // See below.
  private static final long MAX_BYTES = Integer.MAX_VALUE;

  /**
   * The data stored within the memory.
   *
   * <p>Note that the current Ethereum spec don't put a limit on memory, so we could theoretically
   * overflow this. A byte array implementation limits us to 2GiB. But that would cost over 51
   * trillion gas. So this is likely a reasonable limitation, at least at first.
   */
  private MutableBytes data;

  private UInt256 activeWords;
  private int dataSize256;

  public Memory() {
    data = MutableBytes.EMPTY;
    updateSize();
  }

  private void updateSize() {
    dataSize256 = data.size() / Bytes32.SIZE;
    activeWords = UInt256.valueOf(dataSize256);
  }

  private static RuntimeException overflow(final long v) {
    return overflow(String.valueOf(v));
  }

  private static RuntimeException overflow(final String v) {
    // TODO: we should probably have another specific exception so this properly end up as an
    // exceptional halt condition with a clear message (message that can indicate that if anyone
    // runs into this, he should contact us so we know it's a case we do need to handle).
    final String msg = "Memory index or length %s too large, cannot be larger than %d";
    throw new IllegalStateException(String.format(msg, v, MAX_BYTES));
  }

  private void checkByteIndex(final long v) {
    // We can have at most MAX_BYTES, so an index can only at most MAX_BYTES - 1.
    if (v < 0 || v >= MAX_BYTES) throw overflow(v);
  }

  private int asByteIndex(final UInt256 w) {
    try {
      final long v = w.toLong();
      checkByteIndex(v);
      return (int) v;
    } catch (final IllegalStateException e) {
      throw overflow(w.toString());
    }
  }

  private static int asByteLength(final UInt256 l) {
    try {
      // While we can theoretically support up to 32 * Integer.MAX_VALUE due to storing words, and
      // so an index in memory need to be a long internally, we simply cannot load/store more than
      // Integer.MAX_VALUE bytes at a time (Bytes has an int size).
      return l.intValue();
    } catch (final IllegalStateException e) {
      throw overflow(l.toString());
    }
  }

  /**
   * For use in memoryExpansionGasCost() of GasCost. Returns the number of new active words that
   * accommodate at least the number of specified bytes from the provide memory offset.
   *
   * <p>Not that this has to return a UInt256 for Gas calculation, in case someone writes code that
   * require a crazy amount of data. Such allocation should get prohibitive however and we will end
   * up with an Out-of-Gas error.
   *
   * @param location The offset in memory from which we want to accommodate {@code numBytes}.
   * @param numBytes The minimum number of bytes in memory.
   * @return The number of active words that accommodate at least the number of specified bytes.
   */
  UInt256 calculateNewActiveWords(final UInt256 location, final UInt256 numBytes) {
    if (numBytes.isZero()) {
      return activeWords;
    }

    if (location.fitsInt() && numBytes.fitsInt()) {
      // Fast common path (note that we work on int but use long arithmetic to avoid issues)
      final long byteSize = location.toLong() + numBytes.toLong();
      int wordSize = Math.toIntExact(byteSize / Bytes32.SIZE);
      if (byteSize % Bytes32.SIZE != 0) wordSize += 1;
      return wordSize > dataSize256 ? UInt256.valueOf(wordSize) : activeWords;
    } else {
      // Slow, rare path

      // Note that this is one place where, while the result will fit UInt256, we should compute
      // without modulo for extreme cases.
      final BigInteger byteSize =
          location.toUnsignedBigInteger().add(numBytes.toUnsignedBigInteger());
      final BigInteger[] result = byteSize.divideAndRemainder(BigInteger.valueOf(Bytes32.SIZE));
      BigInteger wordSize = result[0];
      if (!result[1].equals(BigInteger.ZERO)) {
        wordSize = wordSize.add(BigInteger.ONE);
      }
      return UInt256s.max(activeWords, UInt256.valueOf(wordSize));
    }
  }

  /**
   * Expands the active words to accommodate the specified byte position.
   *
   * @param offset The location in memory to start with.
   * @param numBytes The number of bytes to get.
   */
  void ensureCapacityForBytes(final UInt256 offset, final UInt256 numBytes) {
    if (!offset.fitsInt()) return;
    if (!numBytes.fitsInt()) return;
    ensureCapacityForBytes(offset.intValue(), numBytes.intValue());
  }

  /**
   * Expands the active words to accommodate the specified byte position.
   *
   * @param offset The location in memory to start with.
   * @param numBytes The number of bytes to get.
   */
  void ensureCapacityForBytes(final int offset, final int numBytes) {
    // Do not increase the memory capacity if no bytes are being written
    // regardless of what the address may be.
    if (numBytes == 0) {
      return;
    }
    final int lastByteIndex = Math.addExact(offset, numBytes);
    final int lastWordRequired = ((lastByteIndex - 1) / Bytes32.SIZE);
    maybeExpandCapacity(lastWordRequired + 1);
  }

  /**
   * Expands the memory to the specified number of active words.
   *
   * @param newActiveWords The new number of active words to expand to.
   */
  private void maybeExpandCapacity(final int newActiveWords) {
    if (dataSize256 >= newActiveWords) return;

    // Require full capacity to guarantee we don't resize more than once.
    final MutableBytes newData = MutableBytes.create(newActiveWords * Bytes32.SIZE);
    data.copyTo(newData, 0);
    data = newData;
    updateSize();
  }

  /**
   * Returns true if the object is equal to this memory instance; otherwise false.
   *
   * @param other The object to compare this memory instance with.
   * @return True if the object is equal to this memory instance.
   */
  @Override
  public boolean equals(final Object other) {
    if (other == null) return false;
    if (other == this) return true;
    if (!(other instanceof Memory)) return false;

    final Memory that = (Memory) other;
    return this.data.equals(that.data);
  }

  @Override
  public int hashCode() {
    return data.hashCode();
  }

  /**
   * Returns the current number of active bytes stored in memory.
   *
   * @return The current number of active bytes stored in memory.
   */
  int getActiveBytes() {
    return data.size();
  }

  /**
   * Returns the current number of active words stored in memory.
   *
   * @return The current number of active words stored in memory.
   */
  UInt256 getActiveWords() {
    return activeWords;
  }

  /**
   * Returns a copy of bytes from memory.
   *
   * @param location The location in memory to start with.
   * @param numBytes The number of bytes to get.
   * @return A fresh copy of the bytes from memory starting at {@code location} and extending {@code
   *     numBytes}.
   */
  public Bytes getBytes(final UInt256 location, final UInt256 numBytes) {
    // Note: if length == 0, we don't require any memory expansion, whatever location is. So
    // we we must call asByteIndex(location) after this check so as it doesn't throw if the location
    // is too big but the length is 0 (which is somewhat nonsensical, but is exercise by some
    // tests).
    final int length = asByteLength(numBytes);
    if (length == 0) {
      return Bytes.EMPTY;
    }

    final int start = asByteIndex(location);

    ensureCapacityForBytes(start, length);
    return data.slice(start, numBytes.intValue());
  }

  /**
   * Copy the bytes from the provided number of bytes from the provided value to memory from the
   * provided offset.
   *
   * <p>Note that this method will extend memory to accommodate the location assigned and bytes
   * copied and so never fails.
   *
   * @param memOffset the location in memory at which to start copying the bytes of {@code value}.
   * @param sourceOffset the location in the source to start copying.
   * @param numBytes the number of bytes to set in memory. Note that this value may differ from
   *     {@code value.size()}: if {@code numBytes < value.size()} bytes, only {@code numBytes} will
   *     be copied from {@code value}; if {@code numBytes < value.size()}, then only the bytes in
   *     {@code value} will be copied, but the memory will be expanded if necessary to cover {@code
   *     numBytes} (in other words, {@link #getActiveWords()} will return a value consistent with
   *     having set {@code numBytes} bytes, even if less than that have been concretely set due to
   *     {@code value} being smaller).
   * @param bytes the bytes to copy to memory from {@code location}.
   */
  public void setBytes(
      final UInt256 memOffset,
      final UInt256 sourceOffset,
      final UInt256 numBytes,
      final Bytes bytes) {
    final int offset = sourceOffset.fitsInt() ? sourceOffset.intValue() : Integer.MAX_VALUE;
    final int length = numBytes.fitsInt() ? numBytes.intValue() : Integer.MAX_VALUE;

    if (offset >= bytes.size()) {
      clearBytes(memOffset, numBytes);
      return;
    }

    final Bytes toCopy = bytes.slice(offset, Math.min(length, bytes.size() - offset));
    setBytes(memOffset, numBytes, toCopy);
  }

  /**
   * Copy the bytes from the provided number of bytes from the provided value to memory from the
   * provided offset.
   *
   * <p>Note that this method will extend memory to accommodate the location assigned and bytes
   * copied and so never fails.
   *
   * @param location the location in memory at which to start copying the bytes of {@code value}.
   * @param numBytes the number of bytes to set in memory. Note that this value may differ from
   *     {@code value.size()}: if {@code numBytes < value.size()} bytes, only {@code numBytes} will
   *     be copied from {@code value}; if {@code numBytes > value.size()}, then only the bytes in
   *     {@code value} will be copied, but the memory will be expanded if necessary to cover {@code
   *     numBytes} (in other words, {@link #getActiveWords()} will return a value consistent with
   *     having set {@code numBytes} bytes, even if less than that have been concretely set due to
   *     {@code value} being smaller).
   * @param taintedValue the bytes to copy to memory from {@code location}.
   */
  public void setBytes(final UInt256 location, final UInt256 numBytes, final Bytes taintedValue) {
    if (numBytes.isZero()) {
      return;
    }

    final int start = asByteIndex(location);
    final int length = asByteLength(numBytes);
    final int srcLength = taintedValue.size();
    final int end = Math.addExact(start, length);

    ensureCapacityForBytes(start, length);
    if (srcLength >= length) {
      data.set(start, taintedValue.slice(0, length));
    } else {
      data.set(start, Bytes.of(new byte[end - start]));
      data.set(start, taintedValue.slice(0, srcLength));
    }
  }

  /**
   * Clears (set to 0) some contiguous number of bytes in memory.
   *
   * @param location The location in memory from which to start clearing the bytes.
   * @param numBytes The number of bytes to clear.
   */
  private void clearBytes(final UInt256 location, final UInt256 numBytes) {
    // See getBytes for why we check length == 0 first, before calling asByteIndex(location).
    final int length = asByteLength(numBytes);
    if (length == 0) {
      return;
    }
    clearBytes(asByteIndex(location), length);
  }

  /**
   * Clears (set to 0) some contiguous number of bytes in memory.
   *
   * @param location The location in memory from which to start clearing the bytes.
   * @param numBytes The number of bytes to clear.
   */
  private void clearBytes(final int location, final int numBytes) {
    if (numBytes == 0) {
      return;
    }

    ensureCapacityForBytes(location, numBytes);
    data.set(location, Bytes.of(new byte[numBytes]));
  }

  /**
   * Sets a single byte in memory at the provided location.
   *
   * @param location the location of the byte to set.
   * @param value the value to set for the byte at {@code location}.
   */
  void setByte(final UInt256 location, final byte value) {
    final int start = asByteIndex(location);
    ensureCapacityForBytes(start, 1);
    data.set(start, value);
  }

  /**
   * Returns a copy of the 32-bytes word that begins at the specified memory location.
   *
   * @param location The memory location the 256-bit word begins at.
   * @return a copy of the 32-bytes word that begins at the specified memory location.
   */
  public Bytes32 getWord(final UInt256 location) {
    final int start = asByteIndex(location);
    ensureCapacityForBytes(start, Bytes32.SIZE);
    return Bytes32.wrap(data.slice(start, Bytes32.SIZE));
  }

  /**
   * Sets a 32-bytes word in memory at the provided location.
   *
   * <p>Note that this method will extend memory to accommodate the location assigned and bytes
   * copied and so never fails.
   *
   * @param location the location at which to start setting the bytes.
   * @param bytes the 32 bytes to copy at {@code location}.
   */
  public void setWord(final UInt256 location, final Bytes32 bytes) {
    final int start = asByteIndex(location);
    ensureCapacityForBytes(start, Bytes32.SIZE);
    data.set(start, bytes);
  }

  @Override
  public String toString() {
    return data.toHexString();
  }
}
