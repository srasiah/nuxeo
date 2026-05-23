/*
 * (C) Copyright 2018-2024 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     pierre
 *     Mickaël Schoentgen
 *     Florent Guillaume
 */
package org.nuxeo.ecm.blob.s3;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.nuxeo.ecm.blob.s3.S3BlobProviderFeature.PREFIX_TEST;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.AWS_ID_PROPERTY;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.AWS_SECRET_PROPERTY;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.AWS_SESSION_TOKEN_PROPERTY;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.BUCKET_NAME_PROPERTY;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.BUCKET_PREFIX_PROPERTY;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.BUCKET_REGION_PROPERTY;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.SYSTEM_PROPERTY_PREFIX;
import static org.nuxeo.ecm.blob.s3.S3DirectBatchHandler.ROLE_ARN_PROPERTY;
import static software.amazon.awssdk.core.SdkSystemSetting.AWS_ROLE_ARN;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletionException;

import jakarta.inject.Inject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.BlobStoreBlobProvider;
import org.nuxeo.ecm.core.io.upload.batch.Batch;
import org.nuxeo.ecm.core.io.upload.batch.BatchFileInfo;
import org.nuxeo.ecm.core.io.upload.batch.BatchHandler;
import org.nuxeo.ecm.core.io.upload.batch.BatchManager;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.transientstore.keyvalueblob.KeyValueBlobTransientStore;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RunnerFeature;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.BlockingInputStreamAsyncRequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.Upload;

/**
 * Tests S3DirectBatchHandler.
 *
 * @since 10.2
 */
@RunWith(FeaturesRunner.class)
@Features({ TestS3DirectUploadAbstract.SetPropertiesFeature.class, CoreFeature.class })
@Deploy("org.nuxeo.ecm.core.management")
@Deploy("org.nuxeo.ecm.core.storage.binarymanager.s3")
public abstract class TestS3DirectUploadAbstract {

    public static final int MULTIPART_THRESHOLD = 5 * 1024 * 1024; // 5MB AWS minimum value

    public static final String TRANSIENT = "transient";

    public static final String S3DIRECT_PREFIX = String.format("%s.%s.", SYSTEM_PROPERTY_PREFIX, TRANSIENT);

    protected static final Random RANDOM = new Random(); // NOSONAR (doesn't need cryptographic strength)

    protected static String envId;

    protected static String envSecret;

    protected static String envToken;

    @Inject
    public BlobManager blobManager;

    @Inject
    public BatchManager batchManager;

    /*
     * Direct Upload extension points contain variables that need to be set before extension points are read, thus this
     * local Feature.
     */
    public static class SetPropertiesFeature implements RunnerFeature {
        @Override
        public void start(FeaturesRunner runner) {
            setProperties();
        }
    }

    public static void setProperties() {
        Map<String, String> properties = S3TestHelper.getProperties();
        properties.forEach(S3TestHelper::setProperty);

        envId = properties.get(AWS_ID_PROPERTY);
        envSecret = properties.get(AWS_SECRET_PROPERTY);
        envToken = properties.get(AWS_SESSION_TOKEN_PROPERTY);
        String envRegion = properties.get(BUCKET_REGION_PROPERTY);
        String roleArn = System.getenv(AWS_ROLE_ARN.environmentVariable());
        String transientBucketName = System.getProperty(
                String.format("%s%s.%s", PREFIX_TEST, TRANSIENT, BUCKET_NAME_PROPERTY));
        assumeTrue("AWS credentials, region, role and bucket not set in the environment variables",
                StringUtils.isNoneBlank(envId, envSecret, envRegion, roleArn, transientBucketName));

        String transientBucketPrefix = String.format("%s-%s/",
                StringUtils.removeEnd(properties.get(S3BlobStoreConfiguration.BUCKET_PREFIX_PROPERTY), "/"),
                "directUploadSource");

        // BatchHander config
        System.setProperty(S3DIRECT_PREFIX + AWS_ID_PROPERTY, envId);
        System.setProperty(S3DIRECT_PREFIX + AWS_SECRET_PROPERTY, envSecret);
        System.setProperty(S3DIRECT_PREFIX + AWS_SESSION_TOKEN_PROPERTY, envToken);
        System.setProperty(S3DIRECT_PREFIX + BUCKET_REGION_PROPERTY, envRegion);
        System.setProperty(S3DIRECT_PREFIX + BUCKET_NAME_PROPERTY, transientBucketName);
        System.setProperty(S3DIRECT_PREFIX + BUCKET_PREFIX_PROPERTY, transientBucketPrefix);
        System.setProperty(S3DIRECT_PREFIX + ROLE_ARN_PROPERTY, roleArn);
    }

    @After
    public void tearDown() {
        S3DirectBatchHandler handler = (S3DirectBatchHandler) batchManager.getHandler("s3");
        BlobProvider dubp = blobManager.getBlobProvider(handler.blobProviderId);
        clearBlobProvider(dubp);
        KeyValueBlobTransientStore ts = (KeyValueBlobTransientStore) handler.getTransientStore();
        clearBlobProvider(ts.getBlobProvider());
    }

    protected void clearBlobProvider(BlobProvider blobProvider) {
        ((BlobStoreBlobProvider) blobProvider).store.clear();
    }

    @Test
    public void testFails() {
        // create and initialize batch
        BatchHandler handler = batchManager.getHandler("s3");
        // complete upload with invalid key
        var e = assertThrows(NoSuchKeyException.class,
                () -> handler.completeUpload(null, null, new BatchFileInfo("invalid key", null, null, 10, null)));
        assertTrue(e.statusCode() == 404);
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.storage.binarymanager.s3.tests:OSGI-INF/test-s3directupload-fail-contrib.xml")
    public void testFailsOnCopyToTransientStore() {
        var e = assertThrows(NuxeoException.class, () -> test("s3fail", 1024));
        assertEquals("putBlobs failed", e.getMessage());
    }

    @Test
    public void testSmall() {
        test("s3", 1024);
    }

    // NXP-33505
    @Test
    public void testKeyWithVersionSeparator() {
        test("s3", "key-foo@bar", 1024);
    }

    @Test
    public void testMultipart() {
        test("s3", MULTIPART_THRESHOLD * 2);
    }

    @Test
    public void testTokenRenewal() {
        // Test that refreshing tokens actually returns back valid and different tokens.
        S3DirectBatchHandler handler = (S3DirectBatchHandler) batchManager.getHandler("s3");
        Batch newBatch = handler.newBatch(null);
        Batch batch = handler.getBatch(newBatch.getKey());

        // Save current details
        Map<String, Object> properties = batch.getProperties();
        String secretKeyId = (String) properties.get(S3DirectBatchHandler.INFO_AWS_SECRET_KEY_ID);
        String secretAccessKey = (String) properties.get(S3DirectBatchHandler.INFO_AWS_SECRET_ACCESS_KEY);
        String sessionToken = (String) properties.get(S3DirectBatchHandler.INFO_AWS_SESSION_TOKEN);
        Long expiration = (Long) properties.get(S3DirectBatchHandler.INFO_EXPIRATION);

        // Renew tokens
        properties = handler.refreshToken(newBatch.getKey());
        String newSecretKeyId = (String) properties.get(S3DirectBatchHandler.INFO_AWS_SECRET_KEY_ID);
        String newSecretAccessKey = (String) properties.get(S3DirectBatchHandler.INFO_AWS_SECRET_ACCESS_KEY);
        String newSessionToken = (String) properties.get(S3DirectBatchHandler.INFO_AWS_SESSION_TOKEN);
        Long newExpiration = (Long) properties.get(S3DirectBatchHandler.INFO_EXPIRATION);

        // Checks
        assertNotEquals(secretKeyId, newSecretKeyId);
        assertNotEquals(secretAccessKey, newSecretAccessKey);
        assertNotEquals(sessionToken, newSessionToken);
        assertTrue(expiration <= newExpiration);
    }

    @Test
    public void testTokenRenewalNullBatchId() {
        // Test that refreshing tokens does not work for an invalid batch ID.
        var handler = batchManager.getHandler("s3");
        var e = assertThrows(NullPointerException.class, () -> handler.refreshToken(null));
        assertTrue(e.getMessage().contains("required batch ID"));
    }

    protected void test(String handlerName, int size) {
        test(handlerName, size, true);
        tearDown();
        test(handlerName, size, false);
    }

    protected void test(String handlerName, int size, boolean keyLookingLikeADigest) {
        String key;
        if (keyLookingLikeADigest) {
            key = "01234567890123456789012345678901"; // same size as MD5
        } else {
            key = "key-" + System.nanoTime(); // with "-" to denote temporary digest
        }
        test(handlerName, key, size);
    }

    protected void test(String handlerName, String key, int size) {
        // generate unique key and random content of given size
        String name = "name" + System.nanoTime();
        byte[] content = generateRandomBytes(size);
        String expectedDigest = DigestUtils.md5Hex(content);

        // create and initialize batch
        S3DirectBatchHandler handler = (S3DirectBatchHandler) batchManager.getHandler(handlerName);
        Batch newBatch = handler.newBatch(null);
        Batch batch = handler.getBatch(newBatch.getKey());

        Map<String, Object> properties = batch.getProperties();
        String bucketName = (String) properties.get(S3DirectBatchHandler.INFO_BUCKET);
        String bucketPrefix = (String) properties.get(S3DirectBatchHandler.INFO_BASE_KEY);

        String prefixedKey = bucketPrefix + key;

        // client side upload
        clientSideUpload(properties, content, key);

        // create privileged client
        properties.put(S3DirectBatchHandler.INFO_AWS_SECRET_KEY_ID, envId);
        properties.put(S3DirectBatchHandler.INFO_AWS_SECRET_ACCESS_KEY, envSecret);
        properties.put(S3DirectBatchHandler.INFO_AWS_SESSION_TOKEN, envToken);

        // check the initial upload has been successful
        try (S3Client priviledgedS3Client = createS3Client(properties)) {
            priviledgedS3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(prefixedKey).build());
        } catch (NoSuchKeyException e) {
            fail("Upload failed");
        }

        BatchFileInfo info = new BatchFileInfo(prefixedKey, name, "text/plain", content.length, null);
        assertTrue(handler.completeUpload(batch.getKey(), key, info));

        // check content
        Blob blob = handler.getBatch(batch.getKey()).getBlob(key);
        try (InputStream stream = blob.getStream()) {
            byte[] bytes = IOUtils.toByteArray(stream);
            assertArrayEquals(content, bytes);
            assertEquals(expectedDigest, blob.getDigest());
            assertEquals("MD5", blob.getDigestAlgorithm());
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }

    protected void clientSideUpload(Map<String, Object> properties, byte[] content, String key) {
        // upload the content with our arbitrary key
        try (S3TransferManager tm = createTransferManager(properties)) {
            String bucket = (String) properties.get(S3DirectBatchHandler.INFO_BUCKET);
            String prefix = (String) properties.get(S3DirectBatchHandler.INFO_BASE_KEY);
            BlockingInputStreamAsyncRequestBody body = AsyncRequestBody.forBlockingInputStream(null);
            Upload upload = tm.upload(
                    builder -> builder.requestBody(body)
                                      .putObjectRequest(req -> req.bucket(bucket)
                                                                  .key(prefix + key)
                                                                  .contentLength((long) content.length)
                                                                  .contentType("text/plain"))
                                      .build());
            body.writeInputStream(new ByteArrayInputStream(content));

            try {
                upload.completionFuture().join();
            } catch (CompletionException e) {
                throw new NuxeoException(e);
            }
        }
    }

    protected S3Client createS3Client(Map<String, Object> properties) {
        boolean accelerated = (boolean) properties.get(S3DirectBatchHandler.INFO_USE_S3_ACCELERATE);
        String awsSessionToken = (String) properties.get(S3DirectBatchHandler.INFO_AWS_SESSION_TOKEN);
        String awsSecretKeyId = (String) properties.get(S3DirectBatchHandler.INFO_AWS_SECRET_KEY_ID);
        String awsSecretAccessKey = (String) properties.get(S3DirectBatchHandler.INFO_AWS_SECRET_ACCESS_KEY);
        AwsCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsSessionCredentials.create(awsSecretKeyId, awsSecretAccessKey, awsSessionToken));
        ApacheHttpClient.Builder apacheHttpClientBuilder = ApacheHttpClient.builder();
        S3ClientBuilder s3ClientBuilder = S3Client.builder()
                                                  .region(Region.of((String) properties.get(
                                                          S3DirectBatchHandler.INFO_AWS_REGION)))
                                                  .credentialsProvider(credentials)
                                                  .httpClientBuilder(apacheHttpClientBuilder);
        if (accelerated) {
            s3ClientBuilder.accelerate(true);
        }
        return s3ClientBuilder.build();
    }

    protected S3TransferManager createTransferManager(Map<String, Object> properties) {
        boolean accelerated = (boolean) properties.get(S3DirectBatchHandler.INFO_USE_S3_ACCELERATE);
        String awsSessionToken = (String) properties.get(S3DirectBatchHandler.INFO_AWS_SESSION_TOKEN);
        String awsSecretKeyId = (String) properties.get(S3DirectBatchHandler.INFO_AWS_SECRET_KEY_ID);
        String awsSecretAccessKey = (String) properties.get(S3DirectBatchHandler.INFO_AWS_SECRET_ACCESS_KEY);
        String region = (String) properties.get(S3DirectBatchHandler.INFO_AWS_REGION);
        AwsCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsSessionCredentials.create(awsSecretKeyId, awsSecretAccessKey, awsSessionToken));
        return S3TransferManager.builder()
                                .s3Client(S3AsyncClient.crtBuilder() // NOSONAR
                                                       .region(Region.of(region))
                                                       .accelerate(accelerated)
                                                       .credentialsProvider(credentials)
                                                       .thresholdInBytes((long) MULTIPART_THRESHOLD)
                                                       .build())
                                .build();
    }

    protected byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

}
