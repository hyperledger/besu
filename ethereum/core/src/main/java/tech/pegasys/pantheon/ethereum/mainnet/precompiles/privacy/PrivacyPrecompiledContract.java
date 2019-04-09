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
package tech.pegasys.pantheon.ethereum.mainnet.precompiles.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.pantheon.crypto.Hash.keccak256;

import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.ReceiveRequest;
import tech.pegasys.pantheon.enclave.types.ReceiveResponse;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.mainnet.AbstractPrecompiledContract;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionProcessor;
import tech.pegasys.pantheon.ethereum.privacy.PrivateStateStorage;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransaction;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionProcessor;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionStorage;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.trie.MerklePatriciaTrie;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.ethereum.vm.OperationTracer;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.util.Base64;

import com.google.common.base.Charsets;
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
    try {
      String key = new String(input.extractArray(), UTF_8);
      ReceiveRequest receiveRequest = new ReceiveRequest(key, enclavePublicKey);
      ReceiveResponse receiveResponse = enclave.receive(receiveRequest);

      final BytesValueRLPInput bytesValueRLPInput =
          new BytesValueRLPInput(
              BytesValue.wrap(Base64.getDecoder().decode(receiveResponse.getPayload())), false);

      PrivateTransaction privateTransaction = PrivateTransaction.readFrom(bytesValueRLPInput);

      WorldUpdater publicWorldState = messageFrame.getWorldState();

      BytesValue privacyGroupId =
          BytesValue.wrap(receiveResponse.getPrivacyGroupId().getBytes(Charsets.UTF_8));
      // get the last world state root hash - or create a new one
      Hash lastRootHash =
          privateStateStorage.getPrivateAccountState(privacyGroupId).orElse(EMPTY_ROOT_HASH);
      MutableWorldState disposablePrivateState =
          privateWorldStateArchive.getMutable(lastRootHash).get();

      WorldUpdater privateWorldStateUpdater = disposablePrivateState.updater();

      TransactionProcessor.Result result =
          privateTransactionProcessor.processTransaction(
              messageFrame.getBlockchain(),
              publicWorldState,
              privateWorldStateUpdater,
              messageFrame.getBlockHeader(),
              privateTransaction,
              messageFrame.getMiningBeneficiary(),
              OperationTracer.NO_TRACING,
              messageFrame.getBlockHashLookup(),
              privacyGroupId);

      if (result.isInvalid() || !result.isSuccessful()) {
        throw new Exception("Unable to process the private transaction");
      }

      if (messageFrame.isPersistingState()) {
        privateWorldStateUpdater.commit();
        disposablePrivateState.persist();
        PrivateStateStorage.Updater privateStateUpdater = privateStateStorage.updater();
        privateStateUpdater.putPrivateAccountState(
            privacyGroupId, disposablePrivateState.rootHash());
        privateStateUpdater.commit();

        Bytes32 txHash = keccak256(RLP.encode(privateTransaction::writeTo));
        PrivateTransactionStorage.Updater privateUpdater = privateTransactionStorage.updater();
        privateUpdater.putTransactionLogs(txHash, result.getLogs());
        privateUpdater.putTransactionResult(txHash, result.getOutput());
        privateUpdater.commit();
      }

      return result.getOutput();
    } catch (IOException e) {
      LOG.fatal("Enclave threw an unhandled exception.", e);
      return BytesValue.EMPTY;
    } catch (Exception e) {
      LOG.fatal(e.getMessage());
      return BytesValue.EMPTY;
    }
  }
}
