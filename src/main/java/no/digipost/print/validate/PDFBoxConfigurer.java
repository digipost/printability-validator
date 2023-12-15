/*
 * Copyright (C) Posten Bring AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.digipost.print.validate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Configure certain aspects of PDFBox, if the defaults provided by the
 * printability-validator are not suitable. These settings are configured using
 * system properties, and to make them effective, they need to be set early,
 * i.e. <strong>before</strong> the static initializer in
 * {@link PdfValidator} is executed.
 * <p>
 * See <a href="https://pdfbox.apache.org/2.0/getting-started.html#pdfbox-and-java-8">pdfbox.apache.org/2.0/getting-started.html#pdfbox-and-java-8</a>
 *
 */
public final class PDFBoxConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(PDFBoxConfigurer.class);

    static final class PDFBoxConfiguration {
        volatile boolean enabled = true;

        volatile boolean useKcmsServiceProvider = true;

        volatile boolean usePureJavaCMYKConversion = true;

        private PDFBoxConfiguration() {
        }
    }

    private static final String alreadyConfiguredWarn =
            "PDFBox system properties has already been configured, and calling {} may not be effective! " +
            "Make sure you call this method early, before using the PdfValidator.";

    private static final AtomicBoolean configured = new AtomicBoolean(false);
    private static final PDFBoxConfiguration pdfBoxConfiguration = new PDFBoxConfiguration();

    public static void doNotConfigurePDFBox() {
        if (configured.get()) {
            LOG.warn(alreadyConfiguredWarn, "doNotConfigurePDFBox()");
        }
        pdfBoxConfiguration.enabled = false;
    }

    public static void useKcmsServiceProvider(boolean use) {
        if (configured.get()) {
            LOG.warn(alreadyConfiguredWarn, "useKcmsServiceProvider(" + use + ")");
        }
        pdfBoxConfiguration.useKcmsServiceProvider = use;
    }

    public static void usePureJavaCMYKConversion(boolean use) {
        if (configured.get()) {
            LOG.warn(alreadyConfiguredWarn, "usePureJavaCMYKConversion(" + use + ")");
        }
        pdfBoxConfiguration.useKcmsServiceProvider = use;
    }

    static synchronized void configure() {
        configured.set(true);
        if (pdfBoxConfiguration.enabled) {
            if (pdfBoxConfiguration.useKcmsServiceProvider) {
                LOG.info(
                        "Configuring sun.java2d.cmm=sun.java2d.cmm.kcms.KcmsServiceProvider as described at " +
                        "https://pdfbox.apache.org/2.0/getting-started.html#pdfbox-and-java-8 " +
                        "to increase PDF color operation.");
                System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider");
            }
            if (pdfBoxConfiguration.usePureJavaCMYKConversion) {
                LOG.info(
                        "Configuring org.apache.pdfbox.rendering.UsePureJavaCMYKConversion=true as described at " +
                        "https://pdfbox.apache.org/2.0/getting-started.html#rendering-performance " +
                        "to increase PDF rendering performance.");
                System.setProperty("org.apache.pdfbox.rendering.UsePureJavaCMYKConversion", "true");
            }
        } else {
            LOG.info("Using default settings for PDFBox for printability-validator library");
        }
    }

    private PDFBoxConfigurer() {
    }
}
