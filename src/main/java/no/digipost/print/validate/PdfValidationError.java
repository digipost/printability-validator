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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum PdfValidationError {

	PDF_IS_ENCRYPTED("PDF-dokumentet er kryptert."),
	TOO_MANY_PAGES_FOR_AUTOMATED_PRINT("PDF-dokumentet inneholder for mange sider."),
	UNSUPPORTED_PDF_VERSION_FOR_PRINT("PDF-dokumentets versjon støttes ikke. Støttede versjoner er "
			+ StringUtils.join(PdfValidator.PDF_VERSIONS_SUPPORTED_FOR_PRINT, ", ") + "."),
	INSUFFICIENT_MARGIN_FOR_PRINT("PDF-dokumentet har for liten venstremarg. Minimum venstremarg er " + PdfValidator.BARCODE_AREA_WIDTH_MM
			+ " mm."),
	UNABLE_TO_VERIFY_SUITABLE_MARGIN_FOR_PRINT("Kunne ikke verifisere venstremargen for PDF-dokumentet. Minimum venstremarg er "
			+ PdfValidator.BARCODE_AREA_WIDTH_MM + " mm."),
	PDF_PARSE_ERROR("Kunne ikke parse PDF-dokumentet."),
	PDF_PARSE_PAGE_ERROR("Kunne ikke parse minst én av sidene i PDF-dokumentet"),
	UNSUPPORTED_DIMENSIONS("PDF-dokumentets dimensjoner støttes ikke. Støttede dimensjoner er A4 (" + PdfValidator.A4_WIDTH_MM + " mm x "
			+ PdfValidator.A4_HEIGHT_MM + " mm)"),
	REFERENCES_INVALID_FONT("Dokumentet refererer til en ikke-standard font som ikke er inkludert i PDF-en."),
	DOCUMENT_TOO_SMALL("PDF-dokumentets størrelse er for liten."),
	INVALID_PDF("PDF-dokumentet er ikke gyldig."),
	DOCUMENT_HAS_NO_PAGES("PDF-dokumentet inneholder ingen sider. Filen kan være korrupt.");

	static final Set<PdfValidationError> OK_FOR_PRINT = Collections.emptySet();
	static final Set<PdfValidationError> OK_FOR_WEB = EnumSet.of(
			PDF_IS_ENCRYPTED, TOO_MANY_PAGES_FOR_AUTOMATED_PRINT, UNSUPPORTED_PDF_VERSION_FOR_PRINT, INSUFFICIENT_MARGIN_FOR_PRINT,
			UNABLE_TO_VERIFY_SUITABLE_MARGIN_FOR_PRINT, UNSUPPORTED_DIMENSIONS, PDF_PARSE_PAGE_ERROR);


	public final String message;

	PdfValidationError(String message) {
		this.message = message;
	}

	public boolean isOkForWeb() {
		return OK_FOR_WEB.contains(this);
	}

	public boolean isOkForPrint() {
		return OK_FOR_PRINT.contains(this);
	}

	@Override
	public String toString() {
		return message;
	}

}
