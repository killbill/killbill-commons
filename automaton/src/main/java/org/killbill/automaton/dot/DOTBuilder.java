/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.automaton.dot;

import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Joiner;
import javax.annotation.Nullable;

public class DOTBuilder {

    private static final String INDENT = "    ";
    private static final String NEW_LINE = "\n";
    private static final String SPACE = " ";
    private static final Joiner SPACE_JOINER = Joiner.on(SPACE);
    private static final String EQUAL = "=";
    private static final String SEMI_COLON = ";";

    private final String name;
    private final StringBuilder output;
    private int nextNodeId;
    private int nextClusterId;
    private int currentIndent;
    private String currentIndentString;

    public DOTBuilder(final String name) {
        this.name = name;
        this.output = new StringBuilder();
        this.nextNodeId = 0;
        this.nextClusterId = 0;
        this.currentIndent = 1;
        rebuildCurrentIndent();
    }

    public int addNode(final String name) {
        return addNode(name, null);
    }

    public int addNode(final String name, @Nullable final Map<String, String> attributesOrNull) {
        // attributes is for example label="Foo" or shape=box
        final Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("label", name);
        if (attributesOrNull != null) {
            attributes.putAll(attributesOrNull);
        }

        final String id = getNodeIdSymbol(nextNodeId);
        nextNodeId++;

        output.append(currentIndentString)
              .append(id);

        addAttributesInlineWithSpace(attributes);

        return nextNodeId - 1;
    }

    public void addPath(final int fromNodeId, final int toNodeId) {
        addPath(fromNodeId, toNodeId, null);
    }

    public void addPath(final int fromNodeId, final int toNodeId, @Nullable final Map<String, String> attributes) {
        addPath(getNodeIdSymbol(fromNodeId), getNodeIdSymbol(toNodeId), true, attributes);
    }

    public void addPath(final String from, final String to, final boolean directed, @Nullable final Map<String, String> attributes) {
        final String edgeSymbol = "-" + (directed ? ">" : "-");
        output.append(currentIndentString)
              .append(from)
              .append(SPACE)
              .append(edgeSymbol)
              .append(SPACE)
              .append(to);

        addAttributesInlineWithSpace(attributes);
    }

    public void openCluster(final String name) {
        openCluster(name, null);
    }

    public void openCluster(final String name, @Nullable final Map<String, String> attributes) {
        output.append(currentIndentString)
              .append("subgraph")
              .append(SPACE)
              .append("cluster_").append(nextClusterId)
              .append(SPACE)
              .append("{")
              .append(NEW_LINE);

        nextClusterId++;
        increaseCurrentIndent();

        output.append(currentIndentString)
              .append("label")
              .append(EQUAL)
              .append("\"")
              .append(name)
              .append("\"")
              .append(SEMI_COLON)
              .append(NEW_LINE);

        addAttributes(attributes);
    }

    public void closeCluster() {
        decreaseCurrentIndent();

        output.append(currentIndentString)
              .append("}")
              .append(NEW_LINE);
    }

    public void open() {
        open(null);
    }

    public void open(@Nullable final Map<String, String> attributes) {
        output.append("digraph")
              .append(SPACE)
              .append(name)
              .append(SPACE)
              .append("{")
              .append(NEW_LINE);

        addAttributesNoBrackets(attributes);
    }

    private void addAttributes(@Nullable final Map<String, String> attributes) {
        if (attributes != null) {
            output.append(currentIndentString);
            addAttributesInlineNoSpace(attributes);
        }
    }

    private void addAttributesNoBrackets(@Nullable final Map<String, String> attributes) {
        if (attributes != null) {
            output.append(currentIndentString);
            addAttributesInline(attributes, false, false);
        }
    }

    private void addAttributesInlineNoSpace(@Nullable final Map<String, String> attributes) {
        addAttributesInline(attributes, false);
    }

    private void addAttributesInlineWithSpace(@Nullable final Map<String, String> attributes) {
        addAttributesInline(attributes, true);
    }

    private void addAttributesInline(@Nullable final Map<String, String> attributes, final boolean withSpace) {
        addAttributesInline(attributes, withSpace, true);
    }

    private void addAttributesInline(@Nullable final Map<String, String> attributes, final boolean withSpace, final boolean withBrackets) {
        if (attributes != null) {
            final String attributesSymbol = (withSpace ? SPACE : "") + (withBrackets ? "[" : "") + SPACE_JOINER.withKeyValueSeparator(EQUAL).join(attributes) + (withBrackets ? "]" : "");
            output.append(attributesSymbol);
        }
        output.append(SEMI_COLON)
              .append(NEW_LINE);
    }

    public void close() {
        output.append("}")
              .append(NEW_LINE);
    }

    private String getNodeIdSymbol(final int nodeId) {
        return "node_" + nodeId;
    }

    private void increaseCurrentIndent() {
        currentIndent++;
        rebuildCurrentIndent();
    }

    private void decreaseCurrentIndent() {
        currentIndent--;
        rebuildCurrentIndent();
    }

    private void rebuildCurrentIndent() {
        currentIndentString = "";
        for (int i = 0; i < currentIndent; i++) {
            currentIndentString += INDENT;
        }
    }

    @Override
    public String toString() {
        return output.toString();
    }
}
