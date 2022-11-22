/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigurationOverviewBuilderTest {
  private ConfigurationOverviewBuilder builder;

  @BeforeEach
  void setUp() {
    builder = new ConfigurationOverviewBuilder();
  }

  @Test
  void setNetwork() {
    final String noNetworkSet = builder.build();
    assertThat(noNetworkSet).doesNotContain("Network:");

    builder.setNetwork("foobar");
    final String networkSet = builder.build();
    assertThat(networkSet).contains("Network: foobar");
  }

  @Test
  void setDataStorage() {
    final String noDataStorageSet = builder.build();
    assertThat(noDataStorageSet).doesNotContain("Data storage:");

    builder.setDataStorage("bonsai");
    final String dataStorageSet = builder.build();
    assertThat(dataStorageSet).contains("Data storage: bonsai");
  }

  @Test
  void setSyncMode() {
    final String noSyncModeSet = builder.build();
    assertThat(noSyncModeSet).doesNotContain("Sync mode:");

    builder.setSyncMode("fast");
    final String syncModeSet = builder.build();
    assertThat(syncModeSet).contains("Sync mode: fast");
  }

  @Test
  void setRpcPort() {
    final String noRpcPortSet = builder.build();
    assertThat(noRpcPortSet).doesNotContain("RPC HTTP port:");

    builder.setRpcPort(42);
    final String rpcPortSet = builder.build();
    assertThat(rpcPortSet).contains("RPC HTTP port: 42");
  }

  @Test
  void setRpcHttpApis() {
    final String noRpcApisSet = builder.build();
    assertThat(noRpcApisSet).doesNotContain("RPC HTTP APIs:");

    final Collection<String> rpcApis = new ArrayList<>();
    rpcApis.add("api1");
    rpcApis.add("api2");
    builder.setRpcHttpApis(rpcApis);
    final String rpcApisSet = builder.build();
    assertThat(rpcApisSet).contains("RPC HTTP APIs: api1,api2");
  }

  @Test
  void setEnginePort() {
    final String noEnginePortSet = builder.build();
    assertThat(noEnginePortSet).doesNotContain("Engine port:");

    builder.setEnginePort(42);
    final String enginePortSet = builder.build();
    assertThat(enginePortSet).contains("Engine port: 42");
  }

  @Test
  void setEngineApis() {
    final String noEngineApisSet = builder.build();
    assertThat(noEngineApisSet).doesNotContain("Engine APIs:");

    final Collection<String> engineApis = new ArrayList<>();
    engineApis.add("api1");
    engineApis.add("api2");
    builder.setEngineApis(engineApis);
    final String engineApisSet = builder.build();
    assertThat(engineApisSet).contains("Engine APIs: api1,api2");
  }

  @Test
  void setHighSpecEnabled() {
    final String highSpecNotEnabled = builder.build();
    assertThat(highSpecNotEnabled).doesNotContain("High spec configuration enabled");

    builder.setHighSpecEnabled();
    final String highSpecEnabled = builder.build();
    assertThat(highSpecEnabled).contains("High spec configuration enabled");
  }
}
