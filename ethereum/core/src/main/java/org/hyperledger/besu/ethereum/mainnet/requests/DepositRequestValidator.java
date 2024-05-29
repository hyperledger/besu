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

import static org.hyperledger.besu.ethereum.mainnet.requests.RequestUtil.getDepositRequests;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.DepositRequest;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.DepositRequestDecoder;
import org.hyperledger.besu.evm.log.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DepositRequestValidator implements RequestValidator {

  private static final Logger LOG = LoggerFactory.getLogger(DepositRequestValidator.class);
  private final Address depositContractAddress;

  public DepositRequestValidator(final Address depositContractAddress) {
    this.depositContractAddress = depositContractAddress;
  }

  @Override
  public boolean validateParameter(final Optional<List<Request>> depositRequests) {
    return depositRequests.isPresent();
  }

  public boolean validateDepositRequests(
      final Block block,
      final List<DepositRequest> actualDepositRequests,
      final List<TransactionReceipt> receipts) {

    List<DepositRequest> expectedDepositRequests = new ArrayList<>();

    for (TransactionReceipt receipt : receipts) {
      for (Log log : receipt.getLogsList()) {
        if (depositContractAddress.equals(log.getLogger())) {
          DepositRequest depositRequest = DepositRequestDecoder.decodeFromLog(log);
          expectedDepositRequests.add(depositRequest);
        }
      }
    }

    boolean isValid = actualDepositRequests.equals(expectedDepositRequests);

    if (!isValid) {
      LOG.warn(
          "Deposits validation failed. Deposits from block body do not match deposits from logs. Block hash: {}",
          block.getHash());
      LOG.debug(
          "Deposits from logs: {}, deposits from block body: {}",
          expectedDepositRequests,
          actualDepositRequests);
    }

    return isValid;
  }

  @Override
  public boolean validate(
      final Block block, final List<Request> requests, final List<TransactionReceipt> receipts) {
    var depositRequests = getDepositRequests(Optional.of(requests)).orElse(Collections.emptyList());
    return validateDepositRequests(block, depositRequests, receipts);
  }
}
