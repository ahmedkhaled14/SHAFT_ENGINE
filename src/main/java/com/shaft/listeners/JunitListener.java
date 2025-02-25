package com.shaft.listeners;

import com.shaft.driver.SHAFT;
import com.shaft.gui.internal.image.ImageProcessingActions;
import com.shaft.listeners.internal.JiraHelper;
import com.shaft.listeners.internal.TestNGListenerHelper;
import com.shaft.properties.internal.PropertiesHelper;
import com.shaft.tools.internal.security.GoogleTink;
import com.shaft.tools.io.internal.ExecutionSummaryReport;
import com.shaft.tools.io.internal.ProjectStructureManager;
import com.shaft.tools.io.internal.ReportManagerHelper;
import io.qameta.allure.Allure;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.*;
import org.testng.Reporter;

import java.util.ArrayList;
import java.util.List;

public class JunitListener implements LauncherSessionListener {
    private static final List<TestIdentifier> passedTests = new ArrayList<>();
    private static final List<TestIdentifier> failedTests = new ArrayList<>();
    private static final List<TestIdentifier> skippedTests = new ArrayList<>();
    private static long executionStartTime;
    private static boolean isEngineReady = false;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        if (!isEngineReady) {
            session.getLauncher().registerTestExecutionListeners(new TestExecutionListener() {
                @Override
                public void testPlanExecutionStarted(TestPlan testPlan) {
//                TestNGListenerHelper.setTotalNumberOfTests(suite);
                    executionStartTime = System.currentTimeMillis();
                    engineSetup();
                    isEngineReady = true;
                }

                @Override
                public void executionSkipped(TestIdentifier testIdentifier, String reason) {
                    afterInvocation();
                    onTestSkipped(testIdentifier, reason);
                }

                @Override
                public void executionStarted(TestIdentifier testIdentifier) {
                    //                JiraHelper.prepareTestResultAttributes(method, iTestResult);
//                TestNGListenerHelper.setTestName(iTestContext);
//                TestNGListenerHelper.logTestInformation(iTestResult);
//                TestNGListenerHelper.failFast(iTestResult);
//                TestNGListenerHelper.skipTestsWithLinkedIssues(iTestResult);
                }

                @Override
                public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
                    afterInvocation();
                    if (testIdentifier.isTest()) {
                        switch (testExecutionResult.getStatus()) {
                            case SUCCESSFUL -> onTestSuccess(testIdentifier);
                            case FAILED, ABORTED -> {
                                Throwable throwable = testExecutionResult.getThrowable().isPresent() ? testExecutionResult.getThrowable().get() : new AssertionError("Test Failed");
                                onTestFailure(testIdentifier, throwable);
                            }
                        }
                    }
                }
            });
        }
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        engineTeardown();
    }

    private void engineSetup() {
        ReportManagerHelper.setDiscreteLogging(true);
        PropertiesHelper.initialize();
        SHAFT.Properties.reporting.set().disableLogging(true);
        //TODO: Enable Properties Helper and refactor the old PropertyFileManager to read any unmapped user properties in a specific directory
        Allure.getLifecycle();
        Reporter.setEscapeHtml(false);
        ProjectStructureManager.initialize(ProjectStructureManager.Mode.JUNIT);
        TestNGListenerHelper.configureJVMProxy();
        GoogleTink.initialize();
        GoogleTink.decrypt();
        SHAFT.Properties.reporting.set().disableLogging(false);

        ReportManagerHelper.logEngineVersion();
        ImageProcessingActions.loadOpenCV();

        ReportManagerHelper.cleanExecutionSummaryReportDirectory();
        ReportManagerHelper.initializeAllureReportingEnvironment();
        ReportManagerHelper.initializeExtentReportingEnvironment();

        ReportManagerHelper.setDiscreteLogging(SHAFT.Properties.reporting.alwaysLogDiscreetly());
        ReportManagerHelper.setDebugMode(SHAFT.Properties.reporting.debugMode());
    }

    private void engineTeardown() {
        ReportManagerHelper.setDiscreteLogging(true);
        JiraHelper.reportExecutionStatusToJira();
        GoogleTink.encrypt();
        ReportManagerHelper.generateAllureReportArchive();
        ReportManagerHelper.openAllureReportAfterExecution();
        long executionEndTime = System.currentTimeMillis();
        ExecutionSummaryReport.generateExecutionSummaryReport(passedTests.size(), failedTests.size(), skippedTests.size(), executionStartTime, executionEndTime);
        ReportManagerHelper.logEngineClosure();
    }

    private void afterInvocation() {
//        IssueReporter.updateTestStatusInCaseOfVerificationFailure(iTestResult);
//        IssueReporter.updateIssuesLog(iTestResult);
//        TestNGListenerHelper.updateConfigurationMethodLogs(iTestResult);
        ReportManagerHelper.setDiscreteLogging(SHAFT.Properties.reporting.alwaysLogDiscreetly());
    }

    private void onTestSuccess(TestIdentifier testIdentifier) {
        passedTests.add(testIdentifier);
        appendToExecutionSummaryReport(testIdentifier, "", ExecutionSummaryReport.StatusIcon.PASSED, ExecutionSummaryReport.Status.PASSED);
    }

    private void onTestFailure(TestIdentifier testIdentifier, Throwable throwable) {
        failedTests.add(testIdentifier);
        appendToExecutionSummaryReport(testIdentifier, throwable.getMessage(), ExecutionSummaryReport.StatusIcon.FAILED, ExecutionSummaryReport.Status.FAILED);
    }

    private void onTestSkipped(TestIdentifier testIdentifier, String reason) {
        skippedTests.add(testIdentifier);
        appendToExecutionSummaryReport(testIdentifier, reason, ExecutionSummaryReport.StatusIcon.SKIPPED, ExecutionSummaryReport.Status.SKIPPED);
    }

    private void appendToExecutionSummaryReport(TestIdentifier testIdentifier, String errorMessage, ExecutionSummaryReport.StatusIcon statusIcon, ExecutionSummaryReport.Status status) {
        if (testIdentifier.getType().isTest()) {
            String caseSuite = testIdentifier.getUniqueIdObject().getSegments().get(1).getValue() + "." + testIdentifier.getUniqueIdObject().getLastSegment().getValue();
            String caseName = testIdentifier.getDisplayName();
            String caseDescription = testIdentifier.getLegacyReportingName();
            String statusMessage = statusIcon.getValue() + status.name();
            Boolean hasIssue = false;
            ExecutionSummaryReport.casesDetailsIncrement(caseSuite, caseName, caseDescription, errorMessage, statusMessage, hasIssue);
        }

    }
}
