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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static java.util.stream.Collectors.joining;

public enum PdfValidationError {

    PDF_IS_ENCRYPTED("The PDF document is encrypted."),
    TOO_MANY_PAGES_FOR_AUTOMATED_PRINT("The PDF document contains too many pages."),
    UNSUPPORTED_PDF_VERSION_FOR_PRINT("The version of the PDF document is not supported. Supported versions are "
            + PdfValidator.PDF_VERSIONS_SUPPORTED_FOR_PRINT.stream().map(String::valueOf).collect(joining(", ")) + "."),
    INSUFFICIENT_MARGIN_FOR_PRINT("The left margin of the PDF document is too narrow. Minimum left margin is " + PdfValidator.BARCODE_AREA_WIDTH_MM
            + " mm."),
    UNABLE_TO_VERIFY_SUITABLE_MARGIN_FOR_PRINT("Could not verify the left margin of the PDF document. Minimum left margin is "
            + PdfValidator.BARCODE_AREA_WIDTH_MM + " mm."),
    PDF_PARSE_ERROR("Could not parse the PDF document."),
    PDF_PARSE_PAGE_ERROR("Could not parse at least one of the pages in the PDF document"),
    UNSUPPORTED_DIMENSIONS("The dimensions of the PDF document are not supported. Supported dimensions are width between %s—%s mm and height between "
            + "%s—%s mm. If these limits should be changed, contact digipost support."),
    REFERENCES_INVALID_FONT("The document refers to a non-standard font that is not included in the PDF."),
    DOCUMENT_TOO_SMALL("The PDF document size is too small."),
    INVALID_PDF("The PDF document is invalid."),
    DOCUMENT_HAS_NO_PAGES("The PDF document does not contain any pages. The file may be corrupt.");

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
