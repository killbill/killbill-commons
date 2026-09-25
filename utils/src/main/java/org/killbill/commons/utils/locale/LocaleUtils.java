/*
 * Copyright 2020-2026 Equinix, Inc
 * Copyright 2014-2026 The Billing Project, LLC
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

package org.killbill.commons.utils.locale;

import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

public class LocaleUtils {
    private LocaleUtils() {
    }

    public static String localeString(final Locale locale, @Nullable final String prefix) {
        return localeString(locale.getLanguage(), locale.getCountry(), prefix);
    }

    public static String localeString(final String language, @Nullable final String prefix) {
        return localeString(language, "", prefix);
    }

    private static String localeString(final String language,
                                       final String country,
                                       @Nullable final String prefix) {
        final StringBuilder tmp = new StringBuilder();
        if (prefix != null) {
            tmp.append(prefix);
            if (!prefix.endsWith("_")) {
                tmp.append("_");
            }
        }

        tmp.append(language);
        if(!country.isEmpty()) {
            tmp.append("_")
               .append(country);
        }
        return tmp.toString();
    }

    // From commons-lang
    public static Locale toLocale(final String str) {
        if (str == null) {
            return null;
        }
        final int len = str.length();
        if (len != 2 && len != 5 && len < 7) {
            throw new IllegalArgumentException("Invalid locale format: " + str);
        }
        final char ch0 = str.charAt(0);
        final char ch1 = str.charAt(1);
        if (ch0 < 'a' || ch0 > 'z' || ch1 < 'a' || ch1 > 'z') {
            throw new IllegalArgumentException("Invalid locale format: " + str);
        }
        if (len == 2) {
            return new Locale(str, "");
        } else {
            if (str.charAt(2) != '_') {
                throw new IllegalArgumentException("Invalid locale format: " + str);
            }
            final char ch3 = str.charAt(3);
            if (ch3 == '_') {
                return new Locale(str.substring(0, 2), "", str.substring(4));
            }
            final char ch4 = str.charAt(4);
            if (ch3 < 'A' || ch3 > 'Z' || ch4 < 'A' || ch4 > 'Z') {
                throw new IllegalArgumentException("Invalid locale format: " + str);
            }
            if (len == 5) {
                return new Locale(str.substring(0, 2), str.substring(3, 5));
            } else {
                if (str.charAt(5) != '_') {
                    throw new IllegalArgumentException("Invalid locale format: " + str);
                }
                return new Locale(str.substring(0, 2), str.substring(3, 5), str.substring(6));
            }
        }
    }

    private static final Map<String, Locale> COUNTRY_LOCALES = Map.ofEntries(
            Map.entry("AF", new Locale("ps", "AF")),
            Map.entry("AL", new Locale("sq", "AL")),
            Map.entry("DZ", new Locale("ar", "DZ")),
            Map.entry("AS", new Locale("en", "AS")),
            Map.entry("AD", new Locale("ca", "AD")),
            Map.entry("AO", new Locale("pt", "AO")),
            Map.entry("AI", new Locale("en", "AI")),
            Map.entry("AQ", new Locale("en", "AQ")),
            Map.entry("AG", new Locale("en", "AG")),
            Map.entry("AR", new Locale("es", "AR")),
            Map.entry("AM", new Locale("hy", "AM")),
            Map.entry("AW", new Locale("nl", "AW")),
            Map.entry("AU", new Locale("en", "AU")),
            Map.entry("AT", new Locale("de", "AT")),
            Map.entry("AZ", new Locale("az", "AZ")),

            Map.entry("BS", new Locale("en", "BS")),
            Map.entry("BH", new Locale("ar", "BH")),
            Map.entry("BD", new Locale("bn", "BD")),
            Map.entry("BB", new Locale("en", "BB")),
            Map.entry("BY", new Locale("be", "BY")),
            Map.entry("BE", new Locale("nl", "BE")),
            Map.entry("BZ", new Locale("en", "BZ")),
            Map.entry("BJ", new Locale("fr", "BJ")),
            Map.entry("BM", new Locale("en", "BM")),
            Map.entry("BT", new Locale("dz", "BT")),
            Map.entry("BO", new Locale("es", "BO")),
            Map.entry("BQ", new Locale("nl", "BQ")),
            Map.entry("BA", new Locale("bs", "BA")),
            Map.entry("BW", new Locale("en", "BW")),
            Map.entry("BV", new Locale("no", "BV")),
            Map.entry("BR", new Locale("pt", "BR")),
            Map.entry("IO", new Locale("en", "IO")),
            Map.entry("BN", new Locale("ms", "BN")),
            Map.entry("BG", new Locale("bg", "BG")),
            Map.entry("BF", new Locale("fr", "BF")),
            Map.entry("BI", new Locale("fr", "BI")),

            Map.entry("CV", new Locale("pt", "CV")),
            Map.entry("KH", new Locale("km", "KH")),
            Map.entry("CM", new Locale("fr", "CM")),
            Map.entry("CA", Locale.CANADA),
            Map.entry("KY", new Locale("en", "KY")),
            Map.entry("CF", new Locale("fr", "CF")),
            Map.entry("TD", new Locale("fr", "TD")),
            Map.entry("CL", new Locale("es", "CL")),
            Map.entry("CN", Locale.CHINA),
            Map.entry("CX", new Locale("en", "CX")),
            Map.entry("CC", new Locale("en", "CC")),
            Map.entry("CO", new Locale("es", "CO")),
            Map.entry("KM", new Locale("ar", "KM")),
            Map.entry("CG", new Locale("fr", "CG")),
            Map.entry("CD", new Locale("fr", "CD")),
            Map.entry("CK", new Locale("en", "CK")),
            Map.entry("CR", new Locale("es", "CR")),
            Map.entry("CI", new Locale("fr", "CI")),
            Map.entry("HR", new Locale("hr", "HR")),
            Map.entry("CU", new Locale("es", "CU")),
            Map.entry("CW", new Locale("nl", "CW")),
            Map.entry("CY", new Locale("el", "CY")),
            Map.entry("CZ", new Locale("cs", "CZ")),

            Map.entry("DK", new Locale("da", "DK")),
            Map.entry("DJ", new Locale("fr", "DJ")),
            Map.entry("DM", new Locale("en", "DM")),
            Map.entry("DO", new Locale("es", "DO")),

            Map.entry("EC", new Locale("es", "EC")),
            Map.entry("EG", new Locale("ar", "EG")),
            Map.entry("SV", new Locale("es", "SV")),
            Map.entry("GQ", new Locale("es", "GQ")),
            Map.entry("ER", new Locale("ti", "ER")),
            Map.entry("EE", new Locale("et", "EE")),
            Map.entry("SZ", new Locale("en", "SZ")),
            Map.entry("ET", new Locale("am", "ET")),

            Map.entry("FK", new Locale("en", "FK")),
            Map.entry("FO", new Locale("fo", "FO")),
            Map.entry("FJ", new Locale("en", "FJ")),
            Map.entry("FI", new Locale("fi", "FI")),
            Map.entry("FR", Locale.FRANCE),
            Map.entry("GF", new Locale("fr", "GF")),
            Map.entry("PF", new Locale("fr", "PF")),
            Map.entry("TF", new Locale("fr", "TF")),

            Map.entry("GA", new Locale("fr", "GA")),
            Map.entry("GM", new Locale("en", "GM")),
            Map.entry("GE", new Locale("ka", "GE")),
            Map.entry("DE", Locale.GERMANY),
            Map.entry("GH", new Locale("en", "GH")),
            Map.entry("GI", new Locale("en", "GI")),
            Map.entry("GR", new Locale("el", "GR")),
            Map.entry("GL", new Locale("kl", "GL")),
            Map.entry("GD", new Locale("en", "GD")),
            Map.entry("GP", new Locale("fr", "GP")),
            Map.entry("GU", new Locale("en", "GU")),
            Map.entry("GT", new Locale("es", "GT")),
            Map.entry("GG", new Locale("en", "GG")),
            Map.entry("GN", new Locale("fr", "GN")),
            Map.entry("GW", new Locale("pt", "GW")),
            Map.entry("GY", new Locale("en", "GY")),

            Map.entry("HT", new Locale("fr", "HT")),
            Map.entry("HM", new Locale("en", "HM")),
            Map.entry("VA", new Locale("it", "VA")),
            Map.entry("HN", new Locale("es", "HN")),
            Map.entry("HK", new Locale("zh", "HK")),
            Map.entry("HU", new Locale("hu", "HU")),

            Map.entry("IS", new Locale("is", "IS")),
            Map.entry("IN", new Locale("en", "IN")),
            Map.entry("ID", new Locale("id", "ID")),
            Map.entry("IR", new Locale("fa", "IR")),
            Map.entry("IQ", new Locale("ar", "IQ")),
            Map.entry("IE", new Locale("en", "IE")),
            Map.entry("IM", new Locale("en", "IM")),
            Map.entry("IL", new Locale("he", "IL")),
            Map.entry("IT", Locale.ITALY),

            Map.entry("JM", new Locale("en", "JM")),
            Map.entry("JP", Locale.JAPAN),
            Map.entry("JE", new Locale("en", "JE")),
            Map.entry("JO", new Locale("ar", "JO")),

            Map.entry("KZ", new Locale("kk", "KZ")),
            Map.entry("KE", new Locale("en", "KE")),
            Map.entry("KI", new Locale("en", "KI")),
            Map.entry("KP", new Locale("ko", "KP")),
            Map.entry("KR", Locale.KOREA),
            Map.entry("KW", new Locale("ar", "KW")),
            Map.entry("KG", new Locale("ky", "KG")),

            Map.entry("LA", new Locale("lo", "LA")),
            Map.entry("LV", new Locale("lv", "LV")),
            Map.entry("LB", new Locale("ar", "LB")),
            Map.entry("LS", new Locale("en", "LS")),
            Map.entry("LR", new Locale("en", "LR")),
            Map.entry("LY", new Locale("ar", "LY")),
            Map.entry("LI", new Locale("de", "LI")),
            Map.entry("LT", new Locale("lt", "LT")),
            Map.entry("LU", new Locale("fr", "LU")),

            Map.entry("MO", new Locale("zh", "MO")),
            Map.entry("MG", new Locale("mg", "MG")),
            Map.entry("MW", new Locale("en", "MW")),
            Map.entry("MY", new Locale("ms", "MY")),
            Map.entry("MV", new Locale("dv", "MV")),
            Map.entry("ML", new Locale("fr", "ML")),
            Map.entry("MT", new Locale("mt", "MT")),
            Map.entry("MH", new Locale("en", "MH")),
            Map.entry("MQ", new Locale("fr", "MQ")),
            Map.entry("MR", new Locale("ar", "MR")),
            Map.entry("MU", new Locale("en", "MU")),
            Map.entry("YT", new Locale("fr", "YT")),
            Map.entry("MX", new Locale("es", "MX")),
            Map.entry("FM", new Locale("en", "FM")),
            Map.entry("MD", new Locale("ro", "MD")),
            Map.entry("MC", new Locale("fr", "MC")),
            Map.entry("MN", new Locale("mn", "MN")),
            Map.entry("ME", new Locale("sr", "ME")),
            Map.entry("MS", new Locale("en", "MS")),
            Map.entry("MA", new Locale("ar", "MA")),
            Map.entry("MZ", new Locale("pt", "MZ")),
            Map.entry("MM", new Locale("my", "MM")),

            Map.entry("NA", new Locale("en", "NA")),
            Map.entry("NR", new Locale("en", "NR")),
            Map.entry("NP", new Locale("ne", "NP")),
            Map.entry("NL", new Locale("nl", "NL")),
            Map.entry("NC", new Locale("fr", "NC")),
            Map.entry("NZ", new Locale("en", "NZ")),
            Map.entry("NI", new Locale("es", "NI")),
            Map.entry("NE", new Locale("fr", "NE")),
            Map.entry("NG", new Locale("en", "NG")),
            Map.entry("NU", new Locale("en", "NU")),
            Map.entry("NF", new Locale("en", "NF")),
            Map.entry("MK", new Locale("mk", "MK")),
            Map.entry("MP", new Locale("en", "MP")),
            Map.entry("NO", new Locale("no", "NO")),

            Map.entry("OM", new Locale("ar", "OM")),

            Map.entry("PK", new Locale("en", "PK")),
            Map.entry("PW", new Locale("en", "PW")),
            Map.entry("PS", new Locale("ar", "PS")),
            Map.entry("PA", new Locale("es", "PA")),
            Map.entry("PG", new Locale("en", "PG")),
            Map.entry("PY", new Locale("es", "PY")),
            Map.entry("PE", new Locale("es", "PE")),
            Map.entry("PH", new Locale("en", "PH")),
            Map.entry("PN", new Locale("en", "PN")),
            Map.entry("PL", new Locale("pl", "PL")),
            Map.entry("PT", new Locale("pt", "PT")),
            Map.entry("PR", new Locale("es", "PR")),

            Map.entry("QA", new Locale("ar", "QA")),

            Map.entry("RE", new Locale("fr", "RE")),
            Map.entry("RO", new Locale("ro", "RO")),
            Map.entry("RU", new Locale("ru", "RU")),
            Map.entry("RW", new Locale("rw", "RW")),

            Map.entry("BL", new Locale("fr", "BL")),
            Map.entry("SH", new Locale("en", "SH")),
            Map.entry("KN", new Locale("en", "KN")),
            Map.entry("LC", new Locale("en", "LC")),
            Map.entry("MF", new Locale("fr", "MF")),
            Map.entry("PM", new Locale("fr", "PM")),
            Map.entry("VC", new Locale("en", "VC")),
            Map.entry("WS", new Locale("sm", "WS")),
            Map.entry("SM", new Locale("it", "SM")),
            Map.entry("ST", new Locale("pt", "ST")),
            Map.entry("SA", new Locale("ar", "SA")),
            Map.entry("SN", new Locale("fr", "SN")),
            Map.entry("RS", new Locale("sr", "RS")),
            Map.entry("SC", new Locale("en", "SC")),
            Map.entry("SL", new Locale("en", "SL")),
            Map.entry("SG", new Locale("en", "SG")),
            Map.entry("SX", new Locale("nl", "SX")),
            Map.entry("SK", new Locale("sk", "SK")),
            Map.entry("SI", new Locale("sl", "SI")),
            Map.entry("SB", new Locale("en", "SB")),
            Map.entry("SO", new Locale("so", "SO")),
            Map.entry("ZA", new Locale("en", "ZA")),
            Map.entry("GS", new Locale("en", "GS")),
            Map.entry("SS", new Locale("en", "SS")),
            Map.entry("ES", new Locale("es", "ES")),
            Map.entry("LK", new Locale("si", "LK")),
            Map.entry("SD", new Locale("ar", "SD")),
            Map.entry("SR", new Locale("nl", "SR")),
            Map.entry("SJ", new Locale("no", "SJ")),
            Map.entry("SE", new Locale("sv", "SE")),
            Map.entry("CH", new Locale("de", "CH")),
            Map.entry("SY", new Locale("ar", "SY")),

            Map.entry("TW", Locale.TAIWAN),
            Map.entry("TJ", new Locale("tg", "TJ")),
            Map.entry("TZ", new Locale("sw", "TZ")),
            Map.entry("TH", new Locale("th", "TH")),
            Map.entry("TL", new Locale("pt", "TL")),
            Map.entry("TG", new Locale("fr", "TG")),
            Map.entry("TK", new Locale("en", "TK")),
            Map.entry("TO", new Locale("en", "TO")),
            Map.entry("TT", new Locale("en", "TT")),
            Map.entry("TN", new Locale("ar", "TN")),
            Map.entry("TR", new Locale("tr", "TR")),
            Map.entry("TM", new Locale("tk", "TM")),
            Map.entry("TC", new Locale("en", "TC")),
            Map.entry("TV", new Locale("en", "TV")),

            Map.entry("UG", new Locale("en", "UG")),
            Map.entry("UA", new Locale("uk", "UA")),
            Map.entry("AE", new Locale("ar", "AE")),
            Map.entry("GB", Locale.UK),
            Map.entry("US", Locale.US),
            Map.entry("UM", new Locale("en", "UM")),
            Map.entry("UY", new Locale("es", "UY")),
            Map.entry("UZ", new Locale("uz", "UZ")),

            Map.entry("VU", new Locale("bi", "VU")),
            Map.entry("VE", new Locale("es", "VE")),
            Map.entry("VN", new Locale("vi", "VN")),
            Map.entry("VG", new Locale("en", "VG")),
            Map.entry("VI", new Locale("en", "VI")),

            Map.entry("WF", new Locale("fr", "WF")),
            Map.entry("EH", new Locale("ar", "EH")),

            Map.entry("YE", new Locale("ar", "YE")),

            Map.entry("ZM", new Locale("en", "ZM")),
            Map.entry("ZW", new Locale("en", "ZW"))
                                                                            );

    public static Locale toLocaleFromCountryCode(final String countryCode) {
        return COUNTRY_LOCALES.get(countryCode);
    }
}
