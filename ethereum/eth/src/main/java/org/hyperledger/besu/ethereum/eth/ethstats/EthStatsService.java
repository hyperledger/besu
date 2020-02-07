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
package org.hyperledger.besu.ethereum.eth.ethstats;

import java.math.BigInteger;
import java.net.URI;
import java.util.List;

import io.vertx.core.Vertx;
import org.apache.tuweni.ethstats.EthStatsReporter;
import org.apache.tuweni.units.bigints.UInt256;
import org.logl.log4j2.Log4j2LoggerProvider;

public class EthStatsService {

  private final EthStatsParameters ethStatsParameters;
  private final String networkInfo;
  private final BigInteger networkId;
  private EthStatsReporter reporter;

  public EthStatsService(
      final EthStatsParameters ethStatsParameters,
      final BigInteger networkId,
      final String networkInfo) {
    this.ethStatsParameters = ethStatsParameters;
    this.networkId = networkId;
    this.networkInfo = networkInfo;
  }

  public void start(final Vertx vertx) {
    Log4j2LoggerProvider provider = new Log4j2LoggerProvider();
    URI ethStatsURI =
        URI.create(
            "ws://" + ethStatsParameters.getHost() + ":" + ethStatsParameters.getPort() + "/api");
    this.reporter =
        new EthStatsReporter(
            vertx,
            provider.getLogger("ethstats"),
            ethStatsURI,
            ethStatsParameters.getSecret(),
            "Hyperledger Besu",
            ethStatsParameters.getNode(),
            ethStatsParameters.getPort(),
            networkId.toString(),
            networkInfo,
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            this::requestHistory);
    reporter.start();
  }

  private void requestHistory(final List<UInt256> blockNumbers) {}

  public void stop() {
    reporter.stop();
  }
}
