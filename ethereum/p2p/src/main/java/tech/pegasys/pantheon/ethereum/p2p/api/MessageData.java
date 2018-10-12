package tech.pegasys.pantheon.ethereum.p2p.api;

import io.netty.buffer.ByteBuf;

/** A P2P Network Message's Data. */
public interface MessageData {

  /**
   * Returns the size of the message.
   *
   * @return Number of bytes {@link #writeTo(ByteBuf)} will write to an output buffer.
   */
  int getSize();

  /**
   * Returns the message's code.
   *
   * @return Message Code
   */
  int getCode();

  /**
   * Puts the message's body into the given {@link ByteBuf}.
   *
   * @param output ByteBuf to write the message to
   */
  void writeTo(ByteBuf output);

  /** Releases the memory underlying this message. */
  void release();

  /** Retains (increments its reference count) the memory underlying this message once. */
  void retain();
}
