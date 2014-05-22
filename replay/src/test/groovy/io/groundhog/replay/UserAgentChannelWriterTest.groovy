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

package io.groundhog.replay

import com.google.common.cache.LoadingCache
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import org.slf4j.Logger
import spock.lang.Specification

/**
 * Tests for {@link UserAgentChannelWriter}.
 */
class UserAgentChannelWriterTest extends Specification {
  def 'Connect header set to close following copy'() {
    def uaRequest = Mock(UserAgentRequest)
    uaRequest.headers() >> HttpHeaders.EMPTY_HEADERS
    def listener = Mock(ReplayResultListener)
    def writer = new UserAgentChannelWriter(uaRequest, listener, Mock(LoadingCache), Mock(Logger))
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")
    request.headers().add(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE)

    when:
    def copiedRequest = writer.copyRequest(request)

    then:
    copiedRequest.headers().get(HttpHeaders.Names.CONNECTION) == "close"
  }
}
