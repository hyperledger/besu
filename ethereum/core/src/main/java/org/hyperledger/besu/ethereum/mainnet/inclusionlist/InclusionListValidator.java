/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.mainnet.inclusionlist;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.BlockGasAccountingStrategy;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strict inclusion list validator per EIP-7805. Returns INCLUSION_LIST_UNSATISFIED only if a
 * transaction could have been included but wasn't.
 *
 * <p>A transaction that does not fit in the block is skipped. With the two-dimensional block gas
 * accounting of EIP-8037 there is no single gas_left to compare the transaction gas limit to, so
 * this uses the same worst case check that block processing applies before executing a transaction,
 * see {@link BlockGasAccountingStrategy#hasBlockCapacity}.
 *
 * <p>As specified by EIP-7805, a transaction that fits is validated against the post-execution
 * state by checking the nonce and balance of its sender, without executing it. Executing it would
 * not find more invalid transactions, since transaction processing runs the same validations, and
 * would make every block import pay for the execution of the inclusion list transactions, and
 * record their state and ancestor block accesses as if the block had made them.
 */
public class InclusionListValidator {

  private static final Logger LOG = LoggerFactory.getLogger(InclusionListValidator.class);

  public static InclusionListValidationResult validate(
      final ProtocolSpec protocolSpec,
      final WorldUpdater worldUpdater,
      final BlockHeader newBlockHeader,
      final long cumulativeExecutionGasUsed,
      final long cumulativeStateGasUsed,
      final Set<Transaction> inclusionListTransactions) {

    LOG.atInfo()
        .setMessage("IL validation: {} IL txs")
        .addArgument(inclusionListTransactions.size())
        .log();

    final TransactionValidator transactionValidator =
        protocolSpec.getTransactionValidatorFactory().get();
    final BlockGasAccountingStrategy blockGasAccountingStrategy =
        protocolSpec.getBlockGasAccountingStrategy();
    final long transactionExecutionGasLimit =
        protocolSpec.getGasCalculator().stateGasCostCalculator().transactionExecutionGasLimit();

    // Apply the 3-step conditional algorithm for each IL transaction
    for (final Transaction tx : inclusionListTransactions) {
      LOG.atInfo().setMessage("Checking IL tx not in block {}").addArgument(tx::toTraceLog).log();

      // Skip blob txs
      if (tx.getType().supportsBlob()) {
        LOG.info("Skipping blob tx {}", tx.toTraceLog());
        continue;
      }

      // the block has no room left for this tx
      if (!blockGasAccountingStrategy.hasBlockCapacity(
          tx.getGasLimit(),
          transactionExecutionGasLimit,
          cumulativeExecutionGasUsed,
          cumulativeStateGasUsed,
          newBlockHeader.getGasLimit())) {
        LOG.atDebug()
            .setMessage(
                "Skipping IL tx {}, the block has no room left for it (execution={}, state={}, limit={})")
            .addArgument(tx::toTraceLog)
            .addArgument(cumulativeExecutionGasUsed)
            .addArgument(cumulativeStateGasUsed)
            .addArgument(newBlockHeader::getGasLimit)
            .log();
        continue;
      }

      final ValidationResult<TransactionInvalidReason> baseValidationResult =
          transactionValidator.validate(
              tx,
              newBlockHeader.getBaseFee(),
              Optional.of(Wei.ZERO),
              TransactionValidationParams.processingBlock());
      if (!baseValidationResult.isValid()) {
        LOG.info("Tx {} not valid reason {}", tx.toTraceLog(), baseValidationResult);
        continue;
      }
      final Address sender = tx.getSender();
      final ValidationResult<TransactionInvalidReason> senderValidationResult =
          transactionValidator.validateForSender(
              tx, worldUpdater.get(sender), TransactionValidationParams.processingBlock());
      if (!senderValidationResult.isValid()) {
        LOG.info("Tx {} not valid reason {}", tx.toTraceLog(), senderValidationResult);
        continue;
      }

      // Transaction passed all the checks — it could have been included but wasn't
      LOG.warn(
          "IL unsatisfied: transaction (sender={}, nonce={}) could have been included but wasn't",
          sender,
          tx.getNonce());
      return InclusionListValidationResult.unsatisfied(
          "Inclusion list not satisfied: transaction "
              + " (sender="
              + sender
              + ", nonce="
              + tx.getNonce()
              + ") could have been included but wasn't");
    }

    return InclusionListValidationResult.valid();
  }
}
