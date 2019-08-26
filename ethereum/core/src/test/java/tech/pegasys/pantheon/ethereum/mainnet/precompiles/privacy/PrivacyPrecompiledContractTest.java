/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.mainnet.precompiles.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.EnclaveException;
import tech.pegasys.pantheon.enclave.types.ReceiveRequest;
import tech.pegasys.pantheon.enclave.types.ReceiveResponse;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.LogSeries;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.ProcessableBlockHeader;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.mainnet.SpuriousDragonGasCalculator;
import tech.pegasys.pantheon.ethereum.privacy.PrivateStateStorage;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransaction;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionProcessor;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionStorage;
import tech.pegasys.pantheon.ethereum.vm.BlockHashLookup;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.ethereum.vm.OperationTracer;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Base64;
import java.util.Optional;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PrivacyPrecompiledContractTest {
  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  private final String actual = "Test String";
  private final String publicKey = "public key";
  private final BytesValue key = BytesValue.wrap(actual.getBytes(UTF_8));
  private PrivacyPrecompiledContract privacyPrecompiledContract;
  private PrivacyPrecompiledContract brokenPrivateTransactionHandler;
  private MessageFrame messageFrame;
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
    Enclave mockEnclave = mock(Enclave.class);
    ReceiveResponse response = new ReceiveResponse(VALID_PRIVATE_TRANSACTION_RLP_BASE64, "");
    when(mockEnclave.receive(any(ReceiveRequest.class))).thenReturn(response);
    return mockEnclave;
  }

  private PrivateTransactionProcessor mockPrivateTxProcessor() {
    PrivateTransactionProcessor mockPrivateTransactionProcessor =
        mock(PrivateTransactionProcessor.class);
    LogSeries logs = mock(LogSeries.class);
    PrivateTransactionProcessor.Result result =
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
    Enclave mockEnclave = mock(Enclave.class);
    when(mockEnclave.receive(any(ReceiveRequest.class))).thenThrow(EnclaveException.class);
    return mockEnclave;
  }

  @Before
  public void setUp() {
    WorldStateArchive worldStateArchive;
    worldStateArchive = mock(WorldStateArchive.class);
    MutableWorldState mutableWorldState = mock(MutableWorldState.class);
    when(mutableWorldState.updater()).thenReturn(mock(WorldUpdater.class));
    when(worldStateArchive.getMutable()).thenReturn(mutableWorldState);
    when(worldStateArchive.getMutable(any())).thenReturn(Optional.of(mutableWorldState));

    PrivateTransactionStorage privateTransactionStorage = mock(PrivateTransactionStorage.class);
    PrivateTransactionStorage.Updater updater = mock(PrivateTransactionStorage.Updater.class);
    when(updater.putTransactionLogs(nullable(Bytes32.class), any())).thenReturn(updater);
    when(updater.putTransactionResult(nullable(Bytes32.class), any())).thenReturn(updater);
    when(privateTransactionStorage.updater()).thenReturn(updater);

    PrivateStateStorage privateStateStorage = mock(PrivateStateStorage.class);
    PrivateStateStorage.Updater storageUpdater = mock(PrivateStateStorage.Updater.class);
    when(storageUpdater.putPrivateAccountState(nullable(Bytes32.class), any()))
        .thenReturn(storageUpdater);
    when(privateStateStorage.updater()).thenReturn(storageUpdater);

    privacyPrecompiledContract =
        new PrivacyPrecompiledContract(
            new SpuriousDragonGasCalculator(),
            publicKey,
            mockEnclave(),
            worldStateArchive,
            privateTransactionStorage,
            privateStateStorage);
    privacyPrecompiledContract.setPrivateTransactionProcessor(mockPrivateTxProcessor());
    brokenPrivateTransactionHandler =
        new PrivacyPrecompiledContract(
            new SpuriousDragonGasCalculator(),
            publicKey,
            brokenMockEnclave(),
            worldStateArchive,
            privateTransactionStorage,
            privateStateStorage);
    messageFrame = mock(MessageFrame.class);
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
