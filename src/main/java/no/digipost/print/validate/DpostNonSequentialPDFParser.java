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

import org.apache.pdfbox.pdfparser.NonSequentialPDFParser;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.persistence.util.COSObjectKey;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

class DpostNonSequentialPDFParser extends NonSequentialPDFParser implements AutoCloseable {

	static {
		// Ensures that the parser does not read the entire PDF to memory.
		System.setProperty(NonSequentialPDFParser.SYSPROP_PARSEMINIMAL, "true");
	}

	public DpostNonSequentialPDFParser(InputStream in) throws IOException {
		super(in);
		super.initialParse();
	}

	public int getNumberOfPages() throws IOException {
		return super.getPageNumber();
	}

	public boolean isEncrypted() {
		return super.getSecurityHandler() != null;
	}

	@Override
	public PDPage getPage(int pageNr) throws IOException {
		// Frigjør minne fortløpende
		if (pageNr % 5 == 0) {
			Set<COSObjectKey> cosObjectKeys = super.xrefTrailerResolver.getXrefTable().keySet();
			for (COSObjectKey cosObjectKey : cosObjectKeys) {
				super.getDocument().removeObject(cosObjectKey);
			}
		}
		return super.getPage(pageNr);
	}

	@Override
    public void close() {
		this.clearResources();
    }

}
