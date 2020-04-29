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
package org.hyperledger.besu.services.pipeline;

/**
 * The interface used to add items to a pipe.
 *
 * @param <T> the type of output.
 */
public interface WritePipe<T> {

  /**
   * Determine if this pipe is still open and accepting output.
   *
   * @return true if and only if the pipe is open.
   */
  boolean isOpen();

  /**
   * Adds a new item to the pipe. This method will block until capacity is available in the pipe.
   * The item will be discarded if the pipe is closed before capacity becomes available.
   *
   * @param value the value to add to the pipe.
   */
  void put(T value);

  /**
   * Determine if this pipe has capacity to accept another item.
   *
   * @return true if the pipe has capacity to accept one more item.
   */
  boolean hasRemainingCapacity();

  /**
   * Close this write pipe indicating that no further data will be published to it. When reading
   * from the other end of this pipe {@link ReadPipe#hasMore()} will continue to return true until
   * all the already queued data has been drained.
   */
  void close();

  /** Abort this pipe. The pipe is closed and any queued data is discarded. */
  void abort();
}
