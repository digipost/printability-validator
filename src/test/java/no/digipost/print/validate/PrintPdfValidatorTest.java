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

import no.digipost.print.validate.PdfValidationSettings.Bleed;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static no.digipost.print.validate.PdfValidationError.DOCUMENT_HAS_NO_PAGES;
import static no.digipost.print.validate.PdfValidationError.INSUFFICIENT_MARGIN_FOR_PRINT;
import static no.digipost.print.validate.PdfValidationError.PDF_IS_ENCRYPTED;
import static no.digipost.print.validate.PdfValidationError.PDF_PARSE_ERROR;
import static no.digipost.print.validate.PdfValidationError.REFERENCES_INVALID_FONT;
import static no.digipost.print.validate.PdfValidationError.TOO_MANY_PAGES_FOR_AUTOMATED_PRINT;
import static no.digipost.print.validate.PdfValidationError.UNSUPPORTED_DIMENSIONS;
import static no.digipost.print.validate.PdfValidationSettings.CHECK_ALL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;

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
    public void failedPdfWithInsufficientMarginForPrintGivesPersonalizedBleedParameters() {
        Bleed bleed = new Bleed(2, 3);
        PdfValidationResult pdfValidationResult = new PdfValidationResult(validationErrors("/pdf/far-from-a4-free-barcode-area.pdf", new PdfValidationSettings(false, false, false, false, bleed.positiveBleedInMM, bleed.negativeBleedInMM)), 1, bleed);

        String errorString = pdfValidationResult.toString();

        assertThat(errorString, is("[" + PdfValidationResult.class.getSimpleName() + " " + String.format(UNSUPPORTED_DIMENSIONS.message,
                PdfValidator.A4_WIDTH_MM - bleed.negativeBleedInMM, PdfValidator.A4_WIDTH_MM + bleed.positiveBleedInMM, PdfValidator.A4_HEIGHT_MM - bleed.negativeBleedInMM, PdfValidator.A4_HEIGHT_MM + bleed.positiveBleedInMM) + "]"));
    }

    @Test
    public void errorMessageMethodReturnsPersonalizedBleedParameters() {
        Bleed bleed = new Bleed(2, 3);
        PdfValidationResult pdfValidationResult = new PdfValidationResult(validationErrors("/pdf/far-from-a4-free-barcode-area.pdf", new PdfValidationSettings(false, false, false, false, bleed.positiveBleedInMM, bleed.negativeBleedInMM)), 1, bleed);

        PdfValidationError error = pdfValidationResult.errors.stream()
                .filter(UNSUPPORTED_DIMENSIONS::equals)
                .findAny().orElseThrow(() -> new RuntimeException("Expected unsupported dimensions validation error"));

        String errorMessage = pdfValidationResult.formattedValidationErrorMessage(error);
        assertThat(errorMessage, containsString(String.format("%d—%d", PdfValidator.A4_WIDTH_MM - bleed.negativeBleedInMM, PdfValidator.A4_WIDTH_MM + bleed.positiveBleedInMM)));
        assertThat(errorMessage, containsString(String.format("%d—%d", PdfValidator.A4_HEIGHT_MM - bleed.negativeBleedInMM, PdfValidator.A4_HEIGHT_MM + bleed.positiveBleedInMM)));
    }

    @Test
    public void doesNotFailPdfWithInsufficientMarginForPrintIfCheckDisabled() {
        PdfValidationSettings innstillinger = new PdfValidationSettings(false, true, true, true);
        assertThat(validationErrors("/pdf/a4-left-margin-14_5mm.pdf", innstillinger), empty());
        assertThat(validationErrors("/pdf/a4-landscape-left-margin-14_5mm.pdf", innstillinger), empty());
    }

    @Test
    public void failsPdfWithTooManyPagesForPrint() {
        assertThat(validationErrors("/pdf/a4-21pages.pdf", CHECK_ALL), contains(TOO_MANY_PAGES_FOR_AUTOMATED_PRINT));
    }

    @Test
    public void doesNotFailPdfWithTooManyPagesForPrintIfCheckDisabled() {
        PdfValidationSettings innstillinger = new PdfValidationSettings(true, true, false, true);
        assertThat(validationErrors("/pdf/a4-21pages.pdf", innstillinger), empty());
    }

    @Test
    public void failsCorruptPdfResultingInNoPages() throws IOException {
        PDDocument zeroPagesDocument = new PDDocument() {
            @Override
            public int getNumberOfPages() {
                return 0;
            }
        };
        assertThat(pdfValidator.validateDocumentForPrint(zeroPagesDocument, CHECK_ALL), contains(DOCUMENT_HAS_NO_PAGES));
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
        assertThat(validationErrors("/pdf/21-pages-and-bogus-fonts.pdf", new PdfValidationSettings(true, false, false, true)), empty());
        assertThat(validationErrors("/pdf/21-pages-and-bogus-fonts.pdf", new PdfValidationSettings(true, false, true, true)), contains(TOO_MANY_PAGES_FOR_AUTOMATED_PRINT));
        assertThat(validationErrors("/pdf/21-pages-and-bogus-fonts.pdf", new PdfValidationSettings(true, true, false, true)), everyItem(is(REFERENCES_INVALID_FONT)));
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
        Path pdf = Paths.get(requireNonNull(PrintPdfValidatorTest.class.getResource(pdfResourceName), pdfResourceName).getFile().replace("%20", " "));
        try {
            return pdfValidator.validate(pdf, printValidationSettings).errors;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
