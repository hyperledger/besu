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

import org.hyperledger.besu.crosschain.core.keys.BlsThresholdPublicKey;
import org.hyperledger.besu.crosschain.core.keys.CrosschainKeyManager;
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
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO: This class needs to be moved to its own module, and it needs to use the Vertx, rather than
// blocking,
// TODO and use the main Vertx instance.
public class CrosschainController {
  protected static final Logger LOG = LogManager.getLogger();

  TransactionPool transactionPool;
  Blockchain blockchain;
  WorldStateArchive worldStateArchive;

  CrosschainProcessor processor;
  CrosschainKeyManager crosschainKeyManager;

  public CrosschainController() {
    this.processor = new CrosschainProcessor();
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
   * Called by the JSON RPC Call cross_getBlockchainPublicKey
   *
   * @return The current public key and meta-data.
   */
  public BlsThresholdPublicKey getBlockchainPublicKey() {
    return this.crosschainKeyManager.getActiveCredentials();
  }

  // TODO: Implement crosschainGetBlockchainPublicKeyGenerationStatus

  /**
   * Called by the JSON RPC method: cross_generateBlockchainKey
   *
   * @param threshold The threshold number of validators that will be needed to sign messages.
   * @return The key version number.
   */
  public long crosschainGenerateBlockchainKey(final int threshold) {
    return this.crosschainKeyManager.generateNewKeys(threshold);
  }
}
