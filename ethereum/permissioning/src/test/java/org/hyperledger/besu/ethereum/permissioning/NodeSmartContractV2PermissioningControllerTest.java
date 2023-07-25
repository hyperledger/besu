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
package org.hyperledger.besu.ethereum.permissioning;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NodeSmartContractV2PermissioningControllerTest {

  /*
   Payloads created using Remix to call method connectionAllowed(string, string, uint16)
  */
  private static final Bytes SOURCE_ENODE_EXPECTED_PAYLOAD_IP =
      Bytes.fromHexString(
          "0x45a59e5b00000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000765f0000000000000000000000000000000000000000000000000000000000000080666362653966383332313834383762336330623530383738313933383830653663323563666438363730386330613062663063613931663063653633333734366138393266653234306166613562396138383062386263613438653861323237303465663933376664646132643763633633653464343165643162343137616500000000000000000000000000000000000000000000000000000000000000093132372e302e302e310000000000000000000000000000000000000000000000");
  private static final Bytes DESTINATION_ENODE_EXPECTED_PAYLOAD_IP =
      Bytes.fromHexString(
          "0x45a59e5b000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000009dd40000000000000000000000000000000000000000000000000000000000000080333534386338376239393230666631366161346264636630316338356632353131376132396165313537346437353962616434386363393436336438653966376333633164316539666230643238653733383938393531663930653032373134616262373730666436643232653930333731383832613435363538383030653900000000000000000000000000000000000000000000000000000000000000093132372e302e302e310000000000000000000000000000000000000000000000");

  private static final EnodeURL SOURCE_ENODE_IPV4 =
      EnodeURLImpl.fromString(
          "enode://fcbe9f83218487b3c0b50878193880e6c25cfd86708c0a0bf0ca91f0ce633746a892fe240afa5b9a880b8bca48e8a22704ef937fdda2d7cc63e4d41ed1b417ae@127.0.0.1:30303");
  private static final EnodeURL DESTINATION_ENODE_IPV4 =
      EnodeURLImpl.fromString(
          "enode://3548c87b9920ff16aa4bdcf01c85f25117a29ae1574d759bad48cc9463d8e9f7c3c1d1e9fb0d28e73898951f90e02714abb770fd6d22e90371882a45658800e9@127.0.0.1:40404");
  private static final EnodeURL SOURCE_ENODE_IPV6 =
      EnodeURLImpl.fromString(
          "enode://fcbe9f83218487b3c0b50878193880e6c25cfd86708c0a0bf0ca91f0ce633746a892fe240afa5b9a880b8bca48e8a22704ef937fdda2d7cc63e4d41ed1b417ae@[::ffff:7f00:0001]:30303");
  private static final EnodeURL DESTINATION_ENODE_IPV6 =
      EnodeURLImpl.fromString(
          "enode://3548c87b9920ff16aa4bdcf01c85f25117a29ae1574d759bad48cc9463d8e9f7c3c1d1e9fb0d28e73898951f90e02714abb770fd6d22e90371882a45658800e9@[::ffff:7f00:0001]:40404");

  private final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
  private final Address contractAddress = Address.ZERO;
  private final TransactionSimulator transactionSimulator = mock(TransactionSimulator.class);
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  private NodeSmartContractV2PermissioningController permissioningController;

  @BeforeEach
  public void beforeEach() {
    permissioningController =
        new NodeSmartContractV2PermissioningController(
            contractAddress, transactionSimulator, metricsSystem);
  }

  @Test
  public void nonExpectedCallOutputThrowsIllegalState() {
    final TransactionSimulatorResult txSimulatorResult =
        transactionSimulatorResult(Bytes.random(10), ValidationResult.valid());

    when(transactionSimulator.processAtHead(eq(callParams(SOURCE_ENODE_EXPECTED_PAYLOAD_IP))))
        .thenReturn(Optional.of(txSimulatorResult));

    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                permissioningController.checkSmartContractRules(
                    SOURCE_ENODE_IPV4, DESTINATION_ENODE_IPV4));
  }

  @Test
  public void falseCallOutputReturnsNotPermitted() {
    final TransactionSimulatorResult txSimulatorResult =
        transactionSimulatorResult(
            NodeSmartContractV2PermissioningController.FALSE_RESPONSE, ValidationResult.valid());

    when(transactionSimulator.processAtHead(eq(callParams(SOURCE_ENODE_EXPECTED_PAYLOAD_IP))))
        .thenReturn(Optional.of(txSimulatorResult));

    boolean isPermitted =
        permissioningController.checkSmartContractRules(SOURCE_ENODE_IPV4, DESTINATION_ENODE_IPV4);
    assertThat(isPermitted).isFalse();
  }

  @Test
  public void failedCallOutputReturnsNotPermitted() {
    final TransactionSimulatorResult txSimulatorResult =
        transactionSimulatorResult(
            NodeSmartContractV2PermissioningController.TRUE_RESPONSE,
            ValidationResult.invalid(TransactionInvalidReason.INTERNAL_ERROR));

    when(transactionSimulator.processAtHead(eq(callParams(SOURCE_ENODE_EXPECTED_PAYLOAD_IP))))
        .thenReturn(Optional.of(txSimulatorResult));

    boolean isPermitted =
        permissioningController.checkSmartContractRules(SOURCE_ENODE_IPV4, DESTINATION_ENODE_IPV4);
    assertThat(isPermitted).isFalse();
  }

  @Test
  public void expectedPayloadWhenCheckingPermissioningWithIPV4() {
    final TransactionSimulatorResult txSimulatorResult =
        transactionSimulatorResult(
            NodeSmartContractV2PermissioningController.TRUE_RESPONSE, ValidationResult.valid());

    when(transactionSimulator.processAtHead(eq(callParams(SOURCE_ENODE_EXPECTED_PAYLOAD_IP))))
        .thenReturn(Optional.of(txSimulatorResult));
    when(transactionSimulator.processAtHead(eq(callParams(DESTINATION_ENODE_EXPECTED_PAYLOAD_IP))))
        .thenReturn(Optional.of(txSimulatorResult));

    boolean isPermitted =
        permissioningController.checkSmartContractRules(SOURCE_ENODE_IPV4, DESTINATION_ENODE_IPV4);
    assertThat(isPermitted).isTrue();

    verify(transactionSimulator, times(2)).processAtHead(any());
  }

  @Test
  public void expectedPayloadWhenCheckingPermissioningWithIPV6() {
    final TransactionSimulatorResult txSimulatorResult =
        transactionSimulatorResult(
            NodeSmartContractV2PermissioningController.TRUE_RESPONSE, ValidationResult.valid());

    when(transactionSimulator.processAtHead(eq(callParams(SOURCE_ENODE_EXPECTED_PAYLOAD_IP))))
        .thenReturn(Optional.of(txSimulatorResult));
    when(transactionSimulator.processAtHead(eq(callParams(DESTINATION_ENODE_EXPECTED_PAYLOAD_IP))))
        .thenReturn(Optional.of(txSimulatorResult));

    boolean isPermitted =
        permissioningController.checkSmartContractRules(SOURCE_ENODE_IPV6, DESTINATION_ENODE_IPV6);
    assertThat(isPermitted).isTrue();

    verify(transactionSimulator, times(2)).processAtHead(any());
  }

  @Test
  public void expectedPayloadWhenCheckingPermissioningWithAlternateDNS()
      throws UnknownHostException {
    final TransactionSimulatorResult txSimulatorResultFalse =
        transactionSimulatorResult(
            NodeSmartContractV2PermissioningController.FALSE_RESPONSE, ValidationResult.valid());

    final TransactionSimulatorResult txSimulatorResultTrue =
        transactionSimulatorResult(
            NodeSmartContractV2PermissioningController.TRUE_RESPONSE, ValidationResult.valid());

    when(transactionSimulator.processAtHead(eq(callParams(SOURCE_ENODE_EXPECTED_PAYLOAD_IP))))
        .thenReturn(Optional.of(txSimulatorResultFalse));
    when(transactionSimulator.processAtHead(eq(callParams(DESTINATION_ENODE_EXPECTED_PAYLOAD_IP))))
        .thenReturn(Optional.of(txSimulatorResultFalse));

    var sourcePayload = sourceEnodeExpectedPayloadDns();
    when(transactionSimulator.processAtHead(eq(callParams(Bytes.fromHexString(sourcePayload)))))
        .thenReturn(Optional.of(txSimulatorResultTrue));
    var destinationPayload = destinationEnodeExpectedPayloadDns();
    when(transactionSimulator.processAtHead(
            eq(callParams(Bytes.fromHexString(destinationPayload)))))
        .thenReturn(Optional.of(txSimulatorResultTrue));

    boolean isPermitted =
        permissioningController.checkSmartContractRules(SOURCE_ENODE_IPV4, DESTINATION_ENODE_IPV4);
    assertThat(isPermitted).isTrue();

    verify(transactionSimulator, times(4)).processAtHead(any());
  }

  private CallParameter callParams(final Bytes payload) {
    return new CallParameter(null, contractAddress, -1, null, null, payload);
  }

  private TransactionSimulatorResult transactionSimulatorResult(
      final Bytes output, final ValidationResult<TransactionInvalidReason> validationResult) {
    return new TransactionSimulatorResult(
        blockDataGenerator.transaction(),
        TransactionProcessingResult.successful(
            blockDataGenerator.logs(1, 1), 0L, 0L, output, validationResult));
  }

  // Note - don't use FunctionEncoder.makeFunction here otherwise we're not really testing anything!
  private String sourceEnodeExpectedPayloadDns() throws UnknownHostException {
    var hostname = InetAddress.getLocalHost().getHostName();
    return "0x45a59e5b00000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000765f0000000000000000000000000000000000000000000000000000000000000080666362653966383332313834383762336330623530383738313933383830653663323563666438363730386330613062663063613931663063653633333734366138393266653234306166613562396138383062386263613438653861323237303465663933376664646132643763633633653464343165643162343137616500000000000000000000000000000000000000000000000000000000000000"
        + Bytes.of(hostname.length()).toUnprefixedHexString()
        + Bytes.of(hostname.getBytes(UTF_8)).toUnprefixedHexString()
        + "00".repeat(32 - hostname.length());
  }

  private String destinationEnodeExpectedPayloadDns() throws UnknownHostException {
    var hostname = InetAddress.getLocalHost().getHostName();
    return "0x45a59e5b000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000009dd40000000000000000000000000000000000000000000000000000000000000080333534386338376239393230666631366161346264636630316338356632353131376132396165313537346437353962616434386363393436336438653966376333633164316539666230643238653733383938393531663930653032373134616262373730666436643232653930333731383832613435363538383030653900000000000000000000000000000000000000000000000000000000000000"
        + Bytes.of(hostname.length()).toUnprefixedHexString()
        + Bytes.of(hostname.getBytes(UTF_8)).toUnprefixedHexString()
        + "00".repeat(32 - hostname.length());
  }
}
