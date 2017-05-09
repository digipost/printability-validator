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

import java.util.Collections;
import java.util.List;

import static java.util.Collections.unmodifiableList;
import static no.digipost.print.validate.PdfValidationSettings.*;
import static no.digipost.print.validate.PdfValidationSettings.DEFAULT_POSITIVE_BLEED_MM;

public final class PdfValidationResult {

    public static final PdfValidationResult EVERYTHING_OK = new PdfValidationResult(Collections.<PdfValidationError>emptyList(), -1, new Bleed(DEFAULT_POSITIVE_BLEED_MM, DEFAULT_NEGATIVE_BLEED_MM));

    public final List<PdfValidationError> errors;
    public final Bleed bleed;
    public final boolean okForPrint;
    public final boolean okForWeb;
    public final int pages;


    PdfValidationResult(List<PdfValidationError> errors, int pages, Bleed bleed) {
        this.pages = pages;
        this.errors = errors != null ? unmodifiableList(errors) : Collections.<PdfValidationError>emptyList();
        this.okForPrint = PdfValidationError.OK_FOR_PRINT.containsAll(this.errors);
        this.okForWeb = PdfValidationError.OK_FOR_WEB.containsAll(this.errors);
        this.bleed = bleed;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }



    private String toStringValue;

    @Override
    public String toString() {
        if (toStringValue == null) {
            StringBuilder sb = new StringBuilder("[");
            sb.append(getClass().getSimpleName());
            for (PdfValidationError printPdfValideringsFeil : errors) {
                final String err;
                if(printPdfValideringsFeil == PdfValidationError.UNSUPPORTED_DIMENSIONS) {
                    err = String.format(PdfValidationError.UNSUPPORTED_DIMENSIONS.toString(), PdfValidator.A4_WIDTH_MM - bleed.negativeBleedInMM,
                            PdfValidator.A4_WIDTH_MM + bleed.positiveBleedInMM, PdfValidator.A4_HEIGHT_MM - bleed.negativeBleedInMM, PdfValidator.A4_HEIGHT_MM + bleed.positiveBleedInMM);
                } else {
                    err = printPdfValideringsFeil.toString();
                }

                sb.append(" ");
                sb.append(err);
            }
            sb.append("]");
            toStringValue = sb.toString();
        }
        return toStringValue;
    }
}
