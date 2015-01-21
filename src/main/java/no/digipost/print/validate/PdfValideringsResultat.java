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

public class PdfValideringsResultat {

	private final List<PdfValideringsFeil> valideringsFeil;
	private Boolean okForPrint;
	private Boolean okForWeb;
	private String toStringValue;
	private final int antallSider;

	PdfValideringsResultat(final List<PdfValideringsFeil> errors, final int antallSider) {
		this.antallSider = antallSider;
		if (errors != null) {
			valideringsFeil = errors;
		} else {
			valideringsFeil = Collections.emptyList();
		}
	}

	public List<PdfValideringsFeil> getValideringsFeil() {
		return valideringsFeil;
	}

	public boolean isOkForPrint() {
		if (okForPrint == null) {
			okForPrint = PdfValideringsFeil.OK_FOR_PRINT.containsAll(valideringsFeil);
		}
		return okForPrint;
	}

	public boolean isOkForWeb() {
		if (okForWeb == null) {
			okForWeb = PdfValideringsFeil.OK_FOR_WEB.containsAll(valideringsFeil);
		}
		return okForWeb;
	}

	@Override
	public String toString() {
		if (toStringValue == null) {
			StringBuilder sb = new StringBuilder("[");
			sb.append(getClass().getSimpleName());
			for (PdfValideringsFeil printPdfValideringsFeil : valideringsFeil) {
				PdfValideringsFeil err = printPdfValideringsFeil;
				sb.append(" ");
				sb.append(err);
			}
			sb.append("]");
			toStringValue = sb.toString();
		}
		return toStringValue;
	}

	public int getAntallSider() {
		return antallSider;
	}

}
