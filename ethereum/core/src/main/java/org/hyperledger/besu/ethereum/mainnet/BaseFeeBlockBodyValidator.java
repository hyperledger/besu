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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.feemarket.TransactionPriceCalculator;

import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseFeeBlockBodyValidator extends MainnetBlockBodyValidator {
  private static final Logger LOG = LoggerFactory.getLogger(BaseFeeBlockBodyValidator.class);

  public BaseFeeBlockBodyValidator(final ProtocolSchedule protocolSchedule) {
    super(protocolSchedule);
  }

  @Override
  public boolean validateBodyLight(
      final ProtocolContext context,
      final Block block,
      final List<TransactionReceipt> receipts,
      final HeaderValidationMode ommerValidationMode) {

    return super.validateBodyLight(context, block, receipts, ommerValidationMode)
        && validateTransactionGasPrice(block);
  }

  @VisibleForTesting
  boolean validateTransactionGasPrice(final Block block) {

    final BlockBody body = block.getBody();
    final List<Transaction> transactions = body.getTransactions();
    final TransactionPriceCalculator transactionPriceCalculator =
        protocolSchedule
            .getByBlockNumber(block.getHeader().getNumber())
            .getFeeMarket()
            .getTransactionPriceCalculator();

    for (final Transaction transaction : transactions) {
      final Optional<Wei> baseFee = block.getHeader().getBaseFee();
      final Wei price = transactionPriceCalculator.price(transaction, baseFee);
      if (price.compareTo(baseFee.orElseThrow()) < 0) {
        LOG.warn(
            "Invalid block: transaction gas price {} must be greater than base fee {}",
            price.toString(),
            baseFee.orElseThrow());
        return false;
      }
    }
    return true;
  }
}
