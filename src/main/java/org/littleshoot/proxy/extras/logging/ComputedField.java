package org.littleshoot.proxy.extras.logging;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import java.util.Locale;
import org.littleshoot.proxy.FlowContext;

/**
 * Enumeration of computed log fields that derive their values from other request/response data.
 * These fields provide analytics and monitoring capabilities by calculating metrics or extracting
 * derived information.
 */
public enum ComputedField implements LogField {
  GEOLOCATION_COUNTRY("geolocation_country", "Country code based on client IP geolocation"),
  REQUEST_SIZE("request_size", "Total request size in bytes"),
  AUTHENTICATION_TYPE("authentication_type", "Type of authentication used (basic/bearer/none)");

  private final String name;
  private final String description;

  ComputedField(String name, String description) {
    this.name = name;
    this.description = description;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String extractValue(
      FlowContext flowContext, TimedRequest timedRequest, HttpResponse response) {
    HttpRequest request = timedRequest.getRequest();
    switch (this) {
      case GEOLOCATION_COUNTRY:
        // Geo country resolution ships with the optional geo module (separate change);
        // report unknown until that lands.
        return "-";

      case REQUEST_SIZE:
        return extractRequestSize(request);

      case AUTHENTICATION_TYPE:
        return extractAuthenticationType(request);

      default:
        return "-";
    }
  }

  private String extractRequestSize(HttpRequest request) {
    if (request == null || request.headers() == null) {
      return "-";
    }
    String contentLength = request.headers().get("Content-Length");
    if (contentLength != null) {
      try {
        long parsedLength = Long.parseLong(contentLength);
        if (parsedLength >= 0) {
          return Long.toString(parsedLength);
        }
      } catch (NumberFormatException ignored) {
        // Fall back to estimation below
      }
    }

    // Estimate request size from headers and URI
    int estimatedSize = request.uri().length();
    estimatedSize += request.method().name().length();
    estimatedSize += request.protocolVersion().text().length();

    // Add headers size (rough estimate)
    for (String name : request.headers().names()) {
      estimatedSize += name.length();
      for (String value : request.headers().getAll(name)) {
        estimatedSize += value.length() + 2; // +2 for ": " separator
      }
    }

    return String.valueOf(estimatedSize);
  }

  private String extractAuthenticationType(HttpRequest request) {
    String authHeader = request.headers().get("Authorization");

    if (authHeader == null) {
      return "none";
    }
    String normalized = authHeader.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("basic ")) {
      return "basic";
    } else if (normalized.startsWith("bearer ")) {
      return "bearer";
    } else if (normalized.startsWith("digest ")) {
      return "digest";
    } else {
      return "other";
    }
  }
}
