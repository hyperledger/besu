package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.CodeDelegation;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.ImmutableCallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EIP7702EstimateGasTest {

  @Mock
  private TransactionSimulator transactionSimulator;

  private EthEstimateGas ethEstimateGas;

  @BeforeEach
  public void setUp() {
    ethEstimateGas = new EthEstimateGas(transactionSimulator);
  }

  @Test
  public void shouldEstimateGasForEIP7702Transaction() {
    // Create a CodeDelegation (authorization)
    final Address delegateAddress = Address.fromHexString("0x1234567890123456789012345678901234567890");
    final CodeDelegation authorization = new CodeDelegation(
        BigInteger.ONE, // chainId
        delegateAddress,
        0L, // nonce
        (byte) 0, // yParity
        Bytes32.random(), // r
        Bytes32.random() // s
    );

    // Create CallParameter with authorization list
    final CallParameter callParameter = ImmutableCallParameter.builder()
        .sender(Address.fromHexString("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"))
        .to(Address.fromHexString("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"))
        .value(Wei.ONE)
        .gas(21000L)
        .addCodeDelegationAuthorizations(authorization)
        .build();

    // Mock the transaction simulator result
    final TransactionSimulatorResult mockResult = mock(TransactionSimulatorResult.class);
    when(mockResult.isSuccessful()).thenReturn(true);
    when(mockResult.getGasEstimate()).thenReturn(50000L); // Base gas + authorization gas
    
    when(transactionSimulator.process(
        any(),
        any(),
        any(OperationTracer.class),
        any()))
        .thenReturn(Optional.of(mockResult));

    // Create JSON-RPC request
    final JsonRpcRequestContext request = new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "eth_estimateGas", new Object[]{callParameter})
    );

    // Execute
    final JsonRpcResponse response = ethEstimateGas.response(request);

    // Verify
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isNotNull();
    
    // The gas estimate should include EIP-7702 costs
    // Base transaction: 21000
    // Per authorization: 12500
    // Total should be at least 33500
    final String gasHex = (String) successResponse.getResult();
    final long gasEstimate = Long.decode(gasHex);
    assertThat(gasEstimate).isGreaterThanOrEqualTo(33500L);
  }

  @Test
  public void shouldEstimateGasForEIP7702TransactionWithMultipleAuthorizations() {
    // Create multiple authorizations
    final Address delegate1 = Address.fromHexString("0x1234567890123456789012345678901234567890");
    final Address delegate2 = Address.fromHexString("0x2345678901234567890123456789012345678901");
    
    final CodeDelegation auth1 = new CodeDelegation(
        BigInteger.ONE, delegate1, 0L, (byte) 0, Bytes32.random(), Bytes32.random()
    );
    final CodeDelegation auth2 = new CodeDelegation(
        BigInteger.ONE, delegate2, 0L, (byte) 0, Bytes32.random(), Bytes32.random()
    );

    // Create CallParameter with multiple authorizations
    final CallParameter callParameter = ImmutableCallParameter.builder()
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
    when(mockResult.getGasEstimate()).thenReturn(46000L); // Base + 2 authorizations
    
    when(transactionSimulator.process(any(), any(), any(OperationTracer.class), any()))
        .thenReturn(Optional.of(mockResult));

    // Create request
    final JsonRpcRequestContext request = new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "eth_estimateGas", new Object[]{callParameter})
    );

    // Execute
    final JsonRpcResponse response = ethEstimateGas.response(request);

    // Verify - should account for 2 authorizations (2 * 12500 = 25000)
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    final String gasHex = (String) ((JsonRpcSuccessResponse) response).getResult();
    final long gasEstimate = Long.decode(gasHex);
    assertThat(gasEstimate).isGreaterThanOrEqualTo(46000L); // 21000 + 25000
  }

  @Test
  public void shouldHandleEIP7702TransactionWithoutExplicitGas() {
    // This test simulates the bug scenario where gas is not explicitly provided
    final CodeDelegation authorization = new CodeDelegation(
        BigInteger.ONE,
        Address.fromHexString("0x1234567890123456789012345678901234567890"),
        0L,
        (byte) 0,
        Bytes32.random(),
        Bytes32.random()
    );

    // Create CallParameter WITHOUT explicit gas
    final CallParameter callParameter = ImmutableCallParameter.builder()
        .sender(Address.fromHexString("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"))
        .to(Address.fromHexString("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"))
        .value(Wei.ONE)
        // No gas parameter - should be estimated
        .addCodeDelegationAuthorizations(authorization)
        .build();

    // Mock successful estimation
    final TransactionSimulatorResult mockResult = mock(TransactionSimulatorResult.class);
    when(mockResult.isSuccessful()).thenReturn(true);
    when(mockResult.getGasEstimate()).thenReturn(33500L);
    
    when(transactionSimulator.process(any(), any(), any(OperationTracer.class), any()))
        .thenReturn(Optional.of(mockResult));

    // Create request
    final JsonRpcRequestContext request = new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "eth_estimateGas", new Object[]{callParameter})
    );

    // Execute - this should NOT throw an error
    final JsonRpcResponse response = ethEstimateGas.response(request);

    // Verify success
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
  }
}