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
package org.hyperledger.besu.enclave;

import java.net.URI;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;

public class EnclaveFactory {

  private final Vertx vertx;
  private static final int CONNECT_TIMEOUT = 1000;

  public EnclaveFactory(final Vertx vertx) {
    this.vertx = vertx;
  }

  public Enclave createVertxEnclave(final URI enclaveUri) {
    if (enclaveUri.getPort() == -1) {
      throw new EnclaveException("Illegal URI - no port specified");
    }

    final HttpClientOptions clientOptions = new HttpClientOptions();
    clientOptions.setDefaultHost(enclaveUri.getHost());
    clientOptions.setDefaultPort(enclaveUri.getPort());
    clientOptions.setConnectTimeout(CONNECT_TIMEOUT);

    final RequestTransmitter vertxTransmitter =
        new VertxRequestTransmitter(vertx.createHttpClient(clientOptions));

    return new Enclave(vertxTransmitter);
  }
}
