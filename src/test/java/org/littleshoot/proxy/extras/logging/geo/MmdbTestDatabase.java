package org.littleshoot.proxy.extras.logging.geo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal MaxMind DB (MMDB) writer used by the geo resolver tests. It produces a valid IPv4-only
 * database with 24-bit search tree records and a small data section, enough to exercise {@code
 * com.maxmind.db.Reader} lookups end to end without shipping a binary fixture.
 *
 * <p>Only what the geo resolver tests need is supported: lookups of the form {@code a.b.c.0/24 ->
 * record}, where each record is a plain map without internal pointers.
 */
final class MmdbTestDatabase {

  /** Sentinel record value meaning "the address is not in the database". */
  private static final int NOT_FOUND = -1;

  /**
   * Sentinel base for data pointer values while the tree is being built. Pointers are re-based at
   * serialization time, once the node count (and therefore the real pointer values) is known.
   */
  private static final int DATA_POINTER_BASE = 1 << 24;

  private static final int RECORD_SIZE = 24;
  private static final int BITS_PER_OCTET = 8;
  private static final int NETWORK_PREFIX_BITS = 24;

  private final List<int[]> nodes = new ArrayList<>();
  private final ByteArrayOutputStream data = new ByteArrayOutputStream();

  MmdbTestDatabase() {
    newNode();
  }

  /** Adds a lookup record for every address in the network {@code a.b.c.0/24}. */
  void addIpv4Record(int a, int b, int c, byte[] record) {
    int dataOffset = data.size();
    data.writeBytes(record);

    int node = 0;
    for (int bitIndex = 0; bitIndex < NETWORK_PREFIX_BITS; bitIndex++) {
      int octet = octetFor(bitIndex, a, b, c);
      int bit = (octet >> (BITS_PER_OCTET - 1 - (bitIndex % BITS_PER_OCTET))) & 1;
      int next = nodes.get(node)[bit];
      if (next == NOT_FOUND) {
        next = newNode();
        nodes.get(node)[bit] = next;
      }
      node = next;
    }

    int pointer = DATA_POINTER_BASE + dataOffset;
    nodes.get(node)[0] = pointer;
    nodes.get(node)[1] = pointer;
  }

  /** Serializes the database to {@code file}. */
  void writeTo(Path file) throws IOException {
    int nodeCount = nodes.size();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int[] node : nodes) {
      writeRecord(out, recordValue(node[0], nodeCount));
      writeRecord(out, recordValue(node[1], nodeCount));
    }
    out.writeBytes(new byte[16]); // data section separator
    data.writeTo(out);

    out.write(0xAB);
    out.write(0xCD);
    out.write(0xEF);
    out.writeBytes("MaxMind.com".getBytes(StandardCharsets.UTF_8));
    writeMetadata(out, nodeCount);

    Files.write(file, out.toByteArray());
  }

  // ---------------------------------------------------------------------------------------------
  // Country record builders (data section entries in the MaxMind DB binary format).
  // ---------------------------------------------------------------------------------------------

  /** Builds {@code {"country": {"iso_code": "<isoCode>"}}}. */
  static byte[] countryRecord(String isoCode) {
    return sectionRecord("country", isoCode);
  }

  /** Builds {@code {"registered_country": {"iso_code": "<isoCode>"}}}. */
  static byte[] registeredCountryRecord(String isoCode) {
    return sectionRecord("registered_country", isoCode);
  }

  /** Builds {@code {"represented_country": {"iso_code": "<isoCode>"}}}. */
  static byte[] representedCountryRecord(String isoCode) {
    return sectionRecord("represented_country", isoCode);
  }

  /** Builds {@code {"country": {}}}, a country section without an ISO code. */
  static byte[] countryRecordWithoutIsoCode() {
    ByteArrayOutputStream record = new ByteArrayOutputStream();
    record.write(mapControlByte(1));
    writeUtf8String(record, "country");
    record.write(mapControlByte(0));
    return record.toByteArray();
  }

  private static byte[] sectionRecord(String section, String isoCode) {
    ByteArrayOutputStream record = new ByteArrayOutputStream();
    record.write(mapControlByte(1));
    writeUtf8String(record, section);
    record.write(mapControlByte(1));
    writeUtf8String(record, "iso_code");
    writeUtf8String(record, isoCode);
    return record.toByteArray();
  }

  // ---------------------------------------------------------------------------------------------
  // Binary format primitives.
  // ----------------------------------------------------------------------------------------------

  /** Control byte of a map with {@code entryCount} entries. */
  private static int mapControlByte(int entryCount) {
    return (7 << 5) | entryCount;
  }

  /**
   * Writes an UTF-8 string field: a control byte carrying the type and the length, then the bytes.
   * Lengths up to 28 fit in the control byte's 5-bit size field; longer strings store a size marker
   * of 29-31 and the remaining length in the bytes that follow.
   */
  private static void writeUtf8String(ByteArrayOutputStream out, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 29) {
      out.write((2 << 5) | bytes.length);
    } else if (bytes.length < 285) {
      out.write((2 << 5) | 29);
      out.write(bytes.length - 29);
    } else {
      out.write((2 << 5) | 30);
      out.write(((bytes.length - 285) >>> 8) & 0xFF);
      out.write((bytes.length - 285) & 0xFF);
    }
    out.writeBytes(bytes);
  }

  /** Writes a search tree record: a 24-bit big-endian value. */
  private static void writeRecord(ByteArrayOutputStream out, int value) {
    out.write((value >>> 16) & 0xFF);
    out.write((value >>> 8) & 0xFF);
    out.write(value & 0xFF);
  }

  private static int recordValue(int value, int nodeCount) {
    if (value == NOT_FOUND) {
      // A record equal to the node count tells the reader the address is not in the database.
      return nodeCount;
    }
    if (value >= DATA_POINTER_BASE) {
      // Data pointers are relative to the node count and skip the 16-byte data section separator.
      return nodeCount + 16 + (value - DATA_POINTER_BASE);
    }
    return value;
  }

  private static void writeMetadata(ByteArrayOutputStream out, int nodeCount) throws IOException {
    out.write(mapControlByte(9));
    writeUint16Entry(out, "binary_format_major_version", 2);
    writeUint16Entry(out, "binary_format_minor_version", 0);
    writeUint64Entry(out, "build_epoch", 1);
    writeStringEntry(out, "database_type", "LittleProxy-Test");
    writeDescriptionEntry(out);
    writeUint16Entry(out, "ip_version", 4);
    writeLanguagesEntry(out);
    writeUint32Entry(out, "node_count", nodeCount);
    writeUint16Entry(out, "record_size", RECORD_SIZE);
  }

  private static void writeUint16Entry(ByteArrayOutputStream out, String key, int value) {
    writeUtf8String(out, key);
    out.write((5 << 5) | 1);
    out.write(value & 0xFF);
  }

  private static void writeUint32Entry(ByteArrayOutputStream out, String key, int value) {
    writeUtf8String(out, key);
    out.write((6 << 5) | 2);
    out.write((value >>> 8) & 0xFF);
    out.write(value & 0xFF);
  }

  private static void writeUint64Entry(ByteArrayOutputStream out, String key, int value) {
    writeUtf8String(out, key);
    out.write(1); // extended type, 1-byte value
    out.write(9 - 7); // extended type byte: uint64
    out.write(value & 0xFF);
  }

  private static void writeStringEntry(ByteArrayOutputStream out, String key, String value) {
    writeUtf8String(out, key);
    writeUtf8String(out, value);
  }

  private static void writeDescriptionEntry(ByteArrayOutputStream out) {
    writeUtf8String(out, "description");
    out.write(mapControlByte(1));
    writeUtf8String(out, "en");
    writeUtf8String(out, "LittleProxy geo resolver test database");
  }

  private static void writeLanguagesEntry(ByteArrayOutputStream out) {
    writeUtf8String(out, "languages");
    out.write(1); // extended type, 1-element array
    out.write(11 - 7); // extended type byte: array
    writeUtf8String(out, "en");
  }

  private static int octetFor(int bitIndex, int a, int b, int c) {
    if (bitIndex < 8) {
      return a;
    }
    if (bitIndex < 16) {
      return b;
    }
    return c;
  }

  private int newNode() {
    nodes.add(new int[] {NOT_FOUND, NOT_FOUND});
    return nodes.size() - 1;
  }
}
