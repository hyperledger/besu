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
package org.hyperledger.besu.ethereum.mainnet.precompiles.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveException;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.LogSeries;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.SpuriousDragonGasCalculator;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Base64;
import java.util.Optional;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PrivacyPrecompiledContractTest {
  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  private final String actual = "Test String";
  private final BytesValue key = BytesValue.wrap(actual.getBytes(UTF_8));
  private PrivacyPrecompiledContract privacyPrecompiledContract;
  private PrivacyPrecompiledContract brokenPrivateTransactionHandler;
  private MessageFrame messageFrame;
  private Blockchain blockchain;
  private final String DEFAULT_OUTPUT = "0x01";

  private static final byte[] VALID_PRIVATE_TRANSACTION_RLP_BASE64 =
      Base64.getEncoder()
          .encode(
              BytesValue.fromHexString(
                      "0xf90113800182520894095e7baea6a6c7c4c2dfeb977efac326af552d87"
                          + "a0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
                          + "ffff801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d"
                          + "495a36649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab94"
                          + "9f53faa07bd2c804ac41316156744d784c4355486d425648586f5a7a7a4267"
                          + "5062572f776a3561784470573958386c393153476f3df85aac41316156744d"
                          + "784c4355486d425648586f5a7a7a42675062572f776a356178447057395838"
                          + "6c393153476f3dac4b6f32625671442b6e4e6c4e594c35454537793349644f"
                          + "6e766966746a69697a706a52742b4854754642733d8a726573747269637465"
                          + "64")
                  .extractArray());

  private Enclave mockEnclave() {
    final Enclave mockEnclave = mock(Enclave.class);
    final ReceiveResponse response =
        new ReceiveResponse(
            VALID_PRIVATE_TRANSACTION_RLP_BASE64, "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");
    when(mockEnclave.receive(any(ReceiveRequest.class))).thenReturn(response);
    return mockEnclave;
  }

  private PrivateTransactionProcessor mockPrivateTxProcessor() {
    final PrivateTransactionProcessor mockPrivateTransactionProcessor =
        mock(PrivateTransactionProcessor.class);
    final LogSeries logs = mock(LogSeries.class);
    final PrivateTransactionProcessor.Result result =
        PrivateTransactionProcessor.Result.successful(
            logs, 0, BytesValue.fromHexString(DEFAULT_OUTPUT), null);
    when(mockPrivateTransactionProcessor.processTransaction(
            nullable(Blockchain.class),
            nullable(WorldUpdater.class),
            nullable(WorldUpdater.class),
            nullable(ProcessableBlockHeader.class),
            nullable(PrivateTransaction.class),
            nullable(Address.class),
            nullable(OperationTracer.class),
            nullable(BlockHashLookup.class),
            nullable(BytesValue.class)))
        .thenReturn(result);

    return mockPrivateTransactionProcessor;
  }

  private Enclave brokenMockEnclave() {
    final Enclave mockEnclave = mock(Enclave.class);
    when(mockEnclave.receive(any(ReceiveRequest.class))).thenThrow(EnclaveException.class);
    return mockEnclave;
  }

  @Before
  public void setUp() {
    final WorldStateArchive worldStateArchive;
    worldStateArchive = mock(WorldStateArchive.class);
    final MutableWorldState mutableWorldState = mock(MutableWorldState.class);
    when(mutableWorldState.updater()).thenReturn(mock(WorldUpdater.class));
    when(worldStateArchive.getMutable()).thenReturn(mutableWorldState);
    when(worldStateArchive.getMutable(any())).thenReturn(Optional.of(mutableWorldState));

    final PrivateStateStorage privateStateStorage = mock(PrivateStateStorage.class);
    final PrivateStateStorage.Updater storageUpdater = mock(PrivateStateStorage.Updater.class);
    when(privateStateStorage.getPrivacyGroupHeadBlockMap(any()))
        .thenReturn(Optional.of(PrivacyGroupHeadBlockMap.EMPTY));
    when(privateStateStorage.getPrivateBlockMetadata(any(), any())).thenReturn(Optional.empty());
    when(storageUpdater.putPrivateBlockMetadata(
            nullable(Bytes32.class), nullable(Bytes32.class), any()))
        .thenReturn(storageUpdater);
    when(storageUpdater.putPrivacyGroupHeadBlockMap(nullable(Bytes32.class), any()))
        .thenReturn(storageUpdater);
    when(storageUpdater.putTransactionLogs(nullable(Bytes32.class), any()))
        .thenReturn(storageUpdater);
    when(storageUpdater.putTransactionResult(nullable(Bytes32.class), any()))
        .thenReturn(storageUpdater);
    when(privateStateStorage.updater()).thenReturn(storageUpdater);

    privacyPrecompiledContract =
        new PrivacyPrecompiledContract(
            new SpuriousDragonGasCalculator(),
            mockEnclave(),
            worldStateArchive,
            privateStateStorage);
    privacyPrecompiledContract.setPrivateTransactionProcessor(mockPrivateTxProcessor());
    brokenPrivateTransactionHandler =
        new PrivacyPrecompiledContract(
            new SpuriousDragonGasCalculator(),
            brokenMockEnclave(),
            worldStateArchive,
            privateStateStorage);
    messageFrame = mock(MessageFrame.class);
    blockchain = mock(Blockchain.class);
    final BlockDataGenerator blockGenerator = new BlockDataGenerator();
    final Block genesis = blockGenerator.genesisBlock();
    final Block block =
        blockGenerator.block(
            new BlockDataGenerator.BlockOptions().setParentHash(genesis.getHeader().getHash()));
    when(blockchain.getGenesisBlock()).thenReturn(genesis);
    when(blockchain.getBlockByHash(block.getHash())).thenReturn(Optional.of(block));
    when(blockchain.getBlockByHash(genesis.getHash())).thenReturn(Optional.of(genesis));
    when(messageFrame.getBlockchain()).thenReturn(blockchain);
    when(messageFrame.getBlockHeader()).thenReturn(block.getHeader());
  }

  @Test
  public void testPrivacyPrecompiledContract() {
    final BytesValue actual = privacyPrecompiledContract.compute(key, messageFrame);

    assertThat(actual).isEqualTo(BytesValue.fromHexString(DEFAULT_OUTPUT));
  }

  @Test
  public void enclaveIsDownWhileHandling() {
    final BytesValue expected = brokenPrivateTransactionHandler.compute(key, messageFrame);

    assertThat(expected).isEqualTo(BytesValue.EMPTY);
  }
}
