/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.blockcreation.txselection;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.blockhash.BlockHashProcessor;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

public record BlockSelectionContext(
    MiningConfiguration miningConfiguration,
    ProcessableBlockHeader pendingBlockHeader,
    ProtocolSpec protocolSpec,
    Wei blobGasPrice,
    Address miningBeneficiary,
    TransactionPool transactionPool) {

  public FeeMarket feeMarket() {
    return protocolSpec.getFeeMarket();
  }

  public GasCalculator gasCalculator() {
    return protocolSpec.getGasCalculator();
  }

  public GasLimitCalculator gasLimitCalculator() {
    return protocolSpec.getGasLimitCalculator();
  }

  public BlockHashProcessor blockHashProcessor() {
    return protocolSpec.getBlockHashProcessor();
  }

  public int maxRlpBlockSize() {
    return protocolSpec.getBlockValidator().maxRlpBlockSize();
  }
}
