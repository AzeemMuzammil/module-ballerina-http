/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.http.transport.contractimpl.sender;

import io.ballerina.stdlib.http.transport.contractimpl.common.certificatevalidation.RevocationVerificationManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.ReferenceCountedOpenSslEngine;
import io.netty.handler.ssl.ocsp.OcspClientHandler;
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLSession;

import static io.ballerina.stdlib.http.transport.contractimpl.common.certificatevalidation.Constants.CACHE_DEFAULT_ALLOCATED_SIZE;
import static io.ballerina.stdlib.http.transport.contractimpl.common.certificatevalidation.Constants.CACHE_DEFAULT_DELAY_MINS;

/**
 * A handler for OCSP stapling.
 */
public class OCSPStaplingHandler extends OcspClientHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OCSPStaplingHandler.class);

    public OCSPStaplingHandler(ReferenceCountedOpenSslEngine engine) {
        super(engine);
    }

    @Override
    protected boolean verify(ChannelHandlerContext ctx, ReferenceCountedOpenSslEngine engine)
            throws Exception {
        //Get the stapled ocsp response from the ssl engine.
        byte[] staple = engine.getOcspResponse();
        if (staple == null) {
            // If the response came from the server does not contain the OCSP staple, client attempts to validate
            // the certificate by directly calling OCSP access location and if that also fails, finally
            // do the CRL validation.
            RevocationVerificationManager revocationVerifier = new RevocationVerificationManager(
                    CACHE_DEFAULT_ALLOCATED_SIZE, CACHE_DEFAULT_DELAY_MINS);
            return revocationVerifier.verifyRevocationStatus(engine.getSession().getPeerCertificates());
        }

        OCSPResp response = new OCSPResp(staple);
        if (response.getStatus() != OCSPResponseStatus.SUCCESSFUL) {
            return false;
        }

        SSLSession session = engine.getSession();
        Certificate[] chain = session.getPeerCertificates();
        BigInteger certSerial = ((X509Certificate) chain[0]).getSerialNumber();

        BasicOCSPResp basicResponse = (BasicOCSPResp) response.getResponseObject();
        SingleResp singleResp = basicResponse.getResponses()[0];

        CertificateStatus status = singleResp.getCertStatus();
        BigInteger ocspSerial = singleResp.getCertID().getSerialNumber();
        if (LOG.isDebugEnabled()) {
            String message = "OCSP status of " + ctx.channel().remoteAddress() +
                    "\n  Status: " + (status == CertificateStatus.GOOD ? "Good" : status) +
                    "\n  This Update: " + singleResp.getThisUpdate() + "\n  Next Update: " +
                    singleResp.getNextUpdate() + "\n  Cert Serial: " + certSerial +
                    "\n  OCSP Serial: " + ocspSerial;
            LOG.debug(message);
        }
        //For an OCSP response to be valid, certificate serial number should be equal to the ocsp serial number.
        return status == CertificateStatus.GOOD && certSerial.equals(ocspSerial);
    }
}

