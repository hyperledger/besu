/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"payloadStatus", "payloadId"})
public class EngineUpdateForkChoiceResult {
  private final EnginePayloadStatusResult payloadStatus;
  private final PayloadIdentifier payloadId;

  public EngineUpdateForkChoiceResult(
      final EnginePayloadStatusResult payloadStatus, final PayloadIdentifier payloadId) {
    this.payloadStatus = payloadStatus;
    this.payloadId = payloadId;
  }

  @JsonGetter(value = "payloadStatus")
  public EnginePayloadStatusResult getPayloadStatus() {
    return payloadStatus;
  }

  @JsonGetter(value = "payloadId")
  @JsonInclude(NON_NULL)
  public String getPayloadId() {
    return Optional.ofNullable(payloadId).map(PayloadIdentifier::toHexString).orElse(null);
  }
}
