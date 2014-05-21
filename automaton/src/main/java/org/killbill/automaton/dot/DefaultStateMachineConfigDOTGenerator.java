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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


import com.google.common.collect.ImmutableMap;
import org.killbill.automaton.*;
import org.killbill.automaton.graph.Helpers;

public class DefaultStateMachineConfigDOTGenerator {

    private final String name;
    private final StateMachineConfig config;

    // We assume state names are unique across all state machines
    private final Map<String, Integer> statesNodeIds = new HashMap<String, Integer>();

    private DOTBuilder dot;

    public DefaultStateMachineConfigDOTGenerator(final String name, final StateMachineConfig config) {
        this.name = name;
        this.config = config;
    }

    public DOTBuilder getDot() {
        return dot;
    }

    public void build() {
        dot = new DOTBuilder(name);
        dot.open();
        //dot.open(ImmutableMap.<String, String>of("splines", "false"));

        for (final StateMachine stateMachine : config.getStateMachines()) {
            final Set<String> initialStates = Helpers.findInitialStates(stateMachine);
            final Set<String> finalStates = Helpers.findFinalStates(stateMachine);

            dot.openCluster(stateMachine.getName());

            for (final State state : stateMachine.getStates()) {
                drawState(state, initialStates, finalStates);
            }

            for (final Transition transition : stateMachine.getTransitions()) {
                drawTransition(transition);
            }

            dot.closeCluster();
        }

        for (final LinkStateMachine linkStateMachine : config.getLinkStateMachines()) {
            drawLinkStateMachine(linkStateMachine);
        }

        dot.close();
    }

    @Override
    public String toString() {
        if (dot == null) {
            build();
        }
        return dot.toString();
    }

    private void drawState(final State state, final Collection<String> initialStates, final Collection<String> finalStates) {
        final Map<String, String> attributes = new HashMap<String, String>();
        if (initialStates.contains(state.getName()) || finalStates.contains(state.getName())) {
            attributes.put("color", "grey");
            attributes.put("style", "filled");
        }

        final int nodeId = dot.addNode(state.getName(), attributes);
        statesNodeIds.put(state.getName(), nodeId);
    }

    private void drawTransition(final Transition transition) {
        final Integer fromNodeId = statesNodeIds.get(transition.getInitialState().getName());
        final Integer toNodeId = statesNodeIds.get(transition.getFinalState().getName());

        final String color;
        switch (transition.getOperationResult()) {
            case FAILURE:
            case EXCEPTION:
                color = "red";
                break;
            case SUCCESS:
                color = "green";
                break;
            case TIMEOUT:
                color = "yellow";
                break;
            default:
                color = "black";
                break;
        }

        final String label = String.format("<%s<SUB>|%s</SUB>>", transition.getOperation().getName(), transition.getOperationResult().name().charAt(0));
        dot.addPath(fromNodeId, toNodeId, ImmutableMap.<String, String>of("label", label, "color", color));
    }

    private void drawLinkStateMachine(final LinkStateMachine linkStateMachine) {
        final Integer fromNodeId = statesNodeIds.get(linkStateMachine.getInitialState().getName());
        final Integer toNodeId = statesNodeIds.get(linkStateMachine.getFinalState().getName());
        dot.addPath(fromNodeId, toNodeId, ImmutableMap.<String, String>of("style", "dotted"));
    }
}
