/*
 * Copyright Hyperledger Besu Contributors.
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

package org.hyperledger.besu.services;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockchainService;

import java.util.Optional;
import java.util.function.Supplier;

/** The Blockchain service implementation. */
@Unstable
public class BlockchainServiceImpl implements BlockchainService {

  private ProtocolContext protocolContext;
  private ProtocolSchedule protocolSchedule;

  /** Create a new instance */
  public BlockchainServiceImpl() {}

  /**
   * Instantiates a new Blockchain service.
   *
   * @param protocolContext the protocol context
   * @param protocolSchedule the protocol schedule
   */
  public void init(final ProtocolContext protocolContext, final ProtocolSchedule protocolSchedule) {
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
  }

  /**
   * Gets block by number
   *
   * @param number the block number
   * @return the BlockContext if block exists otherwise empty
   */
  @Override
  public Optional<BlockContext> getBlockByNumber(final long number) {
    return protocolContext
        .getBlockchain()
        .getBlockByNumber(number)
        .map(block -> blockContext(block::getHeader, block::getBody));
  }

  @Override
  public Hash getChainHeadHash() {
    return protocolContext.getBlockchain().getChainHeadHash();
  }

  @Override
  public BlockHeader getChainHeadHeader() {
    return protocolContext.getBlockchain().getChainHeadHeader();
  }

  @Override
  public Optional<Wei> getNextBlockBaseFee() {
    final var chainHeadHeader = protocolContext.getBlockchain().getChainHeadHeader();
    final var protocolSpec =
        protocolSchedule.getForNextBlockHeader(chainHeadHeader, System.currentTimeMillis());
    return Optional.of(protocolSpec.getFeeMarket())
        .filter(FeeMarket::implementsBaseFee)
        .map(BaseFeeMarket.class::cast)
        .map(
            feeMarket ->
                feeMarket.computeBaseFee(
                    chainHeadHeader.getNumber() + 1,
                    chainHeadHeader.getBaseFee().orElse(Wei.ZERO),
                    chainHeadHeader.getGasUsed(),
                    feeMarket.targetGasUsed(chainHeadHeader)));
  }

  private static BlockContext blockContext(
      final Supplier<BlockHeader> blockHeaderSupplier,
      final Supplier<BlockBody> blockBodySupplier) {
    return new BlockContext() {
      @Override
      public BlockHeader getBlockHeader() {
        return blockHeaderSupplier.get();
      }

      @Override
      public BlockBody getBlockBody() {
        return blockBodySupplier.get();
      }
    };
  }
}
