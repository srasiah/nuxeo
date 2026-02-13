/*
 * (C) Copyright 2006-2025 Nuxeo (http://nuxeo.com/) and others.
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
 *     Tiry
 *     Florent Guillaume
 *     Estelle Giuly <egiuly@nuxeo.com>
 */
package org.nuxeo.ecm.core.convert.service;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.ws.rs.core.MediaType;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.concurrent.ThreadFactories;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.convert.api.ConversionStatus;
import org.nuxeo.ecm.core.convert.api.ConverterCheckResult;
import org.nuxeo.ecm.core.convert.api.ConverterNotAvailable;
import org.nuxeo.ecm.core.convert.api.ConverterNotRegistered;
import org.nuxeo.ecm.core.convert.cache.CacheKeyGenerator;
import org.nuxeo.ecm.core.convert.cache.ConversionCacheGCTask;
import org.nuxeo.ecm.core.convert.cache.ConversionCacheHolder;
import org.nuxeo.ecm.core.convert.extension.ChainedConverter;
import org.nuxeo.ecm.core.convert.extension.ConvertCacheDescriptor;
import org.nuxeo.ecm.core.convert.extension.Converter;
import org.nuxeo.ecm.core.convert.extension.ConverterDescriptor;
import org.nuxeo.ecm.core.convert.extension.ExternalConverter;
import org.nuxeo.ecm.core.convert.extension.GlobalConfigDescriptor;
import org.nuxeo.ecm.core.io.download.DownloadService;
import org.nuxeo.ecm.core.transientstore.work.TransientStoreWork;
import org.nuxeo.ecm.core.work.api.Work;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeEntry;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.runtime.RuntimeMessage;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Descriptor;
import org.nuxeo.runtime.services.config.ConfigurationService;

/**
 * Runtime Component that also provides the POJO implementation of the {@link ConversionService}.
 */
public class ConversionServiceImpl extends DefaultComponent implements ConversionService {

    private static final Logger log = LogManager.getLogger(ConversionServiceImpl.class);

    public static final String CONVERTER_EP = "converter";

    public static final String CONFIG_EP = "configuration";

    /**
     * @since 10.3
     */
    public static final String ENFORCE_SOURCE_MIME_TYPE_CHECK = "nuxeo.convert.enforceSourceMimeTypeCheck";

    protected final Map<String, ConverterDescriptor> converterDescriptors = new HashMap<>();

    protected final MimeTypeTranslationHelper translationHelper = new MimeTypeTranslationHelper();

    /**
     * @since 2025.0
     */
    protected ScheduledExecutorService gcExecutor;

    @Override
    public void activate(ComponentContext context) {
        super.activate(context);
        converterDescriptors.clear();
        translationHelper.clear();
    }

    @Override
    public void deactivate(ComponentContext context) {
        super.deactivate(context);
        converterDescriptors.clear();
        translationHelper.clear();
    }

    /**
     * Component implementation.
     */
    @Override
    @SuppressWarnings("removal")
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {

        if (CONVERTER_EP.equals(extensionPoint)) {
            ConverterDescriptor desc = (ConverterDescriptor) contribution;
            registerConverter(desc);
        } else if (CONFIG_EP.equals(extensionPoint)) {
            ConvertCacheDescriptor descriptor;
            if (contribution instanceof ConvertCacheDescriptor convertCacheDescriptor) {
                descriptor = convertCacheDescriptor;
            } else if (contribution instanceof GlobalConfigDescriptor globalConfigDescriptor) {
                addRuntimeMessage(RuntimeMessage.Level.WARNING,
                        "Global configuration is no more supported, please convert it to convert cache descriptor",
                        RuntimeMessage.Source.EXTENSION, name);
                descriptor = globalConfigDescriptor.toConvertCacheDescriptor();
            } else {
                throw new NuxeoException("Unexpected contribution type: " + contribution);
            }
            register(CONFIG_EP, descriptor);
        } else {
            log.error("Unable to handle unknown extensionPoint {}", extensionPoint);
        }
    }

    @Override
    public void unregisterContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (contribution instanceof ConvertCacheDescriptor descriptor) {
            unregister(CONVERTER_EP, descriptor);
        }
    }

    protected ConvertCacheDescriptor getConvertCacheDescriptor() {
        return getDescriptor(CONFIG_EP, Descriptor.UNIQUE_DESCRIPTOR_ID);
    }

    /* Component API */

    private static ConversionServiceImpl getConversionService() {
        return (ConversionServiceImpl) Framework.getService(ConversionService.class);
    }

    public static Converter getConverter(String converterName) {
        ConverterDescriptor desc = getConversionService().converterDescriptors.get(converterName);
        if (desc == null) {
            return null;
        }
        return desc.getConverterInstance();
    }

    public static ConverterDescriptor getConverterDescriptor(String converterName) {
        return getConversionService().converterDescriptors.get(converterName);
    }

    /**
     * @deprecated since 2025.0, not used anymore
     */
    @Deprecated(since = "2025.0", forRemoval = true)
    public static long getGCIntervalInMinutes() {
        return getConversionService().getConvertCacheDescriptor().getGcRate().toMinutes();
    }

    /**
     * @deprecated since 2025.0, such setting should be done with contribution
     */
    @Deprecated(since = "2025.0", forRemoval = true)
    public static void setGCIntervalInMinutes(long interval) {
        log.warn("setGCIntervalInMinutes is deprecated and does nothing anymore");
    }

    public static void registerConverter(ConverterDescriptor desc) {

        ConversionServiceImpl self = getConversionService();
        if (self.converterDescriptors.containsKey(desc.getConverterName())) {

            ConverterDescriptor existing = self.converterDescriptors.get(desc.getConverterName());
            desc = existing.merge(desc);
        }
        desc.initConverter();
        self.translationHelper.addConverter(desc);
        self.converterDescriptors.put(desc.getConverterName(), desc);
    }

    /**
     * @deprecated since 2025.0, not used anymore
     */
    @Deprecated(since = "2025.0", forRemoval = true)
    public static int getMaxCacheSizeInKB() {
        return (int) getConversionService().getConvertCacheDescriptor().getMaxSize().toKibibytes();
    }

    /**
     * @deprecated since 2025.0, such setting should be done with contribution
     */
    @Deprecated(since = "2025.0", forRemoval = true)
    public static void setMaxCacheSizeInKB(int size) {
        log.warn("setMaxCacheSizeInKB is deprecated and does nothing anymore");
    }

    /**
     * @deprecated since 2025.0, not used anymore
     */
    @Deprecated(since = "2025.0", forRemoval = true)
    public static boolean isCacheEnabled() {
        return getConversionService().getConvertCacheDescriptor().isEnabled();
    }

    /**
     * @deprecated since 2025.0, use {@link #getConvertCacheDirectory()} instead
     */
    @Deprecated(since = "2025.0", forRemoval = true)
    public static String getCacheBasePath() {
        return getConvertCacheDirectory().toString();
    }

    /**
     * @since 2025.0
     */
    public static Path getConvertCacheDirectory() {
        return getConversionService().getConvertCacheDescriptor().getDirectory();
    }

    /* Service API */

    @Override
    public List<String> getRegistredConverters() {
        return new ArrayList<>(converterDescriptors.keySet());
    }

    protected BlobHolder convertThroughHTML(BlobHolder blobHolder, String destMimeType) {
        Blob blob = blobHolder.getBlob();
        String mimetype = blob.getMimeType();
        String filename = blob.getFilename();
        if (destMimeType.equals(mimetype)) {
            return blobHolder;
        }

        Path tempDirectory = null;
        // Convert the blob to HTML
        if (!MediaType.TEXT_HTML.equals(mimetype)) {
            blobHolder = convertBlobToMimeType(blobHolder, MediaType.TEXT_HTML);
            blob = blobHolder.getBlob();
        }
        try {
            tempDirectory = Framework.createTempDirectory("blobs");
            // Replace the image URLs by absolute paths
            DownloadService downloadService = Framework.getService(DownloadService.class);
            blobHolder.setBlob(
                    replaceURLsByAbsolutePaths(blob, tempDirectory, downloadService::resolveBlobFromDownloadUrl));
            // Convert the blob to the destination mimetype
            blobHolder = convertBlobToMimeType(blobHolder, destMimeType);
            adjustBlobName(filename, blobHolder, destMimeType);
        } catch (IOException e) {
            throw new ConversionException(blobHolder, e);
        } finally {
            if (tempDirectory != null) {
                org.apache.commons.io.FileUtils.deleteQuietly(tempDirectory.toFile());
            }
        }
        return blobHolder;
    }

    protected BlobHolder convertBlobToMimeType(BlobHolder bh, String destinationMimeType) {
        return convertToMimeType(destinationMimeType, bh, Collections.emptyMap());
    }

    protected void adjustBlobName(String filename, BlobHolder blobHolder, String mimeType) {
        Blob blob = blobHolder.getBlob();
        adjustBlobName(filename, blob, mimeType);
        blobHolder.setBlob(blob);
    }

    protected void adjustBlobName(String filename, Blob blob, String mimeType) {
        if (StringUtils.isBlank(filename)) {
            filename = "file_" + System.currentTimeMillis();
        } else {
            filename = FilenameUtils.removeExtension(FilenameUtils.getName(filename));
        }
        String extension = Framework.getService(MimetypeRegistry.class)
                                    .getExtensionsFromMimetypeName(mimeType)
                                    .stream()
                                    .findFirst()
                                    .orElse("bin");
        blob.setFilename(filename + "." + extension);
        blob.setMimeType(mimeType);
    }

    /**
     * Replace the image URLs of an HTML blob by absolute local paths.
     *
     * @since 9.1
     */
    protected static Blob replaceURLsByAbsolutePaths(Blob blob, Path tempDirectory, Function<String, Blob> blobResolver)
            throws IOException {
        String initialBlobContent = blob.getString();
        // Find images links in the blob
        Pattern pattern = Pattern.compile("(src=([\"']))(.*?)(\\2)");
        Matcher matcher = pattern.matcher(initialBlobContent);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            // Retrieve the image from the URL
            String url = matcher.group(3);
            Blob imageBlob = blobResolver.apply(url);
            if (imageBlob == null) {
                break;
            }
            // Export the image to a temporary directory in File System
            String safeFilename = FileUtils.getSafeFilename(imageBlob.getFilename());
            File imageFile = tempDirectory.resolve(safeFilename).toFile();
            imageBlob.transferTo(imageFile);
            // Replace the image URL by its absolute local path
            matcher.appendReplacement(sb, "$1" + Matcher.quoteReplacement(imageFile.toPath().toString()) + "$4");
        }
        matcher.appendTail(sb);
        String blobContentWithAbsolutePaths = sb.toString();
        if (blobContentWithAbsolutePaths.equals(initialBlobContent)) {
            return blob;
        }
        // Create a new blob with the new content
        Blob newBlob = new StringBlob(blobContentWithAbsolutePaths, blob.getMimeType(), blob.getEncoding());
        newBlob.setFilename(blob.getFilename());
        return newBlob;
    }

    @Override
    public BlobHolder convert(String converterName, BlobHolder blobHolder, Map<String, Serializable> parameters)
            throws ConversionException {

        // set parameters if null to avoid NPE in converters
        if (parameters == null) {
            parameters = new HashMap<>();
        }

        // exit if not registered
        ConverterCheckResult check = isConverterAvailable(converterName);
        if (!check.isAvailable()) {
            // exit is not installed / configured
            throw new ConverterNotAvailable(converterName, blobHolder);
        }

        ConverterDescriptor desc = converterDescriptors.get(converterName);
        if (desc == null) {
            throw new ConversionException("Converter " + converterName + " can not be found", blobHolder);
        }

        if (blobHolder == null || blobHolder.getBlob() == null) {
            return null;
        }

        // make sure the converter can handle the blob mime type
        String mimeType = blobHolder.getBlob().getMimeType();
        if (!hasSourceMimeType(desc, mimeType)) {
            throw new ConversionException(
                    String.format("%s mime type not supported by %s converter", mimeType, desc.getConverterName()),
                    blobHolder);
        }

        // Check if conversion is unwanted
        if (desc.isBypassIfSameMimeType() && desc.getDestinationMimeType().equals(mimeType)) {
            return blobHolder;
        }

        String cacheKey = CacheKeyGenerator.computeKey(converterName, blobHolder, parameters);

        BlobHolder result = ConversionCacheHolder.getFromCache(cacheKey);

        if (result == null) {
            Converter converter = desc.getConverterInstance();
            result = converter.convert(blobHolder, parameters);

            if (getConvertCacheDescriptor().isEnabled()) {
                ConversionCacheHolder.addToCache(cacheKey, result);
            }
        } else if (result.getBlobs() != null && result.getBlobs().size() == 1) {
            // we need to reset the filename if result is a single file from the cache because the name is just a hash
            result.getBlob().setFilename(null);
        }

        if (result != null) {
            updateResultBlobMimeType(result, desc);
            updateResultBlobFileName(blobHolder, result);
        }

        return result;
    }

    /**
     * Returns true if the converter has the given {@code mimeType} as source mime type, false otherwise.
     *
     * @since 10.3
     */
    protected boolean hasSourceMimeType(ConverterDescriptor converterDescriptor, String mimeType) {
        if (!Framework.getService(ConfigurationService.class).isBooleanTrue(ENFORCE_SOURCE_MIME_TYPE_CHECK)) {
            return true;
        }

        return translationHelper.hasCompatibleMimeType(converterDescriptor.getSourceMimeTypes(), mimeType);
    }

    protected void updateResultBlobMimeType(BlobHolder resultBh, ConverterDescriptor desc) {
        Blob mainBlob = resultBh.getBlob();
        if (mainBlob == null) {
            return;
        }
        String mimeType = mainBlob.getMimeType();
        if (StringUtils.isBlank(mimeType) || mimeType.equals("application/octet-stream")) {
            mainBlob.setMimeType(desc.getDestinationMimeType());
        }
    }

    protected void updateResultBlobFileName(BlobHolder srcBh, BlobHolder resultBh) {
        Blob mainBlob = resultBh.getBlob();
        if (mainBlob == null) {
            return;
        }
        String filename = mainBlob.getFilename();
        if (StringUtils.isBlank(filename) || filename.startsWith("nxblob-")) {
            Blob srcBlob = srcBh.getBlob();
            if (srcBlob != null && StringUtils.isNotBlank(srcBlob.getFilename())) {
                String baseName = FilenameUtils.getBaseName(srcBlob.getFilename());

                MimetypeRegistry mimetypeRegistry = Framework.getService(MimetypeRegistry.class);
                MimetypeEntry mimeTypeEntry = mimetypeRegistry.getMimetypeEntryByMimeType(mainBlob.getMimeType());
                List<String> extensions = mimeTypeEntry.getExtensions();
                String extension;
                if (!extensions.isEmpty()) {
                    extension = extensions.getFirst();
                } else {
                    extension = FilenameUtils.getExtension(filename);
                    if (extension == null) {
                        extension = "bin";
                    }
                }
                mainBlob.setFilename(baseName + "." + extension);
            }

        }
    }

    @Override
    public BlobHolder convertToMimeType(String destinationMimeType, BlobHolder blobHolder,
            Map<String, Serializable> parameters) throws ConversionException {
        String srcMimeType = blobHolder.getBlob().getMimeType();
        String converterName = translationHelper.getConverterName(srcMimeType, destinationMimeType);
        if (converterName == null) {
            // check if a conversion is available through HTML
            converterName = translationHelper.getConverterName(srcMimeType, MediaType.TEXT_HTML);
            if (converterName == null) {
                throw new ConversionException(String.format("No converters available to convert from %s to %s.",
                        srcMimeType, destinationMimeType), blobHolder);
            }
            // Use a chain of 2 converters which will first try to go through HTML,
            // then HTML to the destination mimetype
            return convertThroughHTML(blobHolder, destinationMimeType);
        } else {
            return convert(converterName, blobHolder, parameters);
        }
    }

    @Override
    public List<String> getConverterNames(String sourceMimeType, String destinationMimeType, boolean allowWildcard) {
        return translationHelper.getConverterNames(sourceMimeType, destinationMimeType, allowWildcard);
    }

    @Override
    public String getConverterName(String sourceMimeType, String destinationMimeType, boolean allowWildcard) {
        return translationHelper.getConverterName(sourceMimeType, destinationMimeType, allowWildcard);
    }

    @Override
    public ConverterCheckResult isConverterAvailable(String converterName) throws ConversionException {
        return isConverterAvailable(converterName, false);
    }

    protected final Map<String, ConverterCheckResult> checkResultCache = new HashMap<>();

    @Override
    public ConverterCheckResult isConverterAvailable(String converterName, boolean refresh)
            throws ConverterNotRegistered {

        if (!refresh) {
            if (checkResultCache.containsKey(converterName)) {
                return checkResultCache.get(converterName);
            }
        }

        ConverterDescriptor descriptor = converterDescriptors.get(converterName);
        if (descriptor == null) {
            throw new ConverterNotRegistered(converterName);
        }

        Converter converter = descriptor.getConverterInstance();

        ConverterCheckResult result;
        if (converter instanceof ExternalConverter exConverter) {
            result = exConverter.isConverterAvailable();
        } else if (converter instanceof ChainedConverter chainedConverter) {
            result = new ConverterCheckResult();
            if (chainedConverter.isSubConvertersBased()) {
                for (String subConverterName : chainedConverter.getSubConverters()) {
                    result = isConverterAvailable(subConverterName, refresh);
                    if (!result.isAvailable()) {
                        break;
                    }
                }
            }
        } else {
            // return success since there is nothing to test
            result = new ConverterCheckResult();
        }

        result.setSupportedInputMimeTypes(descriptor.getSourceMimeTypes());
        checkResultCache.put(converterName, result);

        return result;
    }

    @Override
    public boolean isSourceMimeTypeSupported(String converterName, String sourceMimeType) {
        return getConverterDescriptor(converterName).getSourceMimeTypes().contains(sourceMimeType);
    }

    @Override
    public String scheduleConversion(String converterName, BlobHolder blobHolder,
            Map<String, Serializable> parameters) {
        WorkManager workManager = Framework.getService(WorkManager.class);
        ConversionWork work = new ConversionWork(converterName, null, blobHolder, parameters);
        workManager.schedule(work);
        return work.getId();
    }

    @Override
    public String scheduleConversionToMimeType(String destinationMimeType, BlobHolder blobHolder,
            Map<String, Serializable> parameters) {
        WorkManager workManager = Framework.getService(WorkManager.class);
        ConversionWork work = new ConversionWork(null, destinationMimeType, blobHolder, parameters);
        workManager.schedule(work);
        return work.getId();
    }

    @Override
    public ConversionStatus getConversionStatus(String id) {
        WorkManager workManager = Framework.getService(WorkManager.class);
        Work.State workState = workManager.getWorkState(id);
        if (workState == null) {
            String entryKey = TransientStoreWork.computeEntryKey(id);
            if (TransientStoreWork.containsBlobHolder(entryKey)) {
                return new ConversionStatus(id, ConversionStatus.Status.COMPLETED);
            }
            return null;
        }

        return new ConversionStatus(id, ConversionStatus.Status.valueOf(workState.name()));
    }

    @Override
    public BlobHolder getConversionResult(String id, boolean cleanTransientStoreEntry) {
        String entryKey = TransientStoreWork.computeEntryKey(id);
        BlobHolder bh = TransientStoreWork.getBlobHolder(entryKey);
        if (cleanTransientStoreEntry) {
            TransientStoreWork.removeBlobHolder(entryKey);
        }
        return bh;
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (adapter.isAssignableFrom(MimeTypeTranslationHelper.class)) {
            return adapter.cast(translationHelper);
        }
        return super.getAdapter(adapter);
    }

    @Override
    public void start(ComponentContext context) {
        var cacheDescriptor = getConvertCacheDescriptor();
        if (cacheDescriptor.isEnabled()) {
            // ensure cache directory exists
            ConversionCacheHolder.clearCache();
            startGC(cacheDescriptor);
        }
    }

    @Override
    public void stop(ComponentContext context) {
        if (getConvertCacheDescriptor().isEnabled()) {
            endGC();
            ConversionCacheHolder.clearCache();
        }
    }

    protected void startGC(ConvertCacheDescriptor cacheDescriptor) {
        log.debug("Convert cache enabled, starting GC executor");
        gcExecutor = Executors.newSingleThreadScheduledExecutor(
                ThreadFactories.newThreadFactory("Nuxeo-Convert-GC", true));
        long rateInSeconds = cacheDescriptor.getGcRate().toSeconds();
        gcExecutor.scheduleAtFixedRate(new ConversionCacheGCTask(cacheDescriptor.getMaxSize().toKibibytes()),
                rateInSeconds, rateInSeconds, TimeUnit.SECONDS);
    }

    public void endGC() {
        if (gcExecutor != null) {
            log.debug("Shutdown GC executor");
            gcExecutor.shutdown();
            gcExecutor = null;
        }
    }

}
