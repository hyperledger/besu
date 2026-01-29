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
package org.hyperledger.besu.datatypes;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** The Log topic. It contains exactly 32 bytes. */
public class LogTopic extends BytesHolder {

  /**
   * Instantiates a new Log topic.
   *
   * @param bytes the bytes
   */
  private LogTopic(final Bytes bytes) {
    super(bytes);
  }

  /**
   * Wrap log topic.
   *
   * @param bytes the bytes
   * @return the log topic
   */
  public static LogTopic wrap(final Bytes bytes) {
    checkArgument(
        bytes.size() == Bytes32.SIZE,
        "A log topic must be %s bytes long, got %s",
        Bytes32.SIZE,
        bytes.size());
    return new LogTopic(bytes);
  }

  /**
   * Instantiate Log Topic from copy of bytes.
   *
   * @param bytes the bytes
   * @return the log topic
   */
  public static LogTopic of(final Bytes bytes) {
    checkArgument(
        bytes.size() == Bytes32.SIZE,
        "A log topic must be %s bytes long, got %s",
        Bytes32.SIZE,
        bytes.size());
    return new LogTopic(bytes.copy());
  }

  /**
   * Instantiate Log Topic from hex string
   *
   * @param str the str
   * @return the log topic
   */
  public static LogTopic fromHexString(final String str) {
    return str == null ? null : new LogTopic(Bytes.fromHexString(str, 32));
  }

  /**
   * Reads the log topic from the provided RLP input.
   *
   * @param in the input from which to decode the log topic.
   * @return the read log topic.
   */
  public static LogTopic readFrom(final RLPInput in) {
    return new LogTopic(in.readBytes32());
  }

  /**
   * Writes the log topic to the provided RLP output.
   *
   * @param out the output in which to encode the log topic.
   */
  public void writeTo(final RLPOutput out) {
    out.writeBytes(getBytes());
  }
}
