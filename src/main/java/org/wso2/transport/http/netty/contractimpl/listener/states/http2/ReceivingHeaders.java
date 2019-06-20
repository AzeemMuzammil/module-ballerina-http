/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.transport.http.netty.contractimpl.listener.states.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.contractimpl.Http2OutboundRespListener;
import org.wso2.transport.http.netty.contractimpl.common.Util;
import org.wso2.transport.http.netty.contractimpl.common.states.Http2MessageStateContext;
import org.wso2.transport.http.netty.contractimpl.listener.http2.Http2SourceHandler;
import org.wso2.transport.http.netty.contractimpl.listener.http2.InboundMessageHolder;
import org.wso2.transport.http.netty.message.Http2DataFrame;
import org.wso2.transport.http.netty.message.Http2HeadersFrame;
import org.wso2.transport.http.netty.message.Http2PushPromise;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;

import static org.wso2.transport.http.netty.contract.Constants.HTTP2_METHOD;
import static org.wso2.transport.http.netty.contract.Constants.HTTP_VERSION_2_0;
import static org.wso2.transport.http.netty.contractimpl.common.states.Http2StateUtil.notifyRequestListener;
import static org.wso2.transport.http.netty.contractimpl.common.states.Http2StateUtil.setupCarbonRequest;

/**
 * State between start and end of inbound request headers read.
 *
 * @since 6.0.241
 */
public class ReceivingHeaders implements ListenerState {

    private static final Logger LOG = LoggerFactory.getLogger(ReceivingHeaders.class);

    private final Http2SourceHandler http2SourceHandler;
    private final Http2MessageStateContext http2MessageStateContext;

    public ReceivingHeaders(Http2SourceHandler http2SourceHandler, Http2MessageStateContext http2MessageStateContext) {
        this.http2SourceHandler = http2SourceHandler;
        this.http2MessageStateContext = http2MessageStateContext;
    }

    @Override
    public void readInboundRequestHeaders(ChannelHandlerContext ctx, Http2HeadersFrame headersFrame)
            throws Http2Exception {
        int streamId = headersFrame.getStreamId();
        if (headersFrame.isEndOfStream()) {
            // Retrieve HTTP request and add last http content with trailer headers.
            InboundMessageHolder inboundMessageHolder = http2SourceHandler.getStreamIdRequestMap().get(streamId);
            HttpCarbonMessage sourceReqCMsg =
                    inboundMessageHolder != null ? inboundMessageHolder.getInboundMsgOrPushResponse() : null;
            if (sourceReqCMsg != null) {
                readTrailerHeaders(streamId, headersFrame.getHeaders(), sourceReqCMsg);
                //CHECK: Following should be removed only when the response has been sent back to the caller
//                http2SourceHandler.getStreamIdRequestMap().remove(streamId);
            } else if (headersFrame.getHeaders().contains(HTTP2_METHOD)) {
                // if the header frame is an initial header frame and also it has endOfStream
                sourceReqCMsg = setupHttp2CarbonMsg(headersFrame.getHeaders(), streamId);
                // Add empty last http content if no data frames available in the http request
                sourceReqCMsg.addHttpContent(new DefaultLastHttpContent());
                setEventListeners(ctx, streamId, sourceReqCMsg);

            }
            http2MessageStateContext.setListenerState(new EntityBodyReceived(http2MessageStateContext));
        } else {
            // Construct new HTTP Request
            HttpCarbonMessage sourceReqCMsg = setupHttp2CarbonMsg(headersFrame.getHeaders(), streamId);
            sourceReqCMsg.setHttp2MessageStateContext(http2MessageStateContext);
            setEventListeners(ctx, streamId, sourceReqCMsg);
            http2MessageStateContext.setListenerState(new ReceivingEntityBody(http2MessageStateContext));
        }
    }

    private void setEventListeners(ChannelHandlerContext ctx, int streamId, HttpCarbonMessage sourceReqCMsg) {
        InboundMessageHolder inboundMsgHolder = new InboundMessageHolder(sourceReqCMsg);
        // storing to add HttpContent later
        http2SourceHandler.getStreamIdRequestMap().put(streamId, inboundMsgHolder);
        http2SourceHandler.getHttp2ServerChannel().getDataEventListeners()
                .forEach(dataEventListener -> dataEventListener.onStreamInit(ctx, streamId));
        notifyRequestListener(http2SourceHandler, inboundMsgHolder, streamId);
    }

    @Override
    public void readInboundRequestBody(Http2SourceHandler http2SourceHandler, Http2DataFrame dataFrame) {
        LOG.warn("readInboundRequestBody is not a dependant action of this state");
    }

    @Override
    public void writeOutboundResponseHeaders(Http2OutboundRespListener http2OutboundRespListener,
                                             HttpCarbonMessage outboundResponseMsg, HttpContent httpContent,
                                             int streamId) {
        LOG.warn("writeOutboundResponseHeaders is not a dependant action of this state");
    }

    @Override
    public void writeOutboundResponseBody(Http2OutboundRespListener http2OutboundRespListener,
                                          HttpCarbonMessage outboundResponseMsg, HttpContent httpContent,
                                          int streamId) {
        LOG.warn("writeOutboundResponseBody is not a dependant action of this state");
    }

    @Override
    public void writeOutboundPromise(Http2OutboundRespListener http2OutboundRespListener,
                                     Http2PushPromise pushPromise) {
        LOG.warn("writeOutboundPromise is not a dependant action of this state");
    }

    private void readTrailerHeaders(int streamId, Http2Headers headers, HttpCarbonMessage responseMessage)
            throws Http2Exception {
        HttpVersion version = new HttpVersion(HTTP_VERSION_2_0, true);
        LastHttpContent lastHttpContent = new DefaultLastHttpContent();
        HttpHeaders trailers = lastHttpContent.trailingHeaders();
        HttpConversionUtil.addHttp2ToHttpHeaders(streamId, headers, trailers, version, true, false);
        responseMessage.addHttpContent(lastHttpContent);
    }

    private HttpCarbonMessage setupHttp2CarbonMsg(Http2Headers http2Headers, int streamId) throws Http2Exception {
        return setupCarbonRequest(Util.createHttpRequestFromHttp2Headers(http2Headers, streamId), http2SourceHandler,
                                  streamId);
    }
}
