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

package io.groundhog.servlet

import com.google.common.base.Splitter
import io.groundhog.Groundhog
import io.groundhog.capture.CaptureController
import io.groundhog.capture.CaptureWriter
import spock.lang.Specification

/**
 * Tests for {@link CaptureValve}.
 */
class CaptureValveTest extends Specification {
  def 'information contains implementation version'() {
    def valve = new CaptureValve(Mock(CaptureWriter), Mock(CaptureController))

    expect:
    valve.getInfo() == 'io.groundhog.servlet.CaptureValve/' + Groundhog.getVersion();
  }
}
