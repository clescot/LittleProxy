package org.littleshoot.proxy.extras.logging.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * The factory reads JVM system properties and caches the resolver in a static field, so these tests
 * must not run concurrently with each other. The {@link ResourceLock} serializes them against one
 * another; no other test uses these system properties or the factory.
 */
@ResourceLock("littleproxy.logging.geo.resolver-factory")
public final class GeoCountryResolverFactoryTest {

  @TempDir Path tempDir;

  private Path databaseFile;
  private String savedProvider;
  private String savedMmdbPath;

  @BeforeEach
  public void setUp() throws IOException {
    savedProvider = System.clearProperty(GeoCountryResolverFactory.PROVIDER_PROPERTY);
    savedMmdbPath = System.clearProperty(GeoCountryResolverFactory.MMDB_PATH_PROPERTY);
    GeoCountryResolverFactory.closeResolver();

    databaseFile = tempDir.resolve("test-country.mmdb");
    MmdbTestDatabase database = new MmdbTestDatabase();
    database.addIpv4Record(1, 0, 0, MmdbTestDatabase.countryRecord("AU"));
    database.writeTo(databaseFile);
  }

  @AfterEach
  public void tearDown() {
    restoreProperty(GeoCountryResolverFactory.PROVIDER_PROPERTY, savedProvider);
    restoreProperty(GeoCountryResolverFactory.MMDB_PATH_PROPERTY, savedMmdbPath);
    GeoCountryResolverFactory.closeResolver();
  }

  @Test
  public void testDefaultsToLocalResolver() {
    assertThat(GeoCountryResolverFactory.createResolver())
        .isInstanceOf(LocalGeoCountryResolver.class);
  }

  @Test
  public void testExplicitLocalProvider() {
    System.setProperty(GeoCountryResolverFactory.PROVIDER_PROPERTY, "local");
    assertThat(GeoCountryResolverFactory.createResolver())
        .isInstanceOf(LocalGeoCountryResolver.class);
  }

  @Test
  public void testBlankProviderFallsBackToLocalResolver() {
    System.setProperty(GeoCountryResolverFactory.PROVIDER_PROPERTY, "   ");
    assertThat(GeoCountryResolverFactory.createResolver())
        .isInstanceOf(LocalGeoCountryResolver.class);
  }

  @Test
  public void testUnknownProviderFallsBackToLocalResolver() {
    System.setProperty(GeoCountryResolverFactory.PROVIDER_PROPERTY, "maxmind");
    assertThat(GeoCountryResolverFactory.createResolver())
        .isInstanceOf(LocalGeoCountryResolver.class);
  }

  @Test
  public void testMmdbProviderWithoutPathFallsBackToLocalResolver() {
    System.setProperty(GeoCountryResolverFactory.PROVIDER_PROPERTY, "mmdb");
    assertThat(GeoCountryResolverFactory.createResolver())
        .isInstanceOf(LocalGeoCountryResolver.class);
  }

  @Test
  public void testMmdbProviderWithMissingFileFallsBackToLocalResolver() {
    System.setProperty(GeoCountryResolverFactory.PROVIDER_PROPERTY, "mmdb");
    System.setProperty(
        GeoCountryResolverFactory.MMDB_PATH_PROPERTY, tempDir.resolve("missing.mmdb").toString());
    assertThat(GeoCountryResolverFactory.createResolver())
        .isInstanceOf(LocalGeoCountryResolver.class);
  }

  @Test
  public void testMmdbProviderWithValidDatabase() {
    System.setProperty(GeoCountryResolverFactory.PROVIDER_PROPERTY, "  MMDB  ");
    System.setProperty(GeoCountryResolverFactory.MMDB_PATH_PROPERTY, databaseFile.toString());
    assertThat(GeoCountryResolverFactory.createResolver())
        .isInstanceOf(MmdbGeoCountryResolver.class);
  }

  @Test
  public void testGetResolverCachesInstanceUntilClosed() throws IOException {
    System.setProperty(GeoCountryResolverFactory.PROVIDER_PROPERTY, "mmdb");
    System.setProperty(GeoCountryResolverFactory.MMDB_PATH_PROPERTY, databaseFile.toString());

    GeoCountryResolver first = GeoCountryResolverFactory.getResolver();
    assertThat(first).isInstanceOf(MmdbGeoCountryResolver.class);
    assertThat(GeoCountryResolverFactory.getResolver()).isSameAs(first);
  }

  @Test
  public void testCloseResolverReleasesMmdbResolverAndForcesRecreation()
      throws IOException, UnknownHostException {
    System.setProperty(GeoCountryResolverFactory.PROVIDER_PROPERTY, "mmdb");
    System.setProperty(GeoCountryResolverFactory.MMDB_PATH_PROPERTY, databaseFile.toString());

    GeoCountryResolver first = GeoCountryResolverFactory.getResolver();
    InetAddress lookupAddress = InetAddress.getByName("1.0.0.1");
    assertThat(first.resolveCountryCode(lookupAddress)).isEqualTo("AU");

    GeoCountryResolverFactory.closeResolver();
    assertThat(first.resolveCountryCode(lookupAddress)).isEqualTo(GeoCountryResolver.UNKNOWN);

    GeoCountryResolver second = GeoCountryResolverFactory.getResolver();
    assertThat(second).isInstanceOf(MmdbGeoCountryResolver.class);
    assertThat(second).isNotSameAs(first);
    assertThat(second.resolveCountryCode(lookupAddress)).isEqualTo("AU");
  }

  private static void restoreProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}
