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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils;
import org.hyperledger.besu.ethereum.privacy.PrivateStateGenesisAllocator;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.enclave.testutil.EnclaveKeyConfiguration;
import org.hyperledger.enclave.testutil.TesseraTestHarness;
import org.hyperledger.enclave.testutil.TesseraTestHarnessFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PrivacyPrecompiledContractIntegrationTest {

  @TempDir private static Path folder;

  private static final Bytes VALID_PRIVATE_TRANSACTION_RLP =
      Bytes.fromHexString(
          "0xf90113800182520894095e7baea6a6c7c4c2dfeb977efac326af552d87"
              + "a0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
              + "ffff801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d"
              + "495a36649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab94"
              + "9f53faa07bd2c804ac41316156744d784c4355486d425648586f5a7a7a4267"
              + "5062572f776a3561784470573958386c393153476f3df85aac41316156744d"
              + "784c4355486d425648586f5a7a7a42675062572f776a356178447057395838"
              + "6c393153476f3dac4b6f32625671442b6e4e6c4e594c35454537793349644f"
              + "6e766966746a69697a706a52742b4854754642733d8a726573747269637465"
              + "64");
  private static final String DEFAULT_OUTPUT = "0x01";

  private static Enclave enclave;
  private static MessageFrame messageFrame;

  private static TesseraTestHarness testHarness;
  private static WorldStateArchive worldStateArchive;
  private static PrivateStateStorage privateStateStorage;
  private static final Vertx vertx = Vertx.vertx();

  private PrivateTransactionProcessor mockPrivateTxProcessor() {
    final PrivateTransactionProcessor mockPrivateTransactionProcessor =
        mock(PrivateTransactionProcessor.class);
    final TransactionProcessingResult result =
        TransactionProcessingResult.successful(
            null, 0, 0, Bytes.fromHexString(DEFAULT_OUTPUT), null);
    when(mockPrivateTransactionProcessor.processTransaction(
            nullable(WorldUpdater.class),
            nullable(WorldUpdater.class),
            nullable(ProcessableBlockHeader.class),
            nullable(Hash.class),
            nullable(PrivateTransaction.class),
            nullable(Address.class),
            nullable(OperationTracer.class),
            nullable(BlockHashLookup.class),
            nullable(Bytes.class)))
        .thenReturn(result);

    return mockPrivateTransactionProcessor;
  }

  @BeforeAll
  public static void setUpOnce() throws Exception {

    testHarness =
        TesseraTestHarnessFactory.create(
            "enclave",
            Files.createTempDirectory(folder, "enclave"),
            new EnclaveKeyConfiguration("enclave_key_0.pub", "enclave_key_1.key"),
            Optional.empty());

    testHarness.start();

    final EnclaveFactory factory = new EnclaveFactory(vertx);
    enclave = factory.createVertxEnclave(testHarness.clientUrl());
    messageFrame = mock(MessageFrame.class);
    final BlockDataGenerator blockGenerator = new BlockDataGenerator();
    final Block genesis = blockGenerator.genesisBlock();
    final Block block =
        blockGenerator.block(
            new BlockDataGenerator.BlockOptions().setParentHash(genesis.getHeader().getHash()));
    when(messageFrame.getBlockValues()).thenReturn(block.getHeader());
    final PrivateMetadataUpdater privateMetadataUpdater = mock(PrivateMetadataUpdater.class);
    when(privateMetadataUpdater.getPrivateBlockMetadata(any())).thenReturn(null);
    when(privateMetadataUpdater.getPrivacyGroupHeadBlockMap())
        .thenReturn(PrivacyGroupHeadBlockMap.empty());
    when(messageFrame.getContextVariable(
            eq(PrivateStateUtils.KEY_IS_PERSISTING_PRIVATE_STATE), anyBoolean()))
        .thenReturn(false);
    when(messageFrame.getContextVariable(eq(PrivateStateUtils.KEY_PRIVATE_METADATA_UPDATER)))
        .thenReturn(privateMetadataUpdater);
    when(messageFrame.hasContextVariable(eq(PrivateStateUtils.KEY_PRIVATE_METADATA_UPDATER)))
        .thenReturn(true);

    worldStateArchive = mock(WorldStateArchive.class);
    final MutableWorldState mutableWorldState = mock(MutableWorldState.class);
    when(mutableWorldState.updater()).thenReturn(mock(WorldUpdater.class));
    when(worldStateArchive.getMutable()).thenReturn(mutableWorldState);
    when(worldStateArchive.getMutable(any(), any())).thenReturn(Optional.of(mutableWorldState));

    privateStateStorage = mock(PrivateStateStorage.class);
    final PrivateStateStorage.Updater storageUpdater = mock(PrivateStateStorage.Updater.class);
    when(privateStateStorage.getPrivacyGroupHeadBlockMap(any()))
        .thenReturn(Optional.of(PrivacyGroupHeadBlockMap.empty()));
    when(storageUpdater.putPrivateBlockMetadata(
            nullable(Bytes32.class), nullable(Bytes32.class), any()))
        .thenReturn(storageUpdater);
    when(storageUpdater.putTransactionReceipt(
            nullable(Bytes32.class), nullable(Bytes32.class), any()))
        .thenReturn(storageUpdater);
    when(privateStateStorage.updater()).thenReturn(storageUpdater);
  }

  @AfterAll
  public static void tearDownOnce() {
    testHarness.stop();
    vertx.close();
  }

  @Test
  public void testUpCheck() {
    assertThat(enclave.upCheck()).isTrue();
  }

  @Test
  public void testSendAndReceive() {
    final List<String> publicKeys = testHarness.getPublicKeys();

    final PrivateTransaction privateTransaction =
        PrivateTransactionDataFixture.privateContractDeploymentTransactionBesu(publicKeys.get(0));
    final BytesValueRLPOutput bytesValueRLPOutput = new BytesValueRLPOutput();
    privateTransaction.writeTo(bytesValueRLPOutput);

    final String s = bytesValueRLPOutput.encoded().toBase64String();
    final SendResponse sr =
        enclave.send(s, publicKeys.get(0), Lists.newArrayList(publicKeys.get(0)));

    final PrivacyPrecompiledContract privacyPrecompiledContract =
        new PrivacyPrecompiledContract(
            new SpuriousDragonGasCalculator(),
            enclave,
            worldStateArchive,
            new PrivateStateRootResolver(privateStateStorage),
            new PrivateStateGenesisAllocator(
                false, (privacyGroupId, blockNumber) -> Collections::emptyList),
            "IntegrationTest");

    privacyPrecompiledContract.setPrivateTransactionProcessor(mockPrivateTxProcessor());

    final PrecompiledContract.PrecompileContractResult result =
        privacyPrecompiledContract.computePrecompile(
            Bytes.fromBase64String(sr.getKey()), messageFrame);
    final Bytes actual = result.getOutput();

    assertThat(actual).isEqualTo(Bytes.fromHexString(DEFAULT_OUTPUT));
  }

  @Test
  public void testNoPrivateKeyError() throws RuntimeException {
    final List<String> publicKeys = testHarness.getPublicKeys();
    publicKeys.add("noPrivateKey");

    final String s = VALID_PRIVATE_TRANSACTION_RLP.toBase64String();

    final Throwable thrown = catchThrowable(() -> enclave.send(s, publicKeys.get(0), publicKeys));

    assertThat(thrown).hasMessageContaining("Index 9 out of bounds for length 9");
  }

  @Test
  public void testWrongPrivateKeyError() throws RuntimeException {
    final List<String> publicKeys = testHarness.getPublicKeys();
    publicKeys.add("noPrivateKenoPrivateKenoPrivateKenoPrivateK");

    final String s = VALID_PRIVATE_TRANSACTION_RLP.toBase64String();

    final Throwable thrown = catchThrowable(() -> enclave.send(s, publicKeys.get(0), publicKeys));

    assertThat(thrown).hasMessageContaining("Recipient not found for key:");
  }
}
