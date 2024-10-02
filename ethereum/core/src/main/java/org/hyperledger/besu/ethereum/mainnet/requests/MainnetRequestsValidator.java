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
package org.hyperledger.besu.ethereum.mainnet.requests;

import org.hyperledger.besu.datatypes.RequestType;

public class MainnetRequestsValidator {
  public static RequestsValidatorCoordinator pragueRequestsValidator(
      final RequestContractAddresses requestContractAddresses) {
    return new RequestsValidatorCoordinator.Builder()
        .addValidator(RequestType.WITHDRAWAL, new WithdrawalRequestValidator())
        .addValidator(RequestType.CONSOLIDATION, new ConsolidationRequestValidator())
        .addValidator(
            RequestType.DEPOSIT,
            new DepositRequestValidator(requestContractAddresses.getDepositContractAddress()))
        .build();
  }

  public static RequestProcessorCoordinator pragueRequestsProcessors(
      final RequestContractAddresses requestContractAddresses) {
    return new RequestProcessorCoordinator.Builder()
        .addProcessor(
            RequestType.WITHDRAWAL,
            new WithdrawalRequestProcessor(
                requestContractAddresses.getWithdrawalRequestContractAddress()))
        .addProcessor(
            RequestType.CONSOLIDATION,
            new ConsolidationRequestProcessor(
                requestContractAddresses.getConsolidationRequestContractAddress()))
        .addProcessor(
            RequestType.DEPOSIT,
            new DepositRequestProcessor(requestContractAddresses.getDepositContractAddress()))
        .build();
  }
}
