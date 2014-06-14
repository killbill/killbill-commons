/*
 * Copyright 2014 Groupon, Inc
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

package org.killbill.automaton;

import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlID;

import org.killbill.xmlloader.ValidationErrors;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultState extends StateMachineValidatingConfig<DefaultStateMachineConfig> implements State {

    @XmlAttribute(required = true)
    @XmlID
    private String name;

    private DefaultStateMachine stateMachine;

    @Override
    public void initialize(final DefaultStateMachineConfig root, final URI uri) {
    }

    @Override
    public ValidationErrors validate(final DefaultStateMachineConfig root, final ValidationErrors errors) {
        return errors;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void runOperation(final Operation operation, final Operation.OperationCallback operationCallback, final EnteringStateCallback enteringStateCallback, final LeavingStateCallback leavingStateCallback)
            throws MissingEntryException, OperationException {

        OperationException rethrowableException = null;
        OperationResult result = OperationResult.EXCEPTION;
        Transition transition = null;
        State initialState = this;
        try {
            final StateMachine destStateMachine = operation.getStateMachine();

            try {
                final LinkStateMachine linkStateMachine = DefaultLinkStateMachine.findLinkStateMachine(this.getStateMachine(), this, destStateMachine);
                initialState = linkStateMachine.getFinalState();
            } catch (MissingEntryException e) {
                initialState = this;
            }

            // If there is no transition from that state we stop right there.
            if (!((DefaultState) initialState).getStateMachine().hasTransitionsFromStates(initialState.getName())) {
                throw new MissingEntryException("No transiton exists from state " + initialState.getName());
            }

            leavingStateCallback.leavingState(initialState);

            result = operation.run(operationCallback);
            transition = DefaultTransition.findTransition(initialState, operation, result);
        } catch (OperationException e) {
            rethrowableException = e;
            // STEPH what happens if we get an exception here...
            transition = DefaultTransition.findTransition(initialState, operation, e.getOperationResult());
        } catch (RuntimeException e) {
            rethrowableException = new OperationException(e);
            // transition = null - we don't want to transition
        } finally {
            if (transition != null) {
                enteringStateCallback.enteringState(transition.getFinalState(), operationCallback, result, leavingStateCallback);
            }
            if (rethrowableException != null) {
                throw rethrowableException;
            }
        }
    }

    public void setName(final String name) {
        this.name = name;
    }

    public DefaultStateMachine getStateMachine() {
        return stateMachine;
    }

    public void setStateMachine(final DefaultStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }
}
