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

package io.groundhog.proxy

import com.google.common.base.Predicate
import com.google.common.testing.AbstractPackageSanityTests
import io.groundhog.har.HarFileCaptureWriter
import io.groundhog.capture.CaptureWriter

import javax.annotation.Nullable

/**
 * Package sanity tests for {@link io.groundhog.replay}.
 *
 * @author Danny Thomas
 * @since 1.0
 */
class PackageSanityTest extends AbstractPackageSanityTests {
  PackageSanityTest() {
    ignoreClasses(new Predicate<Class<?>>() {
      @Override
      boolean apply(@Nullable Class<?> input) {
        Proxy.class == input
      }
    })
    CaptureWriter captureWriter = new HarFileCaptureWriter(new File(""), false, false, false)
    CaptureFilterSource captureFilterSource = new CaptureFilterSource(captureWriter, new File(""), "", "", 1)
    setDefault(CaptureWriter.class, captureWriter)
    setDefault(CaptureFilterSource.class, captureFilterSource)
    setDefault(ProxyServer.class, new ProxyServer(captureWriter, captureFilterSource, "", 1))
  }
}
