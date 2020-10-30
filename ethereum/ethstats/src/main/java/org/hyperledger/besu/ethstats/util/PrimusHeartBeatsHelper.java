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

public final class PrimusHeartBeatsHelper {

  public static final Pattern PRIMUS_PING_REGEX = Pattern.compile("primus::ping::([\\d]+)");

  public static boolean isHeartBeatsRequest(final String request) {
    return PRIMUS_PING_REGEX.matcher(request).find();
  }

  public static void sendHeartBeatsResponse(final WebSocket webSocket) {
    if (webSocket != null) {
      webSocket.writeTextMessage(String.format("\"primus::pong::%d\"", System.currentTimeMillis()));
    }
  }
}
