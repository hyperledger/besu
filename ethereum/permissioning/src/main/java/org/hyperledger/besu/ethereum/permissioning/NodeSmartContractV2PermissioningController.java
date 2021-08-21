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

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.ImmutableEnodeDnsConfiguration;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;

import com.google.common.net.InetAddresses;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;

/**
 * Controller that can read from a smart contract that exposes the EEA node permissioning v2 call
 * connectionAllowed(string,string,uint16)
 */
public class NodeSmartContractV2PermissioningController
    extends AbstractNodeSmartContractPermissioningController {

  public static final Bytes TRUE_RESPONSE = Bytes.fromHexString(TypeEncoder.encode(new Bool(true)));
  public static final Bytes FALSE_RESPONSE =
      Bytes.fromHexString(TypeEncoder.encode(new Bool(false)));

  public NodeSmartContractV2PermissioningController(
      final Address contractAddress,
      final TransactionSimulator transactionSimulator,
      final MetricsSystem metricsSystem) {
    super(contractAddress, transactionSimulator, metricsSystem);
  }

  @Override
  boolean checkSmartContractRules(final EnodeURL sourceEnode, final EnodeURL destinationEnode) {
    return isPermitted(sourceEnode) && isPermitted(destinationEnode);
  }

  private boolean isPermitted(final EnodeURL enode) {
    return checkProvidedURL(enode) || checkAlternateURL(enode);
  }

  private boolean checkProvidedURL(final EnodeURL enode) {
    return getCallResult(enode);
  }

  private boolean checkAlternateURL(final EnodeURL enode) {
    return isEnodeHostIPAddress(enode.toURI().getHost())
        ? getCallResult(ipToDNS(enode))
        : getCallResult(dnsToIp(enode));
  }

  @NotNull
  private Boolean getCallResult(final EnodeURL enode) {
    return transactionSimulator
        .processAtHead(buildCallParameters(createPayload(enode)))
        .map(this::parseResult)
        .orElse(false);
  }

  private boolean isEnodeHostIPAddress(final String enodeHost) {
    return InetAddresses.isUriInetAddress(enodeHost) || InetAddresses.isInetAddress(enodeHost);
  }

  private EnodeURL dnsToIp(final EnodeURL enodeURL) {
    return EnodeURLImpl.builder().configureFromEnode(enodeURL).build();
  }

  private EnodeURL ipToDNS(final EnodeURL enodeURL) {
    final String dnsHost = InetAddresses.forString(enodeURL.getIpAsString()).getHostName();
    final ImmutableEnodeDnsConfiguration dnsConfig =
        ImmutableEnodeDnsConfiguration.builder().dnsEnabled(true).updateEnabled(true).build();
    return EnodeURLImpl.builder()
        .configureFromEnode(enodeURL)
        .ipAddress(dnsHost, dnsConfig)
        .build();
  }

  private Bytes createPayload(final EnodeURL enodeUrl) {
    try {
      final String hexNodeIdString = enodeUrl.getNodeId().toUnprefixedHexString();
      final String address = enodeUrl.toURI().getHost();
      final int port = enodeUrl.getListeningPortOrZero();

      final Function connectionAllowedFunction =
          FunctionEncoder.makeFunction(
              "connectionAllowed",
              List.of("string", "string", "uint16"),
              List.of(hexNodeIdString, address, port),
              List.of(Bool.TYPE_NAME));
      return Bytes.fromHexString(FunctionEncoder.encode(connectionAllowedFunction));
    } catch (Exception e) {
      throw new RuntimeException(
          "Error building payload to call node permissioning smart contract", e);
    }
  }

  private boolean parseResult(final TransactionSimulatorResult result) {
    switch (result.getResult().getStatus()) {
      case INVALID:
        throw new IllegalStateException("Invalid node permissioning smart contract call");
      case FAILED:
        throw new IllegalStateException("Failed node permissioning smart contract call");
      default:
        break;
    }

    if (result.getOutput().equals(TRUE_RESPONSE)) {
      return true;
    } else if (result.getOutput().equals(FALSE_RESPONSE)) {
      return false;
    } else {
      throw new IllegalStateException("Unexpected result from node permissioning smart contract");
    }
  }
}
