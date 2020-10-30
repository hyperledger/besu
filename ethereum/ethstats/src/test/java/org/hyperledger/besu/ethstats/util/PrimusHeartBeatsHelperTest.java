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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.regex.Pattern;

import io.vertx.core.http.WebSocket;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("unchecked")
public class PrimusHeartBeatsHelperTest {

  public static final Pattern PRIMUS_PONG_REGEX = Pattern.compile("primus::pong::([\\d]+)");

  private final String VALID_PING_MESSAGE = "primus::ping::11111111111";

  @Test
  public void shouldDetectValidPrimusPing() {
    assertThat(PrimusHeartBeatsHelper.isHeartBeatsRequest(VALID_PING_MESSAGE)).isTrue();
  }

  @Test
  public void shouldDetectInvalidPrimusPing() {
    // invalid format
    assertThat(PrimusHeartBeatsHelper.isHeartBeatsRequest("primus:ping:11111111111")).isFalse();
    // missing timestamp
    assertThat(PrimusHeartBeatsHelper.isHeartBeatsRequest("primus:ping")).isFalse();
    // missing primus
    assertThat(PrimusHeartBeatsHelper.isHeartBeatsRequest("primus:pong:11111111111")).isFalse();
    // ping not pong
    assertThat(PrimusHeartBeatsHelper.isHeartBeatsRequest("ping:11111111111")).isFalse();
  }

  @Test
  public void shouldSendValidPrimusPong() {
    final WebSocket webSocket = mock(WebSocket.class);
    PrimusHeartBeatsHelper.sendHeartBeatsResponse(webSocket);
    final ArgumentCaptor<String> pongMessageCaptor = ArgumentCaptor.forClass(String.class);
    verify(webSocket, times(1)).writeTextMessage(pongMessageCaptor.capture());
    assertThat(PRIMUS_PONG_REGEX.matcher(pongMessageCaptor.getValue()).find()).isTrue();
  }
}
