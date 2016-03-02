package no.digipost.print.validate;

import org.junit.Test;

import static no.digipost.print.validate.PdfValidationError.*;
import static no.digipost.print.validate.PdfValidationSettings.SJEKK_ALLE;
import static no.digipost.print.validate.PrintPdfValidatorTest.validationErrors;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class PrintPDFValidatorTestforVersion17 {

	@Test
	public void validatesPdfForPrint() {
		assertThat(validationErrors("/pdf/a4-left-margin-20mm_v17.pdf", SJEKK_ALLE), empty());
		assertThat(validationErrors("/pdf/a4-free-barcode-area_v17.pdf", SJEKK_ALLE), empty());
		assertThat(validationErrors("/pdf/a4-landscape-left-margin-20mm_v17.pdf", SJEKK_ALLE), empty());
	}

	@Test
	public void failsDueToMissingEmbeddedFont() {
		assertThat(validationErrors("/pdf/uten-embeddede-fonter_v17.pdf", SJEKK_ALLE), contains(REFERENCES_INVALID_FONT));
	}

	@Test
	public void doesNotFailDueToMissingEmbeddedFontIfCheckDisabledInSettings() {
		PdfValidationSettings innstillinger = new PdfValidationSettings(true, false, true, true);
		assertThat(validationErrors("/pdf/uten-embeddede-fonter_v17.pdf", innstillinger), empty());
	}

	@Test
	public void doesNotFailDueToWrongVersionIfCheckDisabledInSettings() {
		PdfValidationSettings innstillinger = new PdfValidationSettings(true, true, true, false);
		assertThat(validationErrors("/pdf/pdf-version-17.pdf", innstillinger), contains(UNSUPPORTED_DIMENSIONS));
	}

	@Test
	public void failsPdfWithInsufficientMarginForPrint() {
		assertThat(validationErrors("/pdf/a4-left-margin-17_5mm_v17.pdf", SJEKK_ALLE), contains(INSUFFICIENT_MARGIN_FOR_PRINT));
	}

	@Test
	public void doesNotFailPdfWithInsufficientMarginForPrintIfCheckDisabled() {
		PdfValidationSettings innstillinger = new PdfValidationSettings(false, true, true, true);
		assertThat(validationErrors("/pdf/a4-left-margin-19_5mm_v17.pdf", innstillinger), empty());
	}

	@Test
	public void failsPdfWithTooManyPagesForPrint() {
		assertThat(validationErrors("/pdf/a4-20pages_v17.pdf", SJEKK_ALLE), contains(TOO_MANY_PAGES_FOR_AUTOMATED_PRINT));
	}

	@Test
	public void doesNotFailPdfWithTooManyPagesForPrintIfCheckDisabled() {
		PdfValidationSettings innstillinger = new PdfValidationSettings(true, true, false, true);
		assertThat(validationErrors("/pdf/a4-20pages_v17.pdf", innstillinger), empty());
	}

	@Test
	public void failsCorruptPdfResultingInNoPages() {
		assertThat(validationErrors("/pdf/corrupt_no_pages.pdf", SJEKK_ALLE), contains(DOCUMENT_HAS_NO_PAGES));
	}

	@Test
	public void failCorruptPdf() {
		assertThat(validationErrors("/pdf/corrupt.pdf", SJEKK_ALLE), contains(PDF_PARSE_ERROR));
	}

	@Test
	public void failsPasswordProtectedPdf() {
		assertThat(validationErrors("/pdf/encrypted-with-password.pdf", SJEKK_ALLE), contains(PDF_IS_ENCRYPTED));
	}

	@Test
	public void failsPdfWithUnsupportedDimensionsForPrint() {
		assertThat(validationErrors("/pdf/letter-left-margin-20mm_v17.pdf", SJEKK_ALLE), contains(UNSUPPORTED_DIMENSIONS));
	}

	@Test
	public void failsPdfWithInsufficientMarginAndUnsupportedDimensionsForPrint() {
		assertThat(validationErrors("/pdf/a5-left-margin-15mm_v17.pdf", SJEKK_ALLE), containsInAnyOrder(INSUFFICIENT_MARGIN_FOR_PRINT, UNSUPPORTED_DIMENSIONS));
	}

	@Test
	public void pdfWithBogusFontsAndTooManyPages() {
		assertThat(validationErrors("/pdf/15-pages-and-bogus-fonts_v17.pdf", new PdfValidationSettings(true, false, false, true)), empty());
		assertThat(validationErrors("/pdf/15-pages-and-bogus-fonts_v17.pdf", new PdfValidationSettings(true, false, true, true)), contains(TOO_MANY_PAGES_FOR_AUTOMATED_PRINT));
		assertThat(validationErrors("/pdf/15-pages-and-bogus-fonts_v17.pdf", new PdfValidationSettings(true, true, false, true)), everyItem(is(REFERENCES_INVALID_FONT)));
	}
}
