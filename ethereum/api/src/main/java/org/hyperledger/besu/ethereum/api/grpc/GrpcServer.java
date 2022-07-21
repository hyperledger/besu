package org.hyperledger.besu.ethereum.api.grpc;

import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;

import java.io.IOException;
import java.util.Optional;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

public class GrpcServer {

  private final Server server;

  public GrpcServer(final int port, final StorageProvider storageProvider) {
    this.server =
        ServerBuilder.forPort(port).addService(new ExternalDbService(storageProvider)).build();
  }

  public void start() throws IOException {
    server.start();
  }

  public void stop() {
    server.shutdown();
  }

  static class ExternalDbService extends ExternalDbGrpc.ExternalDbImplBase {

    private final StorageProvider storageProvider;
    private final SegmentMapper segmentMapper = new SegmentMapper();

    public ExternalDbService(final StorageProvider storageProvider) {
      this.storageProvider = storageProvider;
    }

    @Override
    public void getValue(
        final GetRequest request, final StreamObserver<GetResponse> responseObserver) {
      final KeyValueSegmentIdentifier keyValueSegment =
          segmentMapper.getKeyValueSegment(request.getSegment());
      final KeyValueStorage keyValueStorage =
          storageProvider.getStorageBySegmentIdentifier(keyValueSegment);
      final Optional<byte[]> value = keyValueStorage.get(request.getKey().toByteArray());
      final GetResponse response =
          value
              .map(bytes -> GetResponse.newBuilder().setValue(ByteString.copyFrom(bytes)).build())
              .orElseGet(() -> GetResponse.newBuilder().build());
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }
  }
}
