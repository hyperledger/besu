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
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Optional;

import com.google.common.collect.ImmutableMap;
import org.apache.tuweni.bytes.Bytes;

/**
 * Decodes a request from its RLP encoded form.
 *
 * <p>This class provides functionality to decode requests based on their type.
 */
public class RequestDecoder {

  @FunctionalInterface
  interface Decoder {
    Request decode(RLPInput input);
  }

  private static final ImmutableMap<RequestType, Decoder> DECODERS =
      ImmutableMap.of(
          RequestType.WITHDRAWAL,
          WithdrawalRequestDecoder::decode,
          RequestType.DEPOSIT,
          DepositRequestDecoder::decode,
          RequestType.CONSOLIDATION,
          ConsolidationRequestDecoder::decode);

  /**
   * Decodes a request from its RLP encoded bytes.
   *
   * <p>This method first determines the type of the request and then decodes the request data
   * according to the request type.
   *
   * @param rlpInput The RLP encoded request.
   * @return The decoded Request object.
   * @throws IllegalArgumentException if the request type is unsupported or invalid.
   */
  public static Request decode(final RLPInput rlpInput) {
    final Bytes requestBytes = rlpInput.readBytes();
    return getRequestType(requestBytes)
        .map(type -> decodeRequest(requestBytes, type))
        .orElseThrow(() -> new IllegalArgumentException("Unsupported or invalid request type"));
  }

  /**
   * Decodes the request data according to the request type.
   *
   * @param requestBytes The bytes representing the request, including the request type byte.
   * @param requestType The type of the request to decode.
   * @return The decoded Request.
   * @throws IllegalStateException if no decoder is found for the specified request type.
   */
  private static Request decodeRequest(final Bytes requestBytes, final RequestType requestType) {
    // Skip the first byte which is the request type
    RLPInput requestInput = RLP.input(requestBytes.slice(1));
    Decoder decoder =
        Optional.ofNullable(DECODERS.get(requestType))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Decoder not found for request type: " + requestType));
    return decoder.decode(requestInput);
  }

  /**
   * Extracts the request type from the given bytes.
   *
   * @param bytes The bytes from which to extract the request type.
   * @return An Optional containing the RequestType if it could be determined, or an empty Optional
   *     otherwise.
   */
  private static Optional<RequestType> getRequestType(final Bytes bytes) {
    try {
      byte typeByte = bytes.get(0);
      return Optional.of(RequestType.of(typeByte));
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
  }

  public static Request decodeOpaqueBytes(final Bytes input) {

    RequestType type = getRequestType(input).orElseThrow();
    return decodeRequest(input.slice(1), type);
  }
}
