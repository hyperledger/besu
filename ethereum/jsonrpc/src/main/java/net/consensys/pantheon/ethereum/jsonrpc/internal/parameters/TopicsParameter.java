package net.consensys.pantheon.ethereum.jsonrpc.internal.parameters;

import net.consensys.pantheon.ethereum.core.LogTopic;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

public class TopicsParameter {

  private final List<List<LogTopic>> queryTopics = new ArrayList<>();

  @JsonCreator
  public TopicsParameter(final List<List<String>> topics) {
    if (topics != null) {
      for (final List<String> list : topics) {
        final List<LogTopic> inputTopics = new ArrayList<>();
        if (list != null) {
          for (final String input : list) {
            final LogTopic topic =
                input != null ? LogTopic.create(BytesValue.fromHexString(input)) : null;
            inputTopics.add(topic);
          }
        }
        queryTopics.add(inputTopics);
      }
    }
  }

  public List<List<LogTopic>> getTopics() {
    return queryTopics;
  }

  @Override
  public String toString() {
    return "TopicsParameter{" + "queryTopics=" + queryTopics + '}';
  }
}
