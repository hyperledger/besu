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
package org.hyperledger.besu.ethereum.privacy.storage.migration;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.PrivateStorageMigrationTransactionProcessorResult;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivateStorageMigrationTransactionProcessor {

  private final Blockchain blockchain;
  private final ProtocolSchedule<?> protocolSchedule;
  private final WorldStateArchive publicWorldStateArchive;
  private final WorldStateArchive privateWorldStateArchive;
  private final PrivateStateRootResolver privateStateRootResolver;

  public PrivateStorageMigrationTransactionProcessor(
      final Blockchain blockchain,
      final ProtocolSchedule<?> protocolSchedule,
      final WorldStateArchive publicWorldStateArchive,
      final WorldStateArchive privateWorldStateArchive,
      final PrivateStateRootResolver privateStateRootResolver) {
    this.blockchain = blockchain;
    this.protocolSchedule = protocolSchedule;
    this.publicWorldStateArchive = publicWorldStateArchive;
    this.privateWorldStateArchive = privateWorldStateArchive;
    this.privateStateRootResolver = privateStateRootResolver;
  }

  public Optional<PrivateStorageMigrationTransactionProcessorResult> process(
      final String privacyGroupId,
      final PrivateTransaction privateTransaction,
      final BlockHeader header) {
    if (header == null) {
      return Optional.empty();
    }

    final MutableWorldState publicWorldState =
        publicWorldStateArchive.getMutable(header.getStateRoot()).orElse(null);
    if (publicWorldState == null) {
      return Optional.empty();
    }

    // get the last world state root hash or create a new one
    final Bytes32 privacyGroupIdBytes = Bytes32.wrap(Bytes.fromBase64String(privacyGroupId));
    final Hash lastRootHash =
        privateStateRootResolver.resolveLastStateRoot(privacyGroupIdBytes, header.getHash());

    final MutableWorldState disposablePrivateState =
        privateWorldStateArchive
            .getMutable(lastRootHash)
            .orElseThrow(PrivateStorageMigrationException::new);

    final ProtocolSpec<?> protocolSpec = protocolSchedule.getByBlockNumber(header.getNumber());

    final PrivateTransactionProcessor privateTransactionProcessor =
        protocolSpec.getPrivateTransactionProcessor();

    final PrivateTransactionProcessor.Result result =
        privateTransactionProcessor.processTransaction(
            blockchain,
            publicWorldState.updater(),
            disposablePrivateState.updater(),
            header,
            privateTransaction,
            protocolSpec.getMiningBeneficiaryCalculator().calculateBeneficiary(header),
            new DebugOperationTracer(TraceOptions.DEFAULT),
            new BlockHashLookup(header, blockchain),
            privacyGroupIdBytes);

    final PrivateStorageMigrationTransactionProcessorResult txSimulatorResult =
        new PrivateStorageMigrationTransactionProcessorResult(
            result, Optional.of(disposablePrivateState.rootHash()));

    if (result.isSuccessful()) {
      disposablePrivateState.persist();
    }

    return Optional.of(txSimulatorResult);
  }
}
