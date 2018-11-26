package io.rapidpro.surveyor.test;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import io.rapidpro.surveyor.SurveyorApplication;
import io.rapidpro.surveyor.SurveyorPreferences;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;

/**
 * Base for all the instrumented tests
 */
public abstract class BaseApplicationTest {

    protected MockWebServer mockServer;

    @Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    @Rule
    public TestRule logger = new TestWatcher() {
        protected void starting(Description description) {
            SurveyorApplication.LOG.d("========= Starting test: " + description.getClassName() + "#" + description.getMethodName() + " =========");
        }
    };

    @Before
    public void startMockServer() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        String mockServerURL = mockServer.url("/").toString();
        SurveyorApplication.LOG.d("mock server started at " + mockServerURL);

        getSurveyor().setPreference(SurveyorPreferences.HOST, mockServerURL);
        getSurveyor().onTembaHostChanged();
    }

    @After
    public void stopMockServer() throws IOException {
        mockServer.shutdown();

        SurveyorApplication.LOG.d("mock server stopped after " + mockServer.getRequestCount() + " requests");
    }

    /**
     * Clears all shared preferences after each test
     */
    @After
    public void clearPreferences() {
        SharedPreferences.Editor editor = getSurveyor().getPreferences().edit();
        editor.clear();
        editor.apply();
    }

    /**
     * Clears the file system after each test
     */
    @After
    public void clearFiles() throws IOException {
        FileUtils.deleteDirectory(getSurveyor().getOrgsDirectory());
        FileUtils.deleteDirectory(getSurveyor().getStorageDirectory());
    }

    protected SurveyorApplication getSurveyor() {
        return SurveyorApplication.get();
    }

    /**
     * Utility to appear logged in as the given user
     *
     * @param email    the email
     * @param orgUUIDs the set of accessible org UUIDs
     */
    protected void login(String email, Set<String> orgUUIDs) {
        getSurveyor().setPreference(SurveyorPreferences.AUTH_USERNAME, email);
        getSurveyor().setPreference(SurveyorPreferences.PREV_USERNAME, email);
        getSurveyor().setPreference(SurveyorPreferences.AUTH_ORGS, orgUUIDs);
    }

    /**
     * Utility to create an org directory
     *
     * @param uuid         the org UUID
     * @param detailsResId the resource ID of the details file
     */
    protected void installOrg(String uuid, int detailsResId, int flowsResId, int assetsResId) throws IOException {
        // create org directory
        File dir = new File(getSurveyor().getOrgsDirectory(), uuid);
        dir.mkdirs();

        // install details.json
        String detailsJSON = readRawResource(detailsResId);
        FileUtils.writeStringToFile(new File(dir, "details.json"), detailsJSON);

        if (flowsResId > 0) {
            // install flows.json
            String flowsJSON = readRawResource(flowsResId);
            FileUtils.writeStringToFile(new File(dir, "flows.json"), flowsJSON);
        } else {
            // a valid org must have details.json and flows.json
            FileUtils.writeStringToFile(new File(dir, "flows.json"), "[]");
        }

        if (assetsResId > 0) {
            // install assets.json
            String assetsJSON = readRawResource(assetsResId);
            FileUtils.writeStringToFile(new File(dir, "assets.json"), assetsJSON);
        }
    }

    /**
     * Enqueues a response on the mock HTTP server from the given body, MIME type and status code
     */
    protected void mockServerResponse(String body, String mimeType, int code) {
        MockResponse response = new MockResponse()
                .setBody(body)
                .setResponseCode(code)
                .addHeader("Content-Type", mimeType + "; charset=utf-8")
                .addHeader("Cache-Control", "no-cache");

        mockServer.enqueue(response);
    }

    /**
     * Enqueues a response on the mock HTTP server from the given resource file and MIME type and status code
     */
    protected void mockServerResponse(int rawResId, String mimeType, int code) throws IOException {
        mockServerResponse(readRawResource(rawResId), mimeType, code);
    }

    /**
     * Enqueues a redirect response on the mock HTTP server
     */
    protected void mockServerRedirect(String location) {
        MockResponse response = new MockResponse()
                .setResponseCode(HTTP_MOVED_TEMP)
                .setHeader("Location", location);

        mockServer.enqueue(response);
    }

    protected String readRawResource(int rawResId) throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        InputStream input = context.getResources().openRawResource(rawResId);
        return IOUtils.toString(input, StandardCharsets.UTF_8);
    }
}