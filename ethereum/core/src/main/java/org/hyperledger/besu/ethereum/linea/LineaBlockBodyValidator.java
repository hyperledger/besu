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
package org.hyperledger.besu.ethereum.linea;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.BaseFeeBlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LineaBlockBodyValidator extends BaseFeeBlockBodyValidator
    implements BlockBodyValidator {
  private static final Logger LOG = LoggerFactory.getLogger(LineaBlockBodyValidator.class);

  public LineaBlockBodyValidator(final ProtocolSchedule protocolSchedule) {
    super(protocolSchedule);
  }

  @Override
  public boolean validateBodyLight(
      final ProtocolContext context,
      final Block block,
      final List<TransactionReceipt> receipts,
      final HeaderValidationMode ommerValidationMode) {

    return super.validateBodyLight(context, block, receipts, ommerValidationMode)
        && validateCalldataLimit(block);
  }

  @VisibleForTesting
  boolean validateCalldataLimit(final Block block) {

    final BlockBody body = block.getBody();
    final List<Transaction> transactions = body.getTransactions();
    final CalldataLimits calldataLimits =
        protocolSchedule.getByBlockHeader(block.getHeader()).getCalldataLimits();

    int blockCalldataSize = 0;

    for (Transaction tx : transactions) {
      final int txCalldataSize = tx.getPayload().size();
      if (txCalldataSize > calldataLimits.transactionMaxSize()) {
        LOG.warn(
            "Invalid block: calldata bytes {} is greater than max allowed {} for transaction {}",
            txCalldataSize,
            calldataLimits.transactionMaxSize(),
            tx.getHash());
        return false;
      }

      blockCalldataSize += txCalldataSize;
    }

    if (blockCalldataSize > calldataLimits.blockMaxSize()) {
      LOG.warn(
          "Invalid block: sum of all transaction calldata bytes {} is greater than max allowed {}",
          blockCalldataSize,
          calldataLimits.blockMaxSize());
      return false;
    }
    return true;
  }
}
