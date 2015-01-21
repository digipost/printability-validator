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

public class PrintValideringsinnstillinger {

	public final boolean validerVenstremarg;
	public final boolean validerFonter;
	public final boolean validerSideantall;
	public final boolean validerPDFversjon;

	public PrintValideringsinnstillinger(final boolean validerVenstremarg, final boolean validerFonter, final boolean validerSideantall,
			final boolean validerPDFversjon) {
		this.validerVenstremarg = validerVenstremarg;
		this.validerFonter = validerFonter;
		this.validerSideantall = validerSideantall;
		this.validerPDFversjon = validerPDFversjon;
	}

	public static final PrintValideringsinnstillinger SJEKK_ALLE = new PrintValideringsinnstillinger(true, true, true, true);

}
