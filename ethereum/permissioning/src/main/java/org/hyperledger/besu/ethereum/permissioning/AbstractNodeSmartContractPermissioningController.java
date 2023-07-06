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
package org.hyperledger.besu.ethereum.permissioning;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.permissioning.NodeConnectionPermissioningProvider;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public abstract class AbstractNodeSmartContractPermissioningController
    implements NodeConnectionPermissioningProvider {

  protected final Address contractAddress;
  protected final TransactionSimulator transactionSimulator;

  private final Counter checkCounter;
  private final Counter checkCounterPermitted;
  private final Counter checkCounterUnpermitted;

  /**
   * Creates a permissioning controller attached to a blockchain
   *
   * @param contractAddress The address at which the permissioning smart contract resides
   * @param transactionSimulator A transaction simulator with attached blockchain and world state
   * @param metricsSystem The metrics provider that is to be reported to
   */
  protected AbstractNodeSmartContractPermissioningController(
      final Address contractAddress,
      final TransactionSimulator transactionSimulator,
      final MetricsSystem metricsSystem) {
    this.contractAddress = contractAddress;
    this.transactionSimulator = transactionSimulator;

    this.checkCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "node_smart_contract_check_count",
            "Number of times the node smart contract permissioning provider has been checked");
    this.checkCounterPermitted =
        metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "node_smart_contract_check_count_permitted",
            "Number of times the node smart contract permissioning provider has been checked and returned permitted");
    this.checkCounterUnpermitted =
        metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "node_smart_contract_check_count_unpermitted",
            "Number of times the node smart contract permissioning provider has been checked and returned unpermitted");
  }

  /**
   * Check whether a given connection from the source to destination enode should be permitted
   *
   * @param sourceEnode The enode url of the node initiating the connection
   * @param destinationEnode The enode url of the node receiving the connection
   * @return boolean of whether to permit the connection
   */
  @Override
  public boolean isConnectionPermitted(
      final EnodeURL sourceEnode, final EnodeURL destinationEnode) {
    this.checkCounter.inc();

    if (!isContractDeployed()) {
      throw new IllegalStateException("Permissioning contract does not exist");
    }

    if (checkSmartContractRules(sourceEnode, destinationEnode)) {
      this.checkCounterPermitted.inc();
      return true;
    } else {
      this.checkCounterUnpermitted.inc();
      return false;
    }
  }

  private boolean isContractDeployed() {
    final Optional<Boolean> contractExists =
        transactionSimulator.doesAddressExistAtHead(contractAddress);

    return contractExists.isPresent() && contractExists.get();
  }

  abstract boolean checkSmartContractRules(
      final EnodeURL sourceEnode, final EnodeURL destinationEnode);

  protected CallParameter buildCallParameters(final Bytes payload) {
    // Call parameters for simulation don't need other parameters, only the address and the payload
    return new CallParameter(null, contractAddress, -1, null, null, payload);
  }
}
