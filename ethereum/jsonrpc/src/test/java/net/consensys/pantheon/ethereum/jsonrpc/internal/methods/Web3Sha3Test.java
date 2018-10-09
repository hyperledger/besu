package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.junit.Test;

public class Web3Sha3Test {

  private final Web3Sha3 method = new Web3Sha3();

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("web3_sha3");
  }

  @Test
  public void shouldReturnCorrectResult() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2", "web3_sha3", new Object[] {"0x68656c6c6f20776f726c64"});

    final JsonRpcResponse expected =
        new JsonRpcSuccessResponse(
            request.getId(), "0x47173285a8d7341e5e972fc677286384f802f8ef42a5ec5f03bbfa254cb01fad");
    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void shouldReturnEmptyStringResult() {
    final JsonRpcRequest request = new JsonRpcRequest("2", "web3_sha3", new Object[] {""});

    final JsonRpcResponse expected =
        new JsonRpcSuccessResponse(
            request.getId(), "0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470");
    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void shouldReturnErrorOnOddLengthParam() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2", "web3_sha3", new Object[] {"0x68656c6c6f20776f726c6"});

    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void shouldReturnErrorOnNonHexParam() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2", "web3_sha3", new Object[] {"0x68656c6c6fThisIsNotHex"});

    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void shouldReturnErrorOnNoPrefixParam() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2", "web3_sha3", new Object[] {"68656c6c6f20776f726c64"});

    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void shouldReturnErrorOnNoPrefixNonHexParam() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2", "web3_sha3", new Object[] {"68656c6c6fThisIsNotHex"});

    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void shouldReturnErrorOnExtraParam() {
    final JsonRpcRequest request =
        new JsonRpcRequest(
            "2", "web3_sha3", new Object[] {"0x68656c6c6f20776f726c64", "{encode:'hex'}"});

    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void shouldReturnErrorOnNoParam() {
    final JsonRpcRequest request = new JsonRpcRequest("2", "web3_sha3", new Object[] {});

    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }
}
