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
package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Optional;

public class PrivateTransactionSimulator {
  private final Blockchain blockchain;
  private final WorldStateArchive publicWorldStateArchive;
  private final WorldStateArchive privateWorldStateArchive;
  private PrivateStateRootResolver privateStateRootResolver;
  private final ProtocolSchedule<?> protocolSchedule;

  public PrivateTransactionSimulator(
      final Blockchain blockchain,
      final WorldStateArchive publicWorldStateArchive,
      final WorldStateArchive privateWorldStateArchive,
      final PrivateStateRootResolver privateStateRootResolver,
      final ProtocolSchedule<?> protocolSchedule) {
    this.blockchain = blockchain;
    this.publicWorldStateArchive = publicWorldStateArchive;
    this.privateWorldStateArchive = privateWorldStateArchive;
    this.privateStateRootResolver = privateStateRootResolver;
    this.protocolSchedule = protocolSchedule;
  }

  public Optional<PrivateTransactionSimulatorResult> process(
      final PrivateTransaction privateTransaction,
      final BytesValue privacyGroupId,
      final BlockHeader header) {
    if (header == null) {
      return Optional.empty();
    }
    final MutableWorldState publicWorldState =
        publicWorldStateArchive.getMutable(header.getStateRoot()).orElse(null);
    final MutableWorldState privateWorldState =
        privateWorldStateArchive
            .getMutable(
                privateStateRootResolver.resolveLastStateRoot(blockchain, header, privacyGroupId))
            .orElse(null);
    if (publicWorldState == null || privateWorldState == null) {
      return Optional.empty();
    }

    final ProtocolSpec<?> protocolSpec = protocolSchedule.getByBlockNumber(header.getNumber());
    final PrivateTransactionProcessor transactionProcessor =
        protocolSchedule.getByBlockNumber(header.getNumber()).getPrivateTransactionProcessor();

    final PrivateTransactionProcessor.Result result =
        transactionProcessor.processTransaction(
            blockchain,
            publicWorldState.updater(),
            privateWorldState.updater(),
            header,
            privateTransaction,
            protocolSpec.getMiningBeneficiaryCalculator().calculateBeneficiary(header),
            OperationTracer.NO_TRACING,
            new BlockHashLookup(header, blockchain),
            privacyGroupId);

    return Optional.of(
        new PrivateTransactionSimulatorResult(
            privateTransaction, result, privateWorldState.rootHash()));
  }
}
