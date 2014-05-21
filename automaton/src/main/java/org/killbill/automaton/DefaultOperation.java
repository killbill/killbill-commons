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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlID;


@XmlAccessorType(XmlAccessType.NONE)
public class DefaultOperation extends StateMachineValidatingConfig<DefaultStateMachineConfig> implements Operation {

    @XmlAttribute(required = true)
    @XmlID
    private String name;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public OperationResult run(final OperationCallback cb) throws OperationException {
        return cb.doOperationCallback();
    }

    @Override
    public void initialize(final DefaultStateMachineConfig root, final URI uri) {
    }

    @Override
    public ValidationErrors validate(final DefaultStateMachineConfig root, final ValidationErrors errors) {
        return errors;
    }

    public void setName(final String name) {
        this.name = name;
    }
}
