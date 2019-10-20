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
package org.hyperledger.besu.ethereum.p2p.rlpx.wire;

public interface SubProtocol {

  /**
   * Returns the 3 character ascii name of this Wire Sub-protocol.
   *
   * @return the name of this sub-protocol
   */
  String getName();

  /**
   * The number of message codes to reserve for the given version of this sub-protocol.
   *
   * @param protocolVersion the version of the protocol
   * @return the number of reserved message codes in the given version of the sub-protocol
   */
  int messageSpace(int protocolVersion);

  /**
   * Returns true if the given protocol version supports the given message code.
   *
   * @param protocolVersion the version of the protocol
   * @param code the message code to check
   * @return true if the given protocol version supports the given message code
   */
  boolean isValidMessageCode(int protocolVersion, int code);

  /** Message name for a message code not valid within this subprotocol. */
  String INVALID_MESSAGE_NAME = "invalid";

  /**
   * Returns the name of the particular message for this protocol, suitable for human viewing.
   *
   * @param protocolVersion The version of the protocol for the message code.
   * @param code The message code to be named.
   * @return A string of the human readable name of the message, or {@link #INVALID_MESSAGE_NAME} if
   *     it is not a valid in the protocol.
   */
  String messageName(int protocolVersion, int code);
}
