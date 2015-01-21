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

import junit.framework.AssertionFailedError;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static no.digipost.print.validate.PdfValideringsFeil.*;
import static no.digipost.print.validate.PrintValideringsinnstillinger.SJEKK_ALLE;
import static org.apache.commons.lang3.Validate.notNull;

public class PrintPdfValidatorTest {

	private final PrintPdfValidator pdfValidator = new PrintPdfValidator();


	@Test
	public void shouldValidatePdfForPrint() throws Exception {
		assertHasExpectedValidationResult("/pdf/a4-left-margin-20mm.pdf", SJEKK_ALLE);
		assertHasExpectedValidationResult("/pdf/a4-free-barcode-area.pdf", SJEKK_ALLE);
	}

	@Test
	public void shouldFailDueToMissingEmbeddedFont() throws Exception {
		assertHasExpectedValidationResult("/pdf/uten-embeddede-fonter.pdf", SJEKK_ALLE, REFERENCES_INVALID_FONT);
	}

	@Test
	public void shouldNotFailDueToMissingEmbeddedFontIfCheckDisabledInSettings() throws Exception {
		PrintValideringsinnstillinger innstillinger = new PrintValideringsinnstillinger(true, false, true, true);
		assertHasExpectedValidationResult("/pdf/uten-embeddede-fonter.pdf", innstillinger);
	}

	@Test
	public void shouldFailDueToWrongVersion() throws Exception {
		assertHasExpectedValidationResult("/pdf/pdf-version-17.pdf", SJEKK_ALLE, UNSUPPORTED_PDF_VERSION_FOR_PRINT,
				UNSUPPORTED_DIMENSIONS);
	}

	@Test
	public void shouldNotFailDueToWrongVersionIfCheckDisabledInSettings() throws Exception {
		PrintValideringsinnstillinger innstillinger = new PrintValideringsinnstillinger(true, true, true, false);
		assertHasExpectedValidationResult("/pdf/pdf-version-17.pdf", innstillinger, UNSUPPORTED_DIMENSIONS);
	}

	@Test
	public void shouldFailValidationForPdfWithInsufficientMarginForPrint() throws Exception {
		assertHasExpectedValidationResult("/pdf/a4-left-margin-17_5mm.pdf", SJEKK_ALLE,	INSUFFICIENT_MARGIN_FOR_PRINT);
	}

	@Test
	public void shouldNotFailValidationForPdfWithInsufficientMarginForPrintIfCheckDisabled() throws Exception {
		PrintValideringsinnstillinger innstillinger = new PrintValideringsinnstillinger(false, true, true, true);
		assertHasExpectedValidationResult("/pdf/a4-left-margin-19_5mm.pdf", innstillinger);
	}

	@Test
	public void shouldFailValidationForPdfWithTooManyPagesForPrint() throws Exception {
		assertHasExpectedValidationResult("/pdf/a4-20pages.pdf", SJEKK_ALLE, TOO_MANY_PAGES_FOR_AUTOMATED_PRINT);
	}

	@Test
	public void shouldNotFailValidationForPdfWithTooManyPagesForPrintIfCheckDisabled() throws Exception {
		PrintValideringsinnstillinger innstillinger = new PrintValideringsinnstillinger(true, true, false, true);
		assertHasExpectedValidationResult("/pdf/a4-20pages.pdf", innstillinger);
	}

	@Test
	public void shouldFailValidationForCorruptPdfResultingInNoPages() throws Exception {
		assertHasExpectedValidationResult("/pdf/corrupt_no_pages.pdf", SJEKK_ALLE, DOCUMENT_HAS_NO_PAGES);
	}

	@Test
	public void shouldFailValidationForCorruptPdf() throws Exception {
		assertHasExpectedValidationResult("/pdf/corrupt.pdf", SJEKK_ALLE, PDF_PARSE_ERROR);
	}

	@Test
	public void shouldFailValidationForPasswordProtectedPdf() throws Exception {
		assertHasExpectedValidationResult("/pdf/encrypted-with-password.pdf", SJEKK_ALLE, PDF_IS_ENCRYPTED);
	}

	@Test
	public void shouldFailValidationForPdfWithUnsupportedDimensionsForPrint() throws Exception {
		assertHasExpectedValidationResult("/pdf/letter-left-margin-20mm.pdf", SJEKK_ALLE, UNSUPPORTED_DIMENSIONS);
		assertHasExpectedValidationResult("/pdf/a4-landscape-left-margin-20mm.pdf", SJEKK_ALLE,	UNSUPPORTED_DIMENSIONS);
	}

	@Test
	public void shouldFailValidationForPdfWithInsufficientMarginAndUnsupportedDimensionsForPrint() throws Exception {
		assertHasExpectedValidationResult("/pdf/a5-left-margin-15mm.pdf", SJEKK_ALLE, INSUFFICIENT_MARGIN_FOR_PRINT, UNSUPPORTED_DIMENSIONS);
	}


	private void assertHasExpectedValidationResult(String pdfResourceName,
	                                               PrintValideringsinnstillinger printValideringsinnstillinger, PdfValideringsFeil ... expectedErrors) throws IOException {
		File pdf = new File(notNull(getClass().getResource(pdfResourceName), pdfResourceName).getFile().replace("%20", " "));
		PdfValideringsResultat result = pdfValidator.validerForPrint(pdf, printValideringsinnstillinger);

		Set<PdfValideringsFeil> actualErrorSet = new HashSet<>(result.getValideringsFeil());
		Set<PdfValideringsFeil> expectedErrorSet = new HashSet<>(Arrays.asList(expectedErrors));
		if (!actualErrorSet.equals(expectedErrorSet)) {
			throw new AssertionFailedError("Expected errors " + expectedErrorSet + ", but got " + actualErrorSet);
		}
	}
}
