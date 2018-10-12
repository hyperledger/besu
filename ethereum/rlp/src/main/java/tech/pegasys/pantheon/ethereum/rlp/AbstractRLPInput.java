package tech.pegasys.pantheon.ethereum.rlp;

import static com.google.common.base.Preconditions.checkState;

import tech.pegasys.pantheon.ethereum.rlp.RLPDecodingHelpers.Kind;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.MutableBytes32;
import tech.pegasys.pantheon.util.uint.UInt256;
import tech.pegasys.pantheon.util.uint.UInt256Value;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.function.Function;

abstract class AbstractRLPInput implements RLPInput {

  private final boolean lenient;

  protected long size;

  // Information on the item the input currently is at (next thing to read).
  protected long
      currentItem; // Offset in value to the beginning of the item (or value.size() if done)
  private Kind currentKind; // Kind of the item.
  private long currentPayloadOffset; // Offset to the beginning of the current item payload.
  private int currentPayloadSize; // Size of the current item payload.

  // Information regarding opened list. The depth is how many list deep we are, and endOfListOffset
  // holds the offset in value at which each list ends (indexed by depth). Allows to know if we're
  // at the end of our current list, and if there is any unfinished one.
  private int depth;
  private long[] endOfListOffset = new long[4];

  AbstractRLPInput(final boolean lenient) {
    this.lenient = lenient;
  }

  protected void init(final long inputSize, final boolean shouldFitInputSizeExactly) {
    if (inputSize == 0) {
      return;
    }

    currentItem = 0;
    // Initially set the size to the input as prepareCurrentTime() needs it. Once we've prepare the
    // top level item, we know where that item ends exactly and can update the size to that more
    // precise value (which basically mean we'll throw errors on malformed inputs potentially
    // sooner).
    size = inputSize;
    prepareCurrentItem();
    if (currentKind.isList()) {
      size = nextItem();
    }

    // No matter what, if the first item advertise a payload ending after the end of the input, that
    // input is corrupted.
    if (size > inputSize) {
      // Our error message include a snippet of the input and that code assume size is not set
      // outside of the input, and that's exactly the case we're testing, so resetting the size
      // simply for the sake of the error being properly generated.
      final long itemEnd = size;
      size = inputSize;
      throw corrupted(
          "Input doesn't have enough data for RLP encoding: encoding advertise a "
              + "payload ending at byte %d but input has size %d",
          itemEnd, inputSize);
    }

    if (shouldFitInputSizeExactly && inputSize > size) {
      throwMalformed(
          "Input has extra data after RLP encoding: encoding ends at byte %d but "
              + "input has size %d",
          size, inputSize);
    }

    validateCurrentItem();
  }

  protected abstract byte inputByte(long offset);

  protected abstract BytesValue inputSlice(long offset, int length);

  protected abstract Bytes32 inputSlice32(long offset);

  protected abstract String inputHex(long offset, int length);

  protected abstract BigInteger getUnsignedBigInteger(long offset, int length);

  protected abstract int getInt(long offset);

  protected abstract long getLong(long offset);

  /**
   * Sets the input to the item provided (an offset to the beginning of an item) and check this is
   * valid.
   *
   * @param item the value to which the current item is to be set.
   */
  protected void setTo(final long item) {
    currentItem = item;
    if (currentItem >= size) {
      // Setting somewhat safe values so that multiple calls to setTo(nextItem()) don't do anything
      // even when at the end.
      currentKind = null;
      currentPayloadOffset = item;
      currentPayloadSize = 0;
      return;
    }
    prepareCurrentItem();
    validateCurrentItem();
  }

  private void prepareCurrentItem() {
    // Sets the kind of the item, the offset at which his payload starts and the size of this
    // payload.
    final int prefix = inputByte(currentItem) & 0xFF;
    currentKind = Kind.of(prefix);
    switch (currentKind) {
      case BYTE_ELEMENT:
        currentPayloadOffset = currentItem;
        currentPayloadSize = 1;
        break;
      case SHORT_ELEMENT:
        currentPayloadOffset = currentItem + 1;
        currentPayloadSize = prefix - 0x80;
        break;
      case LONG_ELEMENT:
        final int sizeLengthElt = prefix - 0xb7;
        currentPayloadOffset = currentItem + 1 + sizeLengthElt;
        currentPayloadSize = readLongSize(currentItem, sizeLengthElt);
        break;
      case SHORT_LIST:
        currentPayloadOffset = currentItem + 1;
        currentPayloadSize = prefix - 0xc0;
        break;
      case LONG_LIST:
        final int sizeLengthList = prefix - 0xf7;
        currentPayloadOffset = currentItem + 1 + sizeLengthList;
        currentPayloadSize = readLongSize(currentItem, sizeLengthList);
        break;
    }
  }

  private void validateCurrentItem() {
    if (currentKind == Kind.SHORT_ELEMENT) {
      // Validate that a single byte SHORT_ELEMENT payload is not <= 0x7F. If it is, is should have
      // been written as a BYTE_ELEMENT.
      if (currentPayloadSize == 1
          && currentPayloadOffset < size
          && (payloadByte(0) & 0xFF) <= 0x7F) {
        throwMalformed(
            "Malformed RLP item: single byte value 0x%s should have been "
                + "written without a prefix",
            hex(currentPayloadOffset, currentPayloadOffset + 1));
      }
    }

    if (currentPayloadSize > 0 && currentPayloadOffset >= size) {
      throw corrupted(
          "Invalid RLP item: payload should start at offset %d but input has only " + "%d bytes",
          currentPayloadOffset, size);
    }
    if (size - currentPayloadOffset < currentPayloadSize) {
      throw corrupted(
          "Invalid RLP item: payload starting at byte %d should be %d bytes long, but input "
              + "has only %d bytes from that offset",
          currentPayloadOffset, currentPayloadSize, size - currentPayloadOffset);
    }
  }

  /** The size of the item payload for a "long" item, given the length in bytes of the said size. */
  private int readLongSize(final long item, final int sizeLength) {
    // We will read sizeLength bytes from item + 1. There must be enough bytes for this or the input
    // is corrupted.
    if (size - (item + 1) < sizeLength) {
      throw corrupted(
          "Invalid RLP item: value of size %d has not enough bytes to read the %d "
              + "bytes payload size",
          size, sizeLength);
    }

    // That size (which is at least 1 byte by construction) shouldn't have leading zeros.
    if (inputByte(item + 1) == 0) {
      throwMalformed("Malformed RLP item: size of payload has leading zeros");
    }

    final int res = RLPDecodingHelpers.extractSizeFromLong(this::inputByte, item + 1, sizeLength);

    // We should not have had the size written separately if it was less than 56 bytes long.
    if (res < 56) {
      throwMalformed("Malformed RLP item: written as a long item, but size %d < 56 bytes", res);
    }

    return res;
  }

  private long nextItem() {
    return currentPayloadOffset + currentPayloadSize;
  }

  @Override
  public boolean isDone() {
    // The input is done if we're out of input, but also if we've called leaveList() an appropriate
    // amount of times.
    return currentItem >= size && depth == 0;
  }

  private String hex(final long start, final long taintedEnd) {
    final long end = Math.min(taintedEnd, size);
    final long size = end - start;
    if (size < 10) {
      return inputHex(start, Math.toIntExact(size));
    } else {
      return String.format("%s...%s", inputHex(start, 4), inputHex(end - 4, 4));
    }
  }

  private void throwMalformed(final String msg, final Object... params) {
    if (!lenient) throw new MalformedRLPInputException(errorMsg(msg, params));
  }

  private CorruptedRLPInputException corrupted(final String msg, final Object... params) {
    throw new CorruptedRLPInputException(errorMsg(msg, params));
  }

  private RLPException error(final String msg, final Object... params) {
    throw new RLPException(errorMsg(msg, params));
  }

  private RLPException error(final Throwable cause, final String msg, final Object... params) {
    throw new RLPException(errorMsg(msg, params), cause);
  }

  private String errorMsg(final String message, final Object... params) {
    final long start = currentItem;
    final long end = Math.min(size, nextItem());
    final long realStart = Math.max(0, start - 4);
    final long realEnd = Math.min(size, end + 4);
    return String.format(
        message + " (at bytes %d-%d: %s%s[%s]%s%s)",
        concatParams(
            params,
            start,
            end,
            realStart == 0 ? "" : "...",
            hex(realStart, start),
            hex(start, end),
            hex(end, realEnd),
            realEnd == size ? "" : "..."));
  }

  private static Object[] concatParams(final Object[] initial, final Object... others) {
    final Object[] params = Arrays.copyOf(initial, initial.length + others.length);
    System.arraycopy(others, 0, params, initial.length, others.length);
    return params;
  }

  private void checkElt(final String what) {
    if (currentItem >= size) {
      throw error("Cannot read a %s, input is fully consumed", what);
    }
    if (isEndOfCurrentList()) {
      throw error("Cannot read a %s, reached end of current list", what);
    }
    if (currentKind.isList()) {
      throw error("Cannot read a %s, current item is a list", what);
    }
  }

  private void checkElt(final String what, final int expectedSize) {
    checkElt(what);
    if (currentPayloadSize != expectedSize)
      throw error(
          "Cannot read a %s, expecting %d bytes but current element is %d bytes long",
          what, expectedSize, currentPayloadSize);
  }

  private void checkScalar(final String what) {
    checkElt(what);
    if (currentPayloadSize > 0 && payloadByte(0) == 0) {
      throwMalformed("Invalid scalar, has leading zeros bytes");
    }
  }

  private void checkScalar(final String what, final int maxExpectedSize) {
    checkScalar(what);
    if (currentPayloadSize > maxExpectedSize)
      throw error(
          "Cannot read a %s, expecting a maximum of %d bytes but current element is %d bytes long",
          what, maxExpectedSize, currentPayloadSize);
  }

  private byte payloadByte(final int offsetInPayload) {
    return inputByte(currentPayloadOffset + offsetInPayload);
  }

  private BytesValue payloadSlice() {
    return inputSlice(currentPayloadOffset, currentPayloadSize);
  }

  @Override
  public void skipNext() {
    setTo(nextItem());
  }

  @Override
  public long readLongScalar() {
    checkScalar("long scalar", 8);
    long res = 0;
    int shift = 0;
    for (int i = 0; i < currentPayloadSize; i++) {
      res |= ((long) payloadByte(currentPayloadSize - i - 1) & 0xFF) << shift;
      shift += 8;
    }
    if (res < 0) {
      error("long scalar %s is not non-negative", res);
    }
    setTo(nextItem());
    return res;
  }

  @Override
  public int readIntScalar() {
    checkScalar("int scalar", 4);
    int res = 0;
    int shift = 0;
    for (int i = 0; i < currentPayloadSize; i++) {
      res |= (payloadByte(currentPayloadSize - i - 1) & 0xFF) << shift;
      shift += 8;
    }
    setTo(nextItem());
    return res;
  }

  @Override
  public BigInteger readBigIntegerScalar() {
    checkScalar("arbitrary precision scalar");
    final BigInteger res = getUnsignedBigInteger(currentPayloadOffset, currentPayloadSize);
    setTo(nextItem());
    return res;
  }

  private Bytes32 readBytes32Scalar() {
    checkScalar("32-bytes scalar", 32);
    final MutableBytes32 res = MutableBytes32.create();
    payloadSlice().copyTo(res, res.size() - currentPayloadSize);
    setTo(nextItem());
    return res;
  }

  @Override
  public UInt256 readUInt256Scalar() {
    return readBytes32Scalar().asUInt256();
  }

  @Override
  public <T extends UInt256Value<T>> T readUInt256Scalar(final Function<Bytes32, T> bytesWrapper) {
    final Bytes32 bytes = readBytes32Scalar();
    try {
      return bytesWrapper.apply(bytes);
    } catch (final Exception e) {
      throw error(e, "Problem decoding UInt256 scalar");
    }
  }

  @Override
  public byte readByte() {
    checkElt("byte", 1);
    final byte b = payloadByte(0);
    setTo(nextItem());
    return b;
  }

  @Override
  public short readShort() {
    checkElt("2-byte short", 2);
    final short s = (short) ((payloadByte(0) << 8) | (payloadByte(1) & 0xFF));
    setTo(nextItem());
    return s;
  }

  @Override
  public int readInt() {
    checkElt("4-byte int", 4);
    final int res = getInt(currentPayloadOffset);
    setTo(nextItem());
    return res;
  }

  @Override
  public long readLong() {
    checkElt("8-byte long", 8);
    final long res = getLong(currentPayloadOffset);
    setTo(nextItem());
    return res;
  }

  @Override
  public InetAddress readInetAddress() {
    checkElt("inet address");
    if (currentPayloadSize != 4 && currentPayloadSize != 16) {
      throw error(
          "Cannot read an inet address, current element is %d bytes long", currentPayloadSize);
    }
    final byte[] address = new byte[currentPayloadSize];
    for (int i = 0; i < currentPayloadSize; i++) {
      address[i] = payloadByte(i);
    }
    setTo(nextItem());
    try {
      return InetAddress.getByAddress(address);
    } catch (final UnknownHostException e) {
      // InetAddress.getByAddress() only throws for an address of illegal length, and we have
      // validated that length already, this this genuinely shouldn't throw.
      throw new AssertionError(e);
    }
  }

  @Override
  public BytesValue readBytesValue() {
    checkElt("arbitrary bytes value");
    final BytesValue res = payloadSlice();
    setTo(nextItem());
    return res;
  }

  @Override
  public Bytes32 readBytes32() {
    checkElt("32 bytes value", 32);
    final Bytes32 res = inputSlice32(currentPayloadOffset);
    setTo(nextItem());
    return res;
  }

  @Override
  public <T> T readBytesValue(final Function<BytesValue, T> mapper) {
    final BytesValue res = readBytesValue();
    try {
      return mapper.apply(res);
    } catch (final Exception e) {
      throw error(e, "Problem decoding bytes value");
    }
  }

  @Override
  public RLPInput readAsRlp() {
    if (currentItem >= size) {
      throw error("Cannot read current element as RLP, input is fully consumed");
    }
    final long next = nextItem();
    final RLPInput res = RLP.input(inputSlice(currentItem, Math.toIntExact(next - currentItem)));
    setTo(next);
    return res;
  }

  @Override
  public int enterList() {
    return enterList(false);
  }

  /**
   * Enters the list, but does not return the number of item of the entered list. This prevents
   * bouncing all around the file to read values that are probably not even used.
   *
   * @see #enterList()
   * @param skipCount true if the element count is not required.
   * @return -1 if skipCount==true, otherwise, the number of item of the entered list.
   */
  public int enterList(final boolean skipCount) {
    if (currentItem >= size) {
      throw error("Cannot enter a lists, input is fully consumed");
    }
    if (!currentKind.isList()) {
      throw error("Expected current item to be a list, but it is: " + currentKind);
    }

    ++depth;
    if (depth > endOfListOffset.length) {
      endOfListOffset = Arrays.copyOf(endOfListOffset, (endOfListOffset.length * 3) / 2);
    }
    // The first list element is the beginning of the payload. It's end is the end of this item.
    final long listStart = currentPayloadOffset;
    final long listEnd = nextItem();

    if (listEnd > size) {
      throw corrupted(
          "Invalid RLP item: list payload should end at offset %d but input has only %d bytes",
          listEnd, size);
    }

    endOfListOffset[depth - 1] = listEnd;
    int count = -1;

    if (!skipCount) {
      // Count list elements from first one.
      count = 0;
      setTo(listStart);
      while (currentItem < listEnd) {
        ++count;
        setTo(nextItem());
      }
    }

    // And lastly reset on the list first element before returning
    setTo(listStart);
    return count;
  }

  @Override
  public void leaveList() {
    leaveList(false);
  }

  @Override
  public void leaveList(final boolean ignoreRest) {
    checkState(depth > 0, "Not within an RLP list");

    if (!ignoreRest) {
      final long listEndOffset = endOfListOffset[depth - 1];
      if (currentItem < listEndOffset) throw error("Not at the end of the current list");
    }

    --depth;
  }

  @Override
  public boolean nextIsList() {
    return currentKind != null && currentKind.isList();
  }

  @Override
  public boolean nextIsNull() {
    return currentKind == Kind.SHORT_ELEMENT && currentPayloadSize == 0;
  }

  @Override
  public int nextSize() {
    return currentPayloadSize;
  }

  @Override
  public boolean isEndOfCurrentList() {
    return depth > 0 && currentItem >= endOfListOffset[depth - 1];
  }

  @Override
  public void reset() {
    setTo(0);
  }
}
