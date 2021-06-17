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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionReceiptWithMetadata;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecs;
import org.hyperledger.besu.ethereum.mainnet.MutableProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DefaultWorldStateArchive;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class EthFeeHistoryTest {
  final BlockDataGenerator gen = new BlockDataGenerator();
  private MutableBlockchain blockchain;
  private BlockchainQueries blockchainQueries;
  private EthFeeHistory method;
  private ProtocolSchedule protocolSchedule;

  @Before
  public void setUp() {
    protocolSchedule = mock(ProtocolSchedule.class);
    final Block genesisBlock = gen.genesisBlock();
    blockchain = createInMemoryBlockchain(genesisBlock);
    gen.blockSequence(genesisBlock, 10)
        .forEach(block -> blockchain.appendBlock(block, gen.receipts(block)));
    blockchainQueries =
        new BlockchainQueries(
            blockchain,
            new DefaultWorldStateArchive(
                new WorldStateKeyValueStorage(new InMemoryKeyValueStorage()),
                new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage())));
    method = new EthFeeHistory(protocolSchedule, blockchainQueries);
  }

  @Test
  public void params() {
    // should fail because no required params given
    assertThatThrownBy(() -> method.response(feeHistoryRequestContext()))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    // should fail because newestBlock not given
    assertThatThrownBy(() -> method.response(feeHistoryRequestContext(1)))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    // should fail because blockCount not given
    assertThatThrownBy(() -> method.response(feeHistoryRequestContext("latest")))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    // should pass because both required params given
    method.response(feeHistoryRequestContext(1, "latest"));
    // should pass because both required params and optional param given
    method.response(feeHistoryRequestContext(1, "latest", new double[] {1, 20.4}));
  }

  @Test
  public void allFieldsPresentForLatestBlock() {
    final ProtocolSpec londonSpec = mock(ProtocolSpec.class);
    when(londonSpec.getEip1559()).thenReturn(Optional.of(new EIP1559(5)));
    when(protocolSchedule.getByBlockNumber(eq(11L))).thenReturn(londonSpec);
    assertThat(
            ((JsonRpcSuccessResponse)
                    method.response(feeHistoryRequestContext(1, "latest", new double[] {100.0})))
                .getResult())
        .isEqualTo(
            new EthFeeHistory.FeeHistory(
                10,
                List.of(47177L, 53074L),
                List.of(0.9999999992132459),
                Optional.of(List.of(List.of(1524742083L)))));
  }

  // test base fee always being of size > 1
  // test block count goes further than chain head
  // test names of field are alright
  // zeros for empty block
  // ascending order for rewards implies sorting
  // check invalid numerals in parsing

  private JsonRpcRequestContext feeHistoryRequestContext(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eth_feeHistory", params));
  }
}
