package org.hyperledger.besu.cli.subcommands;

import static org.hyperledger.besu.cli.subcommands.RpcClient.RpcMethod.ETH_SEND_RAW_TRANSACTION;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

public class RpcClient {

  public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  private final String url;
  private final OkHttpClient client =
      new OkHttpClient.Builder()
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(30, TimeUnit.SECONDS)
          .build();

  public RpcClient(final String url) {
    this.url = url;
  }

  public String call(final String request) throws IOException {
    return Objects.requireNonNull(
            client
                .newCall(
                    new okhttp3.Request.Builder()
                        .url(url)
                        .post(RequestBody.create(request, JSON))
                        .build())
                .execute()
                .body())
        .string();
  }

  public String eth_sendRawTransaction(final String rawTx) throws IOException {
    return call(ETH_SEND_RAW_TRANSACTION.getTemplate().replaceAll("RAW_TX", rawTx));
  }

  enum RpcMethod {
    ETH_SEND_RAW_TRANSACTION(
        "eth_sendRawTransaction",
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransaction\",\"params\":[\"RAW_TX\"],\"id\":1}");

    private final String method;
    private final String template;

    RpcMethod(final String method, final String template) {
      this.method = method;
      this.template = template;
    }

    public String getMethod() {
      return method;
    }

    public String getTemplate() {
      return template;
    }
  }
}
