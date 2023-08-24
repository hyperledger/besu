package org.hyperledger.besu.services.pipeline.exception;

public class AsyncOperationException extends RuntimeException {

  public AsyncOperationException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
