/*
 *  Copyright 2016-2023 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.qameta.allure.junitxml;

import io.qameta.allure.Issue;
import io.qameta.allure.entity.*;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author charlie (Dmitry Baev).
 */
class JunitXmlPluginTest extends JunitXmlPluginTestBase {

    @Test
    void shouldReadJunitResults() throws Exception {
        process(
                "junitdata/TEST-org.allurefw.report.junit.JunitTestResultsTest.xml",
                "TEST-org.allurefw.report.junit.JunitTestResultsTest.xml"
        );

        final ArgumentCaptor<TestResult> captor = ArgumentCaptor.forClass(TestResult.class);
        verify(visitor, times(5)).visitTestResult(captor.capture());


        assertThat(captor.getAllValues())
                .hasSize(5);

        List<TestResult> failed = filterByStatus(captor.getAllValues(), Status.FAILED);
        List<TestResult> skipped = filterByStatus(captor.getAllValues(), Status.SKIPPED);
        List<TestResult> passed = filterByStatus(captor.getAllValues(), Status.PASSED);

        assertThat(failed)
                .describedAs("Should parse failed status")
                .hasSize(1);

        assertThat(skipped)
                .describedAs("Should parse skipped status")
                .hasSize(1);

        assertThat(passed)
                .describedAs("Should parse passed status")
                .hasSize(3);
    }

    @Test
    void shouldAddLogAsAttachment() throws Exception {
        final Attachment hey = new Attachment().setUid("some-uid");
        when(visitor.visitAttachmentFile(any())).thenReturn(hey);
        process(
                "junitdata/TEST-test.SampleTest.xml", "TEST-test.SampleTest.xml",
                "junitdata/test.SampleTest.txt", "test.SampleTest.txt"
        );

        final ArgumentCaptor<Path> attachmentCaptor = ArgumentCaptor.forClass(Path.class);
        verify(visitor, times(1)).visitAttachmentFile(attachmentCaptor.capture());

        assertThat(attachmentCaptor.getValue())
                .isRegularFile()
                .hasContent("some-test-log");

        final ArgumentCaptor<TestResult> captor = ArgumentCaptor.forClass(TestResult.class);
        verify(visitor, times(1)).visitTestResult(captor.capture());

        final StageResult testStage = captor.getValue().getTestStage();
        assertThat(testStage)
                .describedAs("Should create a test stage")
                .isNotNull();

        assertThat(testStage.getAttachments())
                .describedAs("Should add an attachment")
                .hasSize(1)
                .describedAs("Attachment should has right uid and name")
                .extracting(Attachment::getName, Attachment::getUid)
                .containsExactly(Tuple.tuple("System out", "some-uid"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldAddLabels() throws Exception {
        process(
                "junitdata/TEST-test.SampleTest.xml", "TEST-test.SampleTest.xml"
        );

        final ArgumentCaptor<TestResult> captor = ArgumentCaptor.forClass(TestResult.class);
        verify(visitor, times(1)).visitTestResult(captor.capture());

        assertThat(captor.getAllValues())
                .hasSize(1)
                .flatExtracting(TestResult::getLabels)
                .extracting(Label::getName, Label::getValue)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(LabelName.SUITE.value(), "test.SampleTest"),
                        Tuple.tuple(LabelName.PACKAGE.value(), "test.SampleTest"),
                        Tuple.tuple(LabelName.TEST_CLASS.value(), "test.SampleTest"),
                        Tuple.tuple(LabelName.RESULT_FORMAT.value(), JunitXmlConstants.JUNIT_RESULTS_FORMAT)
                );
    }

    @Test
    void shouldSkipInvalidXml() throws Exception {
        process(
                "junitdata/invalid.xml", "sample-testsuite.xml"
        );

        verify(visitor, times(0)).visitTestResult(any());
    }

    @Test
    void shouldProcessTestsWithRetry() throws Exception {
        process(
                "junitdata/TEST-test.RetryTest.xml", "TEST-test.SampleTest.xml"
        );

        final ArgumentCaptor<TestResult> captor = ArgumentCaptor.forClass(TestResult.class);
        verify(visitor, times(4)).visitTestResult(captor.capture());

        final List<TestResult> results = captor.getAllValues();
        assertThat(results)
                .extracting(TestResult::getName, TestResult::getStatus, TestResult::isHidden, TestResult::getHistoryId)
                .containsExactlyInAnyOrder(
                        Tuple.tuple("searchTest", Status.BROKEN, false, "my.company.tests.SearchTest:my.company.tests.SearchTest#searchTest"),
                        Tuple.tuple("searchTest", Status.BROKEN, true, "my.company.tests.SearchTest:my.company.tests.SearchTest#searchTest"),
                        Tuple.tuple("searchTest", Status.BROKEN, true, "my.company.tests.SearchTest:my.company.tests.SearchTest#searchTest"),
                        Tuple.tuple("searchTest", Status.FAILED, true, "my.company.tests.SearchTest:my.company.tests.SearchTest#searchTest")
                );

        assertThat(results)
                .extracting(TestResult::getStatusMessage, TestResult::getStatusTrace)
                .containsExactlyInAnyOrder(
                        Tuple.tuple("message-root", "trace-root"),
                        Tuple.tuple("message-retried-1", "trace-retried-1"),
                        Tuple.tuple("message-retried-2", "trace-retried-2"),
                        Tuple.tuple("message-retried-3", "trace-retried-3")
                );
    }

    @Test
    void shouldReadStatusMessage() throws Exception {
        process(
                "junitdata/TEST-test.CdataMessage.xml", "TEST-test.SampleTest.xml"
        );


        final ArgumentCaptor<TestResult> captor = ArgumentCaptor.forClass(TestResult.class);
        verify(visitor, times(2)).visitTestResult(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(TestResult::getStatusMessage, TestResult::getStatusTrace)
                .containsExactlyInAnyOrder(
                        tuple("some-message", "some-trace"),
                        tuple(null,null)
                );
    }

    @Test
    void shouldReadSystemOutMessage() throws Exception {
        process(
                "junitdata/TEST-test.CdataMessage.xml", "TEST-test.SampleTest.xml"
        );

        final ArgumentCaptor<TestResult> captor = ArgumentCaptor.forClass(TestResult.class);
        verify(visitor, times(2)).visitTestResult(captor.capture());

        assertThat(captor.getAllValues())
                .filteredOn(result -> result.getTestStage().getSteps().size() == 2)
                .filteredOn(result -> result.getTestStage().getSteps().get(0).getName().equals("output"))
                .filteredOn(result -> result.getTestStage().getSteps().get(1).getName().equals("more output"));
    }

    @Issue("532")
    @Test
    void shouldParseSuitesTag() throws Exception {
        process(
                "junitdata/testsuites.xml", "TEST-test.SampleTest.xml"
        );

        final ArgumentCaptor<TestResult> captor = ArgumentCaptor.forClass(TestResult.class);
        verify(visitor, times(3)).visitTestResult(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(TestResult::getName)
                .containsExactlyInAnyOrder(
                        "should default path to an empty string",
                        "should default consolidate to true",
                        "should default useDotNotation to true"
                );
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldProcessTimestampIfPresent() throws Exception {
        process(
                "junitdata/with-timestamp.xml", "TEST-test.SampleTest.xml"
        );

        final ArgumentCaptor<TestResult> captor = ArgumentCaptor.forClass(TestResult.class);
        verify(visitor, times(1)).visitTestResult(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(TestResult::getTime)
                .extracting(Time::getStart, Time::getStop, Time::getDuration)
                .containsExactlyInAnyOrder(
                        tuple(1507199782999L, 1507199784050L, 1051L)
                );
    }

    @Test
    void shouldUseSuiteNameIfPresent() throws Exception {
        process(
                "junitdata/with-timestamp.xml", "TEST-test.SampleTest.xml"
        );

        final ArgumentCaptor<TestResult> captor = ArgumentCaptor.forClass(TestResult.class);
        verify(visitor, times(1)).visitTestResult(captor.capture());

        assertThat(captor.getAllValues())
                .flatExtracting(TestResult::getLabels)
                .filteredOn("name", "suite")
                .extracting(Label::getValue)
                .containsExactly("LocalSuiteIDOL");

    }

    @Test
    void shouldUseHostnameIfPresent() throws Exception {
        process(
                "junitdata/with-timestamp.xml", "TEST-test.SampleTest.xml"
        );

        final ArgumentCaptor<TestResult> captor = ArgumentCaptor.forClass(TestResult.class);
        verify(visitor, times(1)).visitTestResult(captor.capture());

        assertThat(captor.getAllValues())
                .flatExtracting(TestResult::getLabels)
                .filteredOn("name", "host")
                .extracting(Label::getValue)
                .containsExactly("cbgtalosbld02");

    }

    @Test
    void shouldReadSkippedStatus() throws Exception {
        process(
                "junitdata/TEST-status-attribute.xml", "TEST-test.SampleTest.xml"
        );

        final ArgumentCaptor<TestResult> captor = ArgumentCaptor.forClass(TestResult.class);
        verify(visitor, times(3)).visitTestResult(captor.capture());

        List<TestResult> skipped = filterByStatus(captor.getAllValues(), Status.SKIPPED);

        assertThat(skipped)
                .describedAs("Should parse skipped elements and status attribute")
                .hasSize(2);

    }

    @Test
    void shouldUsePropertiesIfPresent() throws Exception {
        process(
                "junitdata/TEST-test.PropertiesTest.xml", "TEST-test.SampleTest.xml"
        );


        final ArgumentCaptor<TestResult> captor = ArgumentCaptor.forClass(TestResult.class);
        verify(visitor, times(2)).visitTestResult(captor.capture());

        assertThat(captor.getAllValues())
                .flatExtracting(TestResult::getParameters)
                .extracting(Parameter::getName, Parameter::getValue)
                .containsExactlyInAnyOrder(
                        tuple("foo", "bar"),
                        tuple("baz", "some value")
                );
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldProcessFilesWithZuluTimestamp() throws Exception {
        process(
                "junitdata/zulu-timestamp.xml",
                "TEST-test.SampleTest.xml"
        );

        final ArgumentCaptor<TestResult> captor = ArgumentCaptor.forClass(TestResult.class);
        verify(visitor, times(2)).visitTestResult(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(TestResult::getTime)
                .extracting(Time::getStart, Time::getStop, Time::getDuration)
                .containsExactlyInAnyOrder(
                        tuple(1525592511000L, 1525592527211L, 16211L),
                        tuple(1525592511000L, 1525592519477L, 8477L)
                );
    }

}
