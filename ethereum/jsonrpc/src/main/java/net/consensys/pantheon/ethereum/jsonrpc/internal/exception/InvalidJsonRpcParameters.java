package net.consensys.pantheon.ethereum.jsonrpc.internal.exception;

public class InvalidJsonRpcParameters extends InvalidJsonRpcRequestException {

  public InvalidJsonRpcParameters(final String s) {
    super(s);
  }

  public InvalidJsonRpcParameters(final String message, final Throwable cause) {
    super(message, cause);
  }
}
