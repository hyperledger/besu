/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.tests.acceptance.dsl.ethsigner.testutil;

import tech.pegasys.ethsigner.core.Config;
import tech.pegasys.ethsigner.core.signing.ChainIdProvider;

import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.logging.log4j.Level;

public class EthSignerConfig implements Config {
  private final Level logLevel;
  private final InetAddress downstreamHttpHost;
  private final Integer downStreamHttpPort;
  private Duration downstreamHttpRequestTimeout;
  private final InetAddress httpListenHost;
  private final Integer httpListenPort;
  private final ChainIdProvider chainId;
  private final Path dataDirectory;

  public EthSignerConfig(
      final Level logLevel,
      final InetAddress downstreamHttpHost,
      final Integer downStreamHttpPort,
      final Duration downstreamHttpRequestTimeout,
      final InetAddress httpListenHost,
      final Integer httpListenPort,
      final ChainIdProvider chainId,
      final Path dataDirectory) {

    this.logLevel = logLevel;
    this.downstreamHttpHost = downstreamHttpHost;
    this.downStreamHttpPort = downStreamHttpPort;
    this.downstreamHttpRequestTimeout = downstreamHttpRequestTimeout;
    this.httpListenHost = httpListenHost;
    this.httpListenPort = httpListenPort;
    this.chainId = chainId;
    this.dataDirectory = dataDirectory;
  }

  @Override
  public Level getLogLevel() {
    return logLevel;
  }

  @Override
  public InetAddress getDownstreamHttpHost() {
    return downstreamHttpHost;
  }

  @Override
  public Integer getDownstreamHttpPort() {
    return downStreamHttpPort;
  }

  @Override
  public Duration getDownstreamHttpRequestTimeout() {
    return downstreamHttpRequestTimeout;
  }

  @Override
  public InetAddress getHttpListenHost() {
    return httpListenHost;
  }

  @Override
  public Integer getHttpListenPort() {
    return httpListenPort;
  }

  @Override
  public ChainIdProvider getChainId() {
    return chainId;
  }

  @Override
  public Path getDataPath() {
    return dataDirectory;
  }
}
