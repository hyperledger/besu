package tech.pegasys.pantheon.ethereum.core;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.DelegatingBytesValue;

public class LogTopic extends DelegatingBytesValue {

  public static final int SIZE = 32;

  private LogTopic(final BytesValue bytes) {
    super(bytes);
    checkArgument(
        bytes.size() == SIZE, "A log topic must be be %s bytes long, got %s", SIZE, bytes.size());
  }

  public static LogTopic create(final BytesValue bytes) {
    return new LogTopic(bytes);
  }

  public static LogTopic wrap(final BytesValue bytes) {
    return new LogTopic(bytes);
  }

  public static LogTopic of(final BytesValue bytes) {
    return new LogTopic(bytes.copy());
  }

  public static LogTopic fromHexString(final String str) {
    return new LogTopic(BytesValue.fromHexString(str));
  }

  /**
   * Reads the log topic from the provided RLP input.
   *
   * @param in the input from which to decode the log topic.
   * @return the read log topic.
   */
  public static LogTopic readFrom(final RLPInput in) {
    return new LogTopic(in.readBytesValue());
  }

  /**
   * Writes the log topic to the provided RLP output.
   *
   * @param out the output in which to encode the log topic.
   */
  public void writeTo(final RLPOutput out) {
    out.writeBytesValue(this);
  }
}
