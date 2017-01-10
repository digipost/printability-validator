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

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.util.PDFTextStripperByArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Rectangle2D;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;
import static no.digipost.print.validate.PdfValidateStrategy.FULLY_IN_MEMORY;
import static no.digipost.print.validate.PdfValidateStrategy.NON_SEQUENTIALLY;
import static no.digipost.print.validate.PdfValidationError.*;
import static org.apache.commons.lang3.StringUtils.join;


public class PdfValidator {

	private static final Logger LOG = LoggerFactory.getLogger(PdfValidator.class);

	private final PdfFontValidator fontValidator = new PdfFontValidator();

	// MM_TO_UNITS copied from org.apache.pdfbox.pdmodel.PDPage
	private static final double MM_TO_POINTS = 1 / (10 * 2.54f) * 72;

	public static final int MM_VALIDATION_FLEXIBILITY = 10;

	public static final int A4_HEIGHT_MM = 297;
	public static final int A4_WIDTH_MM = 210;
	public static final int BARCODE_AREA_WIDTH_MM = 15;
	public static final int BARCODE_AREA_HEIGHT_MM = 80;
	public static final int BARCODE_AREA_X_POS_MM = 0;
	public static final int BARCODE_AREA_Y_POS_MM = 95;
	public static final List<Float> PDF_VERSIONS_SUPPORTED_FOR_PRINT = Arrays.asList(1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.7f);


	public PdfValidationResult validate(byte[] pdfContent, PdfValidationSettings printValidationSettings) {
		return validateForPrint(new ByteArrayInputStream(pdfContent), printValidationSettings, PdfValidateStrategy.FULLY_IN_MEMORY);
	}

	public PdfValidationResult validate(File pdfFile, PdfValidationSettings printValidationSettings) throws IOException {
		InputStream pdfStream = openFileAsInputStream(pdfFile);
		return validateForPrint(pdfStream, printValidationSettings, PdfValidateStrategy.FULLY_IN_MEMORY);
	}

	/**
	 * @param pdfStream the input stream for reading the PDF. It will be closed before returning from
	 *                  this method
	 * @param readStrategy decides if PDF is completely read into memory or not
	 */
	private PdfValidationResult validateForPrint(InputStream pdfStream, PdfValidationSettings printValidationSettings, PdfValidateStrategy readStrategy) {
		int numberOfPages = -1;
		try {
			List<PdfValidationError> errors;
			try {
				if (readStrategy == NON_SEQUENTIALLY) {
					try (EnhancedNonSequentialPDFParser dpostNonSequentialPDFParser = new EnhancedNonSequentialPDFParser(pdfStream)){
						numberOfPages = dpostNonSequentialPDFParser.getNumberOfPages();
						errors = validateStreamForPrint(dpostNonSequentialPDFParser, printValidationSettings);
					}
				} else if (readStrategy == FULLY_IN_MEMORY) {
					try (PDDocument pdDoc = PDDocument.load(pdfStream)) {
						numberOfPages = pdDoc.getNumberOfPages();
						errors = validateDocumentForPrint(pdDoc, printValidationSettings);
					}
				} else {
					throw new IllegalArgumentException("Unknown " + PdfValidateStrategy.class.getSimpleName() + ": " + readStrategy);
				}
			} catch (Exception e) {
				errors = asList(PdfValidationError.PDF_PARSE_ERROR);
				LOG.info("PDF could not be parsed. (" + e.getMessage() + ")");
				LOG.debug(e.getMessage(), e);
			}

			return new PdfValidationResult(errors, numberOfPages);
		} finally {
			IOUtils.closeQuietly(pdfStream);
		}
	}

	/**
	 * Leser ikke hele dokumentet inn i minnet
	 */
	private List<PdfValidationError> validateStreamForPrint(EnhancedNonSequentialPDFParser dpostNonSequentialPDFParser,
															PdfValidationSettings settings) throws IOException {

		List<PdfValidationError> errors = new ArrayList<>();

		if (dpostNonSequentialPDFParser.isEncrypted()) {
			return failValidationIfEncrypted(errors);
		}

		if (settings.validateNumberOfPages) {
			validerSideantall(dpostNonSequentialPDFParser.getNumberOfPages(), settings.maxNumberOfPages, errors);
		}

		if (settings.validatePDFversion) {
			validatePdfVersion(dpostNonSequentialPDFParser.getDocument().getVersion(), errors);
		}

		boolean documentHasInvalidDimensions = false;
		boolean documentContainsPagesWithInvalidPrintMargins = false;
		boolean documentHasInvalidLeftMargin = false;
		boolean documentHasPagesWhichCannotBeParsed = false;
		for (int i = 1; i <= dpostNonSequentialPDFParser.getNumberOfPages(); i++) {
			PDPage page = null;
			try {
				page = dpostNonSequentialPDFParser.getPage(i);
			} catch (Exception e) {
				documentHasPagesWhichCannotBeParsed = true;
			}
			if (page != null) {

				if (!documentHasInvalidDimensions) {
					if (hasInvalidDimensions(page, settings.bleed)) {
						documentHasInvalidDimensions = true;
					}
				}

				if (settings.validateLeftMargin) {
					if (!documentHasInvalidLeftMargin) {
						try {
							if (hasTextInBarcodeArea(page, settings.bleed)) {
								documentHasInvalidLeftMargin = true;
							}
						} catch (NullPointerException npe) {
							LOG.info("Could not verify margin on the following side " + i);
							documentContainsPagesWithInvalidPrintMargins = true;
						}
					}

				}

				if (settings.validateFonts) {
					validateFonts(fontValidator.getPageFonts(page), errors);
				}

			} else {
				// TODO en eller annen algoritme som kaster feil om et visst antall
				// sider ikke kan parses
				LOG.warn("Could not fetch page {} in the pdf", i);
			}
		}

		addValidationError(documentHasInvalidDimensions, UNSUPPORTED_DIMENSIONS, errors);
		addValidationError(documentHasInvalidLeftMargin, INSUFFICIENT_MARGIN_FOR_PRINT, errors);
		addValidationError(documentHasPagesWhichCannotBeParsed, PDF_PARSE_PAGE_ERROR, errors);
		addValidationError(documentContainsPagesWithInvalidPrintMargins, UNABLE_TO_VERIFY_SUITABLE_MARGIN_FOR_PRINT, errors);

		return errors;

	}

	/**
	 * Leser hele dokumentet inn i minnet
	 */
	private List<PdfValidationError> validateDocumentForPrint(final PDDocument pdDoc, final PdfValidationSettings settings)	throws IOException {
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
		for (PDPage page : getAllPagesFrom(pdDoc)) {
			if (hasInvalidDimensions(page, settings.bleed)) {
				documentHasInvalidDimensions = true;
				break;
			}
		}

		addValidationError(documentHasInvalidDimensions, UNSUPPORTED_DIMENSIONS, errors);

		boolean hasTextInBarcodeArea = false;
		boolean documentContainsPagesWithInvalidPrintMargins = false;
		if (settings.validateLeftMargin) {
			for (PDPage page : getAllPagesFrom(pdDoc)) {
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
			for (PDPage page : getAllPagesFrom(pdDoc)) {
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

	@SuppressWarnings("unchecked")
	private List<PDPage> getAllPagesFrom(final PDDocument pdDoc) {
		return pdDoc.getDocumentCatalog().getAllPages();
	}

	private List<PdfValidationError> failValidationIfEncrypted(List<PdfValidationError> errors) {
		errors.add(PdfValidationError.PDF_IS_ENCRYPTED);
		LOG.info("The pdf is encrypted.");
		return errors;
	}

	private void validateFonts(final Iterable<PDFont> fonter, final List<PdfValidationError> errors) {
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
			fontDescriptions.add(font.getSubType() + " '" + font.getBaseFont() + "'");
		}
		return fontDescriptions;
	}

	private void validatePdfVersion(final float pdfVersion, final List<PdfValidationError> errors) {
		if (!PDF_VERSIONS_SUPPORTED_FOR_PRINT.contains(pdfVersion)) {
			errors.add(PdfValidationError.UNSUPPORTED_PDF_VERSION_FOR_PRINT);
			LOG.info("The PDF is not in valid version. Valid versions are {}. Actual version is {}",
					StringUtils.join(PDF_VERSIONS_SUPPORTED_FOR_PRINT, ", "), pdfVersion);
		}
	}

	private void validerSideantall(final int numberOfPages, int maxPages, final List<PdfValidationError> errors) {
		if (numberOfPages > maxPages) {
			errors.add(PdfValidationError.TOO_MANY_PAGES_FOR_AUTOMATED_PRINT);
			LOG.info("The PDF has too many pages. Max number of pages is {}. Actual number of pages is {}", maxPages, numberOfPages);
		}
		if (numberOfPages == 0) {
			errors.add(PdfValidationError.DOCUMENT_HAS_NO_PAGES);
			LOG.info("The PDF document does not contain any pages. The file may be corrupt.", numberOfPages);
		}
	}

	private boolean hasTextInBarcodeArea(final PDPage pdPage, int bleed) throws IOException {
		SilentZone silentZone = new SilentZone(pdPage.findCropBox(), bleed);
		
		Rectangle2D leftMarginBarcodeArea = new Rectangle2D.Double(silentZone.upperLeftCornerX,
				silentZone.upperLeftCornerY, silentZone.silentZoneXSize, silentZone.silentZoneYSize);

		return hasTextInArea(pdPage, leftMarginBarcodeArea);
	}

	private boolean hasInvalidDimensions(final PDPage page, int bleed) {
		PDRectangle findCropBox = page.findCropBox();
		long pageHeightInMillimeters = pointsTomm(findCropBox.getHeight());
		long pageWidthInMillimeters = pointsTomm(findCropBox.getWidth());
		if (!isPortraitA4(pageWidthInMillimeters, pageHeightInMillimeters, bleed) && !isLandscapeA4(pageWidthInMillimeters, pageHeightInMillimeters, bleed)) {
			LOG.info("One or more pages in the PDF has invalid dimensions.  Valid dimensions are width {} mm and height {} mm, alt " +
					"width {} mm og height {} mm with {} mm lower flexibility. "
					+ "Actual dimensions are width: {} mm and height: {} mm.",
					new Object[] { A4_WIDTH_MM, A4_HEIGHT_MM, A4_HEIGHT_MM, A4_WIDTH_MM, MM_VALIDATION_FLEXIBILITY,
							pageWidthInMillimeters, pageHeightInMillimeters });
			return true;
		} else {
			return false;
		}
	}

	private static boolean isPortraitA4(long pageWidthInMillimeters, long pageHeightInMillimeters, long bleed){
		long minimumWidth = A4_WIDTH_MM - MM_VALIDATION_FLEXIBILITY;
		long maximumWidth = A4_WIDTH_MM + bleed;
		long minimumHeight = A4_HEIGHT_MM - MM_VALIDATION_FLEXIBILITY;
		long maximumHeight = A4_HEIGHT_MM + bleed;
		return pageWidthInMillimeters <= maximumWidth && pageWidthInMillimeters >= minimumWidth
				&& pageHeightInMillimeters <= maximumHeight && pageHeightInMillimeters >= minimumHeight;
	}

	private static boolean isLandscapeA4(long pageWidthInMillimeters, long pageHeightInMillimeters, long bleed){
		return isPortraitA4(pageHeightInMillimeters, pageWidthInMillimeters, bleed);
	}

	private boolean hasTextInArea(final PDPage pdPage, final Rectangle2D area) throws IOException {
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

	private InputStream openFileAsInputStream(final File pdfFile) throws IOException {
		return new BufferedInputStream(Files.newInputStream(pdfFile.toPath()));
	}

	private static double mmToPoints(final int sizeInMillimeters) {
		BigDecimal points = new BigDecimal(sizeInMillimeters * MM_TO_POINTS);
		points = points.setScale(1, RoundingMode.DOWN);
		return points.doubleValue();
	}

	private static long pointsTomm(final double sizeInPoints) {
		return Math.round(sizeInPoints / MM_TO_POINTS);
	}

	private static class SilentZone {
		public final double upperLeftCornerX;
		public final double upperLeftCornerY;
		public final double silentZoneXSize;
		public final double silentZoneYSize;

		private SilentZone(PDRectangle findCropBox, int bleed) {
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
