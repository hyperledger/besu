package net.consensys.pantheon.ethereum.eth.manager;

import net.consensys.pantheon.util.Subscribers;

import java.util.Map;
import java.util.Map.Entry;
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

  public long subscribe(final int messageCode, final MessageCallback callback) {
    return listenersByCode
        .computeIfAbsent(messageCode, key -> new Subscribers<>())
        .subscribe(callback);
  }

  public void unsubscribe(final long listenerId) {
    for (final Entry<Integer, Subscribers<MessageCallback>> entry : listenersByCode.entrySet()) {
      if (entry.getValue().unsubscribe(listenerId)) {
        break;
      }
    }
  }

  @FunctionalInterface
  public interface MessageCallback {
    void exec(EthMessage message);
  }
}
