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

import java.util.Map;
import java.util.TreeMap;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDOTBuilder {

    @Test(groups = "fast", enabled = true, description = "https://github.com/killbill/killbill-commons/issues/4")
    public void testGenerator() throws Exception {
        final DOTBuilder payment = new DOTBuilder("Payment");
        payment.open(new TreeMap<>(Map.of("splines", "false")));

        payment.openCluster("Retry");
        final int retryInit = payment.addNode("INIT", new TreeMap(Map.of("color", "grey", "style", "filled")));
        final int retrySuccess = payment.addNode("SUCCESS", new TreeMap(Map.of("color", "grey", "style", "filled")));
        final int retryFailed = payment.addNode("FAILED", new TreeMap(Map.of("color", "grey", "style", "filled")));
        payment.addPath(retryInit, retrySuccess, new TreeMap(Map.of("label", "\"Op|S\"")));
        payment.addPath(retryInit, retryFailed, new TreeMap(Map.of("label", "\"Op|F\"")));
        payment.addPath(retryFailed, retryFailed, new TreeMap(Map.of("label", "\"Op|F\"")));
        payment.closeCluster();

        payment.openCluster("Transaction");

        payment.openCluster("Authorize");
        final int authInit = payment.addNode("INIT", new TreeMap(Map.of("color", "grey", "style", "filled")));
        final int authSuccess = payment.addNode("SUCCESS", new TreeMap(Map.of("color", "grey", "style", "filled")));
        final int authFailed = payment.addNode("FAILED", new TreeMap(Map.of("color", "grey", "style", "filled")));
        final int authPending = payment.addNode("PENDING");
        payment.addPath(authInit, authSuccess, new TreeMap(Map.of("label", "\"Op|S\"")));
        payment.addPath(authInit, authFailed, new TreeMap(Map.of("label", "\"Op|F\"")));
        payment.addPath(authInit, authPending, new TreeMap(Map.of("label", "\"Op|P\"")));
        payment.addPath(authPending, authSuccess, new TreeMap(Map.of("label", "\"Op|S\"")));
        payment.addPath(authPending, authFailed, new TreeMap(Map.of("label", "\"Op|F\"")));
        payment.closeCluster();

        payment.openCluster("Capture");
        final int captureInit = payment.addNode("INIT", new TreeMap(Map.of("color", "grey", "style", "filled")));
        final int captureSuccess = payment.addNode("SUCCESS", new TreeMap(Map.of("color", "grey", "style", "filled")));
        final int captureFailed = payment.addNode("FAILED", new TreeMap(Map.of("color", "grey", "style", "filled")));
        payment.addPath(captureInit, captureSuccess, new TreeMap(Map.of("label", "\"Op|S\"")));
        payment.addPath(captureInit, captureFailed, new TreeMap(Map.of("label", "\"Op|F\"")));
        payment.closeCluster();

        payment.openCluster("Purchase");
        final int purchaseInit = payment.addNode("INIT", new TreeMap(Map.of("color", "grey", "style", "filled")));
        final int purchaseSuccess = payment.addNode("SUCCESS", new TreeMap(Map.of("color", "grey", "style", "filled")));
        final int purchaseFailed = payment.addNode("FAILED", new TreeMap(Map.of("color", "grey", "style", "filled")));
        payment.addPath(purchaseInit, purchaseSuccess, new TreeMap(Map.of("label", "\"Op|S\"")));
        payment.addPath(purchaseInit, purchaseFailed, new TreeMap(Map.of("label", "\"Op|F\"")));
        payment.closeCluster();

        payment.openCluster("Void");
        final int voidInit = payment.addNode("INIT", new TreeMap(Map.of("color", "grey", "style", "filled")));
        final int voidSuccess = payment.addNode("SUCCESS", new TreeMap(Map.of("color", "grey", "style", "filled")));
        final int voidFailed = payment.addNode("FAILED", new TreeMap(Map.of("color", "grey", "style", "filled")));
        payment.addPath(voidInit, voidSuccess, new TreeMap(Map.of("label", "\"Op|S\"")));
        payment.addPath(voidInit, voidFailed, new TreeMap(Map.of("label", "\"Op|F\"")));
        payment.closeCluster();

        payment.openCluster("Refund");
        final int refundInit = payment.addNode("INIT", new TreeMap(Map.of("color", "grey", "style", "filled")));
        final int refundSuccess = payment.addNode("SUCCESS", new TreeMap(Map.of("color", "grey", "style", "filled")));
        final int refundFailed = payment.addNode("FAILED", new TreeMap(Map.of("color", "grey", "style", "filled")));
        payment.addPath(refundInit, refundSuccess, new TreeMap(Map.of("label", "\"Op|S\"")));
        payment.addPath(refundInit, refundFailed, new TreeMap(Map.of("label", "\"Op|F\"")));
        payment.closeCluster();

        payment.addPath(authSuccess, captureInit, new TreeMap(Map.of("style", "dotted", "label", "CAPTURE_AMOUNT_CHECK")));
        payment.addPath(authSuccess, voidInit, new TreeMap(Map.of("style", "dotted")));
        payment.addPath(captureSuccess, voidInit, new TreeMap(Map.of("style", "dotted")));
        payment.addPath(captureSuccess, refundInit, new TreeMap(Map.of("style", "dotted", "label", "REFUND_AMOUNT_CHECK")));
        payment.addPath(purchaseSuccess, refundInit, new TreeMap(Map.of("style", "dotted", "label", "REFUND_AMOUNT_CHECK")));

        payment.closeCluster(); // Transaction

        payment.openCluster("DirectPayment");

        final int directPaymentInit = payment.addNode("INIT", new TreeMap(Map.of("color", "grey", "style", "filled")));
        final int directPaymentOpen = payment.addNode("OPEN", new TreeMap(Map.of("color", "grey", "style", "filled")));
        final int directPaymentClosed = payment.addNode("CLOSED", new TreeMap(Map.of("color", "grey", "style", "filled")));
        payment.addPath(directPaymentInit, directPaymentOpen);
        payment.addPath(directPaymentOpen, directPaymentClosed);

        payment.addPath(directPaymentInit, authInit, new TreeMap(Map.of("style", "dotted", "color", "blue")));
        payment.addPath(directPaymentInit, purchaseInit, new TreeMap(Map.of("style", "dotted", "color", "blue")));
        payment.addPath(directPaymentOpen, authSuccess, new TreeMap(Map.of("style", "dotted", "color", "green")));
        payment.addPath(directPaymentOpen, captureSuccess, new TreeMap(Map.of("style", "dotted", "color", "green")));
        payment.addPath(directPaymentClosed, refundSuccess, new TreeMap(Map.of("style", "dotted", "color", "green")));
        payment.addPath(directPaymentClosed, voidSuccess, new TreeMap(Map.of("style", "dotted", "color", "green")));

        payment.closeCluster(); // DirectPayment

        payment.close();

        Assert.assertEquals(payment.toString(), "digraph Payment {\n" +
                                                "    splines=false;\n" +
                                                "    subgraph cluster_0 {\n" +
                                                "        label=\"Retry\";\n" +
                                                "        node_0 [color=grey style=filled label=INIT];\n" +
                                                "        node_1 [color=grey style=filled label=SUCCESS];\n" +
                                                "        node_2 [color=grey style=filled label=FAILED];\n" +
                                                "        node_0 -> node_1 [label=\"Op|S\"];\n" +
                                                "        node_0 -> node_2 [label=\"Op|F\"];\n" +
                                                "        node_2 -> node_2 [label=\"Op|F\"];\n" +
                                                "    }\n" +
                                                "    subgraph cluster_1 {\n" +
                                                "        label=\"Transaction\";\n" +
                                                "        subgraph cluster_2 {\n" +
                                                "            label=\"Authorize\";\n" +
                                                "            node_3 [color=grey style=filled label=INIT];\n" +
                                                "            node_4 [color=grey style=filled label=SUCCESS];\n" +
                                                "            node_5 [color=grey style=filled label=FAILED];\n" +
                                                "            node_6 [label=PENDING];\n" +
                                                "            node_3 -> node_4 [label=\"Op|S\"];\n" +
                                                "            node_3 -> node_5 [label=\"Op|F\"];\n" +
                                                "            node_3 -> node_6 [label=\"Op|P\"];\n" +
                                                "            node_6 -> node_4 [label=\"Op|S\"];\n" +
                                                "            node_6 -> node_5 [label=\"Op|F\"];\n" +
                                                "        }\n" +
                                                "        subgraph cluster_3 {\n" +
                                                "            label=\"Capture\";\n" +
                                                "            node_7 [color=grey style=filled label=INIT];\n" +
                                                "            node_8 [color=grey style=filled label=SUCCESS];\n" +
                                                "            node_9 [color=grey style=filled label=FAILED];\n" +
                                                "            node_7 -> node_8 [label=\"Op|S\"];\n" +
                                                "            node_7 -> node_9 [label=\"Op|F\"];\n" +
                                                "        }\n" +
                                                "        subgraph cluster_4 {\n" +
                                                "            label=\"Purchase\";\n" +
                                                "            node_10 [color=grey style=filled label=INIT];\n" +
                                                "            node_11 [color=grey style=filled label=SUCCESS];\n" +
                                                "            node_12 [color=grey style=filled label=FAILED];\n" +
                                                "            node_10 -> node_11 [label=\"Op|S\"];\n" +
                                                "            node_10 -> node_12 [label=\"Op|F\"];\n" +
                                                "        }\n" +
                                                "        subgraph cluster_5 {\n" +
                                                "            label=\"Void\";\n" +
                                                "            node_13 [color=grey style=filled label=INIT];\n" +
                                                "            node_14 [color=grey style=filled label=SUCCESS];\n" +
                                                "            node_15 [color=grey style=filled label=FAILED];\n" +
                                                "            node_13 -> node_14 [label=\"Op|S\"];\n" +
                                                "            node_13 -> node_15 [label=\"Op|F\"];\n" +
                                                "        }\n" +
                                                "        subgraph cluster_6 {\n" +
                                                "            label=\"Refund\";\n" +
                                                "            node_16 [color=grey style=filled label=INIT];\n" +
                                                "            node_17 [color=grey style=filled label=SUCCESS];\n" +
                                                "            node_18 [color=grey style=filled label=FAILED];\n" +
                                                "            node_16 -> node_17 [label=\"Op|S\"];\n" +
                                                "            node_16 -> node_18 [label=\"Op|F\"];\n" +
                                                "        }\n" +
                                                "        node_4 -> node_7 [label=CAPTURE_AMOUNT_CHECK style=dotted];\n" +
                                                "        node_4 -> node_13 [style=dotted];\n" +
                                                "        node_8 -> node_13 [style=dotted];\n" +
                                                "        node_8 -> node_16 [label=REFUND_AMOUNT_CHECK style=dotted];\n" +
                                                "        node_11 -> node_16 [label=REFUND_AMOUNT_CHECK style=dotted];\n" +
                                                "    }\n" +
                                                "    subgraph cluster_7 {\n" +
                                                "        label=\"DirectPayment\";\n" +
                                                "        node_19 [color=grey style=filled label=INIT];\n" +
                                                "        node_20 [color=grey style=filled label=OPEN];\n" +
                                                "        node_21 [color=grey style=filled label=CLOSED];\n" +
                                                "        node_19 -> node_20;\n" +
                                                "        node_20 -> node_21;\n" +
                                                "        node_19 -> node_3 [color=blue style=dotted];\n" +
                                                "        node_19 -> node_10 [color=blue style=dotted];\n" +
                                                "        node_20 -> node_4 [color=green style=dotted];\n" +
                                                "        node_20 -> node_8 [color=green style=dotted];\n" +
                                                "        node_21 -> node_17 [color=green style=dotted];\n" +
                                                "        node_21 -> node_14 [color=green style=dotted];\n" +
                                                "    }\n" +
                                                "}\n");

        //System.out.println(payment.toString()));
        //System.out.flush();
        //Files.write((new File("/var/tmp/payment.dot")).toPath(), payment.toString().getBytes()));
    }
}
