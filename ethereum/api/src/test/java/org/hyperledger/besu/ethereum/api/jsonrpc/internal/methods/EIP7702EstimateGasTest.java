/*
 * Copyright contributors to Besu.
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.ImmutableCallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.math.BigInteger;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EIP7702EstimateGasTest {

  @Mock private BlockchainQueries blockchainQueries;

  @Mock private TransactionSimulator transactionSimulator;

  @Mock private ApiConfiguration apiConfiguration;

  private EthEstimateGas ethEstimateGas;
  private BlockHeader mockBlockHeader;

  @BeforeEach
  public void setUp() {
    // Configure the mock ApiConfiguration with default values
    when(apiConfiguration.getGasCap()).thenReturn(ApiConfiguration.DEFAULT_GAS_CAP);
    when(apiConfiguration.isGasAndPriorityFeeLimitingEnabled()).thenReturn(false);

    // Mock BlockHeader
    mockBlockHeader = mock(BlockHeader.class);
    when(mockBlockHeader.getGasLimit()).thenReturn(30_000_000L);
    when(mockBlockHeader.getBaseFee()).thenReturn(Optional.of(Wei.of(1000000000L)));
    when(mockBlockHeader.getNumber()).thenReturn(1000L);

    // Configure BlockchainQueries mock to return valid values
    when(blockchainQueries.headBlockNumber()).thenReturn(1000L);
    when(blockchainQueries.getBlockHeaderByNumber(anyLong()))
        .thenReturn(Optional.of(mockBlockHeader));
    when(blockchainQueries.getBlockHeaderByNumber(1000L)).thenReturn(Optional.of(mockBlockHeader));

    ethEstimateGas = new EthEstimateGas(blockchainQueries, transactionSimulator, apiConfiguration);
  }

  @Test
  public void shouldEstimateGasForEIP7702Transaction() {
    // Create a CodeDelegation (authorization) using the factory method
    final Address delegateAddress =
        Address.fromHexString("0x1234567890123456789012345678901234567890");
    final org.hyperledger.besu.datatypes.CodeDelegation authorization =
        org.hyperledger.besu.ethereum.core.CodeDelegation.createCodeDelegation(
            BigInteger.ONE,
            delegateAddress,
            "0x0",
            null,
            "0x0",
            Bytes32.random().toHexString(),
            Bytes32.random().toHexString());

    // Create CallParameter with authorization list
    final CallParameter callParameter =
        ImmutableCallParameter.builder()
            .sender(Address.fromHexString("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"))
            .to(Address.fromHexString("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"))
            .value(Wei.ONE)
            .gas(21000L)
            .addCodeDelegationAuthorizations(authorization)
            .build();

    // Mock the transaction simulator result
    final TransactionSimulatorResult mockResult = mock(TransactionSimulatorResult.class);
    when(mockResult.isSuccessful()).thenReturn(true);
    when(mockResult.getGasEstimate()).thenReturn(50000L);
    when(mockResult.result()).thenReturn(null);

    when(transactionSimulator.process(
            any(CallParameter.class),
            any(), // TransactionValidationParams
            any(OperationTracer.class),
            any(), // PreCloseStateHandler
            any(BlockHeader.class)))
        .thenReturn(Optional.of(mockResult));

    // Create JSON-RPC request
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eth_estimateGas", new Object[] {callParameter}));

    // Execute
    final JsonRpcResponse response = ethEstimateGas.response(request);

    // Verify
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isNotNull();

    final String gasHex = (String) successResponse.getResult();
    final long gasEstimate = Long.decode(gasHex);
    assertThat(gasEstimate).isGreaterThanOrEqualTo(33500L);
  }

  @Test
  public void shouldEstimateGasForEIP7702TransactionWithMultipleAuthorizations() {
    // Create multiple authorizations
    final Address delegate1 = Address.fromHexString("0x1234567890123456789012345678901234567890");
    final Address delegate2 = Address.fromHexString("0x2345678901234567890123456789012345678901");

    final org.hyperledger.besu.datatypes.CodeDelegation auth1 =
        org.hyperledger.besu.ethereum.core.CodeDelegation.createCodeDelegation(
            BigInteger.ONE,
            delegate1,
            "0x0",
            null,
            "0x0",
            Bytes32.random().toHexString(),
            Bytes32.random().toHexString());

    final org.hyperledger.besu.datatypes.CodeDelegation auth2 =
        org.hyperledger.besu.ethereum.core.CodeDelegation.createCodeDelegation(
            BigInteger.ONE,
            delegate2,
            "0x0",
            null,
            "0x0",
            Bytes32.random().toHexString(),
            Bytes32.random().toHexString());

    // Create CallParameter with multiple authorizations
    final CallParameter callParameter =
        ImmutableCallParameter.builder()
            .sender(Address.fromHexString("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"))
            .to(Address.fromHexString("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"))
            .value(Wei.ONE)
            .gas(21000L)
            .addCodeDelegationAuthorizations(auth1)
            .addCodeDelegationAuthorizations(auth2)
            .build();

    // Mock result
    final TransactionSimulatorResult mockResult = mock(TransactionSimulatorResult.class);
    when(mockResult.isSuccessful()).thenReturn(true);
    when(mockResult.getGasEstimate()).thenReturn(46000L);
    when(mockResult.result()).thenReturn(null);

    // Match the correct signature
    when(transactionSimulator.process(
            any(CallParameter.class),
            any(), // TransactionValidationParams
            any(OperationTracer.class),
            any(), // PreCloseStateHandler
            any(BlockHeader.class)))
        .thenReturn(Optional.of(mockResult));

    // Create request
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eth_estimateGas", new Object[] {callParameter}));

    // Execute
    final JsonRpcResponse response = ethEstimateGas.response(request);

    // Verify - should account for 2 authorizations (2 * 12500 = 25000)
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    final String gasHex = (String) ((JsonRpcSuccessResponse) response).getResult();
    final long gasEstimate = Long.decode(gasHex);
    assertThat(gasEstimate).isGreaterThanOrEqualTo(46000L);
  }

  @Test
  public void shouldHandleEIP7702TransactionWithoutExplicitGas() {
    // This test simulates the bug scenario where gas is not explicitly provided
    final org.hyperledger.besu.datatypes.CodeDelegation authorization =
        org.hyperledger.besu.ethereum.core.CodeDelegation.createCodeDelegation(
            BigInteger.ONE,
            Address.fromHexString("0x1234567890123456789012345678901234567890"),
            "0x0",
            null,
            "0x0",
            Bytes32.random().toHexString(),
            Bytes32.random().toHexString());

    // Create CallParameter WITHOUT explicit gas
    final CallParameter callParameter =
        ImmutableCallParameter.builder()
            .sender(Address.fromHexString("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"))
            .to(Address.fromHexString("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"))
            .value(Wei.ONE)
            .addCodeDelegationAuthorizations(authorization)
            .build();

    final TransactionSimulatorResult mockResult = mock(TransactionSimulatorResult.class);
    when(mockResult.isSuccessful()).thenReturn(true);
    when(mockResult.getGasEstimate()).thenReturn(33500L);
    when(mockResult.result()).thenReturn(null);

    // Match the correct signature
    when(transactionSimulator.process(
            any(CallParameter.class),
            any(), // TransactionValidationParams
            any(OperationTracer.class),
            any(), // PreCloseStateHandler
            any(BlockHeader.class)))
        .thenReturn(Optional.of(mockResult));

    // Create request
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eth_estimateGas", new Object[] {callParameter}));

    // Execute - this should NOT throw an error
    final JsonRpcResponse response = ethEstimateGas.response(request);

    // Verify success
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
  }
}
