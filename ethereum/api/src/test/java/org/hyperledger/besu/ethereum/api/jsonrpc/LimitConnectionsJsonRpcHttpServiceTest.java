/*
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.ethereum.api.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.junit.Test;

public class LimitConnectionsJsonRpcHttpServiceTest extends JsonRpcHttpServiceTestBase {

  @Test
  public void limitActiveConnections() throws Exception {
    OkHttpClient newClient = new OkHttpClient();
    int i;
    for (i = 0; i < JsonRpcConfiguration.DEFAULT_MAX_ACTIVE_CONNECTIONS; i++) {
      // create a new client for each request because we want to test the limit
      try (final Response resp = newClient.newCall(buildGetRequest("/readiness")).execute()) {
        assertThat(resp.code()).isEqualTo(200);
      }
      newClient = new OkHttpClient();
    }
    // now we should get a rejected connection because we have hit the limit
    assertThat(i).isEqualTo(JsonRpcConfiguration.DEFAULT_MAX_ACTIVE_CONNECTIONS);
    final OkHttpClient newClient2 = new OkHttpClient();

    assertThatThrownBy(() -> newClient2.newCall(buildGetRequest("/readiness")).execute())
        .isInstanceOf(java.net.ConnectException.class)
        .hasMessageContaining("Failed to connect");
  }
}
