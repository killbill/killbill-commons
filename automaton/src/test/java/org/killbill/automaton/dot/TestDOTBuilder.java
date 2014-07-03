/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

public class TestDOTBuilder {

    @Test(groups = "fast", enabled = false, description = "https://github.com/killbill/killbill-commons/issues/4")
    public void testGenerator() throws Exception {
        final DOTBuilder payment = new DOTBuilder("Payment");
        payment.open(ImmutableMap.of("splines", "false"));

        payment.openCluster("Retry");
        final int retryInit = payment.addNode("INIT", ImmutableMap.of("color", "grey", "style", "filled"));
        final int retrySuccess = payment.addNode("SUCCESS", ImmutableMap.of("color", "grey", "style", "filled"));
        final int retryFailed = payment.addNode("FAILED", ImmutableMap.of("color", "grey", "style", "filled"));
        payment.addPath(retryInit, retrySuccess, ImmutableMap.of("label", "\"Op|S\""));
        payment.addPath(retryInit, retryFailed, ImmutableMap.of("label", "\"Op|F\""));
        payment.addPath(retryFailed, retryFailed, ImmutableMap.of("label", "\"Op|F\""));
        payment.closeCluster();

        payment.openCluster("Transaction");

        payment.openCluster("Authorize");
        final int authInit = payment.addNode("INIT", ImmutableMap.of("color", "grey", "style", "filled"));
        final int authSuccess = payment.addNode("SUCCESS", ImmutableMap.of("color", "grey", "style", "filled"));
        final int authFailed = payment.addNode("FAILED", ImmutableMap.of("color", "grey", "style", "filled"));
        final int authPending = payment.addNode("PENDING");
        payment.addPath(authInit, authSuccess, ImmutableMap.of("label", "\"Op|S\""));
        payment.addPath(authInit, authFailed, ImmutableMap.of("label", "\"Op|F\""));
        payment.addPath(authInit, authPending, ImmutableMap.of("label", "\"Op|P\""));
        payment.addPath(authPending, authSuccess, ImmutableMap.of("label", "\"Op|S\""));
        payment.addPath(authPending, authFailed, ImmutableMap.of("label", "\"Op|F\""));
        payment.closeCluster();

        payment.openCluster("Capture");
        final int captureInit = payment.addNode("INIT", ImmutableMap.of("color", "grey", "style", "filled"));
        final int captureSuccess = payment.addNode("SUCCESS", ImmutableMap.of("color", "grey", "style", "filled"));
        final int captureFailed = payment.addNode("FAILED", ImmutableMap.of("color", "grey", "style", "filled"));
        payment.addPath(captureInit, captureSuccess, ImmutableMap.of("label", "\"Op|S\""));
        payment.addPath(captureInit, captureFailed, ImmutableMap.of("label", "\"Op|F\""));
        payment.closeCluster();

        payment.openCluster("Purchase");
        final int purchaseInit = payment.addNode("INIT", ImmutableMap.of("color", "grey", "style", "filled"));
        final int purchaseSuccess = payment.addNode("SUCCESS", ImmutableMap.of("color", "grey", "style", "filled"));
        final int purchaseFailed = payment.addNode("FAILED", ImmutableMap.of("color", "grey", "style", "filled"));
        payment.addPath(purchaseInit, purchaseSuccess, ImmutableMap.of("label", "\"Op|S\""));
        payment.addPath(purchaseInit, purchaseFailed, ImmutableMap.of("label", "\"Op|F\""));
        payment.closeCluster();

        payment.openCluster("Void");
        final int voidInit = payment.addNode("INIT", ImmutableMap.of("color", "grey", "style", "filled"));
        final int voidSuccess = payment.addNode("SUCCESS", ImmutableMap.of("color", "grey", "style", "filled"));
        final int voidFailed = payment.addNode("FAILED", ImmutableMap.of("color", "grey", "style", "filled"));
        payment.addPath(voidInit, voidSuccess, ImmutableMap.of("label", "\"Op|S\""));
        payment.addPath(voidInit, voidFailed, ImmutableMap.of("label", "\"Op|F\""));
        payment.closeCluster();

        payment.openCluster("Refund");
        final int refundInit = payment.addNode("INIT", ImmutableMap.of("color", "grey", "style", "filled"));
        final int refundSuccess = payment.addNode("SUCCESS", ImmutableMap.of("color", "grey", "style", "filled"));
        final int refundFailed = payment.addNode("FAILED", ImmutableMap.of("color", "grey", "style", "filled"));
        payment.addPath(refundInit, refundSuccess, ImmutableMap.of("label", "\"Op|S\""));
        payment.addPath(refundInit, refundFailed, ImmutableMap.of("label", "\"Op|F\""));
        payment.closeCluster();

        payment.addPath(authSuccess, captureInit, ImmutableMap.of("style", "dotted", "label", "CAPTURE_AMOUNT_CHECK"));
        payment.addPath(authSuccess, voidInit, ImmutableMap.of("style", "dotted"));
        payment.addPath(captureSuccess, voidInit, ImmutableMap.of("style", "dotted"));
        payment.addPath(captureSuccess, refundInit, ImmutableMap.of("style", "dotted", "label", "REFUND_AMOUNT_CHECK"));
        payment.addPath(purchaseSuccess, refundInit, ImmutableMap.of("style", "dotted", "label", "REFUND_AMOUNT_CHECK"));

        payment.closeCluster(); // Transaction

        payment.openCluster("DirectPayment");

        final int directPaymentInit = payment.addNode("INIT", ImmutableMap.of("color", "grey", "style", "filled"));
        final int directPaymentOpen = payment.addNode("OPEN", ImmutableMap.of("color", "grey", "style", "filled"));
        final int directPaymentClosed = payment.addNode("CLOSED", ImmutableMap.of("color", "grey", "style", "filled"));
        payment.addPath(directPaymentInit, directPaymentOpen);
        payment.addPath(directPaymentOpen, directPaymentClosed);

        payment.addPath(directPaymentInit, authInit, ImmutableMap.of("style", "dotted", "color", "blue"));
        payment.addPath(directPaymentInit, purchaseInit, ImmutableMap.of("style", "dotted", "color", "blue"));
        payment.addPath(directPaymentOpen, authSuccess, ImmutableMap.of("style", "dotted", "color", "green"));
        payment.addPath(directPaymentOpen, captureSuccess, ImmutableMap.of("style", "dotted", "color", "green"));
        payment.addPath(directPaymentClosed, refundSuccess, ImmutableMap.of("style", "dotted", "color", "green"));
        payment.addPath(directPaymentClosed, voidSuccess, ImmutableMap.of("style", "dotted", "color", "green"));

        payment.closeCluster(); // DirectPayment

        payment.close();

        Assert.assertEquals(payment.toString(), "digraph Payment {\n" +
                                                "    splines=false;\n" +
                                                "    subgraph cluster_0 {\n" +
                                                "        label=\"Retry\";\n" +
                                                "        node_0 [style=filled color=grey label=INIT];\n" +
                                                "        node_1 [style=filled color=grey label=SUCCESS];\n" +
                                                "        node_2 [style=filled color=grey label=FAILED];\n" +
                                                "        node_0 -> node_1 [label=\"Op|S\"];\n" +
                                                "        node_0 -> node_2 [label=\"Op|F\"];\n" +
                                                "        node_2 -> node_2 [label=\"Op|F\"];\n" +
                                                "    }\n" +
                                                "    subgraph cluster_1 {\n" +
                                                "        label=\"Transaction\";\n" +
                                                "        subgraph cluster_2 {\n" +
                                                "            label=\"Authorize\";\n" +
                                                "            node_3 [style=filled color=grey label=INIT];\n" +
                                                "            node_4 [style=filled color=grey label=SUCCESS];\n" +
                                                "            node_5 [style=filled color=grey label=FAILED];\n" +
                                                "            node_6 [label=PENDING];\n" +
                                                "            node_3 -> node_4 [label=\"Op|S\"];\n" +
                                                "            node_3 -> node_5 [label=\"Op|F\"];\n" +
                                                "            node_3 -> node_6 [label=\"Op|P\"];\n" +
                                                "            node_6 -> node_4 [label=\"Op|S\"];\n" +
                                                "            node_6 -> node_5 [label=\"Op|F\"];\n" +
                                                "        }\n" +
                                                "        subgraph cluster_3 {\n" +
                                                "            label=\"Capture\";\n" +
                                                "            node_7 [style=filled color=grey label=INIT];\n" +
                                                "            node_8 [style=filled color=grey label=SUCCESS];\n" +
                                                "            node_9 [style=filled color=grey label=FAILED];\n" +
                                                "            node_7 -> node_8 [label=\"Op|S\"];\n" +
                                                "            node_7 -> node_9 [label=\"Op|F\"];\n" +
                                                "        }\n" +
                                                "        subgraph cluster_4 {\n" +
                                                "            label=\"Purchase\";\n" +
                                                "            node_10 [style=filled color=grey label=INIT];\n" +
                                                "            node_11 [style=filled color=grey label=SUCCESS];\n" +
                                                "            node_12 [style=filled color=grey label=FAILED];\n" +
                                                "            node_10 -> node_11 [label=\"Op|S\"];\n" +
                                                "            node_10 -> node_12 [label=\"Op|F\"];\n" +
                                                "        }\n" +
                                                "        subgraph cluster_5 {\n" +
                                                "            label=\"Void\";\n" +
                                                "            node_13 [style=filled color=grey label=INIT];\n" +
                                                "            node_14 [style=filled color=grey label=SUCCESS];\n" +
                                                "            node_15 [style=filled color=grey label=FAILED];\n" +
                                                "            node_13 -> node_14 [label=\"Op|S\"];\n" +
                                                "            node_13 -> node_15 [label=\"Op|F\"];\n" +
                                                "        }\n" +
                                                "        subgraph cluster_6 {\n" +
                                                "            label=\"Refund\";\n" +
                                                "            node_16 [style=filled color=grey label=INIT];\n" +
                                                "            node_17 [style=filled color=grey label=SUCCESS];\n" +
                                                "            node_18 [style=filled color=grey label=FAILED];\n" +
                                                "            node_16 -> node_17 [label=\"Op|S\"];\n" +
                                                "            node_16 -> node_18 [label=\"Op|F\"];\n" +
                                                "        }\n" +
                                                "        node_4 -> node_7 [style=dotted label=CAPTURE_AMOUNT_CHECK];\n" +
                                                "        node_4 -> node_13 [style=dotted];\n" +
                                                "        node_8 -> node_13 [style=dotted];\n" +
                                                "        node_8 -> node_16 [style=dotted label=REFUND_AMOUNT_CHECK];\n" +
                                                "        node_11 -> node_16 [style=dotted label=REFUND_AMOUNT_CHECK];\n" +
                                                "    }\n" +
                                                "    subgraph cluster_7 {\n" +
                                                "        label=\"DirectPayment\";\n" +
                                                "        node_19 [style=filled color=grey label=INIT];\n" +
                                                "        node_20 [style=filled color=grey label=OPEN];\n" +
                                                "        node_21 [style=filled color=grey label=CLOSED];\n" +
                                                "        node_19 -> node_20;\n" +
                                                "        node_20 -> node_21;\n" +
                                                "        node_19 -> node_3 [style=dotted color=blue];\n" +
                                                "        node_19 -> node_10 [style=dotted color=blue];\n" +
                                                "        node_20 -> node_4 [style=dotted color=green];\n" +
                                                "        node_20 -> node_8 [style=dotted color=green];\n" +
                                                "        node_21 -> node_17 [style=dotted color=green];\n" +
                                                "        node_21 -> node_14 [style=dotted color=green];\n" +
                                                "    }\n" +
                                                "}\n");

        //System.out.println(payment.toString());
        //System.out.flush();
        //Files.write((new File("/var/tmp/payment.dot")).toPath(), payment.toString().getBytes());
    }
}
