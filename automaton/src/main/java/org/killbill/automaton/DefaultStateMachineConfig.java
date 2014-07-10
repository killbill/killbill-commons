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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.killbill.xmlloader.ValidationErrors;

import javax.xml.bind.annotation.*;
import java.net.URI;

@XmlRootElement(name = "stateMachineConfig")
@XmlAccessorType(XmlAccessType.NONE)
public class DefaultStateMachineConfig extends StateMachineValidatingConfig<DefaultStateMachineConfig> implements StateMachineConfig {

    @XmlElementWrapper(name = "stateMachines", required = true)
    @XmlElement(name = "stateMachine", required = true)
    private DefaultStateMachine[] stateMachines;

    @XmlElementWrapper(name = "linkStateMachines", required = false)
    @XmlElement(name = "linkStateMachine", required = false)
    private DefaultLinkStateMachine[] linkStateMachines = new DefaultLinkStateMachine[0];

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
        validateCollection(root, errors, stateMachines);
        validateCollection(root, errors, linkStateMachines);
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

    @Override
    public StateMachine getStateMachineForState(String stateName) throws MissingEntryException {
        for (DefaultStateMachine cur : stateMachines) {
            for (State st : cur.getStates()) {
                if (st.getName().equals(stateName)) {
                    return cur;
                }
            }
        }
        throw new MissingEntryException("Cannot find stateMachine associated with state" + stateName);
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

    public LinkStateMachine findLinkStateMachine(final StateMachine srcStateMachine, final State srcState, final StateMachine dstStateMachine) throws MissingEntryException {

        try {
            return Iterables.tryFind(ImmutableList.<LinkStateMachine>copyOf(linkStateMachines), new Predicate<LinkStateMachine>() {
                @Override
                public boolean apply(final LinkStateMachine input) {
                    return input.getInitialStateMachine().getName().equals(srcStateMachine.getName()) &&
                            input.getInitialState().getName().equals(srcState.getName()) &&
                            input.getFinalStateMachine().getName().equals(dstStateMachine.getName());
                }
            }).get();
        } catch (IllegalStateException e) {
            throw new MissingEntryException("Missing transition for srcStateMachine " + srcStateMachine.getName() +
                    ", srcState = " + srcState.getName() + ", dstStateMachine = " + dstStateMachine.getName(), e);
        }
    }
}
