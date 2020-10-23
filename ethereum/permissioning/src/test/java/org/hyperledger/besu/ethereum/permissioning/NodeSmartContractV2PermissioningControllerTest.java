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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor.Result;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class NodeSmartContractV2PermissioningControllerTest {

  /*
   Payloads created using Remix to call method connectionAllowed(string, bytes16, uint16)
  */
  private static final Bytes SOURCE_ENODE_EXPECTED_PAYLOAD =
      Bytes.fromHexString(
          "0x4df2430d000000000000000000000000000000000000000000000000000000000000006000000000000000000000ffff7f00000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000765f00000000000000000000000000000000000000000000000000000000000000806663626539663833323138343837623363306235303837383139333838306536633235636664383637303863306130626630636139316630636536333337343661383932666532343061666135623961383830623862636134386538613232373034656639333766646461326437636336336534643431656431623431376165");
  private static final Bytes DESTINATION_ENODE_EXPECTED_PAYLOAD =
      Bytes.fromHexString(
          "0x4df2430d000000000000000000000000000000000000000000000000000000000000006000000000000000000000ffff7f000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000009dd400000000000000000000000000000000000000000000000000000000000000803335343863383762393932306666313661613462646366303163383566323531313761323961653135373464373539626164343863633934363364386539663763336331643165396662306432386537333839383935316639306530323731346162623737306664366432326539303337313838326134353635383830306539");

  private static final EnodeURL SOURCE_ENODE_IPV4 =
      EnodeURL.fromString(
          "enode://fcbe9f83218487b3c0b50878193880e6c25cfd86708c0a0bf0ca91f0ce633746a892fe240afa5b9a880b8bca48e8a22704ef937fdda2d7cc63e4d41ed1b417ae@127.0.0.1:30303");
  private static final EnodeURL DESTINATION_ENODE_IPV4 =
      EnodeURL.fromString(
          "enode://3548c87b9920ff16aa4bdcf01c85f25117a29ae1574d759bad48cc9463d8e9f7c3c1d1e9fb0d28e73898951f90e02714abb770fd6d22e90371882a45658800e9@127.0.0.1:40404");
  private static final EnodeURL SOURCE_ENODE_IPV6 =
      EnodeURL.fromString(
          "enode://fcbe9f83218487b3c0b50878193880e6c25cfd86708c0a0bf0ca91f0ce633746a892fe240afa5b9a880b8bca48e8a22704ef937fdda2d7cc63e4d41ed1b417ae@[::ffff:7f00:0001]:30303");
  private static final EnodeURL DESTINATION_ENODE_IPV6 =
      EnodeURL.fromString(
          "enode://3548c87b9920ff16aa4bdcf01c85f25117a29ae1574d759bad48cc9463d8e9f7c3c1d1e9fb0d28e73898951f90e02714abb770fd6d22e90371882a45658800e9@[::ffff:7f00:0001]:40404");

  private final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
  private final Address contractAddress = Address.ZERO;
  private final TransactionSimulator transactionSimulator = mock(TransactionSimulator.class);
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  private NodeSmartContractV2PermissioningController permissioningController;

  @Before
  public void beforeEach() {
    permissioningController =
        new NodeSmartContractV2PermissioningController(
            contractAddress, transactionSimulator, metricsSystem);
  }

  @Test
  public void nonExpectedCallOutputThrowsIllegalState() {
    final TransactionSimulatorResult txSimulatorResult =
        transactionSimulatorResult(Bytes.random(10), ValidationResult.valid());

    when(transactionSimulator.processAtHead(eq(callParams(SOURCE_ENODE_EXPECTED_PAYLOAD))))
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

    when(transactionSimulator.processAtHead(eq(callParams(SOURCE_ENODE_EXPECTED_PAYLOAD))))
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

    when(transactionSimulator.processAtHead(eq(callParams(SOURCE_ENODE_EXPECTED_PAYLOAD))))
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

    when(transactionSimulator.processAtHead(eq(callParams(SOURCE_ENODE_EXPECTED_PAYLOAD))))
        .thenReturn(Optional.of(txSimulatorResult));
    when(transactionSimulator.processAtHead(eq(callParams(DESTINATION_ENODE_EXPECTED_PAYLOAD))))
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

    when(transactionSimulator.processAtHead(eq(callParams(SOURCE_ENODE_EXPECTED_PAYLOAD))))
        .thenReturn(Optional.of(txSimulatorResult));
    when(transactionSimulator.processAtHead(eq(callParams(DESTINATION_ENODE_EXPECTED_PAYLOAD))))
        .thenReturn(Optional.of(txSimulatorResult));

    boolean isPermitted =
        permissioningController.checkSmartContractRules(SOURCE_ENODE_IPV6, DESTINATION_ENODE_IPV6);
    assertThat(isPermitted).isTrue();

    verify(transactionSimulator, times(2)).processAtHead(any());
  }

  private CallParameter callParams(final Bytes payload) {
    return new CallParameter(null, contractAddress, -1, null, null, payload);
  }

  private TransactionSimulatorResult transactionSimulatorResult(
      final Bytes output, final ValidationResult<TransactionInvalidReason> validationResult) {
    return new TransactionSimulatorResult(
        blockDataGenerator.transaction(),
        Result.successful(blockDataGenerator.logs(1, 1), 0L, 0L, output, validationResult));
  }
}
