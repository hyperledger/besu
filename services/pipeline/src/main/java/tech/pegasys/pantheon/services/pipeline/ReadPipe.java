/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.services.pipeline;

import java.util.List;

/**
 * The interface used to read items from a pipe.
 *
 * @param <T> the type of input.
 */
public interface ReadPipe<T> {

  /**
   * Determines if this pipe has more items to be read. The pipe is considered to have no more items
   * when it has either been aborted with {@link WritePipe#abort()} or if all queued items have been
   * read and the pipe has been closed with {@link WritePipe#close()}.
   *
   * @return true if there are more items to process, otherwise false.
   */
  boolean hasMore();

  /**
   * Get and remove the next item from this pipe. This method will block until the next item is
   * available but may still return <code>null</code> if the pipe is closed or the thread
   * interrupted while waiting.
   *
   * @return the next item or <code>null</code> if the pipe is closed or the thread interrupted.
   */
  T get();

  /**
   * Get and remove the next item from this pipe without blocking if it is available.
   *
   * @return the next item or <code>null</code> if the pipe is empty.
   */
  T poll();

  /**
   * Get a batch of values from the pipe containing at most <code>maximumBatchSize</code> items.
   * This method will block until at least one item is available but will not wait until the batch
   * is full.
   *
   * <p>An empty list will be returned if the queue is closed or the thread interrupted while
   * waiting for the next value.
   *
   * @param maximumBatchSize the maximum number of items to read.
   * @return the batch that was read.
   */
  List<T> getBatch(int maximumBatchSize);
}
