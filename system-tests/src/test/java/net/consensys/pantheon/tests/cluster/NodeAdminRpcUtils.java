package net.consensys.pantheon.tests.cluster;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.net.ConnectException;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Awaitility;

/** Helper class that performs common JSON-RPC Admin calls */
public class NodeAdminRpcUtils {
  private static final Logger LOG = LogManager.getLogger();

  public static JsonArray postMethodArray(final TestClusterNode node, final String method)
      throws IOException {
    if (node.isBootNode())
      throw new IllegalArgumentException("Can't Call JSON-RPC on boot node.  node=" + node);
    final String id = "123";
    final String body =
        "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"" + method + "\"}";
    final JsonObject json = node.executeJsonRpc(null, "POST", body);
    return json.getJsonArray("result");
  }

  public static String postMethodString(final TestClusterNode node, final String method)
      throws IOException {
    if (node.isBootNode())
      throw new IllegalArgumentException("Can't Call JSON-RPC on boot node.  node=" + node);
    final String id = "123";
    final String body =
        "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"" + method + "\"}";
    final JsonObject json = node.executeJsonRpc(null, "POST", body);
    return json.getString("result");
  }

  public static JsonArray getPeersJson(final TestClusterNode node) throws IOException {
    return postMethodArray(node, "admin_peers");
  }

  /** Verify JSON-RPC is accessible. */
  public static void testWeb3ClientVersionSuccessful(
      final TestClusterNode node, final String prefix) {
    Awaitility.await()
        .atMost(30, SECONDS)
        .ignoreException(ConnectException.class)
        .until(
            () -> {
              final String result = postMethodString(node, "web3_clientVersion");
              return result.startsWith(prefix);
            });
  }

  public static void testWeb3ClientVersionGeth(final TestClusterNode node) {
    testWeb3ClientVersionSuccessful(node, "Geth/");
  }

  public static void testWeb3ClientVersionPantheon(final TestClusterNode node) {
    testWeb3ClientVersionSuccessful(node, "pantheon/");
  }
}
