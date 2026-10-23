package org.littleshoot.proxy.extras.logging.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

public final class LocalGeoCountryResolverTest {

  private final LocalGeoCountryResolver resolver = new LocalGeoCountryResolver();

  @Test
  public void testLoopbackResolvesToLocal() throws UnknownHostException {
    assertThat(resolver.resolveCountryCode(InetAddress.getByName("127.0.0.1")))
        .isEqualTo(GeoCountryResolver.LOCAL);
  }

  @Test
  public void testSiteLocalIpv4ResolvesToLocal() throws UnknownHostException {
    assertThat(resolver.resolveCountryCode(InetAddress.getByName("192.168.1.1")))
        .isEqualTo(GeoCountryResolver.LOCAL);
  }

  @Test
  public void testUniqueLocalIpv6ResolvesToLocal() throws UnknownHostException {
    assertThat(resolver.resolveCountryCode(InetAddress.getByName("fd12::1")))
        .isEqualTo(GeoCountryResolver.LOCAL);
  }

  @Test
  public void testPublicIpv4ResolvesToUnknown() throws UnknownHostException {
    assertThat(resolver.resolveCountryCode(InetAddress.getByName("8.8.8.8")))
        .isEqualTo(GeoCountryResolver.UNKNOWN);
  }

  @Test
  public void testPublicIpv6ResolvesToUnknown() throws UnknownHostException {
    assertThat(resolver.resolveCountryCode(InetAddress.getByName("2001:4860:4860::8888")))
        .isEqualTo(GeoCountryResolver.UNKNOWN);
  }

  @Test
  public void testNullAddressResolvesToUnknown() {
    assertThat(resolver.resolveCountryCode(null)).isEqualTo(GeoCountryResolver.UNKNOWN);
  }
}
