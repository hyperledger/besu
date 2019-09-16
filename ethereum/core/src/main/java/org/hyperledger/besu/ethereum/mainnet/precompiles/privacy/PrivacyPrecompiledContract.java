/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.mainnet.precompiles.privacy;

import static org.hyperledger.besu.crypto.Hash.keccak256;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogSeries;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.AbstractPrecompiledContract;
import org.hyperledger.besu.ethereum.privacy.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionStorage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivacyPrecompiledContract extends AbstractPrecompiledContract {
  private final Enclave enclave;
  private final String enclavePublicKey;
  private final WorldStateArchive privateWorldStateArchive;
  private final PrivateTransactionStorage privateTransactionStorage;
  private final PrivateStateStorage privateStateStorage;
  private PrivateTransactionProcessor privateTransactionProcessor;
  private static final Hash EMPTY_ROOT_HASH = Hash.wrap(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH);

  private static final Logger LOG = LogManager.getLogger();

  public PrivacyPrecompiledContract(
      final GasCalculator gasCalculator, final PrivacyParameters privacyParameters) {
    this(
        gasCalculator,
        privacyParameters.getEnclavePublicKey(),
        new Enclave(privacyParameters.getEnclaveUri()),
        privacyParameters.getPrivateWorldStateArchive(),
        privacyParameters.getPrivateTransactionStorage(),
        privacyParameters.getPrivateStateStorage());
  }

  PrivacyPrecompiledContract(
      final GasCalculator gasCalculator,
      final String publicKey,
      final Enclave enclave,
      final WorldStateArchive worldStateArchive,
      final PrivateTransactionStorage privateTransactionStorage,
      final PrivateStateStorage privateStateStorage) {
    super("Privacy", gasCalculator);
    this.enclave = enclave;
    this.enclavePublicKey = publicKey;
    this.privateWorldStateArchive = worldStateArchive;
    this.privateTransactionStorage = privateTransactionStorage;
    this.privateStateStorage = privateStateStorage;
  }

  public void setPrivateTransactionProcessor(
      final PrivateTransactionProcessor privateTransactionProcessor) {
    this.privateTransactionProcessor = privateTransactionProcessor;
  }

  @Override
  public Gas gasRequirement(final BytesValue input) {
    return Gas.of(40_000L); // Not sure
  }

  @Override
  public BytesValue compute(final BytesValue input, final MessageFrame messageFrame) {
    final String key = BytesValues.asBase64String(input);
    final ReceiveRequest receiveRequest = new ReceiveRequest(key, enclavePublicKey);

    ReceiveResponse receiveResponse;
    try {
      receiveResponse = enclave.receive(receiveRequest);
    } catch (Exception e) {
      LOG.error("Enclave probably does not have private transaction with key {}.", key, e);
      return BytesValue.EMPTY;
    }

    final BytesValueRLPInput bytesValueRLPInput =
        new BytesValueRLPInput(BytesValues.fromBase64(receiveResponse.getPayload()), false);

    final PrivateTransaction privateTransaction = PrivateTransaction.readFrom(bytesValueRLPInput);

    final WorldUpdater publicWorldState = messageFrame.getWorldState();

    final BytesValue privacyGroupId = BytesValues.fromBase64(receiveResponse.getPrivacyGroupId());

    // get the last world state root hash - or create a new one
    final Hash lastRootHash =
        privateStateStorage.getPrivateAccountState(privacyGroupId).orElse(EMPTY_ROOT_HASH);

    final MutableWorldState disposablePrivateState =
        privateWorldStateArchive.getMutable(lastRootHash).get();

    final WorldUpdater privateWorldStateUpdater = disposablePrivateState.updater();
    final PrivateTransactionProcessor.Result result =
        privateTransactionProcessor.processTransaction(
            messageFrame.getBlockchain(),
            publicWorldState,
            privateWorldStateUpdater,
            messageFrame.getBlockHeader(),
            privateTransaction,
            messageFrame.getMiningBeneficiary(),
            new DebugOperationTracer(TraceOptions.DEFAULT),
            messageFrame.getBlockHashLookup(),
            privacyGroupId);

    if (result.isInvalid() || !result.isSuccessful()) {
      LOG.error(
          "Failed to process the private transaction: {}",
          result.getValidationResult().getErrorMessage());
      return BytesValue.EMPTY;
    }

    if (messageFrame.isPersistingState()) {
      LOG.trace(
          "Persisting private state {} for privacyGroup {}",
          disposablePrivateState.rootHash(),
          privacyGroupId);
      privateWorldStateUpdater.commit();
      disposablePrivateState.persist();

      final PrivateStateStorage.Updater privateStateUpdater = privateStateStorage.updater();
      privateStateUpdater.putPrivateAccountState(privacyGroupId, disposablePrivateState.rootHash());
      privateStateUpdater.commit();

      final Bytes32 txHash = keccak256(RLP.encode(privateTransaction::writeTo));
      final PrivateTransactionStorage.Updater privateUpdater = privateTransactionStorage.updater();
      final LogSeries logs = result.getLogs();
      if (!logs.isEmpty()) {
        privateUpdater.putTransactionLogs(txHash, result.getLogs());
      }
      privateUpdater.putTransactionResult(txHash, result.getOutput());
      privateUpdater.commit();
    }

    return result.getOutput();
  }
}
