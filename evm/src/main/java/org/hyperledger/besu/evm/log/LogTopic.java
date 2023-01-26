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
package org.hyperledger.besu.evm.log;

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.DelegatingBytes32;

/** The Log topic. */
public class LogTopic extends DelegatingBytes32 {

  /**
   * Instantiates a new Log topic.
   *
   * @param bytes the bytes
   */
  protected LogTopic(final Bytes bytes) {
    super(bytes);
  }

  /**
   * Create log topic.
   *
   * @param bytes the bytes
   * @return the log topic
   */
  public static LogTopic create(final Bytes bytes) {
    return new LogTopic(bytes);
  }

  /**
   * Wrap log topic.
   *
   * @param bytes the bytes
   * @return the log topic
   */
  public static LogTopic wrap(final Bytes bytes) {
    return new LogTopic(bytes);
  }

  /**
   * Instantiate Log Topic from copy of bytes.
   *
   * @param bytes the bytes
   * @return the log topic
   */
  public static LogTopic of(final Bytes bytes) {
    return new LogTopic(bytes.copy());
  }

  /**
   * Instantiate Log Topic from hex string
   *
   * @param str the str
   * @return the log topic
   */
  public static LogTopic fromHexString(final String str) {
    return str == null ? null : LogTopic.create(Bytes.fromHexString(str));
  }

  /**
   * Reads the log topic from the provided RLP input.
   *
   * @param in the input from which to decode the log topic.
   * @return the read log topic.
   */
  public static LogTopic readFrom(final RLPInput in) {
    return new LogTopic(in.readBytes());
  }

  /**
   * Writes the log topic to the provided RLP output.
   *
   * @param out the output in which to encode the log topic.
   */
  public void writeTo(final RLPOutput out) {
    out.writeBytes(this);
  }
}
