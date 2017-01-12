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

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.*;

import java.io.IOException;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableList;
import static org.apache.commons.lang3.StringUtils.*;

class PdfFontValidator {

    // Standard Type 1 Fonts (Standard 14 Fonts) -
    // http://en.wikipedia.org/wiki/Portable_Document_Format#Fonts
    // Times (v3) (in regular, italic, bold, and bold italic)
    // Courier (final in regular, oblique, bold and bold oblique)
    // Helvetica (v3) (in regular, oblique, bold and bold oblique)
    // Symbol
    // Zapf Dingbats
    private static final Set<String> STANDARD_14_FONTS = new HashSet<>(asList("TIMES", "COURIER", "HELVETICA", "SYMBOL", "ZAPFDINGBATS"));

    private static final Set<String> WHITE_LISTED_FONTS = new HashSet<>(asList("ARIAL"));

    private static final Set<String> SUPPORTED_FONTS = new HashSet<>();

    static {
        SUPPORTED_FONTS.addAll(STANDARD_14_FONTS);
        SUPPORTED_FONTS.addAll(WHITE_LISTED_FONTS);
    }

    public Collection<PDFont> getPageFonts(PDPage page) throws IOException {
        PDResources resources = page.getResources();
        if (resources != null) {
            Map<String, PDFont> fontMap = resources.getFonts();
            return fontMap.values();
        }
        return emptySet();
    }

    public List<PDFont> findNonSupportedFonts(Iterable<PDFont> fonter) {
        List<PDFont> nonSupported = new ArrayList<>();
        for (PDFont font : fonter) {
            PDFontDescriptor fontDescriptor = font.getFontDescriptor();
            if (fontDescriptor != null) {
                if (!erFontDescriptorAkseptabelForPrint(fontDescriptor)) {
                    nonSupported.add(font);
                }
            } else {
                if (!(font instanceof PDType0Font)) {
                    if (!erAkseptabelForPrint(font.getBaseFont())) {
                        nonSupported.add(font);
                    }
                }
            }
        }
        return unmodifiableList(nonSupported);
    }

    private boolean erFontDescriptorAkseptabelForPrint(PDFontDescriptor fontDescriptor) {
        if (fontDescriptor instanceof PDFontDescriptorDictionary) {
            PDFontDescriptorDictionary pdFontDescriptorDictionary = (PDFontDescriptorDictionary) fontDescriptor;
            if (harIkkeEmbeddedFont(pdFontDescriptorDictionary)) {
                return erAkseptabelForPrint(pdFontDescriptorDictionary.getFontName());
            } else {
                return true;
            }
        } else if (fontDescriptor instanceof PDFontDescriptorAFM) {
            PDFontDescriptorAFM fontDescriptorAFM = (PDFontDescriptorAFM) fontDescriptor;
            return erAkseptabelForPrint(fontDescriptorAFM.getFontName());
        } else {
            throw new IllegalArgumentException("Ukjent font descriptor brukt : " + fontDescriptor.getClass());
        }
    }

    private boolean erAkseptabelForPrint(String fontnavn) {
        if (fontnavn == null) {
            return false;
        }
        String normalisertFontnavn = upperCase(deleteWhitespace(remove(fontnavn, "-")));
        for (String supportertFontnavn : SUPPORTED_FONTS) {
            if (normalisertFontnavn.contains(supportertFontnavn)) {
                return true;
            }
        }
        return false;
    }

    private boolean harIkkeEmbeddedFont(PDFontDescriptorDictionary fontDescriptor) {
        return fontDescriptor.getFontFile() == null &&
                fontDescriptor.getFontFile2() == null &&
                fontDescriptor.getFontFile3() == null;
    }

}
