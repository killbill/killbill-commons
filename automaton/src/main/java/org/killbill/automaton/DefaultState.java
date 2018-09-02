/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.automaton;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlID;

import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultState extends StateMachineValidatingConfig<DefaultStateMachineConfig> implements State, Externalizable {

    @XmlAttribute(required = true)
    @XmlID
    private String name;

    private DefaultStateMachine stateMachine;

    // Required for deserialization
    public DefaultState() {
    }

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
            } catch (final MissingEntryException e) {
                initialState = this;
            }

            // If there is no transition from that state, we stop right there
            if (!((DefaultState) initialState).getStateMachine().hasTransitionsFromStates(initialState.getName())) {
                throw new MissingEntryException("No transition exists from state " + initialState.getName());
            }

            // If there is no enteringState transition regardless of the operation outcome, we stop right there
            boolean hasAtLeastOneEnteringStateTransition = false;
            for (final OperationResult operationResult : OperationResult.values()) {
                try {
                    DefaultTransition.findTransition(initialState, operation, operationResult);
                    hasAtLeastOneEnteringStateTransition = true;
                    break;
                } catch (final MissingEntryException ignored) {
                }
            }
            if (!hasAtLeastOneEnteringStateTransition) {
                throw new MissingEntryException("No entering state transition exists from state " + initialState.getName() + " for operation " + operation.getName());
            }

            leavingStateCallback.leavingState(initialState);

            result = operation.run(operationCallback);
            transition = DefaultTransition.findTransition(initialState, operation, result);
        } catch (final OperationException e) {
            rethrowableException = e;
            // STEPH what happens if we get an exception here...
            transition = DefaultTransition.findTransition(initialState, operation, e.getOperationResult());
        } catch (final RuntimeException e) {
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultState that = (DefaultState) o;

        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeUTF(name);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.name = in.readUTF();
    }
}
