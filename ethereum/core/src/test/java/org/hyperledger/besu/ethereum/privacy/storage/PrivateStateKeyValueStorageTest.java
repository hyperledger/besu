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
package org.hyperledger.besu.ethereum.privacy.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogSeries;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionSimulator;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionSimulatorResult;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

public class PrivateStateKeyValueStorageTest {

  private static final String PRIVACY_GROUP_ID = "tJw12cPM6EZRF5zfHv2zLePL0cqlaDjLn0x1T/V0yzE=";
  private static final String TRANSACTION_KEY = "93Ky7lXwFkMc7+ckoFgUMku5bpr9tz4zhmWmk9RlNng=";
  private static final SECP256K1.KeyPair KEY_PAIR =
      SECP256K1.KeyPair.create(
          SECP256K1.PrivateKey.create(
              new BigInteger(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

  private static final Transaction PUBLIC_TRANSACTION =
      Transaction.builder()
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(Address.DEFAULT_PRIVACY)
          .value(Wei.ZERO)
          .payload(BytesValues.fromBase64(TRANSACTION_KEY))
          .sender(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"))
          .chainId(BigInteger.valueOf(2018))
          .signAndBuild(KEY_PAIR);

  private static final PrivateTransaction VALID_SIGNED_PRIVATE_TRANSACTION =
      PrivateTransaction.builder()
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(null)
          .value(Wei.ZERO)
          .payload(
              BytesValue.fromHexString(
                  "0x608060405234801561001057600080fd5b5060d08061001f6000396000"
                      + "f3fe60806040526004361060485763ffffffff7c010000000000"
                      + "0000000000000000000000000000000000000000000000600035"
                      + "04166360fe47b18114604d5780636d4ce63c146075575b600080"
                      + "fd5b348015605857600080fd5b50607360048036036020811015"
                      + "606d57600080fd5b50356099565b005b348015608057600080fd"
                      + "5b506087609e565b60408051918252519081900360200190f35b"
                      + "600055565b6000549056fea165627a7a72305820cb1d0935d14b"
                      + "589300b12fcd0ab849a7e9019c81da24d6daa4f6b2f003d1b018"
                      + "0029"))
          .sender(
              Address.wrap(BytesValue.fromHexString("0x1c9a6e1ee3b7ac6028e786d9519ae3d24ee31e79")))
          .chainId(BigInteger.valueOf(4))
          .privateFrom(BytesValues.fromBase64("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="))
          .privacyGroupId(BytesValues.fromBase64(PRIVACY_GROUP_ID))
          .restriction(Restriction.RESTRICTED)
          .signAndBuild(KEY_PAIR);

  private PrivateStateKeyValueStorage storage;

  @Before
  public void setUp() {
    storage = emptyStorage();
  }

  @Test
  public void getTransactionLogs_returnsEmpty() {
    assertThat(storage.getTransactionLogs(Hash.EMPTY)).isEmpty();
  }

  @Test
  public void getTransactionOutput_returnsEmpty() {
    assertThat(storage.getTransactionOutput(Hash.EMPTY)).isEmpty();
  }

  @Test
  public void getPrivateBlockMetadata_returnsEmpty() {
    assertThat(storage.getPrivateBlockMetadata(Hash.EMPTY, Hash.EMPTY)).isEmpty();
  }

  @Test
  public void testPerformMigrationOnLegacyEventSuffix() {
    storage
        .updater()
        .putTransactionLogsLegacy(Hash.EMPTY, LogSeries.empty())
        .putTransactionLogsLegacy(Hash.ZERO, LogSeries.empty())
        .commit();

    assertThat(storage.getTransactionLogs(Hash.EMPTY)).isEmpty();
    assertThat(storage.getTransactionLogs(Hash.ZERO)).isEmpty();

    assertThat(storage.getTransactionLogsLegacy(Hash.EMPTY)).contains(LogSeries.empty());
    assertThat(storage.getTransactionLogsLegacy(Hash.ZERO)).contains(LogSeries.empty());

    storage.performMigrations(null, null, null, null, null);

    assertThat(storage.getTransactionLogs(Hash.EMPTY)).contains(LogSeries.empty());
    assertThat(storage.getTransactionLogs(Hash.ZERO)).contains(LogSeries.empty());

    assertThat(storage.getTransactionLogsLegacy(Hash.EMPTY)).isEmpty();
    assertThat(storage.getTransactionLogsLegacy(Hash.ZERO)).isEmpty();
  }

  @Test
  public void testPerformMigrationOnLegacyStateRoot() {
    storage
        .updater()
        .putLatestStateRoot(BytesValues.fromBase64(PRIVACY_GROUP_ID), Hash.EMPTY)
        .commit();

    assertThat(storage.getLatestStateRoot(BytesValues.fromBase64(PRIVACY_GROUP_ID)))
        .contains(Hash.EMPTY);

    final BytesValue enclaveKey = BytesValue.EMPTY;

    final Enclave enclave = mock(Enclave.class);
    when(enclave.receive(
            eq(new ReceiveRequest(TRANSACTION_KEY, BytesValues.asBase64String(enclaveKey)))))
        .thenReturn(
            new ReceiveResponse(
                BytesValues.asBase64String(RLP.encode(VALID_SIGNED_PRIVATE_TRANSACTION::writeTo))
                    .getBytes(UTF_8),
                PRIVACY_GROUP_ID));

    final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
    final Block genesis = blockDataGenerator.genesisBlock();
    final BlockDataGenerator.BlockOptions options = BlockDataGenerator.BlockOptions.create();
    options.setParentHash(genesis.getHash());
    options.setBlockNumber(1);
    options.addTransaction(PUBLIC_TRANSACTION);
    final Block block = blockDataGenerator.block(options);
    final Blockchain blockchain = mock(Blockchain.class);
    when(blockchain.getChainHeadBlockNumber()).thenReturn(1L);
    when(blockchain.getBlockByNumber(0L)).thenReturn(Optional.of(genesis));
    when(blockchain.getBlockByNumber(1L)).thenReturn(Optional.of(block));

    final PrivateTransactionSimulator privateTransactionSimulator =
        mock(PrivateTransactionSimulator.class);
    when(privateTransactionSimulator.process(any(), any(), any()))
        .thenReturn(Optional.of(new PrivateTransactionSimulatorResult(null, null, Hash.EMPTY)));

    storage.performMigrations(
        enclave, enclaveKey, Address.DEFAULT_PRIVACY, blockchain, privateTransactionSimulator);

    final PrivateBlockMetadata expected = PrivateBlockMetadata.empty();
    expected.addPrivateTransactionMetadata(
        new PrivateTransactionMetadata(PUBLIC_TRANSACTION.getHash(), Hash.EMPTY));

    assertThat(storage.getLatestStateRoot(BytesValues.fromBase64(PRIVACY_GROUP_ID))).isEmpty();
    assertThat(
            storage.getPrivateBlockMetadata(
                block.getHash(), Bytes32.wrap(BytesValues.fromBase64(PRIVACY_GROUP_ID))))
        .contains(expected);
  }

  private PrivateStateKeyValueStorage emptyStorage() {
    return new PrivateStateKeyValueStorage(new InMemoryKeyValueStorage());
  }
}
