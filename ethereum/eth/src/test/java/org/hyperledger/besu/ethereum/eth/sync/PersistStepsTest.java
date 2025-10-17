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
package org.hyperledger.besu.ethereum.eth.sync;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncBlockBody;
import org.hyperledger.besu.ethereum.core.SyncBlockWithReceipts;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PersistStepsTest {

  private ProtocolContext protocolContext;
  private DefaultBlockchain blockchain;
  private BlockchainStorage storage;
  private BlockchainStorage.Updater updater;

  @BeforeEach
  public void setUp() {
    protocolContext = mock(ProtocolContext.class);
    blockchain = mock(DefaultBlockchain.class);
    storage = mock(BlockchainStorage.class);
    updater = mock(BlockchainStorage.Updater.class);
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getBlockchainStorage()).thenReturn(storage);
    when(storage.updater()).thenReturn(updater);
  }

  @Test
  public void persistBodies_persists() {
    final PersistBodiesStep step = new PersistBodiesStep(protocolContext);

    final Block block = mock(Block.class);
    final BlockBody body = mock(BlockBody.class);
    final Hash hash = Hash.hash(Bytes.of(1));
    when(block.getHash()).thenReturn(hash);
    when(block.getBody()).thenReturn(body);
    when(blockchain.getBlockBody(hash)).thenReturn(Optional.empty());

    step.apply(List.of(block));

    verify(updater).putBlockBody(hash, body);
    verify(updater).commit();
  }

  @Test
  public void persistSyncBodies_persists() {
    final PersistSyncBodiesStep step = new PersistSyncBodiesStep(protocolContext);

    final SyncBlock syncBlock = mock(SyncBlock.class);
    final SyncBlockBody body = mock(SyncBlockBody.class);
    final Hash hash = Hash.hash(Bytes.of(2));
    when(syncBlock.getHash()).thenReturn(hash);
    when(syncBlock.getBody()).thenReturn(body);
    when(blockchain.getBlockBody(hash)).thenReturn(Optional.empty());

    step.apply(List.of(syncBlock));

    verify(updater).putSyncBlockBody(hash, body);
    verify(updater).commit();
  }

  @Test
  public void persistReceipts_persists() {
    final PersistReceiptsStep step = new PersistReceiptsStep(protocolContext);

    final SyncBlockWithReceipts bwr = mock(SyncBlockWithReceipts.class);
    final Hash hash = Hash.hash(Bytes.of(3));
    final List<TransactionReceipt> receipts = List.of(mock(TransactionReceipt.class));
    when(bwr.getHash()).thenReturn(hash);
    when(bwr.getReceipts()).thenReturn(receipts);
    when(blockchain.getTxReceipts(hash)).thenReturn(Optional.empty());

    step.apply(List.of(bwr));

    verify(updater).putTransactionReceipts(hash, receipts);
    verify(updater).commit();
  }
}


