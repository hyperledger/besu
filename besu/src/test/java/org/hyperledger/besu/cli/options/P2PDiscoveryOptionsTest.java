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
package org.hyperledger.besu.cli.options;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.cli.CommandTestAbstract;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class P2PDiscoveryOptionsTest extends CommandTestAbstract {

  @Test
  public void p2pHostAndPortOptionsAreRespectedAndNotLeakedWithMetricsEnabled() {

    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand("--p2p-host", host, "--p2p-port", String.valueOf(port), "--metrics-enabled");

    verify(mockRunnerBuilder).p2pAdvertisedHost(stringArgumentCaptor.capture());
    verify(mockRunnerBuilder).p2pListenPort(intArgumentCaptor.capture());
    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(stringArgumentCaptor.getValue()).isEqualTo(host);
    assertThat(intArgumentCaptor.getValue()).isEqualTo(port);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    // all other port values remain default
    assertThat(metricsConfigArgumentCaptor.getValue().getPort()).isEqualTo(9545);
    assertThat(metricsConfigArgumentCaptor.getValue().getPushPort()).isEqualTo(9001);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getPort()).isEqualTo(8545);

    // all other host values remain default
    final String defaultHost = "127.0.0.1";
    assertThat(metricsConfigArgumentCaptor.getValue().getHost()).isEqualTo(defaultHost);
    assertThat(metricsConfigArgumentCaptor.getValue().getPushHost()).isEqualTo(defaultHost);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHost()).isEqualTo(defaultHost);
  }

  @Test
  public void p2pInterfaceOptionIsRespected() {

    final String ip = "1.2.3.4";
    parseCommand("--p2p-interface", ip);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(mockRunnerBuilder).p2pListenInterface(stringArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(stringArgumentCaptor.getValue()).isEqualTo(ip);
  }

  @ParameterizedTest
  @ValueSource(strings = {"localhost", " localhost.localdomain", "invalid-host"})
  public void p2pHostMustBeAnIPAddress(final String host) {
    parseCommand("--p2p-host", host);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    String errorMessage = "The provided --p2p-host is invalid: " + host;
    assertThat(commandErrorOutput.toString(UTF_8)).contains(errorMessage);
  }

  @Test
  public void p2pHostMayBeIPv6() {

    final String host = "2600:DB8::8545";
    parseCommand("--p2p-host", host);

    verify(mockRunnerBuilder).p2pAdvertisedHost(stringArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(stringArgumentCaptor.getValue()).isEqualTo(host);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void p2pHostAndPortOptionsAreRespectedAndNotLeaked() {

    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand("--p2p-host", host, "--p2p-port", String.valueOf(port));

    verify(mockRunnerBuilder).p2pAdvertisedHost(stringArgumentCaptor.capture());
    verify(mockRunnerBuilder).p2pListenPort(intArgumentCaptor.capture());
    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(stringArgumentCaptor.getValue()).isEqualTo(host);
    assertThat(intArgumentCaptor.getValue()).isEqualTo(port);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    // all other port values remain default
    assertThat(metricsConfigArgumentCaptor.getValue().getPort()).isEqualTo(9545);
    assertThat(metricsConfigArgumentCaptor.getValue().getPushPort()).isEqualTo(9001);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getPort()).isEqualTo(8545);

    // all other host values remain default
    final String defaultHost = "127.0.0.1";
    assertThat(metricsConfigArgumentCaptor.getValue().getHost()).isEqualTo(defaultHost);
    assertThat(metricsConfigArgumentCaptor.getValue().getPushHost()).isEqualTo(defaultHost);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHost()).isEqualTo(defaultHost);
  }
}
