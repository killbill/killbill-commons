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
package org.jooby.internal;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RoutePattern {

  private static class Rewrite {

    private final Function<String, RouteMatcher> fn;
    private final List<String> vars;
    private final List<String> reverse;
    private final boolean glob;

    public Rewrite(final Function<String, RouteMatcher> fn, final List<String> vars,
        final List<String> reverse, final boolean glob) {
      this.fn = fn;
      this.vars = vars;
      this.reverse = reverse;
      this.glob = glob;
    }
  }

  private static final Pattern GLOB = Pattern
      /**            ?| **    | * | :var           | {var(:.*)} */
      //.compile("\\?|/\\*\\*|\\*|\\:((?:[^/]+)+?)              |\\{((?:\\{[^/]+?\\}|[^/{}]|\\\\[{}])+?)\\}");
      /**           ? | **:name            | * | :var           | */
      .compile(
          "\\?|/\\*\\*(\\:(?:[^/]+))?|\\*|\\:((?:[^/]+)+?)|\\{((?:\\{[^/]+?\\}|[^/{}]|\\\\[{}])+?)\\}");

  private static final Pattern SLASH = Pattern.compile("//+");

  private final Function<String, RouteMatcher> matcher;

  private String pattern;

  private List<String> vars;

  private List<String> reverse;

  private boolean glob;

  public RoutePattern(final String verb, final String pattern) {
    this(verb, pattern, false);
  }

  public RoutePattern(final String verb, final String pattern, boolean ignoreCase) {
    requireNonNull(verb, "A HTTP verb is required.");
    requireNonNull(pattern, "A path pattern is required.");
    this.pattern = normalize(pattern);
    Rewrite rewrite = rewrite(this, verb.toUpperCase(), this.pattern.replace("/**/", "/**"),
        ignoreCase);
    matcher = rewrite.fn;
    vars = rewrite.vars;
    reverse = rewrite.reverse;
    glob = rewrite.glob;
  }

  public boolean glob() {
    return glob;
  }

  public List<String> vars() {
    return vars;
  }

  public String pattern() {
    return pattern;
  }

  public String reverse(final Map<String, Object> vars) {
    return reverse.stream()
        .map(segment -> vars.getOrDefault(segment, segment).toString())
        .collect(Collectors.joining(""));
  }

  public String reverse(final Object... value) {
    List<String> vars = vars();
    Map<String, Object> hash = new HashMap<>();
    for (int i = 0; i < Math.min(vars.size(), value.length); i++) {
      hash.put(vars.get(i), value[i]);
    }
    return reverse(hash);
  }

  public RouteMatcher matcher(final String path) {
    requireNonNull(path, "A path is required.");
    return matcher.apply(path);
  }

  private static Rewrite rewrite(final RoutePattern owner, final String verb, final String pattern,
      boolean ignoreCase) {
    List<String> vars = new LinkedList<>();
    String rwrverb = verbs(verb);
    StringBuilder patternBuilder = new StringBuilder(rwrverb);
    Matcher matcher = GLOB.matcher(pattern);
    int end = 0;
    boolean regex = !rwrverb.equals(verb);
    List<String> reverse = new ArrayList<>();
    boolean glob = false;
    while (matcher.find()) {
      String head = pattern.substring(end, matcher.start());
      patternBuilder.append(Pattern.quote(head));
      reverse.add(head);
      String match = matcher.group();
      if ("?".equals(match)) {
        patternBuilder.append("([^/])");
        reverse.add(match);
        regex = true;
        glob = true;
      } else if ("*".equals(match)) {
        patternBuilder.append("([^/]*)");
        reverse.add(match);
        regex = true;
        glob = true;
      } else if (match.equals("/**")) {
        reverse.add(match);
        patternBuilder.append("($|/.*)");
        regex = true;
        glob = true;
      } else if (match.startsWith("/**:")) {
        reverse.add(match.substring(1));
        String varName = match.substring(4);
        patternBuilder.append("/(?<v").append(vars.size()).append(">($|.*))");
        vars.add(varName);
        regex = true;
        glob = true;
      } else if (match.startsWith(":")) {
        regex = true;
        String varName = match.substring(1);
        patternBuilder.append("(?<v").append(vars.size()).append(">[^/]+)");
        vars.add(varName);
        reverse.add(varName);
      } else if (match.startsWith("{") && match.endsWith("}")) {
        regex = true;
        int colonIdx = match.indexOf(':');
        if (colonIdx == -1) {
          String varName = match.substring(1, match.length() - 1);
          patternBuilder.append("(?<v").append(vars.size()).append(">[^/]+)");
          vars.add(varName);
          reverse.add(varName);
        } else {
          String varName = match.substring(1, colonIdx);
          String regexpr = match.substring(colonIdx + 1, match.length() - 1);
          patternBuilder.append("(?<v").append(vars.size()).append(">");
          patternBuilder.append("**".equals(regexpr) ? "($|.*)" : regexpr);
          patternBuilder.append(')');
          vars.add(varName);
          reverse.add(varName);
        }
      }
      end = matcher.end();
    }
    String tail = pattern.substring(end, pattern.length());
    reverse.add(tail);
    patternBuilder.append(Pattern.quote(tail));
    return new Rewrite(fn(owner, regex, regex ? patternBuilder.toString() : verb + pattern, vars,
        ignoreCase), vars, reverse, glob);
  }

  private static String verbs(final String verb) {
    String[] verbs = verb.split("\\|");
    if (verbs.length == 1) {
      return verb.equals("*") ? "(?:[^/]*)" : verb;
    }
    return "(?:" + verb + ")";
  }

  private static Function<String, RouteMatcher> fn(final RoutePattern owner, final boolean complex,
      final String pattern, final List<String> vars, boolean ignoreCase) {
    return new Function<String, RouteMatcher>() {
      final Pattern regex = complex
          ? Pattern.compile(pattern, ignoreCase ? Pattern.CASE_INSENSITIVE : 0)
          : null;

      @Override
      public RouteMatcher apply(final String fullpath) {
        String path = fullpath.substring(Math.max(0, fullpath.indexOf('/')));
        if (complex) {
          return new RegexRouteMatcher(path, regex.matcher(fullpath), vars);
        }
        return ignoreCase
            ? new SimpleRouteMatcherNoCase(pattern, path, fullpath)
            : new SimpleRouteMatcher(pattern, path, fullpath);
      }
    };
  }

  public static String normalize(final String pattern) {
    if (pattern.equals("*")) {
      return "/**";
    }
    if (pattern.equals("/")) {
      return "/";
    }
    String normalized = SLASH.matcher(pattern).replaceAll("/");
    if (normalized.equals("/")) {
      return "/";
    }
    StringBuilder buffer = new StringBuilder();
    if (!normalized.startsWith("/")) {
      buffer.append("/");
    }
    buffer.append(normalized);
    if (normalized.endsWith("/")) {
      buffer.setLength(buffer.length() - 1);
    }
    return buffer.toString();
  }

  @Override
  public String toString() {
    return pattern;
  }

}
