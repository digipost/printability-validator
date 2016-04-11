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

	public static final double MM_VALIDATION_FLEXIBILITY = 10;

	public static final int A4_HEIGHT_MM = 297;
	public static final int A4_WIDTH_MM = 210;
	public static final int BARCODE_AREA_WIDTH_MM = 18;
	public static final int BARCODE_AREA_HEIGHT_MM = 70;
	public static final int BARCODE_AREA_X_POS_MM = 0;
	public static final int BARCODE_AREA_Y_POS_MM = 100;
	public static final List<Float> PDF_VERSIONS_SUPPORTED_FOR_PRINT = Arrays.asList(1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.7f);


	public PdfValidationResult validate(byte[] pdfContent, PdfValidationSettings printValideringsinnstillinger) {
		return validerForPrint(new ByteArrayInputStream(pdfContent), printValideringsinnstillinger, PdfValidateStrategy.FULLY_IN_MEMORY);
	}

	public PdfValidationResult validate(File pdfFile, PdfValidationSettings printValideringsinnstillinger) throws IOException {
		InputStream pdfStream = openFileAsInputStream(pdfFile);
		return validerForPrint(pdfStream, printValideringsinnstillinger, PdfValidateStrategy.FULLY_IN_MEMORY);
	}

	/**
	 * @param pdfStream the input stream for reading the PDF. It will be closed before returning from
	 *                  this method
	 * @param readStrategy decides if PDF is completely read into memory or not
	 */
	private PdfValidationResult validerForPrint(InputStream pdfStream, PdfValidationSettings printValideringsinnstillinger, PdfValidateStrategy readStrategy) {
		int antallSider = -1;
		try {
			List<PdfValidationError> errors;
			try {
				if (readStrategy == NON_SEQUENTIALLY) {
					try (EnhancedNonSequentialPDFParser dpostNonSequentialPDFParser = new EnhancedNonSequentialPDFParser(pdfStream)){
						antallSider = dpostNonSequentialPDFParser.getNumberOfPages();
						errors = validerStreamForPrint(dpostNonSequentialPDFParser, printValideringsinnstillinger);
					}
				} else if (readStrategy == FULLY_IN_MEMORY) {
					try (PDDocument pdDoc = PDDocument.load(pdfStream)) {
						antallSider = pdDoc.getNumberOfPages();
						errors = validerDokumentForPrint(pdDoc, printValideringsinnstillinger);
					}
				} else {
					throw new IllegalArgumentException("Unknown " + PdfValidateStrategy.class.getSimpleName() + ": " + readStrategy);
				}
			} catch (Exception e) {
				errors = asList(PdfValidationError.PDF_PARSE_ERROR);
				LOG.info("PDF-en kunne ikke parses. (" + e.getMessage() + ")");
				LOG.debug(e.getMessage(), e);
			}

			return new PdfValidationResult(errors, antallSider);
		} finally {
			IOUtils.closeQuietly(pdfStream);
		}
	}

	/**
	 * Leser ikke hele dokumentet inn i minnet
	 */
	private List<PdfValidationError> validerStreamForPrint(EnhancedNonSequentialPDFParser dpostNonSequentialPDFParser,
			PdfValidationSettings innstillinger) throws IOException {

		List<PdfValidationError> errors = new ArrayList<>();

		if (dpostNonSequentialPDFParser.isEncrypted()) {
			return failValidationIfEncrypted(errors);
		}

		if (innstillinger.validerSideantall) {
			validerSideantall(dpostNonSequentialPDFParser.getNumberOfPages(),innstillinger.maksSideantall, errors);
		}

		if (innstillinger.validerPDFversjon) {
			validerPdfVersjon(dpostNonSequentialPDFParser.getDocument().getVersion(), errors);
		}

		boolean dokumentHarUgyldigeDimensjoner = false;
		boolean dokumentHarSiderHvisMarginIkkeLarSegVerifisereForPrint = false;
		boolean dokumentHarUgyldigVenstremarg = false;
		boolean dokumentHarSiderSomIkkeKanParses = false;
		for (int i = 1; i <= dpostNonSequentialPDFParser.getNumberOfPages(); i++) {
			PDPage page = null;
			try {
				page = dpostNonSequentialPDFParser.getPage(i);
			} catch (Exception e) {
				dokumentHarSiderSomIkkeKanParses = true;
			}
			if (page != null) {

				if (!dokumentHarUgyldigeDimensjoner) {
					if (harUgyldigeDimensjoner(page)) {
						dokumentHarUgyldigeDimensjoner = true;
					}
				}

				if (innstillinger.validerVenstremarg) {
					if (!dokumentHarUgyldigVenstremarg) {
						try {
							if (harTekstIStrekkodeomraade(page)) {
								dokumentHarUgyldigVenstremarg = true;
							}
						} catch (NullPointerException npe) {
							LOG.info("Klarte ikke å verifiserere margen på side " + i);
							dokumentHarSiderHvisMarginIkkeLarSegVerifisereForPrint = true;
						}
					}

				}

				if (innstillinger.validerFonter) {
					validerFonter(fontValidator.getPageFonts(page), errors);
				}

			} else {
				// TODO en eller annen algoritme som kaster feil om et visst antall
				// sider ikke kan parses
				LOG.warn("Klarte ikke å hente side nummer {} i pdf-en", i);
			}
		}

		leggTilValideringsfeil(dokumentHarUgyldigeDimensjoner, UNSUPPORTED_DIMENSIONS, errors);
		leggTilValideringsfeil(dokumentHarUgyldigVenstremarg, INSUFFICIENT_MARGIN_FOR_PRINT, errors);
		leggTilValideringsfeil(dokumentHarSiderSomIkkeKanParses, PDF_PARSE_PAGE_ERROR, errors);
		leggTilValideringsfeil(dokumentHarSiderHvisMarginIkkeLarSegVerifisereForPrint, UNABLE_TO_VERIFY_SUITABLE_MARGIN_FOR_PRINT, errors);

		return errors;

	}

	/**
	 * Leser hele dokumentet inn i minnet
	 */
	private List<PdfValidationError> validerDokumentForPrint(final PDDocument pdDoc, final PdfValidationSettings innstillinger)	throws IOException {
		List<PdfValidationError> errors = new ArrayList<>();

		if (pdDoc.isEncrypted()) {
			return failValidationIfEncrypted(errors);
		}

		if (innstillinger.validerSideantall) {
			validerSideantall(pdDoc.getNumberOfPages(), innstillinger.maksSideantall, errors);
		}

		if (innstillinger.validerPDFversjon) {
			validerPdfVersjon(pdDoc.getDocument().getVersion(), errors);
		}

		boolean dokumentHarUgyldigeDimensjoner = false;
		for (PDPage page : getAllPagesFrom(pdDoc)) {
			if (harUgyldigeDimensjoner(page)) {
				dokumentHarUgyldigeDimensjoner = true;
				break;
			}
		}

		leggTilValideringsfeil(dokumentHarUgyldigeDimensjoner, UNSUPPORTED_DIMENSIONS, errors);

		boolean harTekstIStrekkodeomraade = false;
		boolean dokumentHarSiderHvisMarginIkkeLarSegVerifisereForPrint = false;
		if (innstillinger.validerVenstremarg) {
			for (PDPage page : getAllPagesFrom(pdDoc)) {
				try {
					if (harTekstIStrekkodeomraade(page)) {
						harTekstIStrekkodeomraade = true;
						break;
					}
				} catch (NullPointerException npe) {
					dokumentHarSiderHvisMarginIkkeLarSegVerifisereForPrint = true;
					LOG.info("Klarte ikke å verifiserere margen på en side");
				}
			}
		}

		leggTilValideringsfeil(dokumentHarSiderHvisMarginIkkeLarSegVerifisereForPrint, UNABLE_TO_VERIFY_SUITABLE_MARGIN_FOR_PRINT, errors);
		leggTilValideringsfeil(harTekstIStrekkodeomraade, INSUFFICIENT_MARGIN_FOR_PRINT, errors);

		if (innstillinger.validerFonter) {
			for (PDPage page : getAllPagesFrom(pdDoc)) {
				validerFonter(fontValidator.getPageFonts(page), errors);
			}
		}

		return errors;
	}

	private void leggTilValideringsfeil(boolean dokumentHarSiderSomIkkeKanParses, PdfValidationError valideringsfeil,
			List<PdfValidationError> errors) {
		if (dokumentHarSiderSomIkkeKanParses) {
			errors.add(valideringsfeil);
		}
	}

	@SuppressWarnings("unchecked")
	private List<PDPage> getAllPagesFrom(final PDDocument pdDoc) {
		return pdDoc.getDocumentCatalog().getAllPages();
	}

	private List<PdfValidationError> failValidationIfEncrypted(List<PdfValidationError> errors) {
		errors.add(PdfValidationError.PDF_IS_ENCRYPTED);
		LOG.info("PDF-en er kryptert.");
		return errors;
	}

	private void validerFonter(final Iterable<PDFont> fonter, final List<PdfValidationError> errors) {
		List<PDFont> nonSupportedFonts = fontValidator.findNonSupportedFonts(fonter);
		if (!nonSupportedFonts.isEmpty()) {
			errors.add(PdfValidationError.REFERENCES_INVALID_FONT);
			if (LOG.isInfoEnabled()) {
				LOG.info("PDF-en har referanser til en ugyldige fonter: [{}]", join(describe(nonSupportedFonts), ", "));
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

	private void validerPdfVersjon(final float pdfVersion, final List<PdfValidationError> errors) {
		if (!PDF_VERSIONS_SUPPORTED_FOR_PRINT.contains(pdfVersion)) {
			errors.add(PdfValidationError.UNSUPPORTED_PDF_VERSION_FOR_PRINT);
			LOG.info("PDF-en har ikke en gylding versjon. Gyldige versjoner er {}. Faktisk versjon {}",
					StringUtils.join(PDF_VERSIONS_SUPPORTED_FOR_PRINT, ", "), pdfVersion);
		}
	}

	private void validerSideantall(final int numberOfPages, int maxPages, final List<PdfValidationError> errors) {
		if (numberOfPages > maxPages) {
			errors.add(PdfValidationError.TOO_MANY_PAGES_FOR_AUTOMATED_PRINT);
			LOG.info("PDF-en har for mange sider. Maksimum tillatt er {}. Faktisk antall er {}", maxPages, numberOfPages);
		}
		if (numberOfPages == 0) {
			errors.add(PdfValidationError.DOCUMENT_HAS_NO_PAGES);
			LOG.info("PDF-dokumentet inneholder ingen sider. Filen kan være korrupt.", numberOfPages);
		}
	}

	private boolean harTekstIStrekkodeomraade(final PDPage pdPage) throws IOException {
		SilentZone silentZone = new SilentZone(pdPage.findCropBox());
		
		Rectangle2D leftMarginBarcodeArea = new Rectangle2D.Double(silentZone.upperLeftCornerX,
				silentZone.upperLeftCornerY, silentZone.silentZoneXSize, silentZone.silentZoneYSize);

		return harTekstIOmraade(pdPage, leftMarginBarcodeArea);
	}

	private boolean harUgyldigeDimensjoner(final PDPage page) {
		PDRectangle findCropBox = page.findCropBox();
		long pageHeightInMillimeters = pointsTomm(findCropBox.getHeight());
		long pageWidthInMillimeters = pointsTomm(findCropBox.getWidth());
		if (!isPortraitA4(pageWidthInMillimeters, pageHeightInMillimeters) && !isLandscapeA4(pageWidthInMillimeters, pageHeightInMillimeters)) {
			LOG.info("En eller flere sider i PDF-en har ikke godkjente dimensjoner.  Godkjente dimensjoner er bredde {} mm og høyde {} mm, alt " +
					"bredde {} mm og høyde {} mm med {} mm slingringsmonn ned. "
					+ "Faktiske dimensjoner er bredde: {} mm og høyde: {} mm.",
					new Object[] { A4_WIDTH_MM, A4_HEIGHT_MM, A4_HEIGHT_MM, A4_WIDTH_MM, MM_VALIDATION_FLEXIBILITY,
							pageWidthInMillimeters, pageHeightInMillimeters });
			return true;
		} else {
			return false;
		}
	}

	private static boolean isPortraitA4(long pageWidthInMillimeters, long pageHeightInMillimeters){
		return pageWidthInMillimeters <= A4_WIDTH_MM && pageWidthInMillimeters >= A4_WIDTH_MM - MM_VALIDATION_FLEXIBILITY
				&& pageHeightInMillimeters <= A4_HEIGHT_MM && pageHeightInMillimeters >= A4_HEIGHT_MM - MM_VALIDATION_FLEXIBILITY;
	}

	private static boolean isLandscapeA4(long pageWidthInMillimeters, long pageHeightInMillimeters){
		return pageWidthInMillimeters == A4_HEIGHT_MM && pageHeightInMillimeters == A4_WIDTH_MM;
	}

	private boolean harTekstIOmraade(final PDPage pdPage, final Rectangle2D area) throws IOException {
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

		private SilentZone(PDRectangle findCropBox) {
			int pageHeightInMillimeters = (int)pointsTomm(findCropBox.getHeight());
			int pageWidthInMillimeters = (int)pointsTomm(findCropBox.getWidth());
			boolean isLandscape = isLandscapeA4(pageWidthInMillimeters, pageHeightInMillimeters);

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
