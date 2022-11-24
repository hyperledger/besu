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

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.util.log.FramedLogMessage;
import org.hyperledger.besu.util.platform.PlatformDetector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import oshi.PlatformEnum;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

public class ConfigurationOverviewBuilder {
  private String network;
  private String dataStorage;
  private String syncMode;
  private Integer rpcPort;
  private Collection<String> rpcHttpApis;
  private Integer enginePort;
  private Collection<String> engineApis;
  private boolean isHighSpec = false;

  public ConfigurationOverviewBuilder setNetwork(final String network) {
    this.network = network;
    return this;
  }

  public ConfigurationOverviewBuilder setDataStorage(final String dataStorage) {
    this.dataStorage = dataStorage;
    return this;
  }

  public ConfigurationOverviewBuilder setSyncMode(final String syncMode) {
    this.syncMode = syncMode;
    return this;
  }

  public ConfigurationOverviewBuilder setRpcPort(final Integer rpcPort) {
    this.rpcPort = rpcPort;
    return this;
  }

  public ConfigurationOverviewBuilder setRpcHttpApis(final Collection<String> rpcHttpApis) {
    this.rpcHttpApis = rpcHttpApis;
    return this;
  }

  public ConfigurationOverviewBuilder setEnginePort(final Integer enginePort) {
    this.enginePort = enginePort;
    return this;
  }

  public ConfigurationOverviewBuilder setEngineApis(final Collection<String> engineApis) {
    this.engineApis = engineApis;
    return this;
  }

  public ConfigurationOverviewBuilder setHighSpecEnabled() {
    isHighSpec = true;
    return this;
  }

  public String build() {
    final List<String> lines = new ArrayList<>();
    lines.add("Besu " + BesuInfo.class.getPackage().getImplementationVersion());
    lines.add("");
    lines.add("Configuration:");

    if (network != null) {
      lines.add("Network: " + network);
    }

    if (dataStorage != null) {
      lines.add("Data storage: " + dataStorage);
    }

    if (syncMode != null) {
      lines.add("Sync mode: " + syncMode);
    }

    if (rpcHttpApis != null) {
      lines.add("RPC HTTP APIs: " + String.join(",", rpcHttpApis));
    }
    if (rpcPort != null) {
      lines.add("RPC HTTP port: " + rpcPort);
    }

    if (engineApis != null) {
      lines.add("Engine APIs: " + String.join(",", engineApis));
    }
    if (enginePort != null) {
      lines.add("Engine port: " + enginePort);
    }

    if (isHighSpec) {
      lines.add("High spec configuration enabled");
    }

    lines.add("");
    lines.add("Host:");

    lines.add("Java: " + PlatformDetector.getVM());
    lines.add("Maximum heap size: " + normalizeSize(Runtime.getRuntime().maxMemory()));
    lines.add("OS: " + PlatformDetector.getOS());

    if (SystemInfo.getCurrentPlatform() == PlatformEnum.LINUX) {
      final String glibcVersion = PlatformDetector.getGlibc();
      if (glibcVersion != null) {
        lines.add("glibc: " + glibcVersion);
      }
    }

    final HardwareAbstractionLayer hardwareInfo = new SystemInfo().getHardware();

    lines.add("Total memory: " + normalizeSize(hardwareInfo.getMemory().getTotal()));
    lines.add("CPU cores: " + hardwareInfo.getProcessor().getLogicalProcessorCount());

    return FramedLogMessage.generate(lines);
  }

  private String normalizeSize(final long size) {
    return String.format("%.02f", (double) (size) / 1024 / 1024 / 1024) + " GB";
  }
}
