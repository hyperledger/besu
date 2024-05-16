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

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.datatypes.RequestType;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import com.google.common.collect.ImmutableMap;
import org.apache.tuweni.bytes.Bytes;

/** Encodes Request objects into RLP format. */
public class RequestEncoder {

  @FunctionalInterface
  interface Encoder {
    void encode(Request request, RLPOutput output);
  }

  private static final ImmutableMap<RequestType, RequestEncoder.Encoder> ENCODERS =
      ImmutableMap.of(
          RequestType.WITHDRAWAL,
          WithdrawalRequestEncoder::encode,
          RequestType.DEPOSIT,
          DepositEncoder::encode);

  /**
   * Encodes a Request into the provided RLPOutput.
   *
   * @param request The Request to encode.
   * @param rlpOutput The RLPOutput to write the encoded data to.
   */
  public static void encode(final Request request, final RLPOutput rlpOutput) {
    final RequestEncoder.Encoder encoder = getEncoder(request.getType());
    Bytes requestBytes = RLP.encode(out -> encoder.encode(request, out));
    rlpOutput.writeBytes(
        Bytes.concatenate(Bytes.of(request.getType().getSerializedType()), requestBytes));
  }

  /**
   * Encodes a Request into a Bytes object representing the RLP-encoded data.
   *
   * @param request The Request to encode.
   * @return The RLP-encoded data as a Bytes object.
   */
  public static Bytes encodeOpaqueBytes(final Request request) {
    final RequestEncoder.Encoder encoder = getEncoder(request.getType());
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeByte(request.getType().getSerializedType());
    encoder.encode(request, out);
    return out.encoded();
  }

  /**
   * Retrieves the encoder for the specified RequestType.
   *
   * @param requestType The type of the request.
   * @return The encoder for the specified type.
   * @throws NullPointerException if no encoder is found for the specified type.
   */
  private static RequestEncoder.Encoder getEncoder(final RequestType requestType) {
    return checkNotNull(
        ENCODERS.get(requestType), "Encoder not found for request type: %s", requestType);
  }
}
