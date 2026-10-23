package org.littleshoot.proxy.extras.logging.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

public final class IpAddressClassifierTest {

  @Test
  public void testNullAddressIsNotLocalOrPrivate() {
    assertThat(IpAddressClassifier.isLocalOrPrivate(null)).isFalse();
  }

  @Test
  public void testLoopbackIpv4IsLocal() throws UnknownHostException {
    assertThat(IpAddressClassifier.isLocalOrPrivate(address("127.0.0.1"))).isTrue();
  }

  @Test
  public void testAnyLocalIpv4IsLocal() throws UnknownHostException {
    assertThat(IpAddressClassifier.isLocalOrPrivate(address("0.0.0.0"))).isTrue();
  }

  @Test
  public void testTenSubnetIsSiteLocal() throws UnknownHostException {
    assertThat(IpAddressClassifier.isLocalOrPrivate(address("10.0.0.1"))).isTrue();
  }

  @Test
  public void testPrivateRange17216IsSiteLocal() throws UnknownHostException {
    assertThat(IpAddressClassifier.isLocalOrPrivate(address("172.16.0.1"))).isTrue();
  }

  @Test
  public void testPrivateRange192168IsSiteLocal() throws UnknownHostException {
    assertThat(IpAddressClassifier.isLocalOrPrivate(address("192.168.1.1"))).isTrue();
  }

  @Test
  public void testLinkLocalIpv4IsLocal() throws UnknownHostException {
    assertThat(IpAddressClassifier.isLocalOrPrivate(address("169.254.1.1"))).isTrue();
  }

  @Test
  public void testPublicIpv4IsNotLocalOrPrivate() throws UnknownHostException {
    assertThat(IpAddressClassifier.isLocalOrPrivate(address("8.8.8.8"))).isFalse();
  }

  @Test
  public void testOtherPublicIpv4IsNotLocalOrPrivate() throws UnknownHostException {
    assertThat(IpAddressClassifier.isLocalOrPrivate(address("1.1.1.1"))).isFalse();
  }

  @Test
  public void testLoopbackIpv6IsLocal() throws UnknownHostException {
    assertThat(IpAddressClassifier.isLocalOrPrivate(address("::1"))).isTrue();
  }

  @Test
  public void testLinkLocalIpv6IsLocal() throws UnknownHostException {
    assertThat(IpAddressClassifier.isLocalOrPrivate(address("fe80::1"))).isTrue();
  }

  @Test
  public void testUniqueLocalIpv6FcRangeIsPrivate() throws UnknownHostException {
    assertThat(IpAddressClassifier.isLocalOrPrivate(address("fc00::1"))).isTrue();
  }

  @Test
  public void testUniqueLocalIpv6FdRangeIsPrivate() throws UnknownHostException {
    assertThat(IpAddressClassifier.isLocalOrPrivate(address("fd12:3456:789a::1"))).isTrue();
  }

  @Test
  public void testPublicIpv6IsNotLocalOrPrivate() throws UnknownHostException {
    assertThat(IpAddressClassifier.isLocalOrPrivate(address("2001:4860:4860::8888"))).isFalse();
  }

  private static InetAddress address(String literal) throws UnknownHostException {
    return InetAddress.getByName(literal);
  }
}
