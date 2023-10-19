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
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.services.BesuPluginContextImpl;
import org.hyperledger.besu.util.log.FramedLogMessage;
import org.hyperledger.besu.util.platform.PlatformDetector;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import oshi.PlatformEnum;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

/** The Configuration overview builder. */
public class ConfigurationOverviewBuilder {
  @SuppressWarnings("PrivateStaticFinalLoggers")
  private final Logger logger;

  private String network;
  private BigInteger networkId;
  private boolean hasCustomGenesis;
  private String dataStorage;
  private String syncMode;
  private Integer rpcPort;
  private Collection<String> rpcHttpApis;
  private Integer enginePort;
  private Collection<String> engineApis;
  private String engineJwtFilePath;
  private boolean isHighSpec = false;
  private TransactionPoolConfiguration.Implementation txPoolImplementation;
  private Map<String, String> environment;
  private BesuPluginContextImpl besuPluginContext;

  /**
   * @param logger the logger
   */
  public ConfigurationOverviewBuilder(final Logger logger) {
    this.logger = logger;
  }

  /**
   * Sets network.
   *
   * @param network the network
   * @return the network
   */
  public ConfigurationOverviewBuilder setNetwork(final String network) {
    this.network = network;
    return this;
  }

  /**
   * Sets whether a networkId has been specified
   *
   * @param networkId the specified networkId
   * @return the builder
   */
  public ConfigurationOverviewBuilder setNetworkId(final BigInteger networkId) {
    this.networkId = networkId;
    return this;
  }

  /**
   * Sets whether a custom genesis has been specified.
   *
   * @param hasCustomGenesis a boolean representing whether a custom genesis file was specified
   * @return the builder
   */
  public ConfigurationOverviewBuilder setHasCustomGenesis(final boolean hasCustomGenesis) {
    this.hasCustomGenesis = hasCustomGenesis;
    return this;
  }

  /**
   * Sets data storage.
   *
   * @param dataStorage the data storage
   * @return the builder
   */
  public ConfigurationOverviewBuilder setDataStorage(final String dataStorage) {
    this.dataStorage = dataStorage;
    return this;
  }

  /**
   * Sets sync mode.
   *
   * @param syncMode the sync mode
   * @return the builder
   */
  public ConfigurationOverviewBuilder setSyncMode(final String syncMode) {
    this.syncMode = syncMode;
    return this;
  }

  /**
   * Sets rpc port.
   *
   * @param rpcPort the rpc port
   * @return the builder
   */
  public ConfigurationOverviewBuilder setRpcPort(final Integer rpcPort) {
    this.rpcPort = rpcPort;
    return this;
  }

  /**
   * Sets rpc http apis.
   *
   * @param rpcHttpApis the rpc http apis
   * @return the builder
   */
  public ConfigurationOverviewBuilder setRpcHttpApis(final Collection<String> rpcHttpApis) {
    this.rpcHttpApis = rpcHttpApis;
    return this;
  }

  /**
   * Sets engine port.
   *
   * @param enginePort the engine port
   * @return the builder
   */
  public ConfigurationOverviewBuilder setEnginePort(final Integer enginePort) {
    this.enginePort = enginePort;
    return this;
  }

  /**
   * Sets engine apis.
   *
   * @param engineApis the engine apis
   * @return the builder
   */
  public ConfigurationOverviewBuilder setEngineApis(final Collection<String> engineApis) {
    this.engineApis = engineApis;
    return this;
  }

  /**
   * Sets high spec enabled.
   *
   * @return the builder
   */
  public ConfigurationOverviewBuilder setHighSpecEnabled() {
    isHighSpec = true;
    return this;
  }

  /**
   * Sets the txpool implementation in use.
   *
   * @param implementation the txpool implementation
   * @return the builder
   */
  public ConfigurationOverviewBuilder setTxPoolImplementation(
      final TransactionPoolConfiguration.Implementation implementation) {
    txPoolImplementation = implementation;
    return this;
  }

  /**
   * Sets the engine jwt file path.
   *
   * @param engineJwtFilePath the engine apis
   * @return the builder
   */
  public ConfigurationOverviewBuilder setEngineJwtFile(final String engineJwtFilePath) {
    this.engineJwtFilePath = engineJwtFilePath;
    return this;
  }

  /**
   * Sets the environment variables.
   *
   * @param environment the environment variables
   * @return the builder
   */
  public ConfigurationOverviewBuilder setEnvironment(final Map<String, String> environment) {
    this.environment = environment;
    return this;
  }

  /**
   * Build configuration overview.
   *
   * @return the string representing configuration overview
   */
  public String build() {
    final List<String> lines = new ArrayList<>();
    lines.add("Besu version " + BesuInfo.class.getPackage().getImplementationVersion());
    lines.add("");
    lines.add("Configuration:");

    // Don't include the default network if a genesis file has been supplied
    if (network != null && !hasCustomGenesis) {
      lines.add("Network: " + network);
    }

    if (hasCustomGenesis) {
      lines.add("Network: Custom genesis file specified");
    }

    if (networkId != null) {
      lines.add("Network Id: " + networkId);
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
    if (engineJwtFilePath != null) {
      lines.add("Engine JWT: " + engineJwtFilePath);
    }

    if (isHighSpec) {
      lines.add("Experimental high spec configuration enabled");
    }

    lines.add("Using " + txPoolImplementation + " transaction pool implementation");

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

      detectJemalloc(lines);
    }

    final HardwareAbstractionLayer hardwareInfo = new SystemInfo().getHardware();

    lines.add("Total memory: " + normalizeSize(hardwareInfo.getMemory().getTotal()));
    lines.add("CPU cores: " + hardwareInfo.getProcessor().getLogicalProcessorCount());

    lines.add("");
    lines.addAll(getBesuPluginContext().getPluginsSummaryLog());

    return FramedLogMessage.generate(lines);
  }

  private void detectJemalloc(final List<String> lines) {
    Optional.ofNullable(Objects.isNull(environment) ? null : environment.get("BESU_USING_JEMALLOC"))
        .ifPresentOrElse(
            t -> {
              try {
                final String version = PlatformDetector.getJemalloc();
                lines.add("jemalloc: " + version);
              } catch (final Throwable throwable) {
                logger.warn(
                    "BESU_USING_JEMALLOC is present but we failed to load jemalloc library to get the version",
                    throwable);
              }
            },
            () -> {
              // in case the user is using jemalloc without BESU_USING_JEMALLOC env var
              try {
                final String version = PlatformDetector.getJemalloc();
                lines.add("jemalloc: " + version);
              } catch (final Throwable throwable) {
                logger.info(
                    "jemalloc library not found, memory usage may be reduced by installing it");
              }
            });
  }

  private String normalizeSize(final long size) {
    return String.format("%.02f", (double) (size) / 1024 / 1024 / 1024) + " GB";
  }

  private BesuPluginContextImpl getBesuPluginContext() {
    return besuPluginContext;
  }

  /**
   * set the plugin context
   *
   * @param besuPluginContext the plugin context
   */
  public void setPluginContext(final BesuPluginContextImpl besuPluginContext) {
    this.besuPluginContext = besuPluginContext;
  }
}
