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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.DepositRequest;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.DepositRequestDecoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;

public class DepositRequestProcessor implements RequestProcessor {

  public static final Address DEFAULT_DEPOSIT_CONTRACT_ADDRESS =
      Address.fromHexString("0x00000000219ab540356cbb839cbe05303d7705fa");

  private final Optional<Address> depositContractAddress;

  public DepositRequestProcessor(final Address depositContractAddress) {
    this.depositContractAddress = Optional.ofNullable(depositContractAddress);
  }

  @Override
  public Optional<List<? extends Request>> process(final ProcessRequestContext context) {
    if (depositContractAddress.isEmpty()) {
      return Optional.empty();
    }
    List<DepositRequest> depositRequests =
        findDepositRequestsFromReceipts(context.transactionReceipts());
    return Optional.of(depositRequests);
  }

  @VisibleForTesting
  List<DepositRequest> findDepositRequestsFromReceipts(
      final List<TransactionReceipt> transactionReceipts) {
    return depositContractAddress
        .map(
            address ->
                transactionReceipts.stream()
                    .flatMap(receipt -> receipt.getLogsList().stream())
                    .filter(log -> address.equals(log.getLogger()))
                    .map(DepositRequestDecoder::decodeFromLog)
                    .toList())
        .orElse(Collections.emptyList());
  }
}
