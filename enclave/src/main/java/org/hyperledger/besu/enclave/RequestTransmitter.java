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
package org.hyperledger.besu.enclave;

/** The interface Request transmitter. */
public interface RequestTransmitter {

  /**
   * The interface Response body handler.
   *
   * @param <T> the type parameter
   */
  @FunctionalInterface
  interface ResponseBodyHandler<T> {
    /**
     * Convert response.
     *
     * @param statusCode the status code
     * @param body the body
     * @return the t
     */
    T convertResponse(final int statusCode, final byte[] body);
  }

  /**
   * Post operation.
   *
   * @param <T> the type parameter
   * @param mediaType the media type
   * @param content the content
   * @param endpoint the endpoint
   * @param responseBodyHandler the response body handler
   * @return the t
   */
  <T> T post(
      String mediaType,
      String content,
      String endpoint,
      ResponseBodyHandler<T> responseBodyHandler);

  /**
   * Get operation.
   *
   * @param <T> the type parameter
   * @param mediaType the media type
   * @param content the content
   * @param endpoint the endpoint
   * @param responseBodyHandler the response body handler
   * @param withAcceptJsonHeader the with accept json header
   * @return the t
   */
  <T> T get(
      String mediaType,
      String content,
      String endpoint,
      ResponseBodyHandler<T> responseBodyHandler,
      final boolean withAcceptJsonHeader);
}
