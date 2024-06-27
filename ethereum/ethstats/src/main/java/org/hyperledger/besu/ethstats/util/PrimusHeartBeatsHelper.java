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
package org.hyperledger.besu.ethstats.util;

import java.util.regex.Pattern;

import io.vertx.core.http.WebSocket;

/** This class provides helper methods for handling Primus heartbeats. */
public final class PrimusHeartBeatsHelper {

  /** The constant PRIMUS_PING_REGEX. */
  public static final Pattern PRIMUS_PING_REGEX = Pattern.compile("primus::ping::([\\d]+)");

  private PrimusHeartBeatsHelper() {}

  /**
   * Checks if the given request is a heartbeat request.
   *
   * @param request the request to check
   * @return true if the request is a heartbeat request, false otherwise
   */
  public static boolean isHeartBeatsRequest(final String request) {
    return PRIMUS_PING_REGEX.matcher(request).find();
  }

  /**
   * Sends a heartbeat response through the given WebSocket.
   *
   * @param webSocket the WebSocket to send the response through
   */
  public static void sendHeartBeatsResponse(final WebSocket webSocket) {
    if (webSocket != null) {
      webSocket.writeTextMessage(String.format("\"primus::pong::%d\"", System.currentTimeMillis()));
    }
  }
}
