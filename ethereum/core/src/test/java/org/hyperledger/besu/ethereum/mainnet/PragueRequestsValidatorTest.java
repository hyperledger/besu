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
package org.hyperledger.besu.ethereum.mainnet;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.NONE;
import static org.hyperledger.besu.ethereum.mainnet.requests.DepositRequestProcessor.DEFAULT_DEPOSIT_CONTRACT_ADDRESS;
import static org.hyperledger.besu.ethereum.mainnet.requests.MainnetRequestsValidator.pragueRequestsValidator;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.WithdrawalRequest;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestsValidatorCoordinator;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PragueRequestsValidatorTest {

  private final BlockchainSetupUtil blockchainSetupUtil = BlockchainSetupUtil.forMainnet();
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolSpec protocolSpec;
  @Mock private WithdrawalsValidator withdrawalsValidator;

  RequestsValidatorCoordinator requestValidator =
      pragueRequestsValidator(DEFAULT_DEPOSIT_CONTRACT_ADDRESS);

  @BeforeEach
  public void setUp() {
    lenient().when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);
    lenient().when(protocolSpec.getWithdrawalsValidator()).thenReturn(withdrawalsValidator);
    lenient().when(withdrawalsValidator.validateWithdrawals(any())).thenReturn(true);
    lenient().when(withdrawalsValidator.validateWithdrawalsRoot(any())).thenReturn(true);
    lenient().when(protocolSpec.getRequestsValidatorCoordinator()).thenReturn(requestValidator);
  }

  private static final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

  @Test
  void shouldValidateRequestsTest() {
    WithdrawalRequest request =
        new WithdrawalRequest(
            Address.extract(Bytes32.fromHexStringLenient("1")),
            BLSPublicKey.wrap(Bytes48.fromHexStringLenient("1")),
            GWei.ONE);

    WithdrawalRequest requestTwo =
        new WithdrawalRequest(
            Address.extract(Bytes32.fromHexStringLenient("1")),
            BLSPublicKey.wrap(Bytes48.fromHexStringLenient("1")),
            GWei.ZERO);

    Optional<List<Request>> blockRequests = Optional.of(List.of(request));
    Optional<List<Request>> expectedRequests = Optional.of(List.of(requestTwo));
    Hash requestsRoot = BodyValidation.requestsRoot(blockRequests.get());

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setRequestsRoot(requestsRoot)
            .setRequests(blockRequests)
            .setGasUsed(0)
            .setReceiptsRoot(BodyValidation.receiptsRoot(emptyList()))
            .hasTransactions(false)
            .hasOmmers(false)
            .setReceiptsRoot(BodyValidation.receiptsRoot(emptyList()))
            .setLogsBloom(LogsBloomFilter.empty())
            .setParentHash(blockchainSetupUtil.getBlockchain().getChainHeadHash());

    final Block block = blockDataGenerator.block(blockOptions);
    assertThat(block).isNotNull();
    assertThat(
            new MainnetBlockBodyValidator(protocolSchedule)
                .validateBodyLight(
                    blockchainSetupUtil.getProtocolContext(),
                    block,
                    emptyList(),
                    expectedRequests,
                    NONE,
                    BodyValidationMode.FULL))
        .isFalse();
  }
}
