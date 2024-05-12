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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import org.hyperledger.besu.datatypes.RequestType;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.requests.ProhibitedRequestsValidator;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestValidator;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestsValidatorCoordinator;

import java.util.Optional;

public class RequestValidatorProvider {

  public static RequestValidator getDepositRequestValidator(
      final ProtocolSchedule protocolSchedule, final long blockTimestamp, final long blockNumber) {
    return getRequestValidator(protocolSchedule, blockTimestamp, blockNumber, RequestType.DEPOSIT);
  }

  public static RequestValidator getWithdrawalRequestValidator(
      final ProtocolSchedule protocolSchedule, final long blockTimestamp, final long blockNumber) {
    return getRequestValidator(
        protocolSchedule, blockTimestamp, blockNumber, RequestType.WITHDRAWAL);
  }

  private static RequestValidator getRequestValidator(
      final ProtocolSchedule protocolSchedule,
      final long blockTimestamp,
      final long blockNumber,
      final RequestType requestType) {

    RequestsValidatorCoordinator requestsValidatorCoordinator =
        getRequestValidatorCoordinator(protocolSchedule, blockTimestamp, blockNumber);
    return requestsValidatorCoordinator
        .getRequestValidator(requestType)
        .orElse(new ProhibitedRequestsValidator());
  }

  private static RequestsValidatorCoordinator getRequestValidatorCoordinator(
      final ProtocolSchedule protocolSchedule, final long blockTimestamp, final long blockNumber) {

    final BlockHeader blockHeader =
        BlockHeaderBuilder.createDefault()
            .timestamp(blockTimestamp)
            .number(blockNumber)
            .buildBlockHeader();
    return getRequestValidatorCoordinator(protocolSchedule.getByBlockHeader(blockHeader));
  }

  private static RequestsValidatorCoordinator getRequestValidatorCoordinator(
      final ProtocolSpec protocolSchedule) {
    return Optional.ofNullable(protocolSchedule)
        .map(ProtocolSpec::getRequestsValidatorCoordinator)
        .orElseGet(() -> new RequestsValidatorCoordinator.Builder().build());
  }
}
