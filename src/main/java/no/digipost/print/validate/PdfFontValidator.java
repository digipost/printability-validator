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

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static org.apache.commons.lang3.StringUtils.deleteWhitespace;
import static org.apache.commons.lang3.StringUtils.remove;
import static org.apache.commons.lang3.StringUtils.upperCase;

class PdfFontValidator {

    // Standard Type 1 Fonts (Standard 14 Fonts) -
    // http://en.wikipedia.org/wiki/Portable_Document_Format#Fonts
    // Times (v3) (in regular, italic, bold, and bold italic)
    // Courier (final in regular, oblique, bold and bold oblique)
    // Helvetica (v3) (in regular, oblique, bold and bold oblique)
    // Symbol
    // Zapf Dingbats
    private static final Set<String> STANDARD_14_FONTS = unmodifiableSet(new HashSet<>(asList("TIMES", "COURIER", "HELVETICA", "SYMBOL", "ZAPFDINGBATS")));

    private static final Set<String> WHITE_LISTED_FONTS = unmodifiableSet(new HashSet<>(asList("ARIAL")));

    private static final Set<String> SUPPORTED_FONTS = concat(STANDARD_14_FONTS.stream(), WHITE_LISTED_FONTS.stream()).collect(collectingAndThen(toSet(), Collections::unmodifiableSet));

    public Collection<PDFont> getPageFonts(PDPage page) throws IOException {
        PDResources resources = page.getResources();
        if (resources != null) {
            Set<PDFont> fonts = new LinkedHashSet<>();
            for (COSName fontName : resources.getFontNames()) {
                fonts.add(resources.getFont(fontName));
            }
            return fonts;
        }
        return emptySet();
    }

    public List<PDFont> findNonSupportedFonts(Iterable<PDFont> fonter) {
        List<PDFont> nonSupported = new ArrayList<>();
        for (PDFont font : fonter) {
            if (font.isDamaged()) {
                nonSupported.add(font);
            } else {
                PDFontDescriptor fontDescriptor = font.getFontDescriptor();
                if (fontDescriptor != null) {
                    if (!erFontDescriptorAkseptabelForPrint(fontDescriptor)) {
                        nonSupported.add(font);
                    }
                } else {
                    if (!(font instanceof PDType0Font)) {
                        if (!erAkseptabelForPrint(font.getName())) {
                            nonSupported.add(font);
                        }
                    }
                }
            }
        }
        return unmodifiableList(nonSupported);
    }

    private boolean erFontDescriptorAkseptabelForPrint(PDFontDescriptor fontDescriptor) {
        if (harIkkeEmbeddedFont(fontDescriptor)) {
            return erAkseptabelForPrint(fontDescriptor.getFontName());
        } else {
            return true;
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

    private boolean harIkkeEmbeddedFont(PDFontDescriptor fontDescriptor) {
        return fontDescriptor.getFontFile() == null &&
                fontDescriptor.getFontFile2() == null &&
                fontDescriptor.getFontFile3() == null;
    }

}
