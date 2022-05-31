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
package io.ballerina.stdlib.http.api;

import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.flags.SymbolFlags;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.MethodType;
import io.ballerina.runtime.api.types.ServiceType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.stdlib.http.api.nativeimpl.ModuleUtils;
import io.ballerina.stdlib.http.transport.message.HttpCarbonMessage;
import io.ballerina.stdlib.http.uri.DispatcherUtil;
import io.ballerina.stdlib.http.uri.URITemplate;
import io.ballerina.stdlib.http.uri.URITemplateException;
import io.ballerina.stdlib.http.uri.parser.Literal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.ballerina.runtime.api.utils.StringUtils.fromString;
import static io.ballerina.stdlib.http.api.HttpConstants.DEFAULT_BASE_PATH;
import static io.ballerina.stdlib.http.api.HttpUtil.checkConfigAnnotationAvailability;

/**
 * {@code HttpService} This is the http wrapper for the {@code Service} implementation.
 *
 * @since 0.94
 */
public class HttpService implements Service {

    private static final Logger log = LoggerFactory.getLogger(HttpService.class);

    protected static final BString BASE_PATH_FIELD = fromString("basePath");
    private static final BString CORS_FIELD = fromString("cors");
    private static final BString VERSIONING_FIELD = fromString("versioning");
    private static final BString HOST_FIELD = fromString("host");
    private static final BString OPENAPI_DEF_FIELD = fromString("openApiDefinition");
    private static final BString MEDIA_TYPE_SUBTYPE_PREFIX = fromString("mediaTypeSubtypePrefix");
    private static final BString TREAT_NILABLE_AS_OPTIONAL = fromString("treatNilableAsOptional");

    private BObject balService;
    private List<HttpResource> resources;
    private List<String> allAllowedMethods;
    private String basePath;
    private CorsHeaders corsHeaders;
    private URITemplate<Resource, HttpCarbonMessage> uriTemplate;
    private boolean keepAlive = true; //default behavior
    private BMap<BString, Object> compression;
    private String hostName;
    private String chunkingConfig;
    private String mediaTypeSubtypePrefix;
    private String introspectionResourcePath;
    private boolean treatNilableAsOptional = true;
    private List<HTTPInterceptorServicesRegistry> interceptorServicesRegistries;
    private BArray balInterceptorServicesArray;
    private byte[] introspectionPayload = new byte[0];

    protected HttpService(BObject service, String basePath) {
        this.balService = service;
        this.basePath = basePath;
    }

    // Added due to WebSub requirement
    protected HttpService(BObject service) {
        this.balService = service;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    private void setCompressionConfig(BMap<BString, Object> compression) {
        this.compression = compression;
    }

    @Override
    public BMap<BString, Object> getCompressionConfig() {
        return this.compression;
    }

    public void setChunkingConfig(String chunkingConfig) {
        this.chunkingConfig = chunkingConfig;
    }

    @Override
    public String getChunkingConfig() {
        return chunkingConfig;
    }

    public String getName() {
        return HttpUtil.getServiceName(balService);
    }

    public String getPackage() {
        return balService.getType().getPackage().getName();
    }

    public BObject getBalService() {
        return balService;
    }

    public List<HttpResource> getResources() {
        return resources;
    }

    public void setResources(List<HttpResource> resources) {
        this.resources = resources;
    }

    @Override
    public List<String> getAllAllowedMethods() {
        return allAllowedMethods;
    }

    public void setAllAllowedMethods(List<String> allAllowMethods) {
        this.allAllowedMethods = allAllowMethods;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getHostName() {
        return hostName;
    }

    public void setMediaTypeSubtypePrefix(String mediaTypeSubtypePrefix) {
        this.mediaTypeSubtypePrefix = mediaTypeSubtypePrefix;
    }

    @Override
    public String getMediaTypeSubtypePrefix() {
        return mediaTypeSubtypePrefix;
    }

    public void setTreatNilableAsOptional(boolean treatNilableAsOptional) {
        this.treatNilableAsOptional = treatNilableAsOptional;
    }

    public boolean isTreatNilableAsOptional() {
        return treatNilableAsOptional;
    }

    @Override
    public String getBasePath() {
        return basePath;
    }

    // Added due to WebSub requirement
    public void setBasePath(String basePath) {
        if (basePath == null || basePath.trim().isEmpty()) {
            String serviceName = this.getName();
            this.basePath = DEFAULT_BASE_PATH.concat(serviceName.startsWith(HttpConstants.DOLLAR) ? "" : serviceName);
        } else {
            this.basePath = HttpUtil.sanitizeBasePath(basePath);
        }
    }

    public CorsHeaders getCorsHeaders() {
        return corsHeaders;
    }

    public void setCorsHeaders(CorsHeaders corsHeaders) {
        this.corsHeaders = corsHeaders;
        if (this.corsHeaders == null || !this.corsHeaders.isAvailable()) {
            return;
        }
        if (this.corsHeaders.getAllowOrigins() == null) {
            this.corsHeaders.setAllowOrigins(Stream.of("*").collect(Collectors.toList()));
        }
        if (this.corsHeaders.getAllowMethods() == null) {
            this.corsHeaders.setAllowMethods(Stream.of("*").collect(Collectors.toList()));
        }
    }

    public void setIntrospectionPayload(byte[] introspectionPayload) {
        this.introspectionPayload = introspectionPayload.clone();
    }

    public byte[] getIntrospectionPayload() {
        return this.introspectionPayload.clone();
    }

    @Override
    public URITemplate<Resource, HttpCarbonMessage> getUriTemplate() throws URITemplateException {
        if (uriTemplate == null) {
            uriTemplate = new URITemplate<>(new Literal<>(new ResourceDataElement(), "/"));
        }
        return uriTemplate;
    }

    public static HttpService buildHttpService(BObject service, String basePath) {
        HttpService httpService = new HttpService(service, basePath);
        BMap serviceConfig = getHttpServiceConfigAnnotation(service);
        if (checkConfigAnnotationAvailability(serviceConfig)) {
            httpService.setCompressionConfig(
                    (BMap<BString, Object>) serviceConfig.get(HttpConstants.ANN_CONFIG_ATTR_COMPRESSION));
            httpService.setChunkingConfig(serviceConfig.get(HttpConstants.ANN_CONFIG_ATTR_CHUNKING).toString());
            httpService.setCorsHeaders(CorsHeaders.buildCorsHeaders(serviceConfig.getMapValue(CORS_FIELD)));
            httpService.setHostName(serviceConfig.getStringValue(HOST_FIELD).getValue().trim());
            httpService.setIntrospectionPayload(serviceConfig.getArrayValue(OPENAPI_DEF_FIELD).getByteArray());
            if (serviceConfig.containsKey(MEDIA_TYPE_SUBTYPE_PREFIX)) {
                httpService.setMediaTypeSubtypePrefix(serviceConfig.getStringValue(MEDIA_TYPE_SUBTYPE_PREFIX)
                        .getValue().trim());
            }
            httpService.setTreatNilableAsOptional(serviceConfig.getBooleanValue(TREAT_NILABLE_AS_OPTIONAL));
        } else {
            httpService.setHostName(HttpConstants.DEFAULT_HOST);
        }
        processResources(httpService);
        httpService.setAllAllowedMethods(DispatcherUtil.getAllResourceMethods(httpService));
        return httpService;
    }

    private static void processResources(HttpService httpService) {
        List<HttpResource> httpResources = new ArrayList<>();
        for (MethodType resource : ((ServiceType) httpService.getBalService().getType()).getResourceMethods()) {
            if (!SymbolFlags.isFlagOn(resource.getFlags(), SymbolFlags.RESOURCE)) {
                continue;
            }
            updateResourceTree(httpService, httpResources, HttpResource.buildHttpResource(resource, httpService));
        }

        byte[] introspectionPayload = httpService.getIntrospectionPayload();
        if (introspectionPayload.length > 0) {
            updateResourceTree(httpService, httpResources, new HttpIntrospectionResource(httpService,
                                                                                         introspectionPayload));
        } else {
            log.debug("OpenAPI definition is not available");
        }
        processLinks(httpService, httpResources);
        httpService.setResources(httpResources);
    }

    private static void processLinks(HttpService httpService, List<HttpResource> httpResources) {
        for (HttpResource targetResource : httpResources) {
            for (HttpResource.LinkedResource link : targetResource.getLinkedResources()) {
                HttpResource linkedResource = null;
                for (HttpResource resource : httpResources) {
                    if (resource.getResourceName() != null && resource.getResourceName().equals(link.getName())) {
                        if (linkedResource == null) {
                            linkedResource = resource;
                        } else {
                            throw new BallerinaConnectorException("multiple resources found with name: '" +
                                                                  link.getName() + "'");
                        }
                    }
                }
                if (linkedResource != null) {
                    setLinkToResource(httpService, targetResource, link, linkedResource);
                } else {
                    throw new BallerinaConnectorException("cannot find resource with name: '" + link.getName() + "'");
                }
            }
        }
    }

    private static void setLinkToResource(HttpService httpService, HttpResource targetResource,
                                          HttpResource.LinkedResource link, HttpResource linkedResource) {
        BMap<BString, Object> linkMap = ValueCreatorUtils.createHTTPRecordValue(HttpConstants.LINK);
        BString relation = fromString(link.getRelationship());
        linkMap.put(HttpConstants.LINK_REL, relation);
        BString href = fromString(linkedResource.getAbsoluteResourcePath());
        linkMap.put(HttpConstants.LINK_HREF, href);
        BArray methods = getMethodsBArray(linkedResource.getMethods());
        if (methods != null) {
            linkMap.put(HttpConstants.LINK_METHODS, methods);
        }
        String returnMediaType = linkedResource.getReturnMediaType();
        if (httpService.getMediaTypeSubtypePrefix() != null) {
            String specificReturnMediaType = HttpUtil.getMediaTypeWithPrefix(httpService.getMediaTypeSubtypePrefix(),
                                                                             returnMediaType);
            if (specificReturnMediaType != null) {
                returnMediaType = specificReturnMediaType;
            }
        }
        if (returnMediaType != null) {
            BArray types = ValueCreator.createArrayValue(TypeCreator.createArrayType(PredefinedTypes.TYPE_STRING));
            types.append(fromString(returnMediaType));
            linkMap.put(HttpConstants.LINK_TYPES, types);
        }
        targetResource.addLink(relation, linkMap);
    }

    private static BArray getMethodsBArray(List<String> methods) {
        if (methods != null) {
            List<BString> methodsAsBString = methods.stream().map(StringUtils::fromString).collect(Collectors.toList());
            return ValueCreator.createArrayValue(methodsAsBString.toArray(BString[]::new));
        } else {
            return null;
        }
    }

    private static void updateResourceTree(HttpService httpService, List<HttpResource> httpResources,
                                           HttpResource httpResource) {
        try {
            httpService.getUriTemplate().parse(httpResource.getPath(), httpResource, new ResourceElementFactory());
        } catch (URITemplateException | UnsupportedEncodingException e) {
            throw new BallerinaConnectorException(e.getMessage());
        }
        httpResource.setTreatNilableAsOptional(httpService.isTreatNilableAsOptional());
        httpResources.add(httpResource);
    }

    private static BMap getHttpServiceConfigAnnotation(BObject service) {
        return getServiceConfigAnnotation(service, ModuleUtils.getHttpPackageIdentifier(),
                                          HttpConstants.ANN_NAME_HTTP_SERVICE_CONFIG);
    }

    protected static BMap getServiceConfigAnnotation(BObject service, String packagePath,
                                                     String annotationName) {
        String key = packagePath.replaceAll(HttpConstants.REGEX, HttpConstants.SINGLE_SLASH);
        return (BMap) (service.getType()).getAnnotation(fromString(key + ":" + annotationName));
    }

    @Override
    public String getIntrospectionResourcePathHeaderValue() {
        return this.introspectionResourcePath;
    }

    protected void setIntrospectionResourcePathHeaderValue(String introspectionResourcePath) {
        this.introspectionResourcePath = introspectionResourcePath;
    }

    public void setInterceptorServicesRegistries(List<HTTPInterceptorServicesRegistry> interceptorServicesRegistries) {
        this.interceptorServicesRegistries = interceptorServicesRegistries;
    }

    public List<HTTPInterceptorServicesRegistry> getInterceptorServicesRegistries() {
        return this.interceptorServicesRegistries;
    }

    public void setBalInterceptorServicesArray(BArray interceptorServicesArray) {
        this.balInterceptorServicesArray = interceptorServicesArray;
    }

    public BArray getBalInterceptorServicesArray() {
        return this.balInterceptorServicesArray;
    }

    public static void populateInterceptorServicesRegistries(List<HTTPInterceptorServicesRegistry>
                listenerLevelInterceptors, BArray interceptorsArrayFromListener, HttpService service, Runtime runtime) {
        List<HTTPInterceptorServicesRegistry> interceptorServicesRegistries = new ArrayList<>();
        for (HTTPInterceptorServicesRegistry servicesRegistry : listenerLevelInterceptors) {
            interceptorServicesRegistries.add(servicesRegistry);
        }

        BMap serviceConfig = getHttpServiceConfigAnnotation(service.getBalService());
        if (Objects.isNull(serviceConfig)) {
            service.setInterceptorServicesRegistries(interceptorServicesRegistries);
            service.setBalInterceptorServicesArray(interceptorsArrayFromListener);
            return;
        }

        BArray interceptorsArrayFromService = serviceConfig.getArrayValue(HttpConstants.ANN_INTERCEPTORS);
        if (Objects.isNull(interceptorsArrayFromService)) {
            service.setInterceptorServicesRegistries(interceptorServicesRegistries);
            service.setBalInterceptorServicesArray(interceptorsArrayFromListener);
            return;
        }

        Object[] interceptors = interceptorsArrayFromService.getValues();
        List<Object> interceptorServices = new ArrayList<>();
        for (Object interceptor: interceptors) {
            interceptorServices.add(interceptor);
        }

        // Registering all the interceptor services in separate service registries
        for (int i = 0; i < interceptorServices.size(); i++) {
            BObject interceptorService = (BObject) interceptorServices.get(i);
            HTTPInterceptorServicesRegistry servicesRegistry = new HTTPInterceptorServicesRegistry();
            servicesRegistry.setServicesType(HttpUtil.getInterceptorServiceType(interceptorService));
            servicesRegistry.registerInterceptorService(interceptorService, service.getBasePath(), false);
            servicesRegistry.setRuntime(runtime);
            interceptorServicesRegistries.add(servicesRegistry);
        }

        service.setInterceptorServicesRegistries(interceptorServicesRegistries);
        populateBalInterceptorServicesArray(service, interceptorsArrayFromListener, interceptorsArrayFromService);
    }

    private static void populateBalInterceptorServicesArray(HttpService service, BArray fromListener,
                                                            BArray fromService) {
        if (Objects.isNull(fromListener)) {
            service.setBalInterceptorServicesArray(fromService);
        } else {
            BArray interceptorsArray = ValueCreator.createArrayValue((ArrayType) fromListener.getType());
            for (Object interceptor : fromListener.getValues()) {
                if (Objects.isNull(interceptor)) {
                    break;
                }
                interceptorsArray.append(interceptor);
            }
            for (Object interceptor : fromService.getValues()) {
                interceptorsArray.append(interceptor);
            }
            service.setBalInterceptorServicesArray(interceptorsArray);
        }
    }

    public boolean hasInterceptors() {
        return Objects.nonNull(this.getInterceptorServicesRegistries()) &&
                !this.getInterceptorServicesRegistries().isEmpty();
    }
}
