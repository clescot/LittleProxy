package org.littleshoot.proxy;

import io.netty.channel.ChannelHandlerContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.littleshoot.proxy.impl.ClientToProxyConnection;
import org.littleshoot.proxy.impl.ProxyToServerConnection;

/**
 * Extension of {@link FlowContext} that provides additional information (which we know after
 * actually processing the request from the client).
 */
public class FullFlowContext extends FlowContext {
  private final String serverHostAndPort;
  private final ChainedProxy chainedProxy;
  private final ChannelHandlerContext ctx;
  private final String serverConnectionId;
  private final Map<String, Long> timingData = new ConcurrentHashMap<>();

  public FullFlowContext(
      ClientToProxyConnection clientConnection, ProxyToServerConnection serverConnection) {
    super(clientConnection);
    serverHostAndPort = serverConnection.getServerHostAndPort();
    chainedProxy = serverConnection.getChainedProxy();
    this.ctx = serverConnection.getContext();
    this.serverConnectionId = String.valueOf(serverConnection.getId());
  }

  /** The host and port for the server (i.e. the ultimate endpoint). */
  public String getServerHostAndPort() {
    return serverHostAndPort;
  }

  /** The chained proxy (if proxy chaining). */
  public ChainedProxy getChainedProxy() {
    return chainedProxy;
  }

  /** The proxy to server channel context. */
  public ChannelHandlerContext getProxyToServerContext() {
    return ctx;
  }

  /**
   * Returns the server-side connection identifier for this flow context.
   *
   * @return the server connection identifier
   */
  public String getServerConnectionId() {
    return serverConnectionId;
  }

  /**
   * Stores server-connection timing data for this flow. Each {@link FullFlowContext} tracks its own
   * values separately from the client connection's {@link FlowContext} so that concurrent server
   * connections do not clobber each other's measurements.
   *
   * @param key the timing metric key
   * @param value the timing value in milliseconds
   */
  @Override
  public void setTimingData(String key, Long value) {
    Objects.requireNonNull(key, "timing key must not be null");
    Objects.requireNonNull(value, "timing value must not be null");
    timingData.put(key, value);
  }

  /**
   * Retrieves timing data for this flow, preferring this server connection's own values over the
   * client connection's.
   *
   * @param key the timing metric key
   * @return the timing value in milliseconds, or null if not available
   */
  @Override
  public Long getTimingData(String key) {
    return Optional.ofNullable(timingData.get(key)).orElseGet(() -> super.getTimingData(key));
  }

  /**
   * Gets all timing data for this flow, merging this server connection's values over the client
   * connection's.
   *
   * @return map of all timing data
   */
  @Override
  public Map<String, Long> getTimings() {
    Map<String, Long> timings = new HashMap<>(super.getTimings());
    timings.putAll(timingData);
    return timings;
  }
}
