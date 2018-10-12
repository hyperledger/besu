package tech.pegasys.pantheon.util.time;

public class SystemClock implements Clock {

  @Override
  public long millisecondsSinceEpoch() {
    return System.currentTimeMillis();
  }
}
