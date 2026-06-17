/*
 * Copyright 2026 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jooby.internal.ssl;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.Base64;

/**
 * Reads a PEM file and converts it into a list of DERs so that they are imported into a
 * {@link KeyStore} easily.
 *
 * Kindly Borrowed from <a href="http://netty.io">Netty</a>
 */
final class PemReader {

  // ReDoS fix: (?:\s|\r|\n)+ → \s+ (since \s already includes \r and \n,
  // the alternation created ambiguous matching paths for whitespace chars).
  private static final Pattern CERT_PATTERN = Pattern.compile(
      "-+BEGIN\\s+.*CERTIFICATE[^-]*-+\\s+" + // Header
          "([a-z0-9+/=\\r\\n]+)" + // Base64 text
          "-+END\\s+.*CERTIFICATE[^-]*-+", // Footer
      Pattern.CASE_INSENSITIVE);
  private static final Pattern KEY_PATTERN = Pattern.compile(
      "-+BEGIN\\s+.*PRIVATE\\s+KEY[^-]*-+\\s+" + // Header
          "([a-z0-9+/=\\r\\n]+)" + // Base64 text
          "-+END\\s+.*PRIVATE\\s+KEY[^-]*-+", // Footer
      Pattern.CASE_INSENSITIVE);

  private static final Base64.Decoder BASE64_DECODER = Base64.getMimeDecoder();

  static List<ByteBuffer> readCertificates(final File file)
      throws CertificateException, IOException {
    String content = Files.readString(file.toPath(), StandardCharsets.US_ASCII);

    // Originally, we have local variables: base64 = return BaseEncoding.base64().withSeparator("\n", '\n');
    List<ByteBuffer> certs = new ArrayList<>();
    Matcher m = CERT_PATTERN.matcher(content);
    int start = 0;
    while (m.find(start)) {
      ByteBuffer buffer = ByteBuffer.wrap(BASE64_DECODER.decode(m.group(1)));
      certs.add(buffer);

      start = m.end();
    }

    if (certs.isEmpty()) {
      throw new CertificateException("found no certificates: " + file);
    }

    return certs;
  }

  static ByteBuffer readPrivateKey(final File file) throws KeyException, IOException {
    String content = Files.readString(file.toPath(), StandardCharsets.US_ASCII);

    Matcher m = KEY_PATTERN.matcher(content);
    if (!m.find()) {
      throw new KeyException("found no private key: " + file);
    }

    String value = m.group(1);
    return ByteBuffer.wrap(BASE64_DECODER.decode(value));
  }

  private PemReader() {
  }
}
