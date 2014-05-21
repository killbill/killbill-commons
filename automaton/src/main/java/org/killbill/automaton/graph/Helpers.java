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

package org.killbill.automaton.graph;

import org.killbill.automaton.State;
import org.killbill.automaton.StateMachine;
import org.killbill.automaton.Transition;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;


public class Helpers {

    // Might be nicer to return Set<State>, but a bit more difficult to work with
    // We assume state names are unique anyways
    public static Set<String> findInitialStates(final StateMachine stateMachine) {
        return findEdgeStates(stateMachine, true);
    }

    public static Set<String> findFinalStates(final StateMachine stateMachine) {
        return findEdgeStates(stateMachine, false);
    }

    private static Set<String> findEdgeStates(final StateMachine stateMachine, final boolean initial) {
        final Set<String> edgeStates = new HashSet<String>();
        final Collection<String> complementStates = new HashSet<String>();

        for (final Transition transition : stateMachine.getTransitions()) {
            final String stateName = initial ? transition.getFinalState().getName() : transition.getInitialState().getName();
            complementStates.add(stateName);
        }

        for (final State state : stateMachine.getStates()) {
            if (!complementStates.contains(state.getName())) {
                edgeStates.add(state.getName());
            }
        }

        return edgeStates;
    }
}
