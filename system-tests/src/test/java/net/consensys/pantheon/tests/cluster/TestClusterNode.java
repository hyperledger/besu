package net.consensys.pantheon.tests.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.Set;

import com.google.common.base.MoreObjects;
import io.vertx.core.json.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;

public abstract class TestClusterNode {
  private static final Logger LOG = LogManager.getLogger();

  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  protected final String nodeName;
  protected final boolean isBootNode;
  protected final InetSocketAddress discoveryAddress;
  protected final InetSocketAddress jsonRpcAddress;
  protected final InetSocketAddress hostDiscoveryAddress;
  protected final InetSocketAddress hostJsonRpcAddress;
  protected final String enode;

  public TestClusterNode(
      final String nodeName,
      final boolean isBootNode,
      final InetSocketAddress discoveryAddress,
      final InetSocketAddress jsonRpcAddress,
      final InetSocketAddress hostDiscoveryAddress,
      final InetSocketAddress hostJsonRpcAddress,
      final String enode) {
    Assertions.assertThat(enode).isNotNull();
    this.nodeName = nodeName;
    this.isBootNode = isBootNode;
    this.discoveryAddress = discoveryAddress;
    this.jsonRpcAddress = jsonRpcAddress;
    this.hostDiscoveryAddress = hostDiscoveryAddress;
    this.hostJsonRpcAddress = hostJsonRpcAddress;
    this.enode = enode;
  }

  public String getNodeName() {
    return nodeName;
  }

  public boolean isBootNode() {
    return isBootNode;
  }

  public InetSocketAddress getDiscoveryAddress() {
    return discoveryAddress;
  }

  public InetSocketAddress getJsonRpcAddress() {
    return jsonRpcAddress;
  }

  public InetSocketAddress getHostDiscoveryAddress() {
    return hostDiscoveryAddress;
  }

  public InetSocketAddress getHostJsonRpcAddress() {
    return hostJsonRpcAddress;
  }

  public String getEnode() {
    return enode;
  }

  /**
   * TODO: JSON WIZARDS! I bet there is a better way to do this. Pro Tips? TODO: JSON WIZARDS!
   * Should we have a JSON help lib for this stuff? or do we already have one?
   *
   * <p>Execute JsonRpc method against this node. Assumptions: - body is well formed. - HTTP
   * Response code is 200. Exception is thrown on any other response code - HTTP Response is valid
   * JSON-RPC value
   *
   * @param method HTTP Method. eg GET POST
   * @return HTTP Response Body as {@link JsonObject}
   */
  public JsonObject executeJsonRpc(final String path, final String method, final String body)
      throws IOException {
    final OkHttpClient client = new OkHttpClient();

    // TODO: Should this be an incremented or random number?
    final String id = "123";
    final RequestBody reqBody = RequestBody.create(JSON, body);

    String myPath = (path != null) ? path : "";
    if (!myPath.startsWith("/")) myPath = "/" + myPath;

    final String baseUrl =
        "http://"
            + getJsonRpcAddress().getHostName()
            + ":"
            + getJsonRpcAddress().getPort()
            + myPath;
    final Request request = new Request.Builder().method(method, reqBody).url(baseUrl).build();
    LOG.debug("request:" + request);
    try (Response resp = client.newCall(request).execute()) {
      LOG.debug("response head: {}", resp);
      assertThat(resp.code())
          .describedAs("Error processing request\nrequest=[%s]\nesponse=[%s]", request, resp)
          .isEqualTo(200);
      // Check general format of result
      assertThat(resp.body())
          .describedAs("Error processing request\nrequest=[%s]\nesponse=[%s]", request, resp)
          .isNotNull();
      final JsonObject json = new JsonObject(resp.body().string());
      // TODO: assertNoErrors
      assertValidJsonRpcResult(json, id);
      LOG.debug("response body: {}", json);
      return json;
    }
  }

  /** JSON helper method */
  protected static void assertValidJsonRpcResult(final JsonObject json, final Object id) {
    // Check all expected fieldnames are set
    final Set<String> fieldNames = json.fieldNames();
    assertThat(fieldNames.size()).isEqualTo(3);
    assertThat(fieldNames.contains("id")).isTrue();
    assertThat(fieldNames.contains("jsonrpc")).isTrue();
    assertThat(fieldNames.contains("result")).isTrue();

    // Check standard field values
    assertIdMatches(json, id);
    assertThat(json.getString("jsonrpc")).isEqualTo("2.0");
  }

  /** JSON helper method */
  protected static void assertIdMatches(final JsonObject json, final Object expectedId) {
    final Object actualId = json.getValue("id");
    if (expectedId == null) {
      assertThat(actualId).isNull();
      return;
    }

    assertThat(expectedId)
        .isInstanceOfAny(
            String.class, Integer.class, Long.class, Float.class, Double.class, BigInteger.class);
    assertThat(actualId).isInstanceOf(expectedId.getClass());
    assertThat(actualId.toString()).isEqualTo(expectedId.toString());
  }

  /** Start the node */
  public abstract void start();

  /** Stop the node */
  public abstract void stop();

  /** Delete the node and all related data from disk */
  public abstract void delete();

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("nodeName", nodeName)
        .add("isBootNode", isBootNode)
        .add("discoveryAddress", discoveryAddress)
        .add("jsonRpcAddress", jsonRpcAddress)
        .add("hostDiscoveryAddress", hostDiscoveryAddress)
        .add("hostJsonRpcAddress", hostJsonRpcAddress)
        .add("enode", enode)
        .toString();
  }
}
