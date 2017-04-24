/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/

package org.wso2.carbon.transport.jms.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.messaging.CarbonMessageProcessor;
import org.wso2.carbon.transport.jms.exception.JMSConnectorException;
import org.wso2.carbon.transport.jms.factory.JMSConnectionFactory;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

/**
 * The {@link MessageConsumer} used to consume messages in carbon jms transport.
 */
public class JMSMessageConsumer implements MessageConsumer {

    private static final Logger logger = LoggerFactory.getLogger(JMSMessageConsumer.class);

    /**
     * The {@link JMSConnectionFactory} instance which is used to create the consumer.
     */
    private JMSConnectionFactory connectionFactory = null;

    /**
     * The JMS message consumer instance created by the respective JMS client.
     */
    private MessageConsumer messageConsumer;

    /**
     * Tells to use a message receiver instead of a message listener.
     */
    private boolean useReceiver = false;

    /**
     * The {@link Connection} instance represents the jms connection related with this server connector.
     */
    private Connection connection;

    /**
     * The {@link Session} instance represents the jms session related with this server connector.
     */
    private Session session;
    /**
     * The {@link Destination} instance represents a particular jms destination, this server connector listening to.
     */
    private Destination destination;

    /**
     * The service Id which this consumer belongs to.
     */
    private String serviceId;

    /**
     * The {@link CarbonMessageProcessor} instance represents the carbon message processor that handles the incoming
     * messages.
     */
    private CarbonMessageProcessor carbonMessageProcessor;

    /**
     * The {@link String} instance represents the jms connection username.
     */
    private String username;
    /**
     * The {@link String} instance represents the jms connection password.
     */
    private String password;

    /**
     * The retry handle which is going to retry connections when failed.
     */
    private JMSConnectionRetryHandler retryHandler;

    /**
     * The retry interval (in milli seconds) if the connection is lost or if the connection cannot be established.
     */
    private long retryInterval = 10000;
    /**
     * The maximum retry count, for retrying to establish a jms connection with the jms provider.
     */
    private int maxRetryCount = 5;

    /**
     * Create a JMS message consumer and start consuming messages.
     *
     * @param connectionFactory The connection factory to use when creating the connection
     * @param useReceiver Whether to use consumer.receive or use a listener when consuming messages
     * @param carbonMessageProcessor The message processor who is going to process messages consumed from this
     * @param serviceId The service Id which this consumer belongs to
     * @param username The username to use when connecting to the JMS provider
     * @param password The password to use when connecting to the JMS provider
     * @param retryInterval The retry interval in milliseconds to retry connection to JMS provider when failed
     * @param maxRetryCount The maximum retry count to retry when connection to the JMS provider fails
     * @throws JMSConnectorException
     */
    public JMSMessageConsumer(JMSConnectionFactory connectionFactory, boolean useReceiver,
                              CarbonMessageProcessor carbonMessageProcessor, String serviceId, String username,
                              String password, long retryInterval, int maxRetryCount) throws JMSConnectorException {
        this.connectionFactory = connectionFactory;
        this.useReceiver = useReceiver;
        this.username = username;
        this.password = password;
        this.carbonMessageProcessor = carbonMessageProcessor;
        this.serviceId = serviceId;
        this.retryInterval = retryInterval;
        this.maxRetryCount = maxRetryCount;

        retryHandler = new JMSConnectionRetryHandler(this, retryInterval, maxRetryCount);

        startConsuming();
    }

    /**
     * Create consumers and starting consumer threads.
     *
     * @throws JMSConnectorException if starting consumer fails due to a JMS layer error.
     */
    void startConsuming() throws JMSConnectorException {

        try {
            if (null != username && null != password) {
                connection = connectionFactory.createConnection(username, password);
            } else {
                connection = connectionFactory.createConnection();
            }

            ExceptionListener exceptionListener = connection.getExceptionListener();
            if (exceptionListener == null) {
                ExceptionListener jmsExceptionListener = new JMSExceptionListener(this);
                connection.setExceptionListener(jmsExceptionListener);
            } else {
                if (exceptionListener instanceof JMSExceptionListener) {
                    ((JMSExceptionListener) exceptionListener).addConsumer(this);
                } else {
                    throw new JMSConnectorException(
                            "This connection is already assigned with an unknown exception handler");
                }
            }

            connectionFactory.start(connection);
            session = connectionFactory.createSession(connection);

            Destination destination = connectionFactory.getDestination(session);

            messageConsumer = connectionFactory.createMessageConsumer(session, destination);

            if (useReceiver) {
                createMessageReceiver();
            } else {
                createMessageListener();
            }

        } catch (JMSException | JMSConnectorException e) {
            if (!retryHandler.retry()) {
                throw new JMSConnectorException("Connection to JMS server failed and retry was not successful", e);
            }
        }
    }

    /**
     * Close the consumer by closing the relevant {@link Connection}
     *
     * @throws JMSConnectorException if closing the connection fails
     */
    void closeAll() throws JMSConnectorException {

        try {

            if (connection != null) {
                connectionFactory.closeConnection(connection);

                connection = null;
            }

        } catch (JMSConnectorException e) {
            throw new JMSConnectorException("Error closing connection of JMS Service " + serviceId, e);
        } finally {
            messageConsumer = null;
            session = null;
            connection = null;
        }
    }


    /**
     * Create a message listener to a particular jms destination.
     *
     * @throws JMSConnectorException JMS Connector exception can be thrown when trying to connect to jms provider
     */
    private void createMessageListener() throws JMSConnectorException {

        try {
            messageConsumer.setMessageListener(new JMSMessageListener(carbonMessageProcessor, serviceId, session));

            if (logger.isDebugEnabled()) {
                logger.debug("Message listener created for service " + serviceId);
            }

        } catch (JMSException e) {
            throw new JMSConnectorException("Error while initializing message listener", e);
        }
    }

    /**
     * Create a message receiver to retrieve messages.
     *
     * @throws JMSConnectorException Can be thrown when initializing the message handler or receiving messages
     */
    private void createMessageReceiver() throws JMSConnectorException {

        if (logger.isDebugEnabled()) {
            logger.debug("Creating message receiver for service " + serviceId);
        }

        JMSMessageReceiver messageReceiver =
                new JMSMessageReceiver(carbonMessageProcessor, serviceId, session, this);
        messageReceiver.receive();
    }

    void stop() throws JMSConnectorException {
        connectionFactory.stop(connection);
    }

    void start() throws JMSConnectorException {
        connectionFactory.start(connection);
    }

    @Override
    public String getMessageSelector() throws JMSException {
        return messageConsumer.getMessageSelector();
    }

    @Override
    public MessageListener getMessageListener() throws JMSException {
        return messageConsumer.getMessageListener();
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSException {
        messageConsumer.setMessageListener(listener);
    }

    @Override
    public Message receive() throws JMSException {
        return messageConsumer.receive();
    }

    @Override
    public Message receive(long timeout) throws JMSException {
        return messageConsumer.receive(timeout);
    }

    @Override
    public Message receiveNoWait() throws JMSException {
        return messageConsumer.receiveNoWait();
    }

    @Override
    public void close() throws JMSException {
        messageConsumer.close();
    }
}

class JMSMessageConsumerBuilder {
    /**
     * The {@link JMSConnectionFactory} instance which is used to create the consumer.
     */
    private JMSConnectionFactory connectionFactory = null;

    /**
     * Tells to use a message receiver instead of a message listener.
     */
    private boolean useReceiver = false;

    /**
     * The service Id which this consumer belongs to.
     */
    private String serviceId;

    /**
     * The {@link CarbonMessageProcessor} instance represents the carbon message processor that handles the incoming
     * messages.
     */
    private CarbonMessageProcessor carbonMessageProcessor;

    /**
     * The {@link String} instance represents the jms connection username.
     */
    private String username;
    /**
     * The {@link String} instance represents the jms connection password.
     */
    private String password;

    /**
     * The retry interval (in milli seconds) if the connection is lost or if the connection cannot be established.
     */
    private long retryInterval = 10000;
    /**
     * The maximum retry count, for retrying to establish a jms connection with the jms provider.
     */
    private int maxRetryCount = 5;

    /**
     * Initialize the builder with mandatory properties.
     *
     * @param connectionFactory The connection factory to use when creating the JMS connection
     * @param carbonMessageProcessor The message processor who is going to process the consumed messages from this
     * @param serviceId The service Id which invoked this consumer
     */
    public JMSMessageConsumerBuilder(JMSConnectionFactory connectionFactory,
                                     CarbonMessageProcessor carbonMessageProcessor, String serviceId) {
        this.connectionFactory = connectionFactory;
        this.carbonMessageProcessor = carbonMessageProcessor;
        this.serviceId = serviceId;
    }

    public JMSMessageConsumerBuilder setUseReceiver(boolean useReceiver) {
        this.useReceiver = useReceiver;
        return this;
    }

    public JMSMessageConsumerBuilder setUsername(String username) {
        this.username = username;
        return this;
    }

    public JMSMessageConsumerBuilder setPassword(String password) {
        this.password = password;
        return this;
    }

    public JMSMessageConsumerBuilder setRetryInterval(long retryInterval) {
        this.retryInterval = retryInterval;
        return this;
    }

    public JMSMessageConsumerBuilder setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
        return this;
    }

    /**
     * Build the {@link JMSMessageConsumer} with the given data.
     * @return the JMS consumer initialized with all required data
     *
     * @throws JMSConnectorException If initializing the consumer fails
     */
    public JMSMessageConsumer build() throws JMSConnectorException {
        return new JMSMessageConsumer(connectionFactory, useReceiver, carbonMessageProcessor, serviceId, username,
                password, retryInterval, maxRetryCount);
    }

}
