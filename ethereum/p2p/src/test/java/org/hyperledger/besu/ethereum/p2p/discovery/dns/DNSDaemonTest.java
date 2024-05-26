/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.p2p.discovery.dns;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Security;
import java.util.concurrent.TimeUnit;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.tuweni.devp2p.EthereumNodeRecord;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class DNSDaemonTest {
  private static final String holeskyEnr =
      "enrtree://AKA3AM6LPBYEUDMVNU3BSVQJ5AD45Y7YPOHJLEF6W26QOE4VTUDPE@all.holesky.ethdisco.net";
  private DNSDaemon dnsDaemon;

  @BeforeAll
  static void setup() {
    Security.addProvider(new BouncyCastleProvider());
  }

  @AfterEach
  void tearDown() {}

  @Test
  void testDNSDaemon(final Vertx vertx, final VertxTestContext testContext)
      throws InterruptedException {
    dnsDaemon =
        new DNSDaemon(
            holeskyEnr,
            (seq, records) -> {
              System.out.println("seq: " + seq);
              for (EthereumNodeRecord record : records) {
                System.out.println(record);
              }
              if (!records.isEmpty()) {
                  testContext.completeNow();
              }
            },
            0,
            0,
            null);

    DeploymentOptions options =
        new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER).setWorkerPoolSize(1);
    vertx.deployVerticle(dnsDaemon, options);

    assertThat(testContext.awaitCompletion(30, TimeUnit.SECONDS)).isTrue();
  }
}
