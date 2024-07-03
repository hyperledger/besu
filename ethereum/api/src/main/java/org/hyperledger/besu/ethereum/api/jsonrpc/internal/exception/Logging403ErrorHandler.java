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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Logging403ErrorHandler implements Handler<RoutingContext> {

  private static final Logger LOG = LoggerFactory.getLogger(Logging403ErrorHandler.class);

  @Override
  public void handle(final RoutingContext event) {
    LOG.error(event.failure().getMessage());
    LOG.debug(event.failure().getMessage(), event.failure());
    int statusCode = event.statusCode();

    HttpServerResponse response = event.response();
    response.setStatusCode(statusCode).end("Exception thrown handling RPC");
  }
}
