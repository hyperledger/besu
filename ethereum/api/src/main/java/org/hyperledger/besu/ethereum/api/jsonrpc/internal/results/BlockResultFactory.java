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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetPayloadBodiesResultV1.PayloadBody;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockValueCalculator;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.encoding.EncodingContext;
import org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.tuweni.bytes.Bytes;

/** The type Block result factory. */
public class BlockResultFactory {
  /** Default constructor. */
  public BlockResultFactory() {}

  /**
   * Transaction complete block result.
   *
   * @param blockWithMetadata the block with metadata
   * @return the block result
   */
  public BlockResult transactionComplete(
      final BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata) {
    return transactionComplete(blockWithMetadata, false);
  }

  /**
   * Transaction complete block result.
   *
   * @param blockWithMetadata the block with metadata
   * @param includeCoinbase the include coinbase
   * @return the block result
   */
  public BlockResult transactionComplete(
      final BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata,
      final boolean includeCoinbase) {
    final List<TransactionResult> txs =
        blockWithMetadata.getTransactions().stream()
            .map(TransactionCompleteResult::new)
            .collect(Collectors.toList());
    final List<JsonNode> ommers =
        blockWithMetadata.getOmmers().stream()
            .map(Hash::toString)
            .map(TextNode::new)
            .collect(Collectors.toList());
    return new BlockResult(
        blockWithMetadata.getHeader(),
        txs,
        ommers,
        blockWithMetadata.getTotalDifficulty(),
        blockWithMetadata.getSize(),
        includeCoinbase,
        blockWithMetadata.getWithdrawals());
  }

  /**
   * Transaction complete block result.
   *
   * @param block the block
   * @return the block result
   */
  public BlockResult transactionComplete(final Block block) {

    final int count = block.getBody().getTransactions().size();
    final List<TransactionWithMetadata> transactionWithMetadata = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      transactionWithMetadata.add(
          new TransactionWithMetadata(
              block.getBody().getTransactions().get(i),
              block.getHeader().getNumber(),
              block.getHeader().getBaseFee(),
              block.getHash(),
              i));
    }
    final List<TransactionResult> txs =
        transactionWithMetadata.stream()
            .map(TransactionCompleteResult::new)
            .collect(Collectors.toList());

    final List<JsonNode> ommers =
        block.getBody().getOmmers().stream()
            .map(BlockHeader::getHash)
            .map(Hash::toString)
            .map(TextNode::new)
            .collect(Collectors.toList());
    return new BlockResult(
        block.getHeader(), txs, ommers, block.getHeader().getDifficulty(), block.calculateSize());
  }

  /**
   * Payload transaction complete v 1 engine get payload result v 1.
   *
   * @param block the block
   * @return the engine get payload result v 1
   */
  public EngineGetPayloadResultV1 payloadTransactionCompleteV1(final Block block) {
    final List<String> txs =
        block.getBody().getTransactions().stream()
            .map(
                transaction ->
                    TransactionEncoder.encodeOpaqueBytes(transaction, EncodingContext.BLOCK_BODY))
            .map(Bytes::toHexString)
            .collect(Collectors.toList());

    return new EngineGetPayloadResultV1(block.getHeader(), txs);
  }

  /**
   * Payload transaction complete v 2 engine get payload result v 2.
   *
   * @param blockWithReceipts the block with receipts
   * @return the engine get payload result v 2
   */
  public EngineGetPayloadResultV2 payloadTransactionCompleteV2(
      final BlockWithReceipts blockWithReceipts) {
    final List<String> txs =
        blockWithReceipts.getBlock().getBody().getTransactions().stream()
            .map(
                transaction ->
                    TransactionEncoder.encodeOpaqueBytes(transaction, EncodingContext.BLOCK_BODY))
            .map(Bytes::toHexString)
            .collect(Collectors.toList());

    final Wei blockValue = new BlockValueCalculator().calculateBlockValue(blockWithReceipts);
    return new EngineGetPayloadResultV2(
        blockWithReceipts.getHeader(),
        txs,
        blockWithReceipts.getBlock().getBody().getWithdrawals(),
        Quantity.create(blockValue));
  }

  /**
   * Payload bodies complete v 1 engine get payload bodies result v 1.
   *
   * @param blockBodies the block bodies
   * @return the engine get payload bodies result v 1
   */
  public EngineGetPayloadBodiesResultV1 payloadBodiesCompleteV1(
      final List<Optional<BlockBody>> blockBodies) {
    final List<PayloadBody> payloadBodies =
        blockBodies.stream()
            .map(maybeBody -> maybeBody.map(PayloadBody::new).orElse(null))
            .collect(Collectors.toList());
    return new EngineGetPayloadBodiesResultV1(payloadBodies);
  }

  /**
   * Payload transaction complete v 3 engine get payload result v 3.
   *
   * @param blockWithReceipts the block with receipts
   * @return the engine get payload result v 3
   */
  public EngineGetPayloadResultV3 payloadTransactionCompleteV3(
      final BlockWithReceipts blockWithReceipts) {
    final List<String> txs =
        blockWithReceipts.getBlock().getBody().getTransactions().stream()
            .map(
                transaction ->
                    TransactionEncoder.encodeOpaqueBytes(transaction, EncodingContext.BLOCK_BODY))
            .map(Bytes::toHexString)
            .collect(Collectors.toList());

    final Wei blockValue = new BlockValueCalculator().calculateBlockValue(blockWithReceipts);

    final BlobsBundleV1 blobsBundleV1 =
        new BlobsBundleV1(blockWithReceipts.getBlock().getBody().getTransactions());
    return new EngineGetPayloadResultV3(
        blockWithReceipts.getHeader(),
        txs,
        blockWithReceipts.getBlock().getBody().getWithdrawals(),
        Quantity.create(blockValue),
        blobsBundleV1);
  }

  /**
   * Payload transaction complete v 4 engine get payload result v 4.
   *
   * @param blockWithReceipts the block with receipts
   * @return the engine get payload result v 4
   */
  public EngineGetPayloadResultV4 payloadTransactionCompleteV4(
      final BlockWithReceipts blockWithReceipts) {
    final List<String> txs =
        blockWithReceipts.getBlock().getBody().getTransactions().stream()
            .map(
                transaction ->
                    TransactionEncoder.encodeOpaqueBytes(transaction, EncodingContext.BLOCK_BODY))
            .map(Bytes::toHexString)
            .collect(Collectors.toList());

    final Wei blockValue = new BlockValueCalculator().calculateBlockValue(blockWithReceipts);

    final BlobsBundleV1 blobsBundleV1 =
        new BlobsBundleV1(blockWithReceipts.getBlock().getBody().getTransactions());
    return new EngineGetPayloadResultV4(
        blockWithReceipts.getHeader(),
        txs,
        blockWithReceipts.getBlock().getBody().getWithdrawals(),
        blockWithReceipts.getBlock().getBody().getDeposits(),
        blockWithReceipts.getBlock().getBody().getWithdrawalRequests(),
        Quantity.create(blockValue),
        blobsBundleV1);
  }

  /**
   * Transaction hash block result.
   *
   * @param blockWithMetadata the block with metadata
   * @return the block result
   */
  public BlockResult transactionHash(final BlockWithMetadata<Hash, Hash> blockWithMetadata) {
    return transactionHash(blockWithMetadata, false);
  }

  /**
   * Transaction hash block result.
   *
   * @param blockWithMetadata the block with metadata
   * @param includeCoinbase the include coinbase
   * @return the block result
   */
  public BlockResult transactionHash(
      final BlockWithMetadata<Hash, Hash> blockWithMetadata, final boolean includeCoinbase) {
    final List<TransactionResult> txs =
        blockWithMetadata.getTransactions().stream()
            .map(Hash::toString)
            .map(TransactionHashResult::new)
            .collect(Collectors.toList());
    final List<JsonNode> ommers =
        blockWithMetadata.getOmmers().stream()
            .map(Hash::toString)
            .map(TextNode::new)
            .collect(Collectors.toList());
    return new BlockResult(
        blockWithMetadata.getHeader(),
        txs,
        ommers,
        blockWithMetadata.getTotalDifficulty(),
        blockWithMetadata.getSize(),
        includeCoinbase,
        blockWithMetadata.getWithdrawals());
  }
}
