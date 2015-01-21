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
import java.util.Collection;
import java.util.List;

import static no.digipost.print.validate.PdfValidateStrategy.FULLY_IN_MEMORY;
import static no.digipost.print.validate.PdfValidateStrategy.NON_SEQUENTIALLY;
import static no.digipost.print.validate.PdfValideringsFeil.*;


public class PrintPdfValidator {

	private static final Logger LOG = LoggerFactory.getLogger(PrintPdfValidator.class);

	private final PdfFontValidator fontValidator = new PdfFontValidator();

	// MM_TO_UNITS copied from org.apache.pdfbox.pdmodel.PDPage
	private static final double MM_TO_POINTS = 1 / (10 * 2.54f) * 72;

	public static final int A4_HEIGHT_MM = 297;
	public static final int A4_WIDTH_MM = 210;
	public static final int BARCODE_AREA_WIDTH_MM = 18;
	public static final int BARCODE_AREA_HEIGHT_MM = 70;
	public static final int BARCODE_AREA_X_POS_MM = 0;
	public static final int BARCODE_AREA_Y_POS_MM = 100;
	public static final int MAX_PAGES_FOR_AUTOMATED_PRINT = 12;
	public static final List<Float> PDF_VERSIONS_SUPPORTED_FOR_PRINT = Arrays.asList(1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f);


	public PdfValideringsResultat validerForPrint(byte[] content, PrintValideringsinnstillinger printValideringsinnstillinger) {
		return validerForPrint(new ByteArrayInputStream(content), printValideringsinnstillinger, PdfValidateStrategy.FULLY_IN_MEMORY);
	}

	public PdfValideringsResultat validerForPrint(File pdfFile, PrintValideringsinnstillinger printValideringsinnstillinger) throws IOException {
		InputStream pdfStream = openFileAsInputStream(pdfFile);
		return validerForPrint(pdfStream, printValideringsinnstillinger, PdfValidateStrategy.FULLY_IN_MEMORY);
	}

	/**
	 * @param pdfStream the input stream for reading the PDF. It will be closed before returning from
	 *                  this method
	 * @param parseNonSequentially avgjør om PDF-en leses inn i minnet eller ei
	 */
	private PdfValideringsResultat validerForPrint(InputStream pdfStream, PrintValideringsinnstillinger printValideringsinnstillinger, PdfValidateStrategy readStrategy) {
		int antallSider = -1;
		try {
			List<PdfValideringsFeil> errors = new ArrayList<>();
			try {
				if (readStrategy == NON_SEQUENTIALLY) {
					try (DpostNonSequentialPDFParser dpostNonSequentialPDFParser = new DpostNonSequentialPDFParser(pdfStream)){
						antallSider = dpostNonSequentialPDFParser.getNumberOfPages();
						errors = validerStreamForPrint(dpostNonSequentialPDFParser, printValideringsinnstillinger);
					}
				} else if (readStrategy == FULLY_IN_MEMORY) {
					try (PDDocument pdDoc = PDDocument.load(pdfStream)) {
						antallSider = pdDoc.getNumberOfPages();
						errors = validerDokumentForPrint(pdDoc, printValideringsinnstillinger);
					}
				}
			} catch (Exception e) {
				errors.add(PdfValideringsFeil.PDF_PARSE_ERROR);
				LOG.info("PDF-en kunne ikke parses. (" + e.getMessage() + ")");
				LOG.debug(e.getMessage(), e);
			}

			return new PdfValideringsResultat(errors, antallSider);
		} finally {
			IOUtils.closeQuietly(pdfStream);
		}
	}

	/**
	 * Leser ikke hele dokumentet inn i minnet
	 */
	private List<PdfValideringsFeil> validerStreamForPrint(DpostNonSequentialPDFParser dpostNonSequentialPDFParser,
			PrintValideringsinnstillinger innstillinger) throws IOException {

		List<PdfValideringsFeil> errors = new ArrayList<>();

		if (dpostNonSequentialPDFParser.isEncrypted()) {
			return failValidationIfEncrypted(errors);
		}

		if (innstillinger.validerSideantall) {
			validerSideantall(dpostNonSequentialPDFParser.getNumberOfPages(), errors);
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
	private List<PdfValideringsFeil> validerDokumentForPrint(final PDDocument pdDoc, final PrintValideringsinnstillinger innstillinger)
			throws IOException {
		List<PdfValideringsFeil> errors = new ArrayList<>();

		if (pdDoc.isEncrypted()) {
			return failValidationIfEncrypted(errors);
		}

		if (innstillinger.validerSideantall) {
			validerSideantall(pdDoc.getNumberOfPages(), errors);
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

	private void leggTilValideringsfeil(boolean dokumentHarSiderSomIkkeKanParses, PdfValideringsFeil valideringsfeil,
			List<PdfValideringsFeil> errors) {
		if (dokumentHarSiderSomIkkeKanParses) {
			errors.add(valideringsfeil);
		}
	}

	@SuppressWarnings("unchecked")
	private List<PDPage> getAllPagesFrom(final PDDocument pdDoc) {
		return pdDoc.getDocumentCatalog().getAllPages();
	}

	private List<PdfValideringsFeil> failValidationIfEncrypted(List<PdfValideringsFeil> errors) {
		errors.add(PdfValideringsFeil.PDF_IS_ENCRYPTED);
		LOG.info("PDF-en er kryptert.");
		return errors;
	}

	private void validerFonter(final Collection<PDFont> fonter, final List<PdfValideringsFeil> errors) {
		if (!fontValidator.erSupporterteFonter(fonter)) {
			errors.add(PdfValideringsFeil.REFERENCES_INVALID_FONT);
			LOG.info("PDF-en har en referanse til en ugyldig font");
		}

	}

	private void validerPdfVersjon(final float pdfVersion, final List<PdfValideringsFeil> errors) {
		if (!PDF_VERSIONS_SUPPORTED_FOR_PRINT.contains(pdfVersion)) {
			errors.add(PdfValideringsFeil.UNSUPPORTED_PDF_VERSION_FOR_PRINT);
			LOG.info("PDF-en har ikke en gylding versjon. Gyldige versjoner er {}. Faktisk versjon {}",
					StringUtils.join(PDF_VERSIONS_SUPPORTED_FOR_PRINT, ", "), pdfVersion);
		}
	}

	private void validerSideantall(final int numberOfPages, final List<PdfValideringsFeil> errors) {
		if (numberOfPages > MAX_PAGES_FOR_AUTOMATED_PRINT) {
			errors.add(PdfValideringsFeil.TOO_MANY_PAGES_FOR_AUTOMATED_PRINT);
			LOG.info("PDF-en har for mange sider. Maksimum tillatt er {}. Faktisk antall er {}", MAX_PAGES_FOR_AUTOMATED_PRINT, numberOfPages);
		}
		if (numberOfPages == 0) {
			errors.add(PdfValideringsFeil.DOCUMENT_HAS_NO_PAGES);
			LOG.info("PDF-dokumentet inneholder ingen sider. Filen kan være korrupt.", numberOfPages);
		}
	}

	private boolean harTekstIStrekkodeomraade(final PDPage pdPage) throws IOException {
		Rectangle2D leftMarginBarcodeArea = new Rectangle2D.Double(mmToPoints(BARCODE_AREA_X_POS_MM), mmToPoints(BARCODE_AREA_Y_POS_MM),
				mmToPoints(BARCODE_AREA_WIDTH_MM), mmToPoints(BARCODE_AREA_HEIGHT_MM));
		return harTekstIOmraade(pdPage, leftMarginBarcodeArea);
	}

	private boolean harUgyldigeDimensjoner(final PDPage page) {
		PDRectangle pageMediaBox = page.findMediaBox();
		long pageHeightInMillimeters = pointsTomm(pageMediaBox.getHeight());
		long pageWidthInMillimeters = pointsTomm(pageMediaBox.getWidth());
		if ((pageHeightInMillimeters != A4_HEIGHT_MM) || (pageWidthInMillimeters != A4_WIDTH_MM)) {
			LOG.info("En eller flere sider i PDF-en har ikke godkjente dimensjoner.  Godkjente dimensjoner er bredde {} mm og høyde {} mm. "
					+ "Faktiske dimensjoner er bredde {} mm og høyde: {} mm.", new Object[] { A4_WIDTH_MM, A4_HEIGHT_MM, pageWidthInMillimeters,
					pageHeightInMillimeters });
			return true;
		} else {
			return false;
		}
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

	private double mmToPoints(final int sizeInMillimeters) {
		BigDecimal points = new BigDecimal(sizeInMillimeters * MM_TO_POINTS);
		points = points.setScale(1, RoundingMode.DOWN);
		return points.doubleValue();
	}

	private long pointsTomm(final double sizeInPoints) {
		return Math.round(sizeInPoints / MM_TO_POINTS);
	}

}
