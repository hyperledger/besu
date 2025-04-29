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

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.Address;

import java.util.NoSuchElementException;

public class RequestContractAddresses {
  private final Address withdrawalRequestContractAddress;
  private final Address depositContractAddress;
  private final Address consolidationRequestContractAddress;

  private RequestContractAddresses(
      final Address withdrawalRequestContractAddress,
      final Address depositContractAddress,
      final Address consolidationRequestContractAddress) {
    this.withdrawalRequestContractAddress = withdrawalRequestContractAddress;
    this.depositContractAddress = depositContractAddress;
    this.consolidationRequestContractAddress = consolidationRequestContractAddress;
  }

  public static RequestContractAddresses fromGenesis(
      final GenesisConfigOptions genesisConfigOptions) {
    return new RequestContractAddresses(
        genesisConfigOptions
            .getWithdrawalRequestContractAddress()
            .orElseThrow(
                () -> new NoSuchElementException("Withdrawal Request Contract Address not found")),
        genesisConfigOptions
            .getDepositContractAddress()
            .orElseThrow(() -> new NoSuchElementException("Deposit Contract Address not found")),
        genesisConfigOptions
            .getConsolidationRequestContractAddress()
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "Consolidation Request Contract Address not found")));
  }

  public Address getWithdrawalRequestContractAddress() {
    return withdrawalRequestContractAddress;
  }

  public Address getDepositContractAddress() {
    return depositContractAddress;
  }

  public Address getConsolidationRequestContractAddress() {
    return consolidationRequestContractAddress;
  }
}
