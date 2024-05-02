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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.WithdrawalParameter;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.encoding.EncodingContext;
import org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.tuweni.bytes.Bytes;

@JsonPropertyOrder({"payloadBodies"})
public class EngineGetPayloadBodiesResultV1 {

  private final List<PayloadBody> payloadBodies;

  public EngineGetPayloadBodiesResultV1() {
    this.payloadBodies = Collections.<PayloadBody>emptyList();
  }

  public EngineGetPayloadBodiesResultV1(final List<PayloadBody> payloadBody) {
    this.payloadBodies = payloadBody;
  }

  @JsonValue
  public List<PayloadBody> getPayloadBodies() {
    return payloadBodies;
  }

  public static class PayloadBody {
    private final List<String> transactions;
    private final List<WithdrawalParameter> withdrawals;

    public PayloadBody(final BlockBody blockBody) {
      this.transactions =
          blockBody.getTransactions().stream()
              .map(
                  transaction ->
                      TransactionEncoder.encodeOpaqueBytes(transaction, EncodingContext.BLOCK_BODY))
              .map(Bytes::toHexString)
              .collect(Collectors.toList());
      this.withdrawals =
          blockBody
              .getWithdrawals()
              .map(
                  ws ->
                      ws.stream()
                          .map(WithdrawalParameter::fromWithdrawal)
                          .collect(Collectors.toList()))
              .orElse(null);
    }

    @JsonGetter(value = "transactions")
    public List<String> getTransactions() {
      return transactions;
    }

    @JsonGetter(value = "withdrawals")
    public List<WithdrawalParameter> getWithdrawals() {
      return withdrawals;
    }
  }
}
