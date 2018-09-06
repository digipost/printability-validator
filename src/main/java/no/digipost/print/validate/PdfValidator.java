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

import no.digipost.print.validate.PdfValidationSettings.Bleed;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Rectangle2D;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;
import static no.digipost.print.validate.PdfValidationError.INSUFFICIENT_MARGIN_FOR_PRINT;
import static no.digipost.print.validate.PdfValidationError.UNABLE_TO_VERIFY_SUITABLE_MARGIN_FOR_PRINT;
import static no.digipost.print.validate.PdfValidationError.UNSUPPORTED_DIMENSIONS;
import static org.apache.commons.lang3.StringUtils.join;


public class PdfValidator {

    private static final Logger LOG = LoggerFactory.getLogger(PdfValidator.class);

    private final PdfFontValidator fontValidator = new PdfFontValidator();

    // MM_TO_UNITS copied from org.apache.pdfbox.pdmodel.PDPage
    private static final double MM_TO_POINTS = 1 / (10 * 2.54f) * 72;

    public static final int A4_HEIGHT_MM = 297;
    public static final int A4_WIDTH_MM = 210;
    public static final int BARCODE_AREA_WIDTH_MM = 15;
    public static final int BARCODE_AREA_HEIGHT_MM = 80;
    public static final int BARCODE_AREA_X_POS_MM = 0;
    public static final int BARCODE_AREA_Y_POS_MM = 95;
    public static final List<Float> PDF_VERSIONS_SUPPORTED_FOR_PRINT = Arrays.asList(1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.7f);


    public PdfValidationResult validate(byte[] pdfContent, PdfValidationSettings printValidationSettings) {
        return validateForPrint(new ByteArrayInputStream(pdfContent), printValidationSettings);
    }

    public PdfValidationResult validate(Path pdfFile, PdfValidationSettings printValidationSettings) throws IOException {
        try (InputStream pdfStream = openFileAsInputStream(pdfFile)) {
            return validateForPrint(pdfStream, printValidationSettings);
        }
    }

    /**
     * @param pdfStream the input stream for reading the PDF. This method will <strong>not</strong> close the stream.
     * @param printValidationSettings settings for how to perform the validation
     */
    private PdfValidationResult validateForPrint(InputStream pdfStream, PdfValidationSettings printValidationSettings) {
        int numberOfPages = -1;
        List<PdfValidationError> errors;
        try (PDDocument pdDoc = PDDocument.load(pdfStream)) {
            numberOfPages = pdDoc.getNumberOfPages();
            errors = validateDocumentForPrint(pdDoc, printValidationSettings);
        } catch (InvalidPasswordException invalidPassword) {
            errors = failValidationIfEncrypted(new ArrayList<>());
        } catch (Exception e) {
            errors = asList(PdfValidationError.PDF_PARSE_ERROR);
            LOG.info("PDF could not be parsed. ({}: '{}')", e.getClass().getSimpleName(), e.getMessage());
            LOG.debug(e.getMessage(), e);
        }

        return new PdfValidationResult(errors, numberOfPages, printValidationSettings.bleed);
    }

    /**
     * Leser hele dokumentet inn i minnet
     */
    List<PdfValidationError> validateDocumentForPrint(PDDocument pdDoc, PdfValidationSettings settings)	throws IOException {
        List<PdfValidationError> errors = new ArrayList<>();

        if (pdDoc.isEncrypted()) {
            return failValidationIfEncrypted(errors);
        }

        if (settings.validateNumberOfPages) {
            validerSideantall(pdDoc.getNumberOfPages(), settings.maxNumberOfPages, errors);
        }

        if (settings.validatePDFversion) {
            validatePdfVersion(pdDoc.getDocument().getVersion(), errors);
        }

        boolean documentHasInvalidDimensions = false;
        for (PDPage page : pdDoc.getPages()) {
            if (hasInvalidDimensions(page, settings.bleed)) {
                documentHasInvalidDimensions = true;
                break;
            }
        }

        addValidationError(documentHasInvalidDimensions, UNSUPPORTED_DIMENSIONS, errors);

        boolean hasTextInBarcodeArea = false;
        boolean documentContainsPagesWithInvalidPrintMargins = false;
        if (settings.validateLeftMargin) {
            for (PDPage page : pdDoc.getPages()) {
                try {
                    if (hasTextInBarcodeArea(page, settings.bleed)) {
                        hasTextInBarcodeArea = true;
                        break;
                    }
                } catch (NullPointerException npe) {
                    documentContainsPagesWithInvalidPrintMargins = true;
                    LOG.info("Could not validate the margin on one of the sides");
                }
            }
        }

        addValidationError(documentContainsPagesWithInvalidPrintMargins, UNABLE_TO_VERIFY_SUITABLE_MARGIN_FOR_PRINT, errors);
        addValidationError(hasTextInBarcodeArea, INSUFFICIENT_MARGIN_FOR_PRINT, errors);

        if (settings.validateFonts) {
            for (PDPage page : pdDoc.getPages()) {
                validateFonts(fontValidator.getPageFonts(page), errors);
            }
        }

        return errors;
    }

    private void addValidationError(boolean documentContainsPagesThatCannotBeParsed, PdfValidationError validationErrors,
                                    List<PdfValidationError> errors) {
        if (documentContainsPagesThatCannotBeParsed) {
            errors.add(validationErrors);
        }
    }

    private List<PdfValidationError> failValidationIfEncrypted(List<PdfValidationError> errors) {
        errors.add(PdfValidationError.PDF_IS_ENCRYPTED);
        LOG.info("The pdf is encrypted.");
        return errors;
    }

    private void validateFonts(Iterable<PDFont> fonter, List<PdfValidationError> errors) {
        List<PDFont> nonSupportedFonts = fontValidator.findNonSupportedFonts(fonter);
        if (!nonSupportedFonts.isEmpty()) {
            errors.add(PdfValidationError.REFERENCES_INVALID_FONT);
            if (LOG.isInfoEnabled()) {
                LOG.info("The PDF has references to invalid fonts: [{}]", join(describe(nonSupportedFonts), ", "));
            }
        }
    }

    private List<String> describe(Iterable<PDFont> fonts) {
        List<String> fontDescriptions = new ArrayList<>();
        for (PDFont font : fonts) {
            fontDescriptions.add(font.getSubType() + " '" + font.getName() + "'");
        }
        return fontDescriptions;
    }

    private void validatePdfVersion(float pdfVersion, List<PdfValidationError> errors) {
        if (!PDF_VERSIONS_SUPPORTED_FOR_PRINT.contains(pdfVersion)) {
            errors.add(PdfValidationError.UNSUPPORTED_PDF_VERSION_FOR_PRINT);
            LOG.info("The PDF is not in valid version. Valid versions are {}. Actual version is {}",
                    StringUtils.join(PDF_VERSIONS_SUPPORTED_FOR_PRINT, ", "), pdfVersion);
        }
    }

    private void validerSideantall(int numberOfPages, int maxPages, final List<PdfValidationError> errors) {
        if (numberOfPages > maxPages) {
            errors.add(PdfValidationError.TOO_MANY_PAGES_FOR_AUTOMATED_PRINT);
            LOG.info("The PDF has too many pages. Max number of pages is {}. Actual number of pages is {}", maxPages, numberOfPages);
        }
        if (numberOfPages == 0) {
            errors.add(PdfValidationError.DOCUMENT_HAS_NO_PAGES);
            LOG.info("The PDF document does not contain any pages. The file may be corrupt.", numberOfPages);
        }
    }

    private boolean hasTextInBarcodeArea(PDPage pdPage, Bleed bleed) throws IOException {
        SilentZone silentZone = new SilentZone(pdPage.getCropBox(), bleed);

        Rectangle2D leftMarginBarcodeArea = new Rectangle2D.Double(silentZone.upperLeftCornerX,
                silentZone.upperLeftCornerY, silentZone.silentZoneXSize, silentZone.silentZoneYSize);

        return hasTextInArea(pdPage, leftMarginBarcodeArea);
    }

    private boolean hasInvalidDimensions(PDPage page, Bleed bleed) {
        PDRectangle findCropBox = page.getCropBox();
        long pageHeightInMillimeters = pointsTomm(findCropBox.getHeight());
        long pageWidthInMillimeters = pointsTomm(findCropBox.getWidth());
        if (!isPortraitA4(pageWidthInMillimeters, pageHeightInMillimeters, bleed) && !isLandscapeA4(pageWidthInMillimeters, pageHeightInMillimeters, bleed)) {
            LOG.info("One or more pages in the PDF has invalid dimensions.  Valid dimensions are width {} mm and height {} mm, alt " +
                    "width {} mm og height {} mm with {} mm lower flexibility and {} upper flexibility. "
                    + "Actual dimensions are width: {} mm and height: {} mm.",
                    new Object[] { A4_WIDTH_MM, A4_HEIGHT_MM, A4_HEIGHT_MM, A4_WIDTH_MM, bleed.negativeBleedInMM,
                            bleed.positiveBleedInMM, pageWidthInMillimeters, pageHeightInMillimeters });
            return true;
        } else {
            return false;
        }
    }

    private static boolean isPortraitA4(long pageWidthInMillimeters, long pageHeightInMillimeters, Bleed bleed){
        long minimumWidth = A4_WIDTH_MM - bleed.negativeBleedInMM;
        long maximumWidth = A4_WIDTH_MM + bleed.positiveBleedInMM;
        long minimumHeight = A4_HEIGHT_MM - bleed.negativeBleedInMM;
        long maximumHeight = A4_HEIGHT_MM + bleed.positiveBleedInMM;
        return pageWidthInMillimeters <= maximumWidth && pageWidthInMillimeters >= minimumWidth
                && pageHeightInMillimeters <= maximumHeight && pageHeightInMillimeters >= minimumHeight;
    }

    private static boolean isLandscapeA4(long pageWidthInMillimeters, long pageHeightInMillimeters, Bleed bleed){
        return isPortraitA4(pageHeightInMillimeters, pageWidthInMillimeters, bleed);
    }

    private boolean hasTextInArea(PDPage pdPage, Rectangle2D area) throws IOException {
        boolean hasTextInArea = false;
        final PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        stripper.addRegion("marginArea", area);
        stripper.extractRegions(pdPage);
        String text = stripper.getTextForRegion("marginArea");
        if (text != null && text.trim().length() > 0) {
            hasTextInArea = true;
        }
        return hasTextInArea;
    }

    private InputStream openFileAsInputStream(Path pdfFile) throws IOException {
        return new BufferedInputStream(Files.newInputStream(pdfFile));
    }

    private static double mmToPoints(int sizeInMillimeters) {
        BigDecimal points = new BigDecimal(sizeInMillimeters * MM_TO_POINTS);
        points = points.setScale(1, RoundingMode.DOWN);
        return points.doubleValue();
    }

    private static long pointsTomm(double sizeInPoints) {
        return Math.round(sizeInPoints / MM_TO_POINTS);
    }

    private static class SilentZone {
        public final double upperLeftCornerX;
        public final double upperLeftCornerY;
        public final double silentZoneXSize;
        public final double silentZoneYSize;

        private SilentZone(PDRectangle findCropBox, Bleed bleed) {
            int pageHeightInMillimeters = (int)pointsTomm(findCropBox.getHeight());
            int pageWidthInMillimeters = (int)pointsTomm(findCropBox.getWidth());
            boolean isLandscape = isLandscapeA4(pageWidthInMillimeters, pageHeightInMillimeters, bleed);

            this.upperLeftCornerX = upperLeftCornerX(isLandscape);
            this.upperLeftCornerY = upperLeftCornerY(isLandscape, pageHeightInMillimeters);
            this.silentZoneXSize = zoneXSize(isLandscape);
            this.silentZoneYSize = zoneYSize(isLandscape);
        }

        private double upperLeftCornerX(boolean isLandscape){
            if(isLandscape){
                return mmToPoints(BARCODE_AREA_Y_POS_MM);
            } else {
                return mmToPoints(BARCODE_AREA_X_POS_MM);
            }
        }

        private double upperLeftCornerY(boolean isLandscape, int pageHeightInMillimeters){
            if(isLandscape){
                return mmToPoints(pageHeightInMillimeters-BARCODE_AREA_WIDTH_MM);
            } else {
                return mmToPoints(BARCODE_AREA_Y_POS_MM);
            }
        }

        private double zoneXSize(boolean isLandscape){
            if(isLandscape){
                return mmToPoints(BARCODE_AREA_HEIGHT_MM);
            } else {
                return mmToPoints(BARCODE_AREA_WIDTH_MM);
            }
        }

        private double zoneYSize(boolean isLandscape){
            if(isLandscape){
                return mmToPoints(BARCODE_AREA_WIDTH_MM);
            } else {
                return mmToPoints(BARCODE_AREA_HEIGHT_MM);
            }
        }
    }
}
