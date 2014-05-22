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

import com.google.common.io.Resources;
import org.killbill.xmlloader.XMLLoader;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.log4testng.Logger;

public class TestStateMachine {

    private Logger logger = Logger.getLogger(TestStateMachine.class);

    @Test(groups = "fast")
    public void testStateMachine() throws Exception {
        final DefaultStateMachineConfig sms = XMLLoader.getObjectFromString(Resources.getResource("PaymentStates.xml").toExternalForm(), DefaultStateMachineConfig.class);

        Assert.assertEquals(sms.getStateMachines().length, 2);

        final DefaultStateMachine sm1 = sms.getStateMachines()[0];

        Assert.assertEquals(sm1.getStates().length, 3);
        Assert.assertEquals(sm1.getStates()[0].getName(), "AUTH_INIT");
        Assert.assertEquals(sm1.getStates()[1].getName(), "AUTH_SUCCESS");
        Assert.assertEquals(sm1.getStates()[2].getName(), "AUTH_FAILED");

        Assert.assertEquals(sm1.getOperations().length, 1);
        Assert.assertEquals(sm1.getOperations()[0].getName(), "OP_AUTHORIZE");

        Assert.assertEquals(sm1.getTransitions().length, 2);
        Assert.assertEquals(sm1.getTransitions()[0].getInitialState().getName(), "AUTH_INIT");
        Assert.assertEquals(sm1.getTransitions()[0].getOperation().getName(), "OP_AUTHORIZE");
        Assert.assertEquals(sm1.getTransitions()[0].getOperationResult(), OperationResult.SUCCESS);
        Assert.assertEquals(sm1.getTransitions()[0].getFinalState().getName(), "AUTH_SUCCESS");

        Assert.assertEquals(sm1.getTransitions()[1].getInitialState().getName(), "AUTH_INIT");
        Assert.assertEquals(sm1.getTransitions()[1].getOperation().getName(), "OP_AUTHORIZE");
        Assert.assertEquals(sm1.getTransitions()[1].getOperationResult(), OperationResult.FAILURE);
        Assert.assertEquals(sm1.getTransitions()[1].getFinalState().getName(), "AUTH_FAILED");

        final DefaultStateMachine sm2 = sms.getStateMachines()[1];

        Assert.assertEquals(sm2.getStates().length, 4);
        Assert.assertEquals(sm2.getStates()[0].getName(), "CAPTURE_INIT");
        Assert.assertEquals(sm2.getStates()[1].getName(), "CAPTURE_SUCCESS");
        Assert.assertEquals(sm2.getStates()[2].getName(), "CAPTURE_FAILED");
        Assert.assertEquals(sm2.getStates()[3].getName(), "CAPTURE_ERRORED");

        Assert.assertEquals(sm2.getOperations().length, 1);
        Assert.assertEquals(sm2.getOperations()[0].getName(), "OP_CAPTURE");

        Assert.assertEquals(sm2.getTransitions().length, 3);
        Assert.assertEquals(sm2.getTransitions()[0].getInitialState().getName(), "CAPTURE_INIT");
        Assert.assertEquals(sm2.getTransitions()[0].getOperation().getName(), "OP_CAPTURE");
        Assert.assertEquals(sm2.getTransitions()[0].getOperationResult(), OperationResult.SUCCESS);
        Assert.assertEquals(sm2.getTransitions()[0].getFinalState().getName(), "CAPTURE_SUCCESS");

        Assert.assertEquals(sm2.getTransitions()[1].getInitialState().getName(), "CAPTURE_INIT");
        Assert.assertEquals(sm2.getTransitions()[1].getOperation().getName(), "OP_CAPTURE");
        Assert.assertEquals(sm2.getTransitions()[1].getOperationResult(), OperationResult.FAILURE);
        Assert.assertEquals(sm2.getTransitions()[1].getFinalState().getName(), "CAPTURE_FAILED");

        Assert.assertEquals(sm2.getTransitions()[2].getInitialState().getName(), "CAPTURE_INIT");
        Assert.assertEquals(sm2.getTransitions()[2].getOperation().getName(), "OP_CAPTURE");
        Assert.assertEquals(sm2.getTransitions()[2].getOperationResult(), OperationResult.EXCEPTION);
        Assert.assertEquals(sm2.getTransitions()[2].getFinalState().getName(), "CAPTURE_ERRORED");

        Assert.assertEquals(sms.getLinkStateMachines().length, 1);

        Assert.assertEquals(sms.getLinkStateMachines()[0].getInitialState().getName(), "AUTH_SUCCESS");
        Assert.assertEquals(sms.getLinkStateMachines()[0].getInitialStateMachine().getName(), "AUTHORIZE");

        Assert.assertEquals(sms.getLinkStateMachines()[0].getFinalState().getName(), "CAPTURE_INIT");
        Assert.assertEquals(sms.getLinkStateMachines()[0].getFinalStateMachine().getName(), "CAPTURE");

    }

    @Test(groups = "fast")
    public void testStateTransition() throws Exception {
        final DefaultStateMachineConfig sms = XMLLoader.getObjectFromString(Resources.getResource("PaymentStates.xml").toExternalForm(), DefaultStateMachineConfig.class);

        final DefaultStateMachine sm1 = sms.getStateMachines()[1];
        final State state = sm1.getState("CAPTURE_INIT");
        final Operation operation = sm1.getOperation("OP_CAPTURE");

        state.runOperation(operation,
                new Operation.OperationCallback() {
                    @Override
                    public OperationResult doOperationCallback() throws OperationException {
                        return OperationResult.SUCCESS;
                    }
                },
                new State.EnteringStateCallback() {
                    @Override
                    public void enteringState(final State newState, final Operation.OperationCallback operationCallback, final State.LeavingStateCallback leavingStateCallback) {
                        logger.info("Entering state " + newState.getName());
                        Assert.assertEquals(newState.getName(), "CAPTURE_SUCCESS");
                    }
                },
                new State.LeavingStateCallback() {
                    @Override
                    public void leavingState(final State oldState) {
                        logger.info("Leaving state " + oldState.getName());
                        Assert.assertEquals(oldState.getName(), "CAPTURE_INIT");
                    }
                }
        );

        state.runOperation(operation,
                new Operation.OperationCallback() {
                    @Override
                    public OperationResult doOperationCallback() throws OperationException {
                        return OperationResult.FAILURE;
                    }
                },
                new State.EnteringStateCallback() {
                    @Override
                    public void enteringState(final State newState, final Operation.OperationCallback operationCallback, final State.LeavingStateCallback leavingStateCallback) {
                        logger.info("Entering state " + newState.getName());
                        Assert.assertEquals(newState.getName(), "CAPTURE_FAILED");
                    }
                },
                new State.LeavingStateCallback() {
                    @Override
                    public void leavingState(final State oldState) {
                        logger.info("Leaving state " + oldState.getName());
                        Assert.assertEquals(oldState.getName(), "CAPTURE_INIT");
                    }
                }
        );

        try {
            state.runOperation(operation,
                    new Operation.OperationCallback() {
                        @Override
                        public OperationResult doOperationCallback() throws OperationException {
                            throw new OperationException();
                        }
                    },
                    new State.EnteringStateCallback() {
                        @Override
                        public void enteringState(final State newState, final Operation.OperationCallback operationCallback, final State.LeavingStateCallback leavingStateCallback) {
                            logger.info("Entering state " + newState.getName());
                            Assert.assertEquals(newState.getName(), "CAPTURE_ERRORED");
                        }
                    },
                    new State.LeavingStateCallback() {
                        @Override
                        public void leavingState(final State oldState) {
                            logger.info("Leaving state " + oldState.getName());
                            Assert.assertEquals(oldState.getName(), "CAPTURE_INIT");
                        }
                    }
            );
            Assert.fail("Should throw an exception");
        } catch (OperationException e) {
            Assert.assertTrue(true);
        }
    }

    @Test(groups = "fast")
    public void testLinkedStateMachines() throws Exception {

        final DefaultStateMachineConfig sms = XMLLoader.getObjectFromString(Resources.getResource("PaymentStates.xml").toExternalForm(), DefaultStateMachineConfig.class);

        final DefaultStateMachine sm1 = sms.getStateMachines()[0];
        final State state = sm1.getState("AUTH_SUCCESS");

        final DefaultStateMachine sm2 = sms.getStateMachines()[1];
        final Operation operation = sm2.getOperation("OP_CAPTURE");

        state.runOperation(operation,
                new Operation.OperationCallback() {
                    @Override
                    public OperationResult doOperationCallback() throws OperationException {
                        return OperationResult.SUCCESS;
                    }
                },
                new State.EnteringStateCallback() {
                    @Override
                    public void enteringState(final State newState, final Operation.OperationCallback operationCallback, final State.LeavingStateCallback leavingStateCallback) {
                        logger.info("Entering state " + newState.getName());
                        Assert.assertEquals(newState.getName(), "CAPTURE_SUCCESS");
                    }
                },
                new State.LeavingStateCallback() {
                    @Override
                    public void leavingState(final State oldState) {
                        logger.info("Leaving state " + oldState.getName());
                        Assert.assertEquals(oldState.getName(), "CAPTURE_INIT");
                    }
                }
        );
    }
}
