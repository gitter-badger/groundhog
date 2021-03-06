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

package io.groundhog.capture;

import io.groundhog.base.HttpMessages;
import io.groundhog.base.URIScheme;
import io.groundhog.har.HttpArchive;

import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.net.MediaType;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class DefaultCaptureHttpDecoder implements CaptureHttpDecoder {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultCaptureHttpDecoder.class);

  private static final Set<HttpMethod> POST_DECODE_METHODS = Sets.newHashSet(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);
  private static final MediaType MULTIPART_FORM_DATA = MediaType.parse(HttpHeaders.Values.MULTIPART_FORM_DATA);
  private static final MediaType APPLICATION_X_WWW_FORM_URLENCODED = MediaType.parse(HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);

  private final CaptureWriter captureWriter;

  private long startedDateTime;
  private HttpRequest request;
  private boolean isPost;
  private HttpPostRequestDecoder decoder;
  private List<HttpArchive.Param> params;
  private StringBuilder content;
  private HttpResponse response;

  private boolean requestComplete;
  private boolean responseComplete;
  private boolean captureComplete;

  // Quick and dirty solution to print this warning once per request
  private boolean hasWarnedOctetStream;

  public DefaultCaptureHttpDecoder(CaptureWriter captureWriter) {
    this.captureWriter = checkNotNull(captureWriter);
  }

  @Override
  public void request(HttpObject httpObject) {
    requestInternal(httpObject, Optional.<URIScheme>absent());
  }

  @Override
  public void request(HttpObject httpObject, URIScheme scheme) {
    requestInternal(httpObject, Optional.of(scheme));
  }

  private void requestInternal(HttpObject httpObject, Optional<URIScheme> scheme) {
    checkNotNull(httpObject);
    checkNotNull(scheme);
    if (httpObject instanceof HttpRequest) {
      startedDateTime = System.currentTimeMillis();
      request = captureRequest((HttpRequest) httpObject, scheme.get());
    } else if (httpObject instanceof HttpContent && null != request) {
      HttpContent chunk = ((HttpContent) httpObject);
      HttpMethod method = request.getMethod();
      MediaType mediaType = HttpMessages.getMediaType(request);
      if (POST_DECODE_METHODS.contains(method)) {
        isPost = true;
        chunk = chunk.duplicate();
        if (mediaType.is(MediaType.ANY_TEXT_TYPE)) {
          if (null == content) {
            content = new StringBuilder();
          }
          ByteBuf buf = chunk.content();
          content.append(buf.copy().toString(Charsets.UTF_8));
        } else if (mediaType.is(MediaType.OCTET_STREAM)) {
          if (!hasWarnedOctetStream) {
            LOG.warn("{} media types are not currently handled, request {}", mediaType, request);
            hasWarnedOctetStream = true;
          }
        } else if (isDecodedMediaType(mediaType)) {
          if (null == decoder) {
            decoder = new HttpPostRequestDecoder(request);
          }
          if (null == params) {
            params = Lists.newArrayList();
          }
          decoder.offer(chunk);
          try {
            readAvailableData();
          } catch (IOException e) {
            throw Throwables.propagate(e);
          }
        } else {
          throw new IllegalArgumentException("Unsupported POST media type: " + mediaType);
        }
      }
      if (httpObject instanceof LastHttpContent) {
        requestComplete = true;
      }
    }
    writeIfComplete();
  }

  private boolean isDecodedMediaType(MediaType mediaType) {
    return mediaType.is(MULTIPART_FORM_DATA) || mediaType.is(APPLICATION_X_WWW_FORM_URLENCODED);
  }

  private void readAvailableData() throws IOException {
    try {
      while (decoder.hasNext()) {
        InterfaceHttpData data = decoder.next();
        HttpArchive.Param param;
        if (data instanceof Attribute) {
          Attribute attr = (Attribute) data;
          String name = attr.getName();
          String value = attr.getValue();
          param = new HttpArchive.Param(name, value);
        } else if (data instanceof FileUpload) {
          FileUpload upload = (FileUpload) data;
          String name = upload.getName();
          param = new HttpArchive.Param(name, upload.getFilename(), upload.getContentType());
          try {
            captureWriter.writeUpload(upload, startedDateTime);
          } finally {
            decoder.removeHttpDataFromClean(upload);
          }
        } else {
          throw new IOException("Unexpected data" + data.getClass());
        }
        params.add(param);
      }
    } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
      LOG.debug("Reached end of chunk");
    }
  }

  private HttpRequest captureRequest(HttpRequest httpRequest, URIScheme scheme) {
    checkNotNull(httpRequest);
    // Reflect the scheme, host and port and ensure that query parameters are encoded
    URL url = HttpMessages.getUrl(httpRequest, scheme);
    HttpRequest copiedRequest = new DefaultHttpRequest(httpRequest.getProtocolVersion(), httpRequest.getMethod(), url.toExternalForm());
    copiedRequest.headers().set(httpRequest.headers());
    return copiedRequest;
  }

  @Override
  public void response(HttpObject httpObject) {
    checkNotNull(httpObject);
    if (httpObject instanceof HttpResponse) {
      response = captureResponse((HttpResponse) httpObject);
    } else if (httpObject instanceof LastHttpContent) {
      responseComplete = true;
    }
    writeIfComplete();
  }

  private HttpResponse captureResponse(HttpResponse httpResponse) {
    checkNotNull(httpResponse);
    // As it stands, we don't need the response content, so we don't need to keep a FullHttpResponse, but in future we might
    HttpResponse response = new DefaultHttpResponse(httpResponse.getProtocolVersion(), httpResponse.getStatus());
    response.headers().set(httpResponse.headers());
    return response;
  }

  /**
   * Due to Netty's event driven nature, depending on the server behaviour it's possible to still be receiving a request
   * when the response has already been written, so we conditionally write based on whether the request and response
   * have been completed, rather than at the end of the response.
   */
  private void writeIfComplete() {
    if (!(requestComplete && responseComplete)) {
      return;
    }
    checkState(null != request, "Request hasn't been set");
    checkState(null != response, "Response hasn't been set");
    checkState(!captureComplete, "This decoder has already completed");
    CaptureRequest captureRequest;
    if (isPost) {
      if (null != content) {
        captureRequest = new DefaultCapturePostRequest(startedDateTime, request, response, content.toString());
      } else if (null != decoder) {
        captureRequest = new DefaultCapturePostRequest(startedDateTime, request, response, params);
        decoder.destroy();
      } else {
        captureRequest = new DefaultCaptureRequest(startedDateTime, request, response);
      }
    } else {
      captureRequest = new DefaultCaptureRequest(startedDateTime, request, response);
    }
    captureWriter.writeAsync(captureRequest);
    captureComplete = true;
  }

  @Override
  public String toString() {
    Objects.ToStringHelper helper = Objects.toStringHelper(this);
    helper.add("startedDateTime", startedDateTime);
    helper.add("request", request);
    helper.add("isPost", isPost);
    helper.add("decoder", decoder);
    helper.add("params", params);
    helper.add("content", content);
    helper.add("response", response);
    return helper.toString();
  }
}
