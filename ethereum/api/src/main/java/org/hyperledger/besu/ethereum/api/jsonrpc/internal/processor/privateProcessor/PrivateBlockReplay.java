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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.privateProcessor;

import static org.hyperledger.besu.ethereum.mainnet.feemarket.ExcessBlobGasCalculator.calculateExcessBlobGasForParent;

import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.privacy.ExecutedPrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;

import java.util.List;
import java.util.Optional;

public class PrivateBlockReplay {

  private final ProtocolSchedule protocolSchedule;
  private final Blockchain blockchain;
  private final PrivacyController privacyController;

  public PrivateBlockReplay(
      final ProtocolSchedule protocolSchedule,
      final Blockchain blockchain,
      final PrivacyController privacyController) {
    this.protocolSchedule = protocolSchedule;
    this.blockchain = blockchain;
    this.privacyController = privacyController;
  }

  public Optional<PrivateBlockTrace> block(
      final Block block,
      final PrivateBlockMetadata privateBlockMetadata,
      final String enclaveKey,
      final TransactionAction<PrivateTransactionTrace> action) {
    return performActionWithBlock(
        block.getHeader(),
        block.getBody(),
        (body, header, blockchain, transactionProcessor, protocolSpec) -> {
          final Wei dataGasPrice =
              protocolSpec
                  .getFeeMarket()
                  .blobGasPricePerGas(
                      blockchain
                          .getBlockHeader(header.getParentHash())
                          .map(parent -> calculateExcessBlobGasForParent(protocolSpec, parent))
                          .orElse(BlobGas.ZERO));

          final List<PrivateTransactionTrace> transactionTraces =
              privateBlockMetadata.getPrivateTransactionMetadataList().stream()
                  .map(
                      privateTransactionMetadata ->
                          privacyController
                              .findPrivateTransactionByPmtHash(
                                  privateTransactionMetadata.getPrivateMarkerTransactionHash(),
                                  enclaveKey)
                              .map(
                                  executedPrivateTransaction ->
                                      action.performAction(
                                          executedPrivateTransaction,
                                          header,
                                          blockchain,
                                          transactionProcessor,
                                          dataGasPrice))
                              .orElse(null))
                  .toList();

          return Optional.of(new PrivateBlockTrace(transactionTraces));
        });
  }

  public <T> Optional<T> performActionWithBlock(final Hash blockHash, final BlockAction<T> action) {
    Optional<Block> maybeBlock = getBlock(blockHash);
    if (maybeBlock.isEmpty()) {
      maybeBlock = getBadBlock(blockHash);
    }
    return maybeBlock.flatMap(
        block -> performActionWithBlock(block.getHeader(), block.getBody(), action));
  }

  private <T> Optional<T> performActionWithBlock(
      final BlockHeader header, final BlockBody body, final BlockAction<T> action) {
    if (header == null) {
      return Optional.empty();
    }
    if (body == null) {
      return Optional.empty();
    }
    final ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(header);
    final PrivateTransactionProcessor transactionProcessor =
        protocolSpec.getPrivateTransactionProcessor();

    return action.perform(body, header, blockchain, transactionProcessor, protocolSpec);
  }

  private Optional<Block> getBadBlock(final Hash blockHash) {
    final ProtocolSpec protocolSpec =
        protocolSchedule.getByBlockHeader(blockchain.getChainHeadHeader());
    return protocolSpec.getBadBlocksManager().getBadBlock(blockHash);
  }

  private Optional<Block> getBlock(final Hash blockHash) {
    final BlockHeader blockHeader = blockchain.getBlockHeader(blockHash).orElse(null);
    if (blockHeader != null) {
      final BlockBody blockBody = blockchain.getBlockBody(blockHeader.getHash()).orElse(null);
      if (blockBody != null) {
        return Optional.of(new Block(blockHeader, blockBody));
      }
    }
    return Optional.empty();
  }

  @FunctionalInterface
  public interface BlockAction<T> {
    Optional<T> perform(
        BlockBody body,
        BlockHeader blockHeader,
        Blockchain blockchain,
        PrivateTransactionProcessor transactionProcessor,
        ProtocolSpec protocolSpec);
  }

  @FunctionalInterface
  public interface TransactionAction<T> {
    T performAction(
        ExecutedPrivateTransaction transaction,
        BlockHeader blockHeader,
        Blockchain blockchain,
        PrivateTransactionProcessor transactionProcessor,
        Wei dataGasPrice);
  }
}
