package net.consensys.pantheon.ethereum.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;

import com.google.common.collect.Lists;
import io.vertx.core.Vertx;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JsonRpcHttpServiceCorsTest {

  private final Vertx vertx = Vertx.vertx();
  private final OkHttpClient client = new OkHttpClient();
  private JsonRpcHttpService jsonRpcHttpService;

  @Before
  public void before() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();
    configuration.setPort(0);
  }

  @After
  public void after() {
    jsonRpcHttpService.stop().join();
  }

  @Test
  public void requestWithNonAcceptedOriginShouldFail() throws Exception {
    jsonRpcHttpService = createJsonRpcHttpServiceWithAllowedDomains("http://foo.io");

    final Request request =
        new Builder().url(jsonRpcHttpService.url()).header("Origin", "http://bar.me").build();

    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isFalse();
    }
  }

  @Test
  public void requestWithAcceptedOriginShouldSucceed() throws Exception {
    jsonRpcHttpService = createJsonRpcHttpServiceWithAllowedDomains("http://foo.io");

    final Request request =
        new Builder().url(jsonRpcHttpService.url()).header("Origin", "http://foo.io").build();

    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestWithOneOfMultipleAcceptedOriginsShouldSucceed() throws Exception {
    jsonRpcHttpService =
        createJsonRpcHttpServiceWithAllowedDomains("http://foo.io", "http://bar.me");

    final Request request =
        new Builder().url(jsonRpcHttpService.url()).header("Origin", "http://bar.me").build();

    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestWithNoneOfMultipleAcceptedOriginsShouldFail() throws Exception {
    jsonRpcHttpService =
        createJsonRpcHttpServiceWithAllowedDomains("http://foo.io", "http://bar.me");

    final Request request =
        new Builder().url(jsonRpcHttpService.url()).header("Origin", "http://hel.lo").build();

    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isFalse();
    }
  }

  @Test
  public void requestWithNoOriginShouldSucceedWhenNoCorsConfigSet() throws Exception {
    jsonRpcHttpService = createJsonRpcHttpServiceWithAllowedDomains();

    final Request request = new Builder().url(jsonRpcHttpService.url()).build();

    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestWithNoOriginShouldSucceedWhenCorsIsSet() throws Exception {
    jsonRpcHttpService = createJsonRpcHttpServiceWithAllowedDomains("http://foo.io");

    final Request request = new Builder().url(jsonRpcHttpService.url()).build();

    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestWithAnyOriginShouldNotSucceedWhenCorsIsEmpty() throws Exception {
    jsonRpcHttpService = createJsonRpcHttpServiceWithAllowedDomains("");

    final Request request =
        new Builder().url(jsonRpcHttpService.url()).header("Origin", "http://bar.me").build();

    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isFalse();
    }
  }

  @Test
  public void requestWithAnyOriginShouldSucceedWhenCorsIsStart() throws Exception {
    jsonRpcHttpService = createJsonRpcHttpServiceWithAllowedDomains("*");

    final Request request =
        new Builder().url(jsonRpcHttpService.url()).header("Origin", "http://bar.me").build();

    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestWithAccessControlRequestMethodShouldReturnAllowedHeaders() throws Exception {
    jsonRpcHttpService = createJsonRpcHttpServiceWithAllowedDomains("http://foo.io");

    final Request request =
        new Builder()
            .url(jsonRpcHttpService.url())
            .method("OPTIONS", null)
            .header("Access-Control-Request-Method", "OPTIONS")
            .header("Origin", "http://foo.io")
            .build();

    try (Response response = client.newCall(request).execute()) {
      assertThat(response.header("Access-Control-Allow-Headers")).contains("*", "content-type");
    }
  }

  private JsonRpcHttpService createJsonRpcHttpServiceWithAllowedDomains(
      final String... corsAllowedDomains) {
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setPort(0);
    if (corsAllowedDomains != null) {
      config.setCorsAllowedDomains(Lists.newArrayList(corsAllowedDomains));
    }

    final JsonRpcHttpService jsonRpcHttpService =
        new JsonRpcHttpService(vertx, config, new HashMap<>());
    jsonRpcHttpService.start().join();

    return jsonRpcHttpService;
  }
}
