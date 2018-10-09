package net.consensys.pantheon.ethereum.jsonrpc.internal.exception;

public class InvalidJsonRpcRequestException extends IllegalArgumentException {
  public InvalidJsonRpcRequestException(final String message) {
    super(message);
  }

  public InvalidJsonRpcRequestException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
