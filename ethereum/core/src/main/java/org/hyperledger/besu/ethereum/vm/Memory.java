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
package org.hyperledger.besu.ethereum.vm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Objects;

import com.google.common.base.Joiner;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
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
  private static final long MAX_BYTES = 32L * Integer.MAX_VALUE;

  /**
   * The data stored within the memory.
   *
   * <p>Note that the current Ethereum spec don't put a limit on memory, so we could theoretically
   * overflow this. That said we can already store up to 64GB and:
   *
   * <ul>
   *   <li>that's 64GB of underlying bytes, but *a lot* more physical memory use in practice,
   *       because ... Java; worth testing but I suspect all but the beefiest servers would OOM
   *       before we come close to his in the first place.
   *   <li>the price of a transaction needing more than that is likely prohibitive.
   * </ul>
   *
   * So this is likely a reasonable limitation, at least at first (and possibly ever if I'm to bet).
   */
  /*
   * Implementation note: using an array of word have a bunch of advantages: - it can make
   * expansions cheaper (less bytes to copy on resize). - it makes word-related operations simple. -
   * it's an easy way to put a higher limit on addressable memory (Integer.MAX_VALUE word is 32
   * times more capacity than Integer.MAX_VALUE bytes). but it's not without downsides either: -
   * non-word aligned operations (on more than 1 byte) are currently more expansive (could be
   * improved, but with more works). - sequential access, even word-aligned, might be slower than if
   * we allocated larger byte arrays underneath due to cache effects. This is likely good enough
   * initially, but a page-based design (with a page being X word, X to be determined) could be
   * worth exploring as a future improvement.
   *
   * Lastly note that we may want to share the underlying implementation with the VM Stack: the
   * stack is really just growable memory that is grown/accessed from the end and on by fully word,
   * but the same page-based design probably make sense.
   */
  private final ArrayList<Bytes32> data;

  // Really data.size(), but cached as a UInt256 to avoid recomputing it each time.
  private UInt256 activeWords;

  public Memory() {
    this(new ArrayList<>());
  }

  private Memory(final ArrayList<Bytes32> data) {
    this.data = data;
    this.activeWords = UInt256.valueOf(data.size());
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

  private long asByteIndex(final UInt256 w) {
    try {
      final long v = w.toLong();
      checkByteIndex(v);
      return v;
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

  private int wordForByte(final long byteIndex) {
    checkByteIndex(byteIndex);
    return (int) (byteIndex / Bytes32.SIZE);
  }

  private int indexInWord(final long byteIndex) {
    checkByteIndex(byteIndex);
    return (int) (byteIndex - ((byteIndex / Bytes32.SIZE) * Bytes32.SIZE));
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
      final long byteSize = (long) location.intValue() + (long) numBytes.intValue();
      int wordSize = (int) (byteSize / Bytes32.SIZE);
      if (byteSize % Bytes32.SIZE != 0) wordSize += 1;
      return wordSize > data.size() ? UInt256.valueOf(wordSize) : activeWords;
    } else {
      // Slow, rare path

      // Note that this is one place where, while the result will fit UInt256, we should compute
      // without modulo for extreme cases.
      final BigInteger byteSize =
          location.toBytes().toUnsignedBigInteger().add(numBytes.toBytes().toUnsignedBigInteger());
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
   * @param address The location in memory to start with.
   * @param numBytes The number of bytes to get.
   */
  void ensureCapacityForBytes(final long address, final int numBytes) {
    // Do not increase the memory capacity if no bytes are being written
    // regardless of what the address may be.
    if (numBytes == 0) {
      return;
    }
    final int lastWordRequired = wordForByte(address + numBytes - 1);
    maybeExpandCapacity(lastWordRequired + 1);
  }

  /**
   * Expands the memory to the specified number of active words.
   *
   * @param newActiveWords The new number of active words to expand to.
   */
  private void maybeExpandCapacity(final int newActiveWords) {
    if (data.size() >= newActiveWords) return;

    // Require full capacity to guarantee we don't resize more than once.
    data.ensureCapacity(newActiveWords);
    final int toAdd = newActiveWords - data.size();
    for (int i = 0; i < toAdd; i++) {
      data.add(MutableBytes32.create());
    }
    this.activeWords = UInt256.valueOf(data.size());
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
    return Objects.hash(data);
  }

  /**
   * Returns the current number of active bytes stored in memory.
   *
   * @return The current number of active bytes stored in memory.
   */
  long getActiveBytes() {
    return (long) data.size() * Bytes32.SIZE;
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

    final long start = asByteIndex(location);

    ensureCapacityForBytes(start, length);

    // Index of last byte to set.
    final long end = start + length - 1;

    final int startWord = wordForByte(start);
    final int idxInStart = indexInWord(start);
    final int endWord = wordForByte(end);
    final int idxInEnd = indexInWord(end);

    if (startWord == endWord) {
      // Bytes within a word, fast-path.
      final Bytes bytes = data.get(startWord);
      return idxInStart == 0 && length == Bytes32.SIZE
          //          ? bytes.copy()
          //          : bytes.slice(idxInStart, length).copy();
          ? bytes
          : bytes.slice(idxInStart, length);
    }

    // Spans multiple word, slower path.
    final int bytesInStartWord = Bytes32.SIZE - idxInStart;
    final int bytesInEndWord = idxInEnd + 1;

    final MutableBytes result = MutableBytes.create(length);
    int resultIdx = 0;
    data.get(startWord).slice(idxInStart).copyTo(result, resultIdx);
    resultIdx += bytesInStartWord;
    for (int i = startWord + 1; i < endWord; i++) {
      data.get(i).copyTo(result, resultIdx);
      resultIdx += Bytes32.SIZE;
    }
    data.get(endWord).slice(0, bytesInEndWord).copyTo(result, resultIdx);
    return result;
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
   *     be copied from {@code value}; if {@code numBytes < value.size()}, then only the bytes in
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

    final long start = asByteIndex(location);
    final int length = asByteLength(numBytes);

    ensureCapacityForBytes(start, length);

    // We've properly expanded memory as needed. We now have simply have to copy the
    // min(length, value.size()) first bytes of value and clear any bytes that exceed value's length
    if (taintedValue.isEmpty()) {
      clearBytes(location, numBytes);
      return;
    }
    final Bytes value;
    if (taintedValue.size() > length) {
      value = taintedValue.slice(0, length);
    } else if (taintedValue.size() < length) {
      value = taintedValue;
      clearBytes(location.add(taintedValue.size()), numBytes.subtract(taintedValue.size()));
    } else {
      value = taintedValue;
    }

    // Index of last byte to set.
    final long end = start + value.size() - 1;

    final int startWord = wordForByte(start);
    final int idxInStart = indexInWord(start);
    final int endWord = wordForByte(end);

    if (startWord == endWord) {
      // Bytes within a word, fast-path.
      final MutableBytes mb = data.get(startWord).mutableCopy();

      value.copyTo(mb, idxInStart);
      data.set(startWord, (Bytes32) mb.copy());
      return;
    }

    // Spans multiple word, slower path.
    final int bytesInStartWord = Bytes32.SIZE - idxInStart;

    int valueIdx = 0;
    final MutableBytes startMutable = data.get(startWord).mutableCopy();
    value.slice(valueIdx, bytesInStartWord).copyTo(startMutable, idxInStart);
    data.set(startWord, (Bytes32) startMutable.copy());
    valueIdx += bytesInStartWord;
    for (int i = startWord + 1; i < endWord; i++) {
      final MutableBytes mb = data.get(i).mutableCopy();
      value.slice(valueIdx, Bytes32.SIZE).copyTo(mb);
      data.set(i, (Bytes32) mb.copy());
      valueIdx += Bytes32.SIZE;
    }
    final MutableBytes endMutable = data.get(endWord).mutableCopy();
    value.slice(valueIdx).copyTo(endMutable, 0);
    data.set(endWord, (Bytes32) endMutable.copy());
  }

  /**
   * Clears (set to 0) some contiguous number of bytes in memory.
   *
   * @param location The location in memory from which to start clearing the bytes.
   * @param numBytes The number of bytes to clear.
   */
  private void clearBytes(final UInt256 location, final UInt256 numBytes) {
    // See getBytes for why we checki length == 0 first, before calling asByteIndex(location).
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
  private void clearBytes(final long location, final int numBytes) {
    if (numBytes == 0) {
      return;
    }

    ensureCapacityForBytes(location, numBytes);

    // Index of last byte to set.
    final long end = location + numBytes - 1;

    final int startWord = wordForByte(location);
    final int idxInStart = indexInWord(location);
    final int endWord = wordForByte(end);
    final int idxInEnd = indexInWord(end);

    if (startWord == endWord) {
      // Bytes within a word, fast-path.
      final MutableBytes storingBytes = data.get(startWord).mutableCopy();
      final MutableBytes bytes =
          (idxInStart != 0 || numBytes != Bytes32.SIZE)
              ? storingBytes.mutableSlice(idxInStart, numBytes)
              : storingBytes;
      bytes.clear();
      data.set(startWord, (Bytes32) storingBytes);
      return;
    }

    // Spans multiple word, slower path.
    final int bytesInStartWord = Bytes32.SIZE - idxInStart;
    final int bytesInEndWord = idxInEnd + 1;

    final MutableBytes startMutable = data.get(startWord).mutableCopy();
    startMutable.mutableSlice(idxInStart, bytesInStartWord).clear();
    data.set(startWord, (Bytes32) startMutable.copy());
    for (int i = startWord + 1; i < endWord; i++) {
      data.set(i, Bytes32.ZERO);
    }
    final MutableBytes endMutable = data.get(endWord).mutableCopy();
    endMutable.mutableSlice(0, bytesInEndWord).clear();
    data.set(endWord, (Bytes32) endMutable.copy());
  }

  /**
   * Sets a single byte in memory at the provided location.
   *
   * @param location the location of the byte to set.
   * @param value the value to set for the byte at {@code location}.
   */
  void setByte(final UInt256 location, final byte value) {
    final long start = asByteIndex(location);
    ensureCapacityForBytes(start, 1);

    final int word = wordForByte(start);
    final int idxInWord = indexInWord(start);

    final MutableBytes mb = data.get(word).mutableCopy();
    mb.set(idxInWord, value);
    data.set(word, (Bytes32) mb.copy());
  }

  /**
   * Returns a copy of the 32-bytes word that begins at the specified memory location.
   *
   * @param location The memory location the 256-bit word begins at.
   * @return a copy of the 32-bytes word that begins at the specified memory location.
   */
  public Bytes32 getWord(final UInt256 location) {
    final long start = asByteIndex(location);
    ensureCapacityForBytes(start, Bytes32.SIZE);

    final int startWord = wordForByte(start);
    final int idxInStart = indexInWord(start);

    if (idxInStart == 0) {
      // Word-aligned. Fast-path.
      return data.get(startWord).copy();
    }

    // Spans 2 memory word, slower path.
    final MutableBytes32 result = MutableBytes32.create();
    final int sizeInFirstWord = Bytes32.SIZE - idxInStart;
    data.get(startWord).slice(idxInStart, sizeInFirstWord).copyTo(result, 0);
    data.get(startWord + 1)
        .slice(0, Bytes32.SIZE - sizeInFirstWord)
        .copyTo(result, sizeInFirstWord);
    return result;
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
    final long start = asByteIndex(location);
    ensureCapacityForBytes(start, Bytes32.SIZE);

    final int startWord = wordForByte(start);
    final int idxInStart = indexInWord(start);

    if (idxInStart == 0) {
      // Word-aligned. Fast-path.
      data.set(startWord, bytes);
      return;
    }

    // Spans 2 memory word, slower path.
    final int sizeInFirstWord = Bytes32.SIZE - idxInStart;
    final MutableBytes firstWord = data.get(startWord).mutableCopy();
    bytes.slice(0, sizeInFirstWord).copyTo(firstWord, idxInStart);
    final MutableBytes secondWord = data.get(startWord + 1).mutableCopy();
    bytes.slice(sizeInFirstWord).copyTo(secondWord, 0);
    data.set(startWord, (Bytes32) firstWord);
    data.set(startWord + 1, (Bytes32) secondWord);
  }

  @Override
  public String toString() {
    if (data.isEmpty()) {
      return "";
    }

    return '\n' + Joiner.on("\n").join(data);
  }
}
