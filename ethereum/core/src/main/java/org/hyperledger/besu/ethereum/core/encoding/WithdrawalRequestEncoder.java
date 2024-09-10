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
package org.hyperledger.besu.ethereum.core.encoding;

import org.hyperledger.besu.datatypes.RequestType;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.WithdrawalRequest;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import org.apache.tuweni.bytes.Bytes;

public class WithdrawalRequestEncoder {

  /**
   * Encodes a Request into RLP format if it is a WithdrawalRequest.
   *
   * @param request The Request to encode, which must be a WithdrawalRequest.
   * @param rlpOutput The RLPOutput to write the encoded data to.
   * @throws IllegalArgumentException if the provided request is not a WithdrawalRequest.
   */
  public static void encode(final Request request, final RLPOutput rlpOutput) {
    if (!request.getType().equals(RequestType.WITHDRAWAL)) {
      throw new IllegalArgumentException("The provided request is not of type WithdrawalRequest.");
    }
    encodeWithdrawalRequest((WithdrawalRequest) request, rlpOutput);
  }

  /**
   * Encodes the details of a WithdrawalRequest into RLP format.
   *
   * @param withdrawalRequest The WithdrawalRequest to encode.
   * @param rlpOutput The RLPOutput to write the encoded data to.
   */
  private static void encodeWithdrawalRequest(
      final WithdrawalRequest withdrawalRequest, final RLPOutput rlpOutput) {
    rlpOutput.startList();
    rlpOutput.writeBytes(withdrawalRequest.getSourceAddress());
    rlpOutput.writeBytes(withdrawalRequest.getValidatorPubkey());
    rlpOutput.writeUInt64Scalar(withdrawalRequest.getAmount());
    rlpOutput.endList();
  }

  public static Bytes encodeOpaqueBytes(final Request withdrawalRequest) {
    return RLP.encode(rlpOutput -> encode(withdrawalRequest, rlpOutput));
  }
}
