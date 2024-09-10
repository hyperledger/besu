/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.manager.peertask;

import java.util.Optional;

public class PeerTaskExecutorResult<T> {
  private final Optional<T> result;
  private final PeerTaskExecutorResponseCode responseCode;

  public PeerTaskExecutorResult(final T result, final PeerTaskExecutorResponseCode responseCode) {
    this.result = Optional.ofNullable(result);
    this.responseCode = responseCode;
  }

  public Optional<T> getResult() {
    return result;
  }

  public PeerTaskExecutorResponseCode getResponseCode() {
    return responseCode;
  }
}
