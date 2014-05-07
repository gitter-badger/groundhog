/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.groundhog.proxy;

import io.groundhog.capture.*;

import com.google.common.base.Throwables;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import org.littleshoot.proxy.HttpFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A capturing {@link HttpFilters}.
 *
 * @author Danny Thomas
 * @since 1.0
 */
public class CaptureHttpFilter implements HttpFilters {
  private static final Logger LOG = LoggerFactory.getLogger(CaptureHttpFilter.class);

  private final HttpCaptureDecoder captureDecoder;
  private final CaptureWriter captureWriter;
  private final String protocol;
  private final String host;
  private final int port;

  public CaptureHttpFilter(CaptureWriter captureWriter, File uploadLocation, String protocol, String host, int port) {
    captureDecoder = new DefaultHttpCaptureDecoder(uploadLocation);
    this.captureWriter = checkNotNull(captureWriter);
    this.protocol = checkNotNull(protocol);
    this.host = checkNotNull(host);
    checkArgument(port > 0, "Port must be greater than zero");
    this.port = port;
  }

  @Override
  public HttpResponse requestPre(HttpObject httpObject) {
    checkNotNull(httpObject);
    try {
      if (httpObject instanceof HttpRequest) {
        HttpRequest request = (HttpRequest) httpObject;
        if (CaptureHttpController.isControlRequest(request)) {
          return CaptureHttpController.handleControlRequest(request, captureWriter);
        }
      }

      captureDecoder.request(httpObject);
    } catch (Exception e) {
      LOG.error("Failed to capture request", e);
    }

    if (httpObject instanceof HttpRequest) {
      rewriteUri((HttpRequest) httpObject);
    }
    return null;
  }

  @Override
  public HttpResponse requestPost(HttpObject httpObject) {
    checkNotNull(httpObject);
    return null;
  }

  @Override
  public HttpObject responsePre(HttpObject httpObject) {
    checkNotNull(httpObject);
    try {
      captureDecoder.response(httpObject);
    } catch (Exception e) {
      LOG.error("Failed to capture response", e);
    }
    return httpObject;
  }

  @Override
  public HttpObject responsePost(HttpObject httpObject) {
    checkNotNull(httpObject);
    if (httpObject instanceof LastHttpContent) {
      try {
        CaptureRequest captureRequest = captureDecoder.complete();
        captureWriter.writeAsync(captureRequest);
      } catch (Exception e) {
        LOG.error("Failed to complete and write request", e);
      } finally {
        captureDecoder.destroy();
      }
    }
    return httpObject;
  }

  void rewriteUri(HttpRequest request) {
    try {
      // Use the java.net.URL class to parse the URI and retrieve it's path and query string.
      // URL is used because it handles decoded URLs, while URI only handles encoded URLs.
      // Note that request.getUri() may return the path without a scheme, domain and port.
      URL url;
      String uri = request.getUri();
      if (uri.startsWith("http")) {
        url = new URL(request.getUri());
      } else {
        if (uri.startsWith("/")) {
          url = new URL(protocol, host, port, request.getUri());
        } else {
          url = new URL(protocol, host, port, "/" + request.getUri());
        }
      }

      URL redirect = new URL(protocol, host, port, url.getFile());
      request.setUri(redirect.toExternalForm());

    } catch (MalformedURLException e) {
      LOG.error("A valid URL was not requested");
      Throwables.propagate(e);
    }
  }
}