/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.util.Subscribers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EthMessages {
  private final Map<Integer, Subscribers<MessageCallback>> listenersByCode =
      new ConcurrentHashMap<>();

  void dispatch(final EthMessage message) {
    final Subscribers<MessageCallback> listeners = listenersByCode.get(message.getData().getCode());
    if (listeners == null) {
      return;
    }

    listeners.forEach(callback -> callback.exec(message));
  }

  public void subscribe(final int messageCode, final MessageCallback callback) {
    listenersByCode.computeIfAbsent(messageCode, key -> Subscribers.create()).subscribe(callback);
  }

  @FunctionalInterface
  public interface MessageCallback {
    void exec(EthMessage message);
  }
}
