package tech.pegasys.pantheon.services.kvstore;

public class InMemoryKeyValueStorageTest extends AbstractKeyValueStorageTest {

  @Override
  protected KeyValueStorage createStore() throws Exception {
    return new InMemoryKeyValueStorage();
  }
}
