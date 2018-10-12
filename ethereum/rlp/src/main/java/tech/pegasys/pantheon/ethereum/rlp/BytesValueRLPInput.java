package tech.pegasys.pantheon.ethereum.rlp;

import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.math.BigInteger;

/** An {@link RLPInput} that reads RLP encoded data from a {@link BytesValue}. */
public class BytesValueRLPInput extends AbstractRLPInput {

  // The RLP encoded data.
  private final BytesValue value;

  public BytesValueRLPInput(final BytesValue value, final boolean lenient) {
    super(lenient);
    this.value = value;
    init(value.size(), true);
  }

  @Override
  protected byte inputByte(final long offset) {
    return value.get(Math.toIntExact(offset));
  }

  @Override
  protected BytesValue inputSlice(final long offset, final int length) {
    return value.slice(Math.toIntExact(offset), length);
  }

  @Override
  protected Bytes32 inputSlice32(final long offset) {
    return Bytes32.wrap(value, Math.toIntExact(offset));
  }

  @Override
  protected String inputHex(final long offset, final int length) {
    return value.slice(Math.toIntExact(offset), length).toString().substring(2);
  }

  @Override
  protected BigInteger getUnsignedBigInteger(final long offset, final int length) {
    return BytesValues.asUnsignedBigInteger(value.slice(Math.toIntExact(offset), length));
  }

  @Override
  protected int getInt(final long offset) {
    return value.getInt(Math.toIntExact(offset));
  }

  @Override
  protected long getLong(final long offset) {
    return value.getLong(Math.toIntExact(offset));
  }

  @Override
  public BytesValue raw() {
    return value;
  }
}
