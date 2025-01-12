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

import org.hyperledger.besu.consensus.merge.PayloadWrapper;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetPayloadBodiesResultV1.PayloadBody;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.encoding.EncodingContext;
import org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.tuweni.bytes.Bytes;

public class BlockResultFactory {

  public BlockResult transactionComplete(
      final BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata) {
    return transactionComplete(blockWithMetadata, false);
  }

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

  public EngineGetPayloadResultV2 payloadTransactionCompleteV2(final PayloadWrapper payload) {
    final var blockWithReceipts = payload.blockWithReceipts();
    final List<String> txs =
        blockWithReceipts.getBlock().getBody().getTransactions().stream()
            .map(
                transaction ->
                    TransactionEncoder.encodeOpaqueBytes(transaction, EncodingContext.BLOCK_BODY))
            .map(Bytes::toHexString)
            .collect(Collectors.toList());

    return new EngineGetPayloadResultV2(
        blockWithReceipts.getHeader(),
        txs,
        blockWithReceipts.getBlock().getBody().getWithdrawals(),
        Quantity.create(payload.blockValue()));
  }

  public EngineGetPayloadBodiesResultV1 payloadBodiesCompleteV1(
      final List<Optional<BlockBody>> blockBodies) {
    final List<PayloadBody> payloadBodies =
        blockBodies.stream()
            .map(maybeBody -> maybeBody.map(PayloadBody::new).orElse(null))
            .collect(Collectors.toList());
    return new EngineGetPayloadBodiesResultV1(payloadBodies);
  }

  public EngineGetPayloadResultV3 payloadTransactionCompleteV3(final PayloadWrapper payload) {
    final var blockWithReceipts = payload.blockWithReceipts();
    final List<String> txs =
        blockWithReceipts.getBlock().getBody().getTransactions().stream()
            .map(
                transaction ->
                    TransactionEncoder.encodeOpaqueBytes(transaction, EncodingContext.BLOCK_BODY))
            .map(Bytes::toHexString)
            .collect(Collectors.toList());

    final BlobsBundleV1 blobsBundleV1 =
        new BlobsBundleV1(blockWithReceipts.getBlock().getBody().getTransactions());
    return new EngineGetPayloadResultV3(
        blockWithReceipts.getHeader(),
        txs,
        blockWithReceipts.getBlock().getBody().getWithdrawals(),
        Quantity.create(payload.blockValue()),
        blobsBundleV1);
  }

  public EngineGetPayloadResultV4 payloadTransactionCompleteV4(final PayloadWrapper payload) {
    final var blockWithReceipts = payload.blockWithReceipts();
    final List<String> txs =
        blockWithReceipts.getBlock().getBody().getTransactions().stream()
            .map(
                transaction ->
                    TransactionEncoder.encodeOpaqueBytes(transaction, EncodingContext.BLOCK_BODY))
            .map(Bytes::toHexString)
            .collect(Collectors.toList());
    final Optional<List<String>> requestsWithoutRequestId =
        payload
            .requests()
            .map(
                rqs ->
                    rqs.stream()
                        .sorted(Comparator.comparing(Request::getType))
                        .filter(r -> !r.getData().isEmpty())
                        .map(Request::getEncodedRequest)
                        .map(Bytes::toHexString)
                        .toList());

    final BlobsBundleV1 blobsBundleV1 =
        new BlobsBundleV1(blockWithReceipts.getBlock().getBody().getTransactions());
    return new EngineGetPayloadResultV4(
        blockWithReceipts.getHeader(),
        txs,
        blockWithReceipts.getBlock().getBody().getWithdrawals(),
        requestsWithoutRequestId,
        Quantity.create(payload.blockValue()),
        blobsBundleV1);
  }

  public BlockResult transactionHash(final BlockWithMetadata<Hash, Hash> blockWithMetadata) {
    return transactionHash(blockWithMetadata, false);
  }

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
