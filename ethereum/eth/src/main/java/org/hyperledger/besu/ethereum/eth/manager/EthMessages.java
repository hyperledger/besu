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
package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.util.Subscribers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.annotations.VisibleForTesting;

public class EthMessages {
  private final Map<Integer, Subscribers<MessageCallback>> listenersByCode =
      new ConcurrentHashMap<>();
  private final Map<Integer, MessageResponseConstructor> messageResponseConstructorsByCode =
      new ConcurrentHashMap<>();

  public Optional<MessageData> dispatch(final EthMessage ethMessage) {
    final int code = ethMessage.getData().getCode();

    // trigger arbitrary side-effecting listeners
    Optional.ofNullable(listenersByCode.get(code))
        .ifPresent(
            listeners -> listeners.forEach(messageCallback -> messageCallback.exec(ethMessage)));

    return Optional.ofNullable(messageResponseConstructorsByCode.get(code))
        .map(
            messageResponseConstructor ->
                messageResponseConstructor.response(ethMessage.getData()));
  }

  public long subscribe(final int messageCode, final MessageCallback callback) {
    return listenersByCode
        .computeIfAbsent(messageCode, key -> Subscribers.create())
        .subscribe(callback);
  }

  public void unsubscribe(final long subscriptionId, final int messageCode) {
    if (listenersByCode.containsKey(messageCode)) {
      listenersByCode.get(messageCode).unsubscribe(subscriptionId);
      if (listenersByCode.get(messageCode).getSubscriberCount() < 1) {
        listenersByCode.remove(messageCode);
      }
    }
  }

  public void registerResponseConstructor(
      final int messageCode, final MessageResponseConstructor messageResponseConstructor) {
    messageResponseConstructorsByCode.put(messageCode, messageResponseConstructor);
  }

  @VisibleForTesting
  public List<Integer> messageCodesHandled() {
    List<Integer> retval = new ArrayList<>();
    retval.addAll(messageResponseConstructorsByCode.keySet());
    retval.addAll(listenersByCode.keySet());
    return retval;
  }

  @FunctionalInterface
  public interface MessageCallback {
    void exec(EthMessage message);
  }

  @FunctionalInterface
  public interface MessageResponseConstructor {
    MessageData response(MessageData message);
  }
}
