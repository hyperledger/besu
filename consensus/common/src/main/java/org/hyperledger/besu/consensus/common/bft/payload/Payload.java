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
package org.hyperledger.besu.consensus.common.bft.payload;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import org.apache.tuweni.bytes.Bytes;

/** The interface Payload. */
public interface Payload extends RoundSpecific {

  /**
   * Write to.
   *
   * @param rlpOutput the rlp output
   */
  void writeTo(final RLPOutput rlpOutput);

  /**
   * Encoded.
   *
   * @return the bytes
   */
  default Bytes encoded() {
    BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    writeTo(rlpOutput);

    return rlpOutput.encoded();
  }

  /**
   * Gets message type.
   *
   * @return the message type
   */
  int getMessageType();

  /**
   * Read digest.
   *
   * @param messageData the message data
   * @return the hash
   */
  static Hash readDigest(final RLPInput messageData) {
    return Hash.wrap(messageData.readBytes32());
  }

  /**
   * Hash for signature.
   *
   * @return the hash
   */
  Hash hashForSignature();
}
