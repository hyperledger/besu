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
