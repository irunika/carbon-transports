/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.wso2.carbon.transport.http.netty.listener;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.messaging.CarbonCallback;
import org.wso2.carbon.messaging.CarbonMessage;
import org.wso2.carbon.messaging.CarbonMessageProcessor;
import org.wso2.carbon.transport.http.netty.common.Constants;
import org.wso2.carbon.transport.http.netty.common.HttpRoute;
import org.wso2.carbon.transport.http.netty.common.Util;
import org.wso2.carbon.transport.http.netty.config.ListenerConfiguration;
import org.wso2.carbon.transport.http.netty.internal.HTTPTransportContextHolder;
import org.wso2.carbon.transport.http.netty.message.HTTPCarbonMessage;
import org.wso2.carbon.transport.http.netty.sender.channel.TargetChannel;
import org.wso2.carbon.transport.http.netty.sender.channel.pool.ConnectionManager;

import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * A Class responsible for handle  incoming message through netty inbound pipeline.
 */
public class SourceHandler extends ChannelInboundHandlerAdapter {
    private static Logger log = LoggerFactory.getLogger(SourceHandler.class);

    protected ChannelHandlerContext ctx;
    protected HTTPCarbonMessage cMsg;
    protected ConnectionManager connectionManager;
    private Map<String, TargetChannel> channelFutureMap = new HashMap<>();
    protected Map<String, GenericObjectPool> targetChannelPool;
    protected ListenerConfiguration listenerConfiguration;
    private WebSocketServerHandshaker handshaker;


    public ListenerConfiguration getListenerConfiguration() {
        return listenerConfiguration;
    }

    public SourceHandler(ConnectionManager connectionManager, ListenerConfiguration listenerConfiguration)
            throws Exception {
        this.listenerConfiguration = listenerConfiguration;
        this.connectionManager = connectionManager;
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        // Start the server connection Timer

        if (HTTPTransportContextHolder.getInstance().getHandlerExecutor() != null) {

            HTTPTransportContextHolder.getInstance().getHandlerExecutor()
                    .executeAtSourceConnectionInitiation(Integer.toString(ctx.hashCode()));
        }

        this.ctx = ctx;
        this.targetChannelPool = connectionManager.getTargetChannelPool();

    }

    @SuppressWarnings("unchecked")
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        if (msg instanceof FullHttpMessage) {

            publishToMessageProcessor(msg);
            ByteBuf content = ((FullHttpMessage) msg).content();
            cMsg.addHttpContent(new DefaultLastHttpContent(content));
            cMsg.setEndOfMsgAdded(true);
            if (HTTPTransportContextHolder.getInstance().getHandlerExecutor() != null) {
                HTTPTransportContextHolder.getInstance().getHandlerExecutor().executeAtSourceRequestSending(cMsg);
            }

        } else if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            HttpHeaders headers = request.headers();
            /*
            Checks whether the incoming HttpRequest is a WebSocket Upgrade Request
            or a normal HttpRequest
             */
            if (headers.get("Connection").equalsIgnoreCase("Upgrade")) {
                if (headers.get("Upgrade").equalsIgnoreCase("websocket")) {
                    log.info("Upgrading the connection from Http to WebSocket for " +
                                     "channel : " + ctx.channel());
                    handleWebSocketHandshake(ctx, request);
                    //Replace HTTP handlers  with  new Handlers for WebSocket in the pipeline
                    ChannelPipeline pipeline = ctx.pipeline();
                    pipeline.replace("handler",
                                     "ws_handler",
                                     new  WebSocketSourceHandler(this.connectionManager,
                                                                 this.listenerConfiguration,
                                                                 request.getUri()));
                    log.info("WebSocket upgrade is successful");
                }
            } else {
                publishToMessageProcessor(msg);
            }

        } else {
            if (cMsg != null) {
                if (msg instanceof HttpContent) {
                    HttpContent httpContent = (HttpContent) msg;
                    cMsg.addHttpContent(httpContent);
                    if (msg instanceof LastHttpContent) {
                        cMsg.setEndOfMsgAdded(true);
                        if (HTTPTransportContextHolder.getInstance().getHandlerExecutor() != null) {

                            HTTPTransportContextHolder.getInstance().getHandlerExecutor().
                                    executeAtSourceRequestSending(cMsg);
                        }

                    }
                }
            }
        }
    }


    /* Do the handshaking for WebSocket request */
    private void handleWebSocketHandshake(ChannelHandlerContext ctx, HttpRequest req) throws URISyntaxException {
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(getWebSocketURL(req),
                                                                                          null, true);
        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
        }
    }


    private String getWebSocketURL(HttpRequest req) {
        String url =  "ws://" + req.headers().get("Host") + req.getUri();
        return url;
    }

    //Carbon Message is published to registered message processor and Message Processor should return transport thread
    //immediately
    private void publishToMessageProcessor(Object msg) {
        cMsg = (HTTPCarbonMessage) setupCarbonMessage(msg);
        if (HTTPTransportContextHolder.getInstance().getHandlerExecutor() != null) {
            HTTPTransportContextHolder.getInstance().getHandlerExecutor().executeAtSourceRequestReceiving(cMsg);
        }

        boolean continueRequest = true;

        if (HTTPTransportContextHolder.getInstance().getHandlerExecutor() != null) {

            continueRequest = HTTPTransportContextHolder.getInstance().getHandlerExecutor()
                    .executeRequestContinuationValidator(cMsg, carbonMessage -> {
                        CarbonCallback responseCallback = (CarbonCallback) cMsg
                                .getProperty(org.wso2.carbon.messaging.Constants.CALL_BACK);
                        responseCallback.done(carbonMessage);
                    });

        }
        if (continueRequest) {
            CarbonMessageProcessor carbonMessageProcessor = HTTPTransportContextHolder.getInstance()
                    .getMessageProcessor();
            if (carbonMessageProcessor != null) {
                try {
                    carbonMessageProcessor.receive(cMsg, new ResponseCallback(this.ctx));
                } catch (Exception e) {
                    log.error("Error while submitting CarbonMessage to CarbonMessageProcessor", e);
                }
            } else {
                log.error("Cannot find registered MessageProcessor for forward the message");
            }
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Stop the connector timer
        ctx.close();
        if (HTTPTransportContextHolder.getInstance().getHandlerExecutor() != null) {
            HTTPTransportContextHolder.getInstance().getHandlerExecutor()
                    .executeAtSourceConnectionTermination(Integer.toString(ctx.hashCode()));
        }
        connectionManager.notifyChannelInactive();
    }

    public void addTargetChannel(HttpRoute route, TargetChannel targetChannel) {
        channelFutureMap.put(route.toString(), targetChannel);
    }

    public void removeChannelFuture(HttpRoute route) {
        log.debug("Removing channel future from map");
        channelFutureMap.remove(route.toString());
    }

    public TargetChannel getChannel(HttpRoute route) {
        return channelFutureMap.get(route.toString());
    }

    public Map<String, GenericObjectPool> getTargetChannelPool() {
        return targetChannelPool;
    }

    public ChannelHandlerContext getInboundChannelContext() {
        return ctx;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (ctx != null && ctx.channel().isActive()) {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    protected CarbonMessage setupCarbonMessage(Object msg) {
        cMsg = new HTTPCarbonMessage();
        if (HTTPTransportContextHolder.getInstance().getHandlerExecutor() != null) {
            HTTPTransportContextHolder.getInstance().getHandlerExecutor().executeAtSourceRequestReceiving(cMsg);
        }
        cMsg.setProperty(Constants.PORT, ((InetSocketAddress) ctx.channel().remoteAddress()).getPort());
        cMsg.setProperty(Constants.HOST, ((InetSocketAddress) ctx.channel().remoteAddress()).getHostName());

        HttpRequest httpRequest = (HttpRequest) msg;

        cMsg.setProperty(Constants.TO, httpRequest.getUri());
        cMsg.setProperty(Constants.CHNL_HNDLR_CTX, this.ctx);
        cMsg.setProperty(Constants.SRC_HNDLR, this);
        cMsg.setProperty(Constants.HTTP_VERSION, httpRequest.getProtocolVersion().text());
        cMsg.setProperty(Constants.HTTP_METHOD, httpRequest.getMethod().name());
        cMsg.setProperty(org.wso2.carbon.messaging.Constants.LISTENER_PORT,
                ((InetSocketAddress) ctx.channel().localAddress()).getPort());
        cMsg.setProperty(org.wso2.carbon.messaging.Constants.LISTENER_INTERFACE_ID, listenerConfiguration.getId());
        cMsg.setProperty(org.wso2.carbon.messaging.Constants.PROTOCOL, httpRequest.getProtocolVersion().protocolName());
        if (listenerConfiguration.getSslConfig() != null) {
            cMsg.setProperty(Constants.IS_SECURED_CONNECTION, true);
        } else {
            cMsg.setProperty(Constants.IS_SECURED_CONNECTION, false);
        }
        cMsg.setProperty(Constants.LOCAL_ADDRESS, ctx.channel().localAddress());
        cMsg.setProperty(Constants.LOCAL_NAME, ((InetSocketAddress) ctx.channel().localAddress()).getHostName());
        cMsg.setProperty(Constants.REMOTE_ADDRESS, ctx.channel().remoteAddress());
        cMsg.setProperty(Constants.REMOTE_HOST, ((InetSocketAddress) ctx.channel().remoteAddress()).getHostName());
        cMsg.setProperty(Constants.REMOTE_PORT, ((InetSocketAddress) ctx.channel().remoteAddress()).getPort());
        cMsg.setProperty(Constants.REQUEST_URL, httpRequest.getUri());
        ChannelHandler handler = ctx.handler();

        cMsg.setProperty(Constants.CHANNEL_ID, ((SourceHandler) handler).getListenerConfiguration().getId());

        cMsg.setHeaders(Util.getHeaders(httpRequest).getAll());
        return cMsg;
    }
}
