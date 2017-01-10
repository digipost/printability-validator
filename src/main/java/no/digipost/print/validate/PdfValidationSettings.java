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


public class PdfValidationSettings {

	public final boolean validateLeftMargin;
	public final boolean validateFonts;
	public final boolean validateNumberOfPages;
    public final int maxNumberOfPages;
	public final boolean validatePDFversion;
    public static final int STANDARD_MAX_PAGES_FOR_AUTOMATED_PRINT = 14;
	public static final int DEFAULT_BLEED_MM = 0;
	public final int bleed;

    public PdfValidationSettings(boolean validateLeftMargin, boolean validateFonts, boolean validateNumberOfPages, boolean validatePDFversion, int bleed) {
        this(validateLeftMargin, validateFonts, validateNumberOfPages, STANDARD_MAX_PAGES_FOR_AUTOMATED_PRINT, validatePDFversion, bleed);
    }

	public PdfValidationSettings(boolean validateLeftMargin, boolean validateFonts, boolean validateNumberOfPages, boolean validatePDFversion) {
		this(validateLeftMargin, validateFonts, validateNumberOfPages, STANDARD_MAX_PAGES_FOR_AUTOMATED_PRINT, validatePDFversion, DEFAULT_BLEED_MM);
	}

	public PdfValidationSettings(boolean validateLeftMargin, boolean validateFonts, boolean validateNumberOfPages, int maxNumberOfPages, boolean validatePDFversion, int bleed) {
		this.validateLeftMargin = validateLeftMargin;
		this.validateFonts = validateFonts;
		this.validateNumberOfPages = validateNumberOfPages;
        this.maxNumberOfPages = maxNumberOfPages;
		this.validatePDFversion = validatePDFversion;
		this.bleed = bleed;
	}

	public static final PdfValidationSettings CHECK_ALL = new PdfValidationSettings(true, true, true, true);

}
