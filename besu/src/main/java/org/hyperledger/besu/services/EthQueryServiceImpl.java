/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.services;

import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.util.NonceProvider;
import org.hyperledger.besu.plugin.data.Address;
import org.hyperledger.besu.plugin.services.query.EthQueryService;

public class EthQueryServiceImpl implements EthQueryService {
  private final NonceProvider nonceProvider;
  private final BlockchainQueries blockchain;

  public EthQueryServiceImpl(
      final BlockchainQueries blockchain, final NonceProvider nonceProvider) {
    this.blockchain = blockchain;
    this.nonceProvider = nonceProvider;
  }

  @Override
  public long getTransactionCount(final Address address) {
    return nonceProvider.getNonce(org.hyperledger.besu.ethereum.core.Address.fromPlugin(address));
  }

  @Override
  public long blockNumber(final Address address) {
    return blockchain.headBlockNumber();
  }
}
