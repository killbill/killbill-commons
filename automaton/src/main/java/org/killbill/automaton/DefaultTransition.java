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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;

import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultTransition extends StateMachineValidatingConfig<DefaultStateMachineConfig> implements Transition, Externalizable {

    @XmlElement(name = "initialState", required = true)
    @XmlIDREF
    private DefaultState initialState;

    @XmlElement(name = "operation", required = true)
    @XmlIDREF
    private DefaultOperation operation;

    @XmlElement(name = "operationResult", required = true)
    private OperationResult operationResult;

    @XmlElement(name = "finalState", required = true)
    @XmlIDREF
    private DefaultState finalState;

    private DefaultStateMachine stateMachine;

    // Required for deserialization
    public DefaultTransition() {
    }

    @Override
    public String getName() {
        return initialState.getName() + "-" + operation.getName() + "-" + operationResult;
    }

    @Override
    public State getInitialState() {
        return initialState;
    }

    @Override
    public Operation getOperation() {
        return operation;
    }

    @Override
    public OperationResult getOperationResult() {
        return operationResult;
    }

    @Override
    public State getFinalState() {
        return finalState;
    }

    public DefaultStateMachine getStateMachine() {
        return stateMachine;
    }

    public void setStateMachine(final DefaultStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    @Override
    public void initialize(final DefaultStateMachineConfig root, final URI uri) {
    }

    @Override
    public ValidationErrors validate(final DefaultStateMachineConfig root, final ValidationErrors errors) {
        return errors;
    }

    public void setInitialState(final DefaultState initialState) {
        this.initialState = initialState;
    }

    public void setOperation(final DefaultOperation operation) {
        this.operation = operation;
    }

    public void setOperationResult(final OperationResult operationResult) {
        this.operationResult = operationResult;
    }

    public void setFinalState(final DefaultState finalState) {
        this.finalState = finalState;
    }

    public static Transition findTransition(final State initialState, final Operation operation, final OperationResult operationResult)
            throws MissingEntryException {
        return ((DefaultState) initialState).getStateMachine().findTransition(initialState, operation, operationResult);
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeObject(initialState);
        out.writeObject(operation);
        out.writeBoolean(operationResult != null);
        if (operationResult != null) {
            out.writeUTF(operationResult.name());
        }
        out.writeObject(finalState);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.initialState = (DefaultState) in.readObject();
        this.operation = (DefaultOperation) in.readObject();
        this.operationResult = in.readBoolean() ? OperationResult.valueOf(in.readUTF()) : null;
        this.finalState = (DefaultState) in.readObject();
    }
}
