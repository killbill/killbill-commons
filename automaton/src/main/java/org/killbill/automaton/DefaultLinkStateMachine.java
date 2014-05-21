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
public class DefaultLinkStateMachine extends StateMachineValidatingConfig<DefaultStateMachineConfig> implements LinkStateMachine {

    @XmlElement(name = "initialStateMachine", required = true)
    @XmlIDREF
    private DefaultStateMachine initialStateMachine;

    @XmlElement(name = "initialState", required = true)
    @XmlIDREF
    private DefaultState initialState;

    @XmlElement(name = "finalStateMachine", required = true)
    @XmlIDREF
    private DefaultStateMachine finalStateMachine;

    @XmlElement(name = "finalState", required = true)
    @XmlIDREF
    private DefaultState finalState;

    @Override
    public String getName() {
        return initialStateMachine.getName() + "-" + finalStateMachine.getName();
    }

    @Override
    public StateMachine getInitialStateMachine() {
        return initialStateMachine;
    }

    @Override
    public State getInitialState() {
        return initialState;
    }

    @Override
    public StateMachine getFinalStateMachine() {
        return finalStateMachine;
    }

    @Override
    public State getFinalState() {
        return finalState;
    }

    @Override
    public void initialize(final DefaultStateMachineConfig root, final URI uri) {
    }

    @Override
    public ValidationErrors validate(final DefaultStateMachineConfig root, final ValidationErrors errors) {
        return errors;
    }

    public void setInitialStateMachine(final DefaultStateMachine initialStateMachine) {
        this.initialStateMachine = initialStateMachine;
    }

    public void setInitialState(final DefaultState initialState) {
        this.initialState = initialState;
    }

    public void setFinalStateMachine(final DefaultStateMachine finalStateMachine) {
        this.finalStateMachine = finalStateMachine;
    }

    public void setFinalState(final DefaultState finalState) {
        this.finalState = finalState;
    }


    public static LinkStateMachine findLinkStateMachine(final StateMachine srcStateMachine, final State srcState, final StateMachine dstStateMachine) throws MissingEntryException {
        return ((DefaultStateMachine) srcStateMachine).getStateMachineConfig().findLinkStateMachine(srcStateMachine, srcState, dstStateMachine);
    }
}
