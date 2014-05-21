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
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "stateMachineConfig")
@XmlAccessorType(XmlAccessType.NONE)
public class DefaultStateMachineConfig extends StateMachineValidatingConfig<DefaultStateMachineConfig> implements StateMachineConfig {

    @XmlElementWrapper(name = "stateMachines", required = true)
    @XmlElement(name = "stateMachine", required = true)
    private DefaultStateMachine[] stateMachines;

    @XmlElementWrapper(name = "linkStateMachines", required = true)
    @XmlElement(name = "linkStateMachine", required = true)
    private DefaultLinkStateMachine[] linkStateMachines;

    @Override
    public void initialize(final DefaultStateMachineConfig root, final URI uri) {
        for (DefaultStateMachine cur : stateMachines) {
            cur.initialize(root, uri);
        }
        for (DefaultLinkStateMachine cur : linkStateMachines) {
            cur.initialize(root, uri);
        }
    }

    @Override
    public ValidationErrors validate(final DefaultStateMachineConfig root, final ValidationErrors errors) {
        return errors;
    }

    @Override
    public StateMachine getStateMachine(final String stateMachineName) throws MissingEntryException {
        return (StateMachine) getEntry(stateMachines, stateMachineName);
    }


    @Override
    public LinkStateMachine getLinkStateMachine(final String linkStateMachineName) throws MissingEntryException {
        return (LinkStateMachine) getEntry(linkStateMachines, linkStateMachineName);
    }

    @Override
    public LinkStateMachine[] getLinkStateMachines() {
        return linkStateMachines;
    }

    public DefaultStateMachine[] getStateMachines() {
        return stateMachines;
    }


    public void setStateMachines(final DefaultStateMachine[] stateMachines) {
        this.stateMachines = stateMachines;
    }

    public void setLinkStateMachines(final DefaultLinkStateMachine[] linkStateMachines) {
        this.linkStateMachines = linkStateMachines;
    }
}
