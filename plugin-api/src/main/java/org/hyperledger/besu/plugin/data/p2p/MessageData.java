/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.plugin.data.p2p;

import java.math.BigInteger;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;

/** A P2P Network Message's Data for plugin use. */
public interface MessageData {
  /**
   * Returns the size of the message.
   *
   * @return Number of bytes in this data.
   */
  int getSize();

  /**
   * Returns the message's code.
   *
   * @return Message Code
   */
  int getCode();

  /**
   * Get the serialized representation for this message
   *
   * @return the serialized representation of this message
   */
  Bytes getData();

  /**
   * Wrap message data with a request ID.
   *
   * @param requestId the request ID to wrap with
   * @return wrapped message data
   */
  MessageData wrapMessageData(final BigInteger requestId);

  /**
   * Unwrap message data to extract request ID and inner message.
   *
   * @return map entry containing request ID and unwrapped message data
   */
  Map.Entry<BigInteger, MessageData> unwrapMessageData();

  /**
   * Subclasses can implement this method to return a human-readable version of the raw data.
   *
   * @return return a human-readable version of the raw data
   */
  default String toStringDecoded() {
    return "N/A";
  }
}
