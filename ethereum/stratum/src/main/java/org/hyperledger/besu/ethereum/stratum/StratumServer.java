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
package org.hyperledger.besu.ethereum.stratum;

import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.chain.PoWObserver;
import org.hyperledger.besu.ethereum.mainnet.EthHash;
import org.hyperledger.besu.ethereum.mainnet.PoWSolution;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import com.google.common.util.concurrent.AtomicDouble;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DecoderResultProvider;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.NetSocketInternal;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP server allowing miners to connect to the client over persistent TCP connections, using the
 * various Stratum protocols.
 */
@Deprecated(since = "24.12.0")
public class StratumServer implements PoWObserver {

  private static final Logger logger = LoggerFactory.getLogger(StratumServer.class);
  private static final String VERTX_HANDLER_NAME = "handler";

  private final Vertx vertx;
  private final int port;
  private final String networkInterface;
  protected final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicLong numberOfMiners = new AtomicLong(0);
  private final AtomicDouble currentDifficulty = new AtomicDouble(0.0);
  private final StratumProtocol[] protocols;
  private final Counter connectionsCount;
  private final Counter disconnectionsCount;
  private NetServer server;

  public StratumServer(
      final Vertx vertx,
      final PoWMiningCoordinator miningCoordinator,
      final int port,
      final String networkInterface,
      final String extraNonce,
      final MetricsSystem metricsSystem) {
    this.vertx = vertx;
    this.port = port;
    this.networkInterface = networkInterface;
    protocols =
        new StratumProtocol[] {
          new GetWorkProtocol(miningCoordinator.getEpochCalculator()),
          new Stratum1Protocol(extraNonce, miningCoordinator),
          new Stratum1EthProxyProtocol(miningCoordinator)
        };
    metricsSystem.createLongGauge(
        BesuMetricCategory.STRATUM, "miners", "Number of miners connected", numberOfMiners::get);
    metricsSystem.createGauge(
        BesuMetricCategory.STRATUM,
        "difficulty",
        "Current mining difficulty",
        currentDifficulty::get);
    this.connectionsCount =
        metricsSystem.createCounter(
            BesuMetricCategory.STRATUM, "connections", "Number of connections over time");
    this.disconnectionsCount =
        metricsSystem.createCounter(
            BesuMetricCategory.STRATUM, "disconnections", "Number of disconnections over time");
  }

  public Future<NetServer> start() {
    if (started.compareAndSet(false, true)) {
      server =
          vertx.createNetServer(
              new NetServerOptions().setPort(port).setHost(networkInterface).setTcpKeepAlive(true));
      server.connectHandler(this::handle);
      return server
          .listen()
          .onSuccess(
              v ->
                  logger.info("Stratum server started on {}:{}", networkInterface, v.actualPort()));
    }
    return Future.succeededFuture(server);
  }

  private void handle(final NetSocket socket) {
    connectionsCount.inc();
    numberOfMiners.incrementAndGet();
    NetSocketInternal internalSocket = (NetSocketInternal) socket;
    StratumConnection conn =
        new StratumConnection(
            protocols, response -> sendLineBasedResponse(internalSocket, response));
    ChannelPipeline pipeline = internalSocket.channelHandlerContext().pipeline();
    pipeline.addBefore(
        VERTX_HANDLER_NAME,
        "stratumDecoder",
        new HttpRequestDecoder() {
          @Override
          protected void decode(
              final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out)
              throws Exception {
            ByteBuf inputCopy = in.copy();
            int indexBeforeDecode = inputCopy.readerIndex();
            super.decode(ctx, inputCopy, out);
            if (out.isEmpty()) { // to process last chunk
              super.decode(ctx, inputCopy, out);
            }
            DecoderResultProvider httpDecodingResult =
                (DecoderResultProvider) out.get(out.size() - 1);
            ChannelPipeline pipeline = ctx.pipeline();
            if (httpDecodingResult.decoderResult().isFailure()) {
              logger.trace("Received non-HTTP request, switching to line-based protocol");
              out.remove(httpDecodingResult);
              pipeline.addBefore(
                  VERTX_HANDLER_NAME, "frameDecoder", new LineBasedFrameDecoder(240));
              pipeline.addBefore(
                  VERTX_HANDLER_NAME,
                  "lineTrimmer",
                  new MessageToMessageDecoder<ByteBuf>() {
                    @Override
                    protected void decode(
                        final ChannelHandlerContext ctx,
                        final ByteBuf message,
                        final List<Object> out) {
                      if (message.readableBytes() > 0
                          && message.getByte(message.readableBytes() - 1) == '\n') {
                        if (message.readableBytes() > 1
                            && message.getByte(message.readableBytes() - 2) == '\r') {
                          out.add(message.readRetainedSlice(message.readableBytes() - 2));
                        } else {
                          out.add(message.readRetainedSlice(message.readableBytes() - 1));
                        }
                      } else {
                        out.add(message.retain());
                      }
                    }
                  });
              pipeline.addBefore(
                  VERTX_HANDLER_NAME, "stringEncoder", new StringEncoder(CharsetUtil.UTF_8));
              pipeline.remove(this);
            } else {
              String httpEncoderHandlerName = "httpEncoder";
              if (pipeline.get(httpEncoderHandlerName) == null) {
                logger.trace("Received HTTP request, switching to HTTP protocol");
                pipeline.addBefore(
                    VERTX_HANDLER_NAME, httpEncoderHandlerName, new HttpResponseEncoder());
              }
              if (httpDecodingResult instanceof HttpRequest request
                  && !request.method().equals(HttpMethod.POST)) {
                logger.debug("Received non-POST request");
                in.skipBytes(in.readableBytes()); // skip body decode
              } else {
                in.skipBytes(inputCopy.readerIndex() - indexBeforeDecode);
              }
            }
          }
        });
    Buffer requestBody = Buffer.buffer();
    internalSocket.messageHandler(
        obj -> {
          try {
            if (obj instanceof HttpRequest request && !request.method().equals(HttpMethod.POST)) {
              sendHttpResponse(
                  internalSocket, HttpResponseStatus.METHOD_NOT_ALLOWED, Unpooled.EMPTY_BUFFER);
            } else if (obj instanceof HttpContent httpContent) {
              requestBody.appendBuffer(Buffer.buffer(httpContent.content()));
              if (obj instanceof LastHttpContent) {
                try {
                  conn.handleBuffer(
                      requestBody,
                      response ->
                          sendHttpResponse(
                              internalSocket,
                              HttpResponseStatus.OK,
                              Unpooled.wrappedBuffer(response.getBytes(CharsetUtil.UTF_8))));
                } catch (IllegalArgumentException e) {
                  logger.warn("Invalid message {}", requestBody);
                  sendHttpResponse(
                      internalSocket, HttpResponseStatus.BAD_REQUEST, Unpooled.EMPTY_BUFFER);
                } catch (Exception e) {
                  logger.warn("Unexpected error", e);
                  sendHttpResponse(
                      internalSocket,
                      HttpResponseStatus.INTERNAL_SERVER_ERROR,
                      Unpooled.EMPTY_BUFFER);
                } finally {
                  conn.close();
                }
              }
            } else if (obj instanceof ByteBuf value && value.readableBytes() > 0) {
              try {
                conn.handleBuffer(
                    Buffer.buffer(value),
                    response -> sendLineBasedResponse(internalSocket, response));
              } catch (Exception e) {
                logger.error("Error handling request", e);
                internalSocket.close().onFailure(ex -> logger.error("Error closing socket", ex));
              }
            }
          } catch (Exception e) {
            pipeline.fireExceptionCaught(e);
          }
        });
    internalSocket.exceptionHandler(
        ex -> {
          logger.error("Unknown error", ex);
          internalSocket
              .close()
              .onFailure(
                  closingException -> logger.error("Error closing socket", closingException));
        });
    socket.closeHandler(
        (aVoid) -> {
          logger.debug("Socket closed");
          conn.close();
          numberOfMiners.decrementAndGet();
          disconnectionsCount.inc();
        });
  }

  private static void sendHttpResponse(
      final NetSocketInternal internalSocket,
      final HttpResponseStatus responseStatus,
      final ByteBuf content) {
    ByteBuf contentResponse = content;
    if (logger.isTraceEnabled()) {
      contentResponse = content.copy();
    }
    internalSocket
        .writeMessage(
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, responseStatus, contentResponse))
        .onFailure(ex -> logger.error("Failed to write response", ex))
        .onSuccess(
            writeResult ->
                internalSocket
                    .close()
                    .onSuccess(
                        v ->
                            logger.trace(
                                "<< {}",
                                content.isReadable()
                                    ? content.toString(StandardCharsets.UTF_8)
                                    : "no content"))
                    .onFailure(ex -> logger.error("Failed to close socket", ex)));
  }

  private static Future<Void> sendLineBasedResponse(
      final NetSocketInternal internalSocket, final String response) {
    return internalSocket
        .writeMessage(response + '\n') // response is delimited by a newline
        .onSuccess(v -> logger.trace("<< {}", response))
        .onFailure(ex -> logger.error("Failed to send response: {}", response, ex));
  }

  public Future<Void> stop() {
    if (started.compareAndSet(true, false)) {
      return server.close();
    }
    logger.debug("Stopping StratumServer that was not running");
    return Future.succeededFuture();
  }

  @Override
  public void newJob(final PoWSolverInputs poWSolverInputs) {
    if (!started.get()) {
      logger.debug("Discarding {} as stratum server is not started", poWSolverInputs);
      return;
    }
    logger.debug("stratum newJob with inputs: {}", poWSolverInputs);
    for (StratumProtocol protocol : protocols) {
      protocol.setCurrentWorkTask(poWSolverInputs);
    }

    // reverse the target calculation to get the difficulty
    // and ensure we do not get divide by zero:
    UInt256 difficulty =
        Optional.of(poWSolverInputs.getTarget().toUnsignedBigInteger())
            .filter(td -> td.compareTo(BigInteger.ONE) > 0)
            .map(EthHash.TARGET_UPPER_BOUND::divide)
            .map(UInt256::valueOf)
            .orElse(UInt256.MAX_VALUE);

    currentDifficulty.set(difficulty.toUnsignedBigInteger().doubleValue());
  }

  @Override
  public void setSubmitWorkCallback(final Function<PoWSolution, Boolean> submitSolutionCallback) {
    for (StratumProtocol protocol : protocols) {
      protocol.setSubmitCallback(submitSolutionCallback);
    }
  }
}
