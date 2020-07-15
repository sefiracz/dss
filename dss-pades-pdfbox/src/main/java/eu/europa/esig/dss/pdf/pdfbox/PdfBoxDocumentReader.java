/**
 * DSS - Digital Signature Services
 * Copyright (C) 2015 European Commission, provided under the CEF programme
 * 
 * This file is part of the "DSS - Digital Signature Services" project.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package eu.europa.esig.dss.pdf.pdfbox;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.pdf.PdfDict;
import eu.europa.esig.dss.pdf.PdfDocumentReader;
import eu.europa.esig.dss.pdf.PdfDssDict;
import eu.europa.esig.dss.pdf.PdfSigDictWrapper;
import eu.europa.esig.dss.pdf.SingleDssDict;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.ByteRange;
import eu.europa.esig.dss.validation.PdfSignatureDictionary;

public class PdfBoxDocumentReader implements PdfDocumentReader {

	private static final Logger LOG = LoggerFactory.getLogger(PdfBoxDocumentReader.class);
	
	private DSSDocument dssDocument;
	
	private final PDDocument pdDocument;
	
	/**
	 * Default constructor of the PDFBox implementation of the Reader
	 * 
	 * @param dssDocument {@link DSSDocument} to read
	 * @throws IOException if an exception occurs
	 * @throws eu.europa.esig.dss.pades.exception.InvalidPasswordException if the password is not provided or invalid for a protected document
	 */
	public PdfBoxDocumentReader(DSSDocument dssDocument) throws IOException, eu.europa.esig.dss.pades.exception.InvalidPasswordException {
		this(dssDocument, null);
	}
	
	/**
	 * The PDFBox implementation of the Reader
	 * 
	 * @param dssDocument {@link DSSDocument} to read
	 * @param passwordProtection {@link String} a password to open a protected document
	 * @throws IOException if an exception occurs
	 * @throws eu.europa.esig.dss.pades.exception.InvalidPasswordException if the password is not provided or invalid for a protected document
	 */
	public PdfBoxDocumentReader(DSSDocument dssDocument, String passwordProtection) throws IOException, eu.europa.esig.dss.pades.exception.InvalidPasswordException {
		Objects.requireNonNull(dssDocument, "The document must be defined!");
		this.dssDocument = dssDocument;
		try (InputStream is = dssDocument.openStream()) {
			this.pdDocument = PDDocument.load(is, passwordProtection);
		} catch (InvalidPasswordException e) {
            throw new eu.europa.esig.dss.pades.exception.InvalidPasswordException(e.getMessage());
		}
	}

	/**
	 * The PDFBox implementation of the Reader
	 * 
	 * @param binaries a byte array of a PDF to read
	 * @param passwordProtection {@link String} a password to open a protected document
	 * @throws IOException if an exception occurs
	 * @throws eu.europa.esig.dss.pades.exception.InvalidPasswordException if the password is not provided or invalid for a protected document
	 */
	public PdfBoxDocumentReader(byte[] binaries, String passwordProtection) throws IOException, eu.europa.esig.dss.pades.exception.InvalidPasswordException {
		Objects.requireNonNull(binaries, "The document binaries must be defined!");
		try {
			this.pdDocument = PDDocument.load(binaries, passwordProtection);
		} catch (InvalidPasswordException e) {
            throw new eu.europa.esig.dss.pades.exception.InvalidPasswordException(e.getMessage());
		}
	}

	@Override
	public PdfDssDict getDSSDictionary() {
		PdfDict catalog = new PdfBoxDict(pdDocument.getDocumentCatalog().getCOSObject(), pdDocument);
		return SingleDssDict.extract(catalog);
	}

	@Override
	public Map<PdfSignatureDictionary, List<String>> extractSigDictionaries() throws IOException {
		Map<PdfSignatureDictionary, List<String>> pdfDictionaries = new LinkedHashMap<>();
		Map<Long, PdfSignatureDictionary> pdfObjectDictMap = new LinkedHashMap<>();

		List<PDSignatureField> pdSignatureFields = pdDocument.getSignatureFields();
		if (Utils.isCollectionNotEmpty(pdSignatureFields)) {
			LOG.debug("{} signature(s) found", pdSignatureFields.size());

			for (PDSignatureField signatureField : pdSignatureFields) {

				String signatureFieldName = signatureField.getPartialName();

				COSObject sigDictObject = signatureField.getCOSObject().getCOSObject(COSName.V);
				if (sigDictObject == null || !(sigDictObject.getObject() instanceof COSDictionary)) {
					LOG.warn("Signature field with name '{}' does not contain a signature", signatureFieldName);
					continue;
				}

				long sigDictNumber = sigDictObject.getObjectNumber();
				PdfSignatureDictionary signature = pdfObjectDictMap.get(sigDictNumber);
				if (signature == null) {
					try {
						PdfDict dictionary = new PdfBoxDict((COSDictionary) sigDictObject.getObject(), pdDocument);
						signature = new PdfSigDictWrapper(dictionary);
					} catch (Exception e) {
						LOG.warn("Unable to create a PdfSignatureDictionary for field with name '{}'",
								signatureFieldName, e);
						continue;
					}

					List<String> fieldNames = new ArrayList<>();
					fieldNames.add(signatureFieldName);
					pdfDictionaries.put(signature, fieldNames);
					pdfObjectDictMap.put(sigDictNumber, signature);

				} else {
					List<String> fieldNameList = pdfDictionaries.get(signature);
					fieldNameList.add(signatureFieldName);
					LOG.warn("More than one field refers to the same signature dictionary: {}!", fieldNameList);

				}

			}
		}
		return pdfDictionaries;
	}

	@Override
	public boolean isSignatureCoversWholeDocument(PdfSignatureDictionary signatureDictionary) {
		ByteRange byteRange = signatureDictionary.getByteRange();
		try (InputStream is = dssDocument.openStream()) {
			long originalBytesLength = Utils.getInputStreamSize(is);
			// /ByteRange [0 575649 632483 10206]
			long beforeSignatureLength = (long)byteRange.getFirstPartEnd() - byteRange.getFirstPartStart();
			long expectedCMSLength = (long)byteRange.getSecondPartStart() - byteRange.getFirstPartEnd() - byteRange.getFirstPartStart();
			long afterSignatureLength = byteRange.getSecondPartEnd();
			long totalCoveredByByteRange = beforeSignatureLength + expectedCMSLength + afterSignatureLength;

			return (originalBytesLength == totalCoveredByByteRange);
		} catch (IOException e) {
			LOG.warn("Cannot determine the original file size for the document. Reason : {}", e.getMessage());
			return false;
		}
	}

	@Override
	public void close() throws IOException {
		pdDocument.close();
	}

}
