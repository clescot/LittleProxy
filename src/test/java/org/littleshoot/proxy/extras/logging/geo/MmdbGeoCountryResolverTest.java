package org.littleshoot.proxy.extras.logging.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class MmdbGeoCountryResolverTest {

  @TempDir Path tempDir;

  private Path databaseFile;

  @BeforeEach
  public void setUp() throws IOException {
    databaseFile = tempDir.resolve("test-country.mmdb");
    MmdbTestDatabase database = new MmdbTestDatabase();
    database.addIpv4Record(1, 0, 0, MmdbTestDatabase.countryRecord("AU"));
    database.addIpv4Record(2, 0, 0, MmdbTestDatabase.countryRecord("de"));
    database.addIpv4Record(3, 0, 0, MmdbTestDatabase.registeredCountryRecord("IT"));
    database.addIpv4Record(4, 0, 0, MmdbTestDatabase.representedCountryRecord("ES"));
    database.addIpv4Record(5, 0, 0, MmdbTestDatabase.countryRecord(""));
    database.addIpv4Record(6, 0, 0, MmdbTestDatabase.countryRecordWithoutIsoCode());
    database.writeTo(databaseFile);
  }

  @Test
  public void testConstructorRejectsNullPath() {
    assertThatThrownBy(() -> new MmdbGeoCountryResolver(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testConstructorRejectsMissingDatabaseFile() {
    assertThatThrownBy(() -> new MmdbGeoCountryResolver(tempDir.resolve("missing.mmdb")))
        .isInstanceOf(IOException.class);
  }

  @Test
  public void testResolvesCountryCodeFromCountrySection() throws IOException {
    try (MmdbGeoCountryResolver resolver = newResolver()) {
      assertThat(resolver.resolveCountryCode(address("1.0.0.1"))).isEqualTo("AU");
    }
  }

  @Test
  public void testNormalizesCountryCodeToUpperCase() throws IOException {
    try (MmdbGeoCountryResolver resolver = newResolver()) {
      assertThat(resolver.resolveCountryCode(address("2.0.0.1"))).isEqualTo("DE");
    }
  }

  @Test
  public void testFallsBackToRegisteredCountry() throws IOException {
    try (MmdbGeoCountryResolver resolver = newResolver()) {
      assertThat(resolver.resolveCountryCode(address("3.0.0.1"))).isEqualTo("IT");
    }
  }

  @Test
  public void testFallsBackToRepresentedCountry() throws IOException {
    try (MmdbGeoCountryResolver resolver = newResolver()) {
      assertThat(resolver.resolveCountryCode(address("4.0.0.1"))).isEqualTo("ES");
    }
  }

  @Test
  public void testEmptyIsoCodeResolvesToUnknown() throws IOException {
    try (MmdbGeoCountryResolver resolver = newResolver()) {
      assertThat(resolver.resolveCountryCode(address("5.0.0.1")))
          .isEqualTo(GeoCountryResolver.UNKNOWN);
    }
  }

  @Test
  public void testCountrySectionWithoutIsoCodeResolvesToUnknown() throws IOException {
    try (MmdbGeoCountryResolver resolver = newResolver()) {
      assertThat(resolver.resolveCountryCode(address("6.0.0.1")))
          .isEqualTo(GeoCountryResolver.UNKNOWN);
    }
  }

  @Test
  public void testAddressOutsideDatabaseResolvesToUnknown() throws IOException {
    try (MmdbGeoCountryResolver resolver = newResolver()) {
      assertThat(resolver.resolveCountryCode(address("9.9.9.9")))
          .isEqualTo(GeoCountryResolver.UNKNOWN);
    }
  }

  @Test
  public void testIpv6AddressOutsideDatabaseResolvesToUnknown() throws IOException {
    try (MmdbGeoCountryResolver resolver = newResolver()) {
      assertThat(resolver.resolveCountryCode(address("2001:4860:4860::8888")))
          .isEqualTo(GeoCountryResolver.UNKNOWN);
    }
  }

  @Test
  public void testLocalAddressesResolveToLocalWithoutDatabaseLookup() throws IOException {
    try (MmdbGeoCountryResolver resolver = newResolver()) {
      assertThat(resolver.resolveCountryCode(address("127.0.0.1")))
          .isEqualTo(GeoCountryResolver.LOCAL);
      assertThat(resolver.resolveCountryCode(address("192.168.1.1")))
          .isEqualTo(GeoCountryResolver.LOCAL);
      assertThat(resolver.resolveCountryCode(address("fd12::1")))
          .isEqualTo(GeoCountryResolver.LOCAL);
    }
  }

  @Test
  public void testNullAddressResolvesToUnknown() throws IOException {
    try (MmdbGeoCountryResolver resolver = newResolver()) {
      assertThat(resolver.resolveCountryCode(null)).isEqualTo(GeoCountryResolver.UNKNOWN);
    }
  }

  @Test
  public void testLookupsAfterCloseResolveToUnknown() throws IOException {
    MmdbGeoCountryResolver resolver = newResolver();
    assertThat(resolver.resolveCountryCode(address("1.0.0.1"))).isEqualTo("AU");
    resolver.close();
    assertThat(resolver.resolveCountryCode(address("1.0.0.1")))
        .isEqualTo(GeoCountryResolver.UNKNOWN);
  }

  private MmdbGeoCountryResolver newResolver() throws IOException {
    return new MmdbGeoCountryResolver(databaseFile);
  }

  private static InetAddress address(String literal) throws UnknownHostException {
    return InetAddress.getByName(literal);
  }
}
