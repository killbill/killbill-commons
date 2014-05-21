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

import org.killbill.xmlloader.ValidationErrors;

import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultTransition extends StateMachineValidatingConfig<DefaultStateMachineConfig> implements Transition {

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
}
