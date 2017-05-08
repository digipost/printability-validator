/**
 * Copyright (C) Posten Norge AS
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

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static no.digipost.print.validate.PdfValidationError.*;
import static no.digipost.print.validate.PdfValidationSettings.CHECK_ALL;
import static org.apache.commons.lang3.Validate.notNull;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class PrintPdfValidatorTest {

    private static final PdfValidator pdfValidator = new PdfValidator();

    @Test
    public void validatesPdfForPrint() {
        assertThat(validationErrors("/pdf/a4-left-margin-15_1mm.pdf", CHECK_ALL), empty());
        assertThat(validationErrors("/pdf/a4-landscape-left-margin-15_1mm.pdf", CHECK_ALL), empty());
        assertThat(validationErrors("/pdf/a4-free-barcode-area.pdf", CHECK_ALL), empty());
        assertThat(validationErrors("/pdf/nearly-a4-free-barcode-area.pdf", CHECK_ALL), empty());
        assertThat(validationErrors("/pdf/a4-landscape-free-barcode-area.pdf", CHECK_ALL), empty());
        assertThat(validationErrors("/pdf/nearly-a4-rotated_free-barcode-area.pdf", CHECK_ALL), empty());
        assertThat(validationErrors("/pdf/a4-landscape.pdf", CHECK_ALL), empty());

        assertThat(validationErrors("/pdf/a4-left-margin-20mm_v16.pdf", CHECK_ALL), empty());
        assertThat(validationErrors("/pdf/a4-landscape_v16.pdf", CHECK_ALL), empty());

        assertThat(validationErrors("/pdf/a4-left-margin-20mm_v17.pdf", CHECK_ALL), empty());
        assertThat(validationErrors("/pdf/a4-landscape_v17.pdf", CHECK_ALL), empty());
    }

    @Test
    public void failsDueToMissingEmbeddedFont() {
        assertThat(validationErrors("/pdf/uten-embeddede-fonter.pdf", CHECK_ALL), contains(REFERENCES_INVALID_FONT));
    }

    @Test
    public void doesNotFailDueToMissingEmbeddedFontIfCheckDisabledInSettings() {
        PdfValidationSettings innstillinger = new PdfValidationSettings(true, false, true, true);
        assertThat(validationErrors("/pdf/uten-embeddede-fonter.pdf", innstillinger), empty());
    }

    @Test
    public void failsPdfWithInsufficientMarginForPrint() {
        assertThat(validationErrors("/pdf/far-from-a4-free-barcode-area.pdf", CHECK_ALL), contains(UNSUPPORTED_DIMENSIONS, INSUFFICIENT_MARGIN_FOR_PRINT));
        assertThat(validationErrors("/pdf/a4-full-page.pdf", CHECK_ALL), contains(INSUFFICIENT_MARGIN_FOR_PRINT));
    }

    @Test
    public void doesNotFailPdfWithInsufficientMarginForPrintIfCheckDisabled() {
        PdfValidationSettings innstillinger = new PdfValidationSettings(false, true, true, true);
        assertThat(validationErrors("/pdf/a4-left-margin-14_5mm.pdf", innstillinger), empty());
        assertThat(validationErrors("/pdf/a4-landscape-left-margin-14_5mm.pdf", innstillinger), empty());
    }

    @Test
    public void failsPdfWithTooManyPagesForPrint() {
        assertThat(validationErrors("/pdf/a4-20pages.pdf", CHECK_ALL), contains(TOO_MANY_PAGES_FOR_AUTOMATED_PRINT));
    }

    @Test
    public void doesNotFailPdfWithTooManyPagesForPrintIfCheckDisabled() {
        PdfValidationSettings innstillinger = new PdfValidationSettings(true, true, false, true);
        assertThat(validationErrors("/pdf/a4-20pages.pdf", innstillinger), empty());
    }

    @Test
    public void failsCorruptPdfResultingInNoPages() {
        assertThat(validationErrors("/pdf/corrupt_no_pages.pdf", CHECK_ALL), contains(DOCUMENT_HAS_NO_PAGES));
    }

    @Test
    public void failCorruptPdf() {
        assertThat(validationErrors("/pdf/corrupt.pdf", CHECK_ALL), contains(PDF_PARSE_ERROR));
    }

    @Test
    public void failsPasswordProtectedPdf() {
        assertThat(validationErrors("/pdf/encrypted-with-password.pdf", CHECK_ALL), contains(PDF_IS_ENCRYPTED));
    }

    @Test
    public void failsPdfWithUnsupportedDimensionsForPrint() {
        assertThat(validationErrors("/pdf/letter-left-margin-20mm.pdf", CHECK_ALL), contains(UNSUPPORTED_DIMENSIONS));
        assertThat(validationErrors("/pdf/letter-landscape-left-margin-20mm.pdf", CHECK_ALL), contains(UNSUPPORTED_DIMENSIONS));
        assertThat(validationErrors("/pdf/far-from-a4-free-barcode-area.pdf", CHECK_ALL), contains(UNSUPPORTED_DIMENSIONS, INSUFFICIENT_MARGIN_FOR_PRINT));
    }

    @Test
    public void failsPdfWithInsufficientMarginAndUnsupportedDimensionsForPrint() {
        assertThat(validationErrors("/pdf/a5-left-margin-15mm.pdf", CHECK_ALL), containsInAnyOrder(INSUFFICIENT_MARGIN_FOR_PRINT, UNSUPPORTED_DIMENSIONS));
        assertThat(validationErrors("/pdf/a5-landscape-left-margin-15mm.pdf", CHECK_ALL), containsInAnyOrder(INSUFFICIENT_MARGIN_FOR_PRINT, UNSUPPORTED_DIMENSIONS));
    }

    @Test
    public void pdfWithBogusFontsAndTooManyPages() {
        assertThat(validationErrors("/pdf/15-pages-and-bogus-fonts.pdf", new PdfValidationSettings(true, false, false, true)), empty());
        assertThat(validationErrors("/pdf/15-pages-and-bogus-fonts.pdf", new PdfValidationSettings(true, false, true, true)), contains(TOO_MANY_PAGES_FOR_AUTOMATED_PRINT));
        assertThat(validationErrors("/pdf/15-pages-and-bogus-fonts.pdf", new PdfValidationSettings(true, true, false, true)), everyItem(is(REFERENCES_INVALID_FONT)));
    }

    @Test
    public void doesNotFailPDFLargerThatA4WhenPositiveBleedSettingIsActivated() {
        assertThat(validationErrors("/pdf/a4-pdf-with-10mm-bleed.pdf", new PdfValidationSettings(true, true, true, true, 10, 10)), empty());
    }

    @Test
    public void doesNotFailPDFSmallerThanA4WhenNegativeBleedSettingIsActivated() {
        assertThat(validationErrors("/pdf/letter-left-margin-20mm.pdf", new PdfValidationSettings(true, true, true, true, 10, 20)), empty());
    }

    @Test
    public void failsForPDFLargerThatA4WhenBleedSettingIsInactive() {
        assertThat(validationErrors("/pdf/a4-pdf-with-10mm-bleed.pdf", CHECK_ALL), containsInAnyOrder(UNSUPPORTED_DIMENSIONS));
    }

    public static List<PdfValidationError> validationErrors(String pdfResourceName, PdfValidationSettings printValidationSettings) {
        File pdf = new File(notNull(PrintPdfValidatorTest.class.getResource(pdfResourceName), pdfResourceName).getFile().replace("%20", " "));
        try {
            return pdfValidator.validate(pdf, printValidationSettings).errors;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
