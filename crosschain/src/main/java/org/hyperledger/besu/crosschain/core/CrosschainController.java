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
package org.hyperledger.besu.crosschain.core;

import org.hyperledger.besu.crosschain.core.keys.BlsThresholdCryptoSystem;
import org.hyperledger.besu.crosschain.core.keys.BlsThresholdPublicKey;
import org.hyperledger.besu.crosschain.core.keys.CoordinationContractInformation;
import org.hyperledger.besu.crosschain.core.keys.CrosschainKeyManager;
import org.hyperledger.besu.crosschain.core.keys.KeyStatus;
import org.hyperledger.besu.crosschain.core.keys.generation.KeyGenFailureToCompleteReason;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcRequestException;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.CrosschainTransaction;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.WorldState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO: This class needs to be moved to its own module, and it needs to use the Vertx, rather than
// blocking,
// TODO and use the main Vertx instance.

/**
 * This class is the entry point for ALL JSON RPC calls into the crosschain core code. It is the
 * class the is initialise when the Ethereum Client starts up, and holds references to all of the
 * parts of the crosschain core code.
 */
public class CrosschainController {
  protected static final Logger LOG = LogManager.getLogger();

  TransactionPool transactionPool;
  Blockchain blockchain;
  WorldStateArchive worldStateArchive;

  CrosschainProcessor processor;
  CrosschainKeyManager crosschainKeyManager;

  MultichainManager multichainManager;

  public CrosschainController() {
    this.multichainManager = new MultichainManager();
    this.processor = new CrosschainProcessor(this.multichainManager);
    this.crosschainKeyManager = CrosschainKeyManager.getCrosschainKeyManager();
  }

  public void init(
      final TransactionSimulator transactionSimulator,
      final TransactionPool transactionPool,
      final BigInteger sidechainId,
      final SECP256K1.KeyPair nodeKeys,
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive) {
    this.processor.init(
        transactionSimulator,
        transactionPool,
        sidechainId,
        nodeKeys,
        blockchain,
        worldStateArchive);
    this.crosschainKeyManager.init(sidechainId, nodeKeys);
    this.transactionPool = transactionPool;
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
  }

  /**
   * Execute a subordinate transaction.
   *
   * @param transaction Subordinate Transaction to execute.
   * @return Validaiton result.
   */
  /**
   * Execute a subordinate transaction.
   *
   * @param transaction Subordinate Transaction to execute.
   * @return Validaiton result.
   */
  public ValidationResult<TransactionValidator.TransactionInvalidReason> addLocalTransaction(
      final CrosschainTransaction transaction) {
    // Get Subordinate View results.
    if (this.processor.processSubordinates(transaction, false)) {
      return ValidationResult.invalid(
          TransactionValidator.TransactionInvalidReason.CROSSCHAIN_FAILED_SUBORDINATE_VIEW);
    }

    Optional<ValidationResult<TransactionValidator.TransactionInvalidReason>> executionError =
        this.processor.trialExecution(transaction);
    if (executionError.isPresent()) {
      return executionError.get();
    }

    // Dispatch Subordinate Transactions if the trial execution worked OK.
    if (this.processor.processSubordinates(transaction, true)) {
      return ValidationResult.invalid(
          TransactionValidator.TransactionInvalidReason.CROSSCHAIN_FAILED_SUBORDINATE_TRANSACTION);
    }

    // TODO there is a synchronized inside this call. This should be surrounded by a Vertx
    // blockingExecutor, maybe
    ValidationResult<TransactionValidator.TransactionInvalidReason> validationResult =
        this.transactionPool.addLocalTransaction(transaction);

    if (transaction.getType().isLockableTransaction()) {
      validationResult.ifValid(
          () -> {
            this.processor.startCrosschainTransactionCommitIgnoreTimeOut(transaction);
          });
    }
    return validationResult;
  }

  /**
   * Execute a subordinate view.
   *
   * @param subordinateView The subordinate view to process.
   * @param blockNumber Execute view at this block number.
   * @return Result or an error.
   */
  public Object getSignedSubordinateViewResult(
      final CrosschainTransaction subordinateView, final long blockNumber) {
    // Get Subordinate View results.
    if (this.processor.processSubordinates(subordinateView, false)) {
      return TransactionValidator.TransactionInvalidReason.CROSSCHAIN_FAILED_SUBORDINATE_VIEW;
    }

    Object resultObj = this.processor.executeSubordinateView(subordinateView, blockNumber);
    TransactionProcessor.Result txResult;
    if (resultObj instanceof TransactionSimulatorResult) {
      TransactionSimulatorResult resultTxSim = (TransactionSimulatorResult) resultObj;
      BytesValue resultBytesValue = resultTxSim.getOutput();
      LOG.info("Transaction Simulator Result: " + resultBytesValue.toString());

      BytesValue signedResult = resultBytesValue;
      // TODO RESULT IS NOT SIGNED
      //      BytesValue signedResult =
      //          this.subordinateViewCoordinator.getSignedResult(
      //              subordinateView, blockNumber, resultBytesValue);

      // Replace the output with the output and signature in the result object.
      txResult =
          MainnetTransactionProcessor.Result.successful(
              resultTxSim.getResult().getLogs(),
              resultTxSim.getResult().getGasRemaining(),
              signedResult,
              resultTxSim.getValidationResult());
      return new TransactionSimulatorResult(subordinateView, txResult);
    } else {
      // An error occurred - propagate the error.
      LOG.info("Transaction Simulator returned an error");
      return resultObj;
    }
  }

  /**
   * Called by the JSON RPC method: CrossCheckUnlock.
   *
   * <p>If a contract is lockable and locked, then check with the Crosschain Coordination Contract
   * which is coordinating the Crosschain Transaction to see if the transaction has completed and if
   * the contract can be unlocked.
   *
   * @param address Address of contract to check.
   */
  public void checkUnlock(final Address address) {

    // TODO For the moment just unlock the contract.

    // Is the contract lockable and is it locked?
    Hash latestBlockStateRootHash = this.blockchain.getChainHeadBlock().getHeader().getStateRoot();
    final Optional<WorldState> maybeWorldState = worldStateArchive.get(latestBlockStateRootHash);
    if (maybeWorldState.isEmpty()) {
      LOG.error("Crosschain Signalling Transaction: Can't fetch world state");
      return;
    }
    WorldState worldState = maybeWorldState.get();
    final Account contract = worldState.get(address);

    if (!contract.isLockable()) {
      throw new InvalidJsonRpcRequestException("Contract is not lockable");
    }

    if (contract.isLocked()) {
      // TODO here we need to check the Crosschain Coordination Contract.

      this.processor.sendSignallingTransaction(address);
    }
  }

  /**
   * Called by the JSON RPC method: cross_startThresholdKeyGeneration.
   *
   * @param threshold The threshold number of validators that will be needed to sign messages.
   * @param algorithm The ECC curve and message digest function to be used.
   * @return The key version number.
   */
  public long startThresholdKeyGeneration(
      final int threshold, final BlsThresholdCryptoSystem algorithm) {
    return this.crosschainKeyManager.generateNewKeys(threshold, algorithm);
  }

  /**
   * Called by the JSON RPC method: cross_getKeyStatus.
   *
   * @param keyVersion version of key to fetch information about.
   * @return Indicates the status of the key.
   */
  public KeyStatus getKeyStatus(final long keyVersion) {
    return this.crosschainKeyManager.getKeyStatus(keyVersion);
  }

  /**
   * Called by the JSON RPC Call cross_getKeyGenNodesDroppedOutOfKeyGeneration
   *
   * @param keyVersion version of key to fetch information about.
   * @return The current public key and meta-data.
   */
  public Map<BigInteger, KeyGenFailureToCompleteReason> getKeyGenNodesDroppedOutOfKeyGeneration(
      final long keyVersion) {
    return this.crosschainKeyManager.getKeyGenNodesDroppedOutOfKeyGeneration(keyVersion);
  }

  /**
   * Called by the JSON RPC Call cross_getKeyGenFailureReason. Returns the top level reason why the
   * key generation failed, if it did. If a key generation didn't fail, then the indicate is
   * success.
   *
   * @param keyVersion version of key to fetch information about.
   * @return key generation failure status.
   */
  public KeyGenFailureToCompleteReason getKeyGenFailureReason(final long keyVersion) {
    return this.crosschainKeyManager.getKeyGenFailureReason(keyVersion);
  }

  /**
   * Called by the JSON RPC call: cross_getKeyGenActiveNodes. Returns the list of nodes that hold
   * secret shares and who can participate in threshold signing. During a key generation, this will
   * be the set of nodes still active in the key generation process.
   *
   * @param keyVersion version of key to fetch information about.
   * @return nodes active in a threshold key.
   */
  public Set<BigInteger> getKeyGenActiveNodes(final long keyVersion) {
    return this.crosschainKeyManager.getKeyGenActiveNodes(keyVersion);
  }

  /**
   * Called by the JSON RPC call: cross_activateKeyVersion.
   *
   * @param keyVersion Key version to activate.
   */
  public void activateKey(final long keyVersion) {
    this.crosschainKeyManager.activateKey(keyVersion);
  }

  /**
   * Called by JSON RPC call: cross_getActiveKeyVersion
   *
   * @return The key that is currently active. -1 is returned if no key is active.
   */
  public long getActiveKeyVersion() {
    return this.crosschainKeyManager.getActiveKeyVersion();
  }

  /**
   * Called by the JSON RPC Call cross_getBlockchainPublicKey
   *
   * @return The current public key and meta-data.
   */
  public BlsThresholdPublicKey getBlockchainPublicKey() {
    return this.crosschainKeyManager.getActivePublicKey();
  }

  /**
   * Called by the JSON RPC Call cross_getBlockchainPublicKeyByVersion
   *
   * @param keyVersion to fetch key for.
   * @return The current public key and meta-data.
   */
  public BlsThresholdPublicKey getBlockchainPublicKey(final long keyVersion) {
    return this.crosschainKeyManager.getPublicKey(keyVersion);
  }

  public void setKeyGenerationContractAddress(final Address address) {
    this.crosschainKeyManager.setKeyGenerationContractAddress(address);
  }

  public void addCoordinationContract(
      final BigInteger blockchainId, final Address address, final String ipAddressAndPort) {
    this.crosschainKeyManager.addCoordinationContract(blockchainId, address, ipAddressAndPort);
  }

  public void removeCoordinationContract(final BigInteger blockchainId, final Address address) {
    this.crosschainKeyManager.removeCoordinationContract(blockchainId, address);
  }

  public Collection<CoordinationContractInformation> listCoordinationContracts() {
    return this.crosschainKeyManager.getAllCoordinationContracts();
  }

  public void addMultichainNode(final BigInteger blockchainId, final String ipAddressAndPort) {
    this.multichainManager.addNode(blockchainId, ipAddressAndPort);
  }

  public void removeMultichainNode(final BigInteger blockchainId) {
    this.multichainManager.removeNode(blockchainId);
  }

  public Set<BigInteger> listMultichainNodes() {
    return this.multichainManager.listAllNodes();
  }
}
